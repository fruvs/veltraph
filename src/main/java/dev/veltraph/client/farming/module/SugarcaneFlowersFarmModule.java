package dev.veltraph.client.farming.module;

import dev.veltraph.client.config.ConfigService;
import dev.veltraph.client.config.FarmingConfig;
import dev.veltraph.client.farming.domain.FarmLayoutProfile;
import dev.veltraph.client.farming.domain.FarmLayoutRegistry;
import dev.veltraph.client.farming.movement.Angles;
import dev.veltraph.client.world.BlockPos;
import dev.veltraph.client.world.Vec3;
import dev.veltraph.client.farming.movement.FarmAimController;
import dev.veltraph.client.farming.movement.TurnDirection;
import dev.veltraph.client.farming.movement.planner.SugarcanePlanner;
import dev.veltraph.client.farming.movement.planner.AceCaneFlowerPlanner;
import dev.veltraph.client.log.VeltraphLog;
import dev.veltraph.client.service.FarmServices;
import dev.veltraph.client.world.Direction;

import java.util.List;
import java.util.Random;

/**
 * Recovered cane traversal plus the explicitly selected Ace two-plot creator course.
 * The current stable 4.0.30 owner registers cane, rose bush and sunflower targets together.
 * Ace retains its fixed diagonal view and literal creator row/return chords; RECOVERED keeps
 * the original configured lateral/BACK behavior. Both protect planted cane bases and structure.
 * Flower targets require the matching lower half; server growth is observed, never fabricated.
 */
public final class SugarcaneFlowersFarmModule extends FarmModule {

    public static final String ID = "sugarcane_flowers";

    /** Creator's horizontal view. Zero pitch reaches upper cane/flower cells from the author lane. */
    private static final float CANE_PITCH = 0f;

    public enum Design { RECOVERED, ACE_TWO_PLOT }
    public enum Crop { CANE, SUNFLOWER, ROSE_BUSH }
    public record Selection(Design design,Crop crop) {
        public Selection { java.util.Objects.requireNonNull(design); java.util.Objects.requireNonNull(crop); }
    }
    private Design design = Design.RECOVERED;
    private Crop crop = Crop.CANE;
    private java.util.function.Supplier<Selection> selectionSupplier;
    private Selection preparedSelection;
    private final AceCaneFlowerPlanner ace = new AceCaneFlowerPlanner();
    private Vec3 acePrevious;
    private int aceStill;
    private boolean aceCarpet;
    private float aceYaw;
    private boolean aceReturnCorridorSeen;
    public void setDesign(Design value) {
        if(isEnabled()) throw new IllegalStateException("Stop farming before changing design");
        design=java.util.Objects.requireNonNull(value);
        preparedSelection=null;
    }
    public Design design() { refreshStoppedSelection(); return design; }
    public void setCrop(Crop value) {
        if(isEnabled()) throw new IllegalStateException("Stop farming before changing crop");
        crop=java.util.Objects.requireNonNull(value);
        preparedSelection=null;
    }
    public Crop crop() { refreshStoppedSelection(); return crop; }
    /** Effective engine identity for cycle markers: prepared start or active capture, otherwise stopped config. */
    public Selection capturedSelection() {
        if(isEnabled()) return new Selection(design,crop);
        if(preparedSelection!=null) return preparedSelection;
        refreshStoppedSelection();
        return new Selection(design,crop);
    }
    /** Factory-only optional config source. Explicit setter fixtures remain unbound. */
    public void bindSelection(java.util.function.Supplier<Selection> supplier) {
        if(isEnabled()) throw new IllegalStateException("Stop farming before binding selection");
        selectionSupplier=java.util.Objects.requireNonNull(supplier); preparedSelection=null;
        refreshStoppedSelection();
    }
    private void applySelection(Selection selection) { design=selection.design(); crop=selection.crop(); }
    private void refreshStoppedSelection() {
        if(!isEnabled() && selectionSupplier!=null)
            applySelection(java.util.Objects.requireNonNull(selectionSupplier.get(),"Farm selection supplier"));
    }
    @Override protected java.util.Optional<String> configurationReadinessError() {
        refreshStoppedSelection();
        if(design==Design.RECOVERED && crop!=Crop.CANE) {
            preparedSelection=null;
            return java.util.Optional.of("Flower harvesting requires the Ace two-plot design; recovered flower traversal is not yet verified.");
        }
        preparedSelection=selectionSupplier==null ? null : new Selection(design,crop);
        return java.util.Optional.empty();
    }
    public AceCaneFlowerPlanner.Phase acePhase() { return ace.phase(); }
    public int aceRows() { return ace.rows(); }
    public int aceLoops() { return ace.loops(); }

    private final FarmLayoutProfile profile;
    private final SugarcanePlanner planner;

    /** Macro-owned base heading, captured once at onEnable() — never the live player facing. */
    private Direction baseHeading = Direction.NORTH;
    private FarmAimController aim;

    // Last input decisions, surfaced in diagnostics().
    private boolean heldForward;
    private boolean heldBack;
    private Vec3 blockedAt;
    private BlockPos lastTurnAt;
    private boolean heldLeft;
    private boolean heldRight;
    private boolean heldBreak;
    private String blocker = "idle";

    public SugarcaneFlowersFarmModule(FarmServices services, ConfigService config, FarmLayoutRegistry registry) {
        super(ID, "Sugarcane/Flowers", services, config, registry);
        this.profile = registry.byId(FarmLayoutRegistry.SUGARCANE_FLOWERS).orElseThrow();
        this.planner = new SugarcanePlanner(config);
    }

    @Override
    protected List<String> toolKeywords() {
        if(preparedSelection==null) refreshStoppedSelection();
        Crop toolCrop=preparedSelection==null ? crop : preparedSelection.crop();
        return toolCrop==Crop.CANE ? List.of("hoe", "cane") : List.of("hoe", "flower", "sunflower", "rose");
    }

    @Override
    public FarmLayoutProfile activeProfile() {
        refreshStoppedSelection();
        return profile;
    }

    @Override
    public void onEnable() {
        if(selectionSupplier!=null) applySelection(preparedSelection==null
                ? java.util.Objects.requireNonNull(selectionSupplier.get(),"Farm selection supplier") : preparedSelection);
        preparedSelection=null;
        planner.reset();
        resetAce();
        blockedAt = null;
        lastTurnAt = null;
        this.aim = buildAim();
        // Macro-owned heading: snap the live yaw to a cardinal once and keep it. The diagonal aim is then
        // baseYaw +/- 45 relative to THIS heading (not the live, panning camera facing).
        this.baseHeading = Direction.fromYaw(Angles.snapToCardinal(services.rotation().yaw()));
        this.aceYaw = (float)(45+90*Math.round((services.rotation().yaw()-45)/90));
        if(design==Design.ACE_TWO_PLOT) inferAceStart();
        this.aim.setTarget(diagonalYaw(), CANE_PITCH);
        this.blocker = "starting";
        releaseInputDecisions();
        installFarmTargetGuard(this::allowedCaneTarget);
        VeltraphLog.details("Sugarcane.onEnable", "captured base heading=" + baseHeading + " ("
                + baseHeading.toYaw() + "deg) dir=" + planner.currentDir()
                + " diagonalYaw=" + String.format("%.1f", diagonalYaw()));
    }

    @Override
    protected void onResumeAfterRewarp() {
        installFarmTargetGuard(this::allowedCaneTarget);
        planner.reset();
        resetAce();
        blockedAt = null;
        lastTurnAt = null;
        releaseInputDecisions();
        if(design==Design.ACE_TWO_PLOT) inferAceStart();
    }

    @Override
    protected void onTickFarming() {
        if (aim == null) {
            aim = buildAim();
            aim.setTarget(diagonalYaw(), CANE_PITCH);
        }
        if(design==Design.ACE_TWO_PLOT) { tickAce(); return; }
        TurnDirection dir = planner.currentDir();
        VeltraphLog.verbose("Sugarcane.tick", () -> "dir=" + dir + " back=" + planner.isOnBackLeg()
                + " grounded=" + services.player().onGround());

        // Smoothly hold the recovered diagonal aim every tick (no one-shot snap).
        retargetAndAim();

        // Motion gate: only move while on the ground with the tool selected (mirrors S-shape gating).
        if (!services.player().onGround() || !toolReady()) {
            blocker = !services.player().onGround() ? "not on ground" : "tool not selected";
            blockedAt = null;
            pauseMotion();
            return;
        }

        if (aim.aimError(services.rotation().yaw(), services.rotation().pitch())
                > config.get(FarmingConfig.ROTATION_PAUSE_ERROR_DEG)) {
            blocker = "aligning diagonal aim";
            blockedAt = null;
            pauseMotion();
            return;
        }

        Clearance clearance = clearance(planner.currentDir());
        if (clearance == Clearance.UNKNOWN_OR_UNSUPPORTED) {
            blocker = "unloaded or unsupported lane";
            blockedAt = null;
            pauseMotion();
            return;
        }
        if (clearance == Clearance.BLOCKED) {
            Vec3 position = services.player().position();
            if (blockedAt != null && position.horizontalDistanceTo(blockedAt) < 0.1 && canTurnHere()) {
                planner.onLaneSwitch();
                lastTurnAt = services.player().blockPos();
                blockedAt = null;
                clearance = clearance(planner.currentDir());
            } else {
                blockedAt = position;
            }
        } else {
            blockedAt = null;
        }
        if (clearance == Clearance.UNKNOWN_OR_UNSUPPORTED) {
            blocker = "return lane unloaded or unsupported";
            pauseMotion();
            return;
        }
        blocker = clearance == Clearance.BLOCKED ? "waiting at row obstruction" : "farming " + planner.currentDir();
        // Keep the actual requested key held into a solid row end while confirming stillness.
        // The new leg uses BACK literally; adding forward would cancel or redirect the motion.
        // This recovered controller harvests cane, never planted bases or arbitrary end structures.
        boolean upperCane=services.breaking().targetBlockId().equals("minecraft:sugar_cane")
                && services.breaking().targetBlockPos().filter(p->services.world().isLoaded(p.down())
                        && services.world().isBlock(p.down(),"minecraft:sugar_cane")).isPresent();
        setBreaking(upperCane);
        setForward(false);
        setBack(planner.currentDir() == TurnDirection.BACK);
        setStrafe(planner.strafeSide());
    }

    private boolean canTurnHere() {
        BlockPos current = services.player().blockPos();
        return lastTurnAt == null || current.y() != lastTurnAt.y()
                || (current.x() != lastTurnAt.x() && current.z() != lastTurnAt.z());
    }

    private enum Clearance { CLEAR, BLOCKED, UNKNOWN_OR_UNSUPPORTED }

    /** Sample the swept feet/head and support footprint for the literal diagonal key direction. */
    private Clearance clearance(TurnDirection input) {
        double yaw = Math.toRadians(diagonalYaw());
        double dx = input == TurnDirection.BACK ? Math.sin(yaw)
                : input == TurnDirection.LEFT ? Math.cos(yaw) : -Math.cos(yaw);
        double dz = input == TurnDirection.BACK ? -Math.cos(yaw)
                : input == TurnDirection.LEFT ? Math.sin(yaw) : -Math.sin(yaw);
        Vec3 pos = services.player().position();
        boolean obstructed = false;
        for (double x : new double[]{-0.29, 0.29}) for (double z : new double[]{-0.29, 0.29}) {
            BlockPos foot = new Vec3(pos.x() + dx * 0.3 + x, pos.y(), pos.z() + dz * 0.3 + z).toBlockPos();
            if (!services.world().isLoaded(foot) || !services.world().isLoaded(foot.up())
                    || !services.world().isLoaded(foot.down()) || !services.world().isSolidFloor(foot.down()))
                return Clearance.UNKNOWN_OR_UNSUPPORTED;
            obstructed |= !services.world().isPassable(foot) || !services.world().isPassable(foot.up());
        }
        return obstructed ? Clearance.BLOCKED : Clearance.CLEAR;
    }

    private boolean allowedCaneTarget(BlockPos position) {
        if(!isEnabled() || !services.world().isLoaded(position) || !services.world().isLoaded(position.down())) return false;
        String id = crop==Crop.CANE ? "minecraft:sugar_cane" : crop==Crop.SUNFLOWER ? "minecraft:sunflower" : "minecraft:rose_bush";
        return services.world().isBlock(position,id) && services.world().isBlock(position.down(),id);
    }

    private void resetAce() { ace.reset(); acePrevious=null; aceStill=0; aceCarpet=false; aceReturnCorridorSeen=false; }

    /** The creator has no fixed start: read the existing local walls when enabling/rearming. */
    private void inferAceStart() {
        BlockPos feet=services.player().position().toBlockPos();Direction row=Direction.fromYaw(aceYaw+45);
        Direction progress=row.rotateYCounterclockwise();
        for(int y=0;y<=1;y++) {
            BlockPos p=feet.up(y);
            if(!services.world().isLoaded(p.offset(row,1)) || !services.world().isLoaded(p.offset(row.opposite(),1))
                    || !services.world().isLoaded(p.offset(progress,1)) || !services.world().isLoaded(p.offset(progress.opposite(),1)))return;
            if(services.world().isBlock(p.offset(row,1),"minecraft:white_stained_glass")
                    && services.world().isBlock(p.offset(row.opposite(),1),"minecraft:white_stained_glass")) {
                ace.reset(AceCaneFlowerPlanner.Phase.LOOP_CHORD);aceReturnCorridorSeen=true;return;
            }
            if(services.world().isBlock(p.offset(progress,1).down(),"minecraft:soul_sand")) {
                ace.reset(AceCaneFlowerPlanner.Phase.ROW_CHORD);return;
            }
            if(services.world().isBlock(p.offset(progress.opposite(),1),"minecraft:white_stained_glass")) {
                ace.reset(AceCaneFlowerPlanner.Phase.LOOP_EXIT);return;
            }
            if(services.world().isBlock(p.offset(progress,1),"minecraft:white_stained_glass")
                    && !services.world().isBlock(feet.down(),"minecraft:soul_sand")) {
                ace.reset(AceCaneFlowerPlanner.Phase.ROW_LEFT);return;
            }
        }
    }

    private void tickAce() {
        retargetAndAim();
        if(!toolReady() || aim.aimError(services.rotation().yaw(),services.rotation().pitch())
                >config.get(FarmingConfig.ROTATION_PAUSE_ERROR_DEG)) {
            blocker="aligning Ace view/tool"; acePrevious=null; aceStill=0; pauseMotion(); return;
        }
        Vec3 pos=services.player().position();
        BlockPos feet=pos.toBlockPos();
        // The author course includes ordinary water. Ground-only cane gating cannot traverse its loop.
        for(int y=-1;y<=2;y++) for(int x=-1;x<=1;x++) for(int z=-1;z<=1;z++) {
            if(!services.world().isLoaded(new BlockPos(feet.x()+x,feet.y()+y,feet.z()+z))) {
                blocker="Ace course unloaded"; acePrevious=null; aceStill=0; pauseMotion(); return;
            }
        }
        boolean carpet=nearMarker(feet,"minecraft:white_carpet");
        boolean trapdoor=nearMarker(feet,"minecraft:oak_trapdoor");
        if(acePrevious!=null && pos.horizontalDistanceTo(acePrevious)<.025) aceStill++; else aceStill=0;
        acePrevious=pos;
        var old=ace.phase();
        boolean returnEnd=false;
        if(old==AceCaneFlowerPlanner.Phase.LOOP_CHORD) {
            Direction row=Direction.fromYaw(aceYaw+45);
            boolean walls=services.world().isBlock(feet.offset(row,1),"minecraft:white_stained_glass")
                    && services.world().isBlock(feet.offset(row.opposite(),1),"minecraft:white_stained_glass");
            if(walls) aceReturnCorridorSeen=true;
            returnEnd=aceReturnCorridorSeen && !walls;
        }
        ace.observe(carpet && !aceCarpet,trapdoor,(aceStill>=4 && aceBodyBlocked(pos)) || returnEnd);
        aceCarpet=carpet;
        if(old!=ace.phase()) { aceStill=0; acePrevious=null;
            if(ace.phase()==AceCaneFlowerPlanner.Phase.LOOP_CHORD) aceReturnCorridorSeen=false;
        }
        var keys=ace.keys();
        setForward(keys.forward()); setBack(keys.back());
        services.input().setLeftHeld(keys.left()); heldLeft=keys.left();
        services.input().setRightHeld(keys.right()); heldRight=keys.right();
        services.input().setSprintHeld(keys.sprint());
        setBreaking(services.breaking().targetBlockPos().filter(this::allowedCaneTarget).isPresent());
        blocker="Ace "+ace.phase()+" rows="+ace.rows()+" loops="+ace.loops();
    }

    private boolean aceBodyBlocked(Vec3 pos) {
        var keys=ace.keys();double yaw=Math.toRadians(aceYaw);
        int forward=(keys.forward()?1:0)-(keys.back()?1:0);
        int lateral=(keys.left()?1:0)-(keys.right()?1:0);
        double dx=-Math.sin(yaw)*forward+Math.cos(yaw)*lateral;
        double dz=Math.cos(yaw)*forward+Math.sin(yaw)*lateral;
        double length=Math.hypot(dx,dz);
        return length>0 && !services.world().bodyClear(new Vec3(pos.x()+dx*.3/length,pos.y(),pos.z()+dz*.3/length),.3,1.8);
    }

    private boolean nearMarker(BlockPos feet,String id) {
        for(int y=-1;y<=1;y++) for(int x=-1;x<=1;x++) for(int z=-1;z<=1;z++)
            if(services.world().isBlock(new BlockPos(feet.x()+x,feet.y()+y,feet.z()+z),id)) return true;
        return false;
    }
    @Override protected void onDisableFarming() {
        preparedSelection=null;
        clearFarmTargetGuard();
        resetAce();
        super.onDisableFarming();
        releaseInputDecisions();
    }

    // ---- Aim (mirrors SShapeRowEngine.buildAim()/retargetAndAim()) ----

    private FarmAimController buildAim() {
        FarmAimController.Settings settings = new FarmAimController.Settings(
                config.get(FarmingConfig.ROTATION_MAX_YAW_DEG_PER_TICK).floatValue(),
                config.get(FarmingConfig.ROTATION_MAX_PITCH_DEG_PER_TICK).floatValue(),
                config.get(FarmingConfig.ROTATION_ACCELERATION),
                config.get(FarmingConfig.ROTATION_TOLERANCE_DEG).floatValue(),
                config.get(FarmingConfig.ROTATION_PAUSE_ERROR_DEG).floatValue(),
                design!=Design.ACE_TWO_PLOT && config.get(FarmingConfig.ROTATION_USE_MOUSE_GCD),
                config.get(FarmingConfig.ROTATION_MOUSE_SENSITIVITY),
                design!=Design.ACE_TWO_PLOT && config.get(FarmingConfig.ROTATION_JITTER),
                config.get(FarmingConfig.ROTATION_JITTER_MAX_DEG).floatValue(),
                config.get(FarmingConfig.ROTATION_JITTER_MIN_INTERVAL_TICKS),
                config.get(FarmingConfig.ROTATION_JITTER_MAX_INTERVAL_TICKS),
                config.get(FarmingConfig.ROTATION_JITTER_HOLD_TICKS));
        return design==Design.ACE_TWO_PLOT ? new FarmAimController(settings,new Random(),.001f) : new FarmAimController(settings, new Random());
    }

    /** Track the recovered diagonal aim continuously and step the smooth aim one tick (no snap). */
    private void retargetAndAim() {
        aim.retarget(diagonalYaw(), CANE_PITCH);
        float curYaw = services.rotation().yaw();
        float curPitch = services.rotation().pitch();
        float[] next = aim.step(curYaw, curPitch);
        services.rotation().aim(next[0], next[1]);
        VeltraphLog.details("Sugarcane.aim", () -> String.format(
                "target=%.1f/%.1f current=%.1f/%.1f err=%.1f", diagonalYaw(), CANE_PITCH,
                curYaw, curPitch, aim.aimError(curYaw, curPitch)));
    }

    /** Recovered diagonal aim: macro-owned base heading yaw + (LEFT ? +45 : -45). */
    private float diagonalYaw() {
        return design==Design.ACE_TWO_PLOT ? aceYaw : (float) planner.aimYaw(baseHeading.toYaw());
    }

    // ---- Inputs ----

    private void setForward(boolean held) {
        if (held != heldForward) {
            VeltraphLog.verbose("Sugarcane.input", "forward " + (held ? "HELD" : "released"));
        }
        heldForward = held;
        services.input().setForwardHeld(held);
    }

    private void setBack(boolean held) {
        heldBack = held;
        services.input().setBackHeld(held);
    }

    private void setStrafe(TurnDirection side) {
        boolean left = side == TurnDirection.LEFT;
        boolean right = side == TurnDirection.RIGHT;
        if (left != heldLeft || right != heldRight) {
            VeltraphLog.verbose("Sugarcane.input",
                    "strafe " + (left ? "LEFT" : right ? "RIGHT" : "none"));
        }
        heldLeft = left;
        heldRight = right;
        services.input().setLeftHeld(left);
        services.input().setRightHeld(right);
    }

    private void setBreaking(boolean breaking) {
        if (breaking != heldBreak) {
            VeltraphLog.details("Sugarcane.break", "breaking " + (breaking ? "ON" : "off"));
        }
        heldBreak = breaking;
        services.breaking().setBreaking(breaking);
    }

    private void pauseMotion() {
        setForward(false);
        setBack(false);
        setStrafe(null);
        setBreaking(false);
        services.input().setSprintHeld(false);
    }

    private void releaseInputDecisions() {
        heldForward = heldBack = heldLeft = heldRight = heldBreak = false;
    }

    @Override
    public String debugState() {
        if(design==Design.ACE_TWO_PLOT) return "Ace/"+crop+"/"+ace.phase();
        return "Sugarcane/Flowers/" + planner.currentDir() + (planner.isOnBackLeg() ? " (back)" : "");
    }

    @Override
    public String diagnostics() {
        if(design==Design.ACE_TWO_PLOT) return debugState()+" rows="+ace.rows()+" loops="+ace.loops()+" pos="+services.player().position()+" "+blocker;
        float curYaw = services.rotation().yaw();
        float curPitch = services.rotation().pitch();
        float err = aim == null ? 0f : aim.aimError(curYaw, curPitch);
        return String.format(
                "Sugarcane/Flowers dir=%s%s toolSlot=%d%n"
                + "  heading=%s base=%.0f diagonal=%.1f tgt=%.1f/%.1f cur=%.1f/%.1f err=%.1f%n"
                + "  keys[F=%b B=%b L=%b R=%b break=%b] strafe=%s clearance=%s blocker=%s",
                planner.currentDir(), planner.isOnBackLeg() ? " (back)" : "", chosenToolSlot(),
                baseHeading, baseHeading.toYaw(), diagonalYaw(),
                aim == null ? diagonalYaw() : aim.targetYaw(), aim == null ? CANE_PITCH : aim.targetPitch(),
                curYaw, curPitch, err,
                heldForward, heldBack, heldLeft, heldRight, heldBreak, planner.strafeSide(),
                clearance(planner.currentDir()), blocker);
    }

    public TurnDirection currentDir() {
        return planner.currentDir();
    }

    /** Passive exact maintenance checkpoint. No services, geometry, clocks or planner callbacks run. */
    public dev.veltraph.client.farming.module.FarmMaintenanceState maintenanceState() {
        return dev.veltraph.client.farming.module.FarmMaintenanceState.of(this,
                "design", design,
                "crop", crop,
                "selectionSupplier", selectionSupplier,
                "preparedSelection", preparedSelection,
                "ace", ace == null ? null : ace.maintenanceState(),
                "acePrevious", acePrevious,
                "aceStill", aceStill,
                "aceCarpet", aceCarpet,
                "aceYaw", aceYaw,
                "aceReturnCorridorSeen", aceReturnCorridorSeen,
                "profile", profile,
                "planner", planner == null ? null : planner.maintenanceState(),
                "baseHeading", baseHeading,
                "aim", aim == null ? null : aim.maintenanceState(),
                "heldForward", heldForward,
                "heldBack", heldBack,
                "blockedAt", blockedAt,
                "lastTurnAt", lastTurnAt,
                "heldLeft", heldLeft,
                "heldRight", heldRight,
                "heldBreak", heldBreak,
                "blocker", blocker);
    }
    @Override protected java.util.Optional<FarmMaintenanceState> maintenanceProgress(){
        return java.util.Optional.of(maintenanceState());
    }
}
