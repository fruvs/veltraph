package dev.veltraph.client.farming.module;

import dev.veltraph.client.config.ConfigService;
import dev.veltraph.client.config.FarmingConfig;
import dev.veltraph.client.config.MushroomFarmDesign;
import dev.veltraph.client.farming.movement.planner.MushroomRingPlanner;
import dev.veltraph.client.farming.movement.planner.MushroomRingLaneClassifier;
import dev.veltraph.client.farming.movement.planner.MushroomRingContinuation;
import dev.veltraph.client.farming.domain.FarmLayoutProfile;
import dev.veltraph.client.farming.domain.FarmLayoutRegistry;
import dev.veltraph.client.farming.domain.FarmMovementMode;
import dev.veltraph.client.farming.movement.Angles;
import dev.veltraph.client.farming.movement.FarmAimController;
import dev.veltraph.client.farming.movement.TurnDirection;
import dev.veltraph.client.farming.movement.planner.MushroomStaircasePlanner;
import dev.veltraph.client.log.VeltraphLog;
import dev.veltraph.client.service.FarmServices;
import dev.veltraph.client.service.WorldService;
import dev.veltraph.client.world.BlockPos;
import dev.veltraph.client.world.Direction;
import dev.veltraph.client.world.ProbeResult;
import dev.veltraph.client.world.Vec3;

import java.util.List;
import java.util.Random;

/**
 * Mushroom farming module with two modes:
 *
 * <ul>
 *   <li>{@link FarmMovementMode#CLASSICAL} — fixed-side row traversal using {@code MUSHROOM_DIR}.</li>
 *   <li>{@link FarmMovementMode#STAIRCASE} — wired on the recovered {@link MushroomStaircasePlanner}
 *       FOUND/BLOCKED/NULL probe selector + EXTREME/SLIGHT path-mode.</li>
 * </ul>
 *
 * <p>This module now mirrors the proven Wart/Crops {@code SShapeRowEngine} integration pattern: a
 * <b>macro-owned heading</b> is captured once at start (snapped from the live yaw) and used for all
 * geometry/probes; a {@link FarmAimController} (built from {@code ROTATION_*}) holds the crop aim
 * smoothly each tick (no one-tick snap); and motion is gated on {@code onGround()} + tool-ready +
 * standable-position checks.
 *
 * <p>Recovered staircase step motion (§5d {@code FarmingMovementPlannerHotspots.md} /
 * {@code FARMING_LAYOUT_PLANNER_DEEP_RECOVERY.md} §4): the depth-3 staircase probe selects a turn:
 * when it returns {@code MUSHROOM_DIR} the macro steps to that side <i>and</i> forward into the
 * standable continuation of the staircase (the side+forward "stair step"); when it returns
 * {@code FORWARD} the macro continues straight up/forward. EXTREME vs SLIGHT path-mode is derived from
 * the same down-and-forward walkability and recorded for diagnostics.
 *
 * <p>The separate {@link MushroomFarmDesign#RING_CURRENT} author adaptation holds an authored
 * diagonal view with literal W/S selected from locally verified trapdoor/non-trapdoor lane roles.
 * Local outgoing guide arms and actual wall contact resolve corners; bounded shape checks admit
 * native creator stairs and the clear lower-slab drop. Unknown or unsupported context pauses.
 * The four yaw variants represent orientation choices; layer-specific yaw changes are not inferred.
 *
 * <p>Red/brown mushrooms are drop variants, not separate traversals.
 */
public final class MushroomFarmModule extends FarmModule {

    public static final String ID = "mushroom";

    /** Recovered staircase probe depth (FarmingMovementPlannerHotspots.md §5d: depth 3). */
    private static final int STAIRCASE_PROBE_DEPTH = 3;

    /**
     * Anti-stuck gate (§5d: "anti-stuck gate speed {@code < 0.1} + elapsed {@code > l}"). The speed
     * threshold {@code 0.1} is CONFIRMED; the elapsed bound {@code l} is a DES-salted opaque long, so
     * exact bytes are unavailable. inferred: a 1500 ms "considered stuck" window — a plain readable
     * constant standing in for the salted bound, never a config key.
     */
    private static final double ANTI_STUCK_SPEED = 0.1;
    private static final long ANTI_STUCK_ELAPSED_MS = 1500L; // inferred: salted bound, see §5d

    private final FarmLayoutProfile classicalProfile;
    private final FarmLayoutProfile staircaseProfile;
    private final MushroomStaircasePlanner planner;
    private final dev.veltraph.client.farming.movement.planner.MushroomReferencePlanner referencePlanner=new dev.veltraph.client.farming.movement.planner.MushroomReferencePlanner();
    private boolean referenceAligned;
    private final MushroomRingPlanner ring = new MushroomRingPlanner();
    private final MushroomRingLaneClassifier ringLaneClassifier = new MushroomRingLaneClassifier();
    private final MushroomRingContinuation ringContinuation = new MushroomRingContinuation();
    private Direction ringTravel;
    private boolean ringStartForward;
    private MushroomFarmDesign design=MushroomFarmDesign.RECOVERED;
    private final FarmLayoutProfile ringProfile;

    public MushroomFarmDesign design() { return design; }
    public void setDesign(MushroomFarmDesign design) {
        this.design = java.util.Objects.requireNonNull(design);
        config.set(FarmingConfig.MUSHROOM_FARM_DESIGN,design);
    }
    public int ringTurns() { return ring.turns(); }

    private FarmMovementMode mode = FarmMovementMode.CLASSICAL;
    private TurnDirection lastSelection = TurnDirection.FORWARD;
    private MushroomStaircasePlanner.PathMode pathMode = MushroomStaircasePlanner.PathMode.EXTREME;

    // Macro-owned heading captured once at start (never the live player.facing()).
    private Direction heading = Direction.NORTH;
    private FarmAimController aim;

    // Anti-stuck bookkeeping.
    private long stillSinceMs = -1L;
    private String blocker = "none";

    // Last input decisions, surfaced in diagnostics().
    private boolean heldForward;
    private boolean heldLeft;
    private boolean heldRight;

    public MushroomFarmModule(FarmServices services, ConfigService config, FarmLayoutRegistry registry) {
        super(ID, "Mushroom", services, config, registry);
        this.classicalProfile = registry.byId(FarmLayoutRegistry.MUSHROOM_CLASSICAL).orElseThrow();
        this.staircaseProfile = registry.byId(FarmLayoutRegistry.MUSHROOM_STAIRCASE).orElseThrow();
        this.ringProfile = registry.byId(FarmLayoutRegistry.MUSHROOM_RING_CURRENT).orElseThrow();
        this.planner = new MushroomStaircasePlanner(config);
    }

    public FarmMovementMode mode() {
        return mode;
    }

    public void setMode(FarmMovementMode mode) {
        if (mode != FarmMovementMode.CLASSICAL && mode != FarmMovementMode.STAIRCASE) {
            throw new IllegalArgumentException("Mushroom supports CLASSICAL or STAIRCASE, got " + mode);
        }
        this.mode = mode;
    }

    @Override
    protected List<String> toolKeywords() {
        return List.of("hoe", "mushroom");
    }

    @Override
    public FarmLayoutProfile activeProfile() {
        return switch(config.get(FarmingConfig.MUSHROOM_FARM_DESIGN)){
            case RING_CURRENT->ringProfile;
            case FARMHELPER_FIXED_45->registry.byId(FarmLayoutRegistry.MUSHROOM_FIXED_45).orElseThrow();
            case FARMHELPER_ROTATE_30->registry.byId(FarmLayoutRegistry.MUSHROOM_ROTATE_30).orElseThrow();
            case FARMHELPER_SDS->registry.byId(FarmLayoutRegistry.MUSHROOM_SDS).orElseThrow();
            case RECOVERED->mode==FarmMovementMode.STAIRCASE?staircaseProfile:classicalProfile;
        };
    }

    @Override
    public void onEnable() {
        design=config.get(FarmingConfig.MUSHROOM_FARM_DESIGN);
        ringStartForward=config.get(FarmingConfig.MUSHROOM_RING_START_FORWARD);
        ring.start(services.rotation().yaw(),ringStartForward);
        ringTravel=null;
        // Capture the macro-owned heading once (mirror SShapeRowEngine.start()): snap the live yaw to
        // the nearest cardinal and freeze it; all geometry/probes use this, not player.facing().
        this.heading = Direction.fromYaw(Angles.snapToCardinal(services.rotation().yaw()));
        if(dev.veltraph.client.farming.movement.planner.MushroomReferencePlanner.supports(design))referencePlanner.start(design,(float)heading.toYaw(),services.rotation().yaw(),config.get(FarmingConfig.MUSHROOM_DIR));
        referenceAligned=false;
        this.aim = buildAim();
        this.aim.setTarget(headingYaw(), cropPitch());
        this.lastSelection = planner.mushroomDir();
        this.pathMode = MushroomStaircasePlanner.PathMode.EXTREME;
        this.stillSinceMs = -1L;
        this.blocker = "starting";
        installFarmTargetGuard(this::allowedMushroomTarget);
        VeltraphLog.details("MushroomFarmModule.start",
                "captured heading=" + heading + " (" + headingYaw() + "deg) mode=" + mode);
    }

    @Override
    protected void onTickFarming() {
        if(config.get(FarmingConfig.MUSHROOM_FARM_DESIGN)!=design){pauseMotion();blocker="mushroom profile changed; ordinary restart required";setEnabled(false);return;}
        if (design == MushroomFarmDesign.RING_CURRENT) { tickRing(); return; }
        if(dev.veltraph.client.farming.movement.planner.MushroomReferencePlanner.supports(design)){tickReference();return;}
        if (aim == null) {
            aim = buildAim();
        }
        // Smoothly hold the decoded signed 30-degree yaw offset and zero pitch (no snap).
        retargetAndAim();

        // Gate: only drive motion when grounded + tool ready (recovered standable/on-ground gating).
        boolean grounded = services.player().onGround();
        boolean tool = toolReady();
        if (!grounded || !tool) {
            blocker = !grounded ? "not on ground" : "tool not selected";
            pauseMotion();
            VeltraphLog.verbose("MushroomFarmModule.tick", () -> "gated: " + blocker);
            return;
        }

        if (aim.aimError(services.rotation().yaw(), services.rotation().pitch())
                > config.get(FarmingConfig.ROTATION_PAUSE_ERROR_DEG)) {
            blocker="aligning recovered mushroom aim";pauseMotion();return;
        }
        services.breaking().setBreaking(mushroomAtCrosshair());

        // Classify the path-mode from down-forward walkability along the heading (§5d, src 160).
        MushroomStaircasePlanner.PathMode newMode =
                planner.classifyPathMode(heading, this::downForwardWalkable);
        if (newMode != pathMode) {
            VeltraphLog.details("MushroomFarmModule.pathMode", pathMode + " -> " + newMode);
            pathMode = newMode;
        }

        if (!atRowEnd()) {
            // Mid-row: continue forward, breaking the mushroom in front. Reset the still timer.
            setForward(true);
            setStrafe(null);
            blocker = "none";
            updateAntiStuck();
            return;
        }

        // Row end: choose the turn. STAIRCASE keys on the depth-3 probe; CLASSICAL is fixed-side.
        TurnDirection target = (mode == FarmMovementMode.STAIRCASE)
                ? planner.selectDirection(heading, this::probeStep, true)
                : planner.mushroomDir();
        if (target != lastSelection) {
            // DETAILS: selection transition (the staircase decision the live test needs to see).
            VeltraphLog.details("MushroomFarmModule.select",
                    lastSelection + " -> " + target + " (" + pathMode + ", heading=" + heading + ")");
        }
        lastSelection = target;
        applyStep(target);
        updateAntiStuck();
    }

    /**
     * Recovered staircase step motion. When the selector returns {@code MUSHROOM_DIR}, step to that
     * side <i>and</i> forward into the standable continuation of the staircase (the side+forward stair
     * step), provided that continuation is standable; otherwise fall back to a plain side step. When the
     * selector returns {@code FORWARD}, continue straight forward/up the staircase.
     */
    private void applyStep(TurnDirection target) {
        if (target == TurnDirection.FORWARD) {
            setStrafe(null);
            setForward(true);
            blocker = "none";
            return;
        }
        // MUSHROOM_DIR side step: only commit the side+forward when the diagonal continuation is
        // standable (recovered standable-position check), so we step onto the next stair, not off it.
        if (sideForwardStandable(target)) {
            setStrafe(target);
            setForward(true);
            blocker = "none";
        } else {
            // Continuation not standable yet: side step only, hold off the forward commit.
            setStrafe(target);
            setForward(false);
            blocker = "side continuation not standable";
        }
    }


    @Override protected void onResumeAfterRewarp() {
        installFarmTargetGuard(this::allowedMushroomTarget);
        if (design == MushroomFarmDesign.RING_CURRENT) { ring.start(services.rotation().yaw(),ringStartForward); ringTravel=null;  pauseMotion(); }
    }

    /** Source variants remain separate from the author ring and recovered staircase selector. */
    private void tickReference(){
        if(aim==null)aim=buildAim();
        if(!services.player().onGround()||!toolReady()){pauseMotion();blocker="reference ground/tool gate";return;}
        var p=services.player().position();
        referencePlanner.observe(referenceClearance(p,heading.rotateYCounterclockwise()),
                referenceClearance(p,heading.rotateYClockwise()),referenceClearance(p,heading.opposite()));
        retargetAndAim();
        if(!referenceAligned){referenceAligned=aim.aimError(services.rotation().yaw(),services.rotation().pitch())<1;
            if(!referenceAligned){pauseMotion();blocker="initial reference alignment";return;}}
        var keys=referencePlanner.keys();float live=services.rotation().yaw();double a=Math.toRadians(live);
        double forward=(keys.forward()?1:0)-(keys.back()?1:0),left=(keys.left()?1:0)-(keys.right()?1:0);
        double dx=-Math.sin(a)*forward+Math.cos(a)*left,dz=Math.cos(a)*forward+Math.sin(a)*left;
        AxisClearance x=ringAxis(p,dx,0),z=ringAxis(p,0,dz);
        boolean moving=Math.abs(dx)+Math.abs(dz)>.001&&x!=AxisClearance.UNKNOWN&&z!=AxisClearance.UNKNOWN
                &&x!=AxisClearance.EDGE&&z!=AxisClearance.EDGE
                &&(Math.abs(dx)>.001&&x==AxisClearance.CLEAR||Math.abs(dz)>.001&&z==AxisClearance.CLEAR);
        setForward(moving&&keys.forward());services.input().setBackHeld(moving&&keys.back());
        setStrafe(moving?(keys.left()?TurnDirection.LEFT:keys.right()?TurnDirection.RIGHT:null):null);
        services.input().setSprintHeld(false);
        var fresh=services.breaking().freshTargetBlockPos().filter(this::allowedMushroomTarget);
        boolean target=services.breaking().targetBlockPos().filter(this::allowedMushroomTarget).filter(t->fresh.filter(t::equals).isPresent()).isPresent();
        services.breaking().setBreaking(target);
        blocker="reference "+referencePlanner.state()+(moving?"":" native collision/support refusal");
    }
    private dev.veltraph.client.farming.movement.planner.MushroomReferencePlanner.Clearance referenceClearance(Vec3 p,Direction direction){
        var c=ringAxis(p,direction.dx(),direction.dz(),1);
        return switch(c){case CLEAR->dev.veltraph.client.farming.movement.planner.MushroomReferencePlanner.Clearance.CLEAR;
            case BLOCKED->dev.veltraph.client.farming.movement.planner.MushroomReferencePlanner.Clearance.BLOCKED;
            case EDGE,UNKNOWN->dev.veltraph.client.farming.movement.planner.MushroomReferencePlanner.Clearance.UNKNOWN;};
    }

    /** The author ring keeps one captured diagonal view; no route/corner camera turns are inferred. */
    private void tickRing() {
        if(aim==null){aim=buildAim();aim.setTarget(ring.yaw(),0);}
        retargetAndAim();
        if(!services.player().onGround()||!toolReady()
                ||aim.aimError(services.rotation().yaw(),services.rotation().pitch())>2) {
            blocker="ring ground/tool/aim gate";ring.pause();pauseMotion();return;
        }
        Vec3 position=services.player().position();
        var lane=ringLaneClassifier.classify(services.world(),position);
        MushroomRingContinuation.Decision continuation=null;
        boolean approachContact=false;
        if(lane==MushroomRingLaneClassifier.Lane.AMBIGUOUS) {
            continuation=ringContinuation.find(services.world(),position,ring.yaw(),ringTravel);
            if(continuation!=null){
                boolean nextForward=continuation.lane()==MushroomRingLaneClassifier.Lane.NON_TRAPDOOR;
                if(ringTravel!=null&&nextForward!=ring.forward()
                        &&(continuation.phase()==MushroomRingContinuation.Phase.CORNER||continuation.phase()==MushroomRingContinuation.Phase.STAIR)) {
                    AxisClearance near=ringAxis(position,ringTravel.dx(),ringTravel.dz());
                    // Reach the actual bounding plane before changing W/S. A predicted wall may be
                    // less than a full tick away; normal vanilla collision resolves that last distance.
                    approachContact=near!=AxisClearance.EDGE&&near!=AxisClearance.UNKNOWN
                            &&ringAxis(position,ringTravel.dx(),ringTravel.dz(),.001)==AxisClearance.CLEAR;
                }
                if(approachContact)lane=ring.forward()?MushroomRingLaneClassifier.Lane.NON_TRAPDOOR:MushroomRingLaneClassifier.Lane.TRAPDOOR;
                else {lane=continuation.lane();ringTravel=continuation.travel();}
            }
        }
        if(!ring.selectLane(lane)) {
            blocker="ring lane "+lane;pauseMotion();return;
        }
        if(continuation==null) {
            Direction axis=ringLaneClassifier.axis(services.world(),position,lane);
            if(axis!=null)ringTravel=MushroomRingContinuation.projection(ring.yaw(),ring.forward(),axis)>0?axis:axis.opposite();
        }
        var cropFacing=continuation!=null?MushroomRingLaneClassifier.CropFacing.READY:
                ringLaneClassifier.cropFacing(services.world(),position,ring.yaw());
        if(cropFacing!=MushroomRingLaneClassifier.CropFacing.READY) {
            blocker="ring crop facing "+cropFacing;pauseMotion();return;
        }
        double a=Math.toRadians(ring.yaw()),sign=ring.forward()?1:-1;
        AxisClearance x=ringAxis(position,-Math.sin(a)*sign,0),z=ringAxis(position,0,Math.cos(a)*sign);
        if(approachContact) {
            if(ringTravel.dx()!=0&&x==AxisClearance.BLOCKED)x=AxisClearance.CLEAR;
            if(ringTravel.dz()!=0&&z==AxisClearance.BLOCKED)z=AxisClearance.CLEAR;
        }
        boolean stair=!approachContact&&continuation!=null&&continuation.phase()==MushroomRingContinuation.Phase.STAIR;
        boolean drop=continuation!=null&&continuation.phase()==MushroomRingContinuation.Phase.DROP;
        if(stair) {
            if(ringTravel.dx()!=0&&x==AxisClearance.BLOCKED&&ringStep(position,Math.signum(-Math.sin(a)*sign)*.12,0))x=AxisClearance.CLEAR;
            if(ringTravel.dz()!=0&&z==AxisClearance.BLOCKED&&ringStep(position,0,Math.signum(Math.cos(a)*sign)*.12))z=AxisClearance.CLEAR;
        }
        if(drop) {
            if(ringTravel.dx()!=0&&x==AxisClearance.EDGE)x=AxisClearance.CLEAR;
            if(ringTravel.dz()!=0&&z==AxisClearance.EDGE)z=AxisClearance.CLEAR;
        }
        if(x==AxisClearance.UNKNOWN||z==AxisClearance.UNKNOWN||(x==AxisClearance.EDGE&&z!=AxisClearance.BLOCKED)
                ||(z==AxisClearance.EDGE&&x!=AxisClearance.BLOCKED)) {
            blocker="unloaded or unsupported ring direction";ring.pause();pauseMotion();return;
        }
        if(x!=AxisClearance.CLEAR&&z!=AxisClearance.CLEAR) {
            blocker="blocked authored ring lane";pauseMotion();return;
        }
        setStrafe(null);setForward(ring.forward());services.input().setBackHeld(!ring.forward());
        // The renderer may still point at its just-predicted AIR cell. A fresh native pick can
        // retain the held attack for the next real cached mushroom without changing movement/view.
        // Vanilla still supplies the actual interaction coordinate to the same crop/tool guard.
        boolean crop=mushroomAtCrosshair()
                ||services.breaking().freshTargetBlockPos().filter(this::allowedMushroomTarget).isPresent();
        services.input().setSprintHeld(false);services.breaking().setBreaking(crop);
        blocker="author W/S "+(ring.forward()?"W":"S")+(approachContact?" CONTACT":continuation==null?"":" "+continuation.phase());
    }
    private boolean ringStep(Vec3 feet,double dx,double dz) {
        Vec3 upper=new Vec3(feet.x()+dx,feet.y()+.5,feet.z()+dz);
        return services.world().bodyClear(upper,.3,1.8)&&services.world().hasGroundSupport(upper,.3);
    }
    private enum AxisClearance {CLEAR,BLOCKED,EDGE,UNKNOWN}
    private AxisClearance ringAxis(Vec3 position,double dx,double dz) {
        return ringAxis(position,dx,dz,.12);
    }
    private AxisClearance ringAxis(Vec3 position,double dx,double dz,double step) {
        var next=new Vec3(position.x()+Math.signum(dx)*step,position.y(),position.z()+Math.signum(dz)*step);
        WorldService world=services.world();
        for(double x:new double[]{-.3,.3})for(double z:new double[]{-.3,.3}) {
            if(!world.isLoaded(new Vec3(next.x()+x,next.y()-.025,next.z()+z).toBlockPos())
                    ||!world.isLoaded(new Vec3(next.x()+x,next.y()+1.8,next.z()+z).toBlockPos()))return AxisClearance.UNKNOWN;
        }
        if(!world.bodyClear(next,.3,1.8))return AxisClearance.BLOCKED;
        return world.hasGroundSupport(next,.3)?AxisClearance.CLEAR:AxisClearance.EDGE;
    }
    private boolean allowedMushroomTarget(BlockPos position) {
        WorldService world=services.world();
        return isEnabled() && world.isLoaded(position)
                && (world.isBlock(position,"minecraft:brown_mushroom")||world.isBlock(position,"minecraft:red_mushroom"))
                &&(design!=MushroomFarmDesign.RING_CURRENT||world.isLoaded(position.down())&&world.isBlock(position.down(),"minecraft:mycelium"));
    }
    private boolean mushroomAtCrosshair() {
        String crop=services.breaking().targetBlockId();
        return crop.equals("minecraft:brown_mushroom")||crop.equals("minecraft:red_mushroom");
    }
    // ---- World probes ----

    /**
     * Classify a staircase probe in {@code dir} as FOUND / BLOCKED / NONE, scanning up to the
     * recovered depth 3. A standable continuation (level or one step up/down) within reach is FOUND; a
     * body+head wall is BLOCKED; otherwise NONE.
     */
    private ProbeResult probeStep(Direction dir) {
        WorldService world = services.world();
        BlockPos base = services.player().blockPos();
        for (int i = 1; i <= STAIRCASE_PROBE_DEPTH; i++) {
            BlockPos ahead = base.offset(dir, i);
            if (!world.isPassable(ahead) || !world.isPassable(ahead.up())) {
                return ProbeResult.BLOCKED;
            }
            if (world.isStandable(ahead.down()) || world.isStandable(ahead)) {
                return ProbeResult.FOUND;
            }
        }
        return ProbeResult.NONE;
    }

    /**
     * Down-and-forward walkability along {@code dir} for the EXTREME/SLIGHT path-mode (§5d, src 160):
     * {@code base = playerPos.down().offset(dir)} is walkable when it has a standable surface (a real
     * stair step continues downward/forward).
     */
    private boolean downForwardWalkable(Direction dir) {
        BlockPos base = services.player().blockPos().down().offset(dir);
        return services.world().isStandable(base);
    }

    /**
     * Row-end against the <b>macro-owned heading</b> (never {@code player.facing()}): we are at a row
     * end when the next block along the heading is not a valid step (head/body blocked, or no standable
     * floor level or one step up/down). Keeping this local — rather than the generic
     * {@code WorldRowGeometry}, which keys off the live facing — keeps the row-end check consistent with
     * the captured heading and the staircase probes.
     */
    private boolean atRowEnd() {
        return !forwardStep(heading);
    }

    /** A standable, head/body-clear step exists one block along {@code dir} (level or one step down). */
    private boolean forwardStep(Direction dir) {
        WorldService world = services.world();
        BlockPos body = services.player().blockPos().offset(dir);
        if (!world.isLoaded(body) || !world.isPassable(body) || !world.isPassable(body.up())) {
            return false;
        }
        // Floor level (body.down()) or one step down (body) — same standable semantics as probeStep.
        return world.isStandable(body.down()) || world.isStandable(body);
    }

    /**
     * Whether the side+forward diagonal continuation of the staircase is standable: the block one to
     * the side and one forward of the heading must have a standable floor (level or one step up/down),
     * mirroring the recovered standable-position check before committing the stair step.
     */
    private boolean sideForwardStandable(TurnDirection side) {
        Direction lateral = side == TurnDirection.RIGHT
                ? heading.rotateYClockwise()
                : heading.rotateYCounterclockwise();
        WorldService world = services.world();
        BlockPos diag = services.player().blockPos().offset(lateral).offset(heading);
        return world.isStandable(diag.down()) || world.isStandable(diag) || world.isStandable(diag.up());
    }

    private void updateAntiStuck() {
        // §5d anti-stuck: speed < 0.1 for longer than the (salted) elapsed bound -> re-probe direction.
        long now = services.clock().nowMillis();
        if (services.player().horizontalSpeed() < ANTI_STUCK_SPEED) {
            if (stillSinceMs < 0) {
                stillSinceMs = now;
            } else if (now - stillSinceMs > ANTI_STUCK_ELAPSED_MS && mode == FarmMovementMode.STAIRCASE) {
                // Stuck: re-run the selector against the opposite heading meaning by flipping the turn
                // side this tick so a wedged staircase step is reconsidered next tick.
                blocker = "anti-stuck reprobe";
                lastSelection = lastSelection == TurnDirection.FORWARD
                        ? planner.mushroomDir()
                        : TurnDirection.FORWARD;
                VeltraphLog.details("MushroomFarmModule.antiStuck",
                        "still > " + ANTI_STUCK_ELAPSED_MS + "ms -> reconsider, next=" + lastSelection);
                stillSinceMs = now;
            }
        } else {
            stillSinceMs = -1L;
        }
    }

    // ---- Aim (mirror SShapeRowEngine) ----

    private FarmAimController buildAim() {
        FarmAimController.Settings settings = new FarmAimController.Settings(
                config.get(FarmingConfig.ROTATION_MAX_YAW_DEG_PER_TICK).floatValue(),
                config.get(FarmingConfig.ROTATION_MAX_PITCH_DEG_PER_TICK).floatValue(),
                config.get(FarmingConfig.ROTATION_ACCELERATION),
                config.get(FarmingConfig.ROTATION_TOLERANCE_DEG).floatValue(),
                config.get(FarmingConfig.ROTATION_PAUSE_ERROR_DEG).floatValue(),
                config.get(FarmingConfig.ROTATION_USE_MOUSE_GCD),
                config.get(FarmingConfig.ROTATION_MOUSE_SENSITIVITY),
                // The creator ring prescribes one fixed diagonal view; general optional pulses
                // cannot change its yaw/pitch midway through a complete lap.
                design == MushroomFarmDesign.RECOVERED && config.get(FarmingConfig.ROTATION_JITTER),
                config.get(FarmingConfig.ROTATION_JITTER_MAX_DEG).floatValue(),
                config.get(FarmingConfig.ROTATION_JITTER_MIN_INTERVAL_TICKS),
                config.get(FarmingConfig.ROTATION_JITTER_MAX_INTERVAL_TICKS),
                config.get(FarmingConfig.ROTATION_JITTER_HOLD_TICKS));
        return new FarmAimController(settings, new Random());
    }

    private void retargetAndAim() {
        aim.retarget(headingYaw(), cropPitch());
        float curYaw = services.rotation().yaw();
        float curPitch = services.rotation().pitch();
        float[] next = aim.step(curYaw, curPitch);
        services.rotation().aim(next[0], next[1]);
        VeltraphLog.verbose("MushroomFarmModule.aim", () -> String.format(
                "target=%.1f/%.1f current=%.1f/%.1f err=%.1f", headingYaw(), cropPitch(),
                curYaw, curPitch, aim.aimError(curYaw, curPitch)));
    }

    private float headingYaw() {
        if(dev.veltraph.client.farming.movement.planner.MushroomReferencePlanner.supports(design))return referencePlanner.yaw();
        return design == MushroomFarmDesign.RING_CURRENT ? ring.yaw()
                : (float) Angles.wrapDegrees((float) heading.toYaw() + (planner.mushroomDir()==TurnDirection.LEFT ? 30f : -30f));
    }

    /** Decoded exact aim mode is pitch zero; humanized original range was -2.5..1.5. */
    private float cropPitch() { return dev.veltraph.client.farming.movement.planner.MushroomReferencePlanner.supports(design)?referencePlanner.pitch():0; }

    // ---- Inputs ----

    private void setForward(boolean held) {
        if (held != heldForward) {
            VeltraphLog.verbose("MushroomFarmModule.input", "forward " + (held ? "HELD" : "released"));
        }
        heldForward = held;
        services.input().setForwardHeld(held);
    }

    private void setStrafe(TurnDirection side) {
        boolean left = side == TurnDirection.LEFT;
        boolean right = side == TurnDirection.RIGHT;
        if (left != heldLeft || right != heldRight) {
            VeltraphLog.verbose("MushroomFarmModule.input",
                    "strafe " + (left ? "LEFT" : right ? "RIGHT" : "none"));
        }
        heldLeft = left;
        heldRight = right;
        services.input().setLeftHeld(left);
        services.input().setRightHeld(right);
    }

    private void pauseMotion() {
        setForward(false);
        services.input().setBackHeld(false);
        setStrafe(null);
        services.breaking().setBreaking(false);
    }

    @Override
    protected void onDisableFarming() {
        clearFarmTargetGuard();
        heldForward = heldLeft = heldRight = false;
        super.onDisableFarming();
    }

    @Override
    public String debugState() {
        if(design==MushroomFarmDesign.RING_CURRENT)return "Mushroom/Ring/"+(ring.forward()?"W":"S")+"/turns="+ring.turns();
        return "Mushroom/" + design + "/" + mode + "/" + lastSelection + "/" + pathMode;
    }

    /** Live diagnostics for {@code /vdebug macro} (mirror WartCropsFarmModule.diagnostics()). */
    @Override
    public String diagnostics() {
        if(dev.veltraph.client.farming.movement.planner.MushroomReferencePlanner.supports(design))return "Mushroom/"+design+"/"+referencePlanner.state()+" aim="+referencePlanner.yaw()+"/"+referencePlanner.pitch()+" blocker="+blocker;
        if (design == MushroomFarmDesign.RING_CURRENT) return "Mushroom/RING turns="+ring.turns()
                +" input="+(ring.forward()?"W":"S")+" yaw="+ring.yaw()+" blocker="+blocker;
        float curYaw = services.rotation().yaw();
        float curPitch = services.rotation().pitch();
        float err = aim == null ? 0f : aim.aimError(curYaw, curPitch);
        float tgtYaw = aim == null ? headingYaw() : aim.targetYaw();
        float tgtPitch = aim == null ? cropPitch() : aim.targetPitch();
        return String.format(
                "Mushroom %s sel=%s path=%s toolSlot=%d%n"
                + "  heading=%s head=%.0f tgt=%.0f/%.0f cur=%.0f/%.0f err=%.1f%n"
                + "  keys[F=%b L=%b R=%b] atRowEnd=%b blocker=%s",
                mode, lastSelection, pathMode, chosenToolSlot(),
                heading, headingYaw(), tgtYaw, tgtPitch, curYaw, curPitch, err,
                heldForward, heldLeft, heldRight, atRowEnd(), blocker);
    }

    public TurnDirection lastSelection() {
        return lastSelection;
    }

    public MushroomStaircasePlanner.PathMode pathMode() {
        return pathMode;
    }

    public Direction heading() {
        return heading;
    }

    /** Passive exact maintenance checkpoint. No services, geometry, clocks or planner callbacks run. */
    public dev.veltraph.client.farming.module.FarmMaintenanceState maintenanceState() {
        return dev.veltraph.client.farming.module.FarmMaintenanceState.of(this,
                "classicalProfile", classicalProfile,
                "staircaseProfile", staircaseProfile,
                "planner", planner == null ? null : planner.maintenanceState(),
                "ring", ring == null ? null : ring.maintenanceState(),
                "referencePlanner",referencePlanner.maintenanceState(),"referenceAligned",referenceAligned,
                "ringLaneClassifier", ringLaneClassifier,
                "ringContinuation", ringContinuation,
                "ringTravel", ringTravel,
                "ringStartForward", ringStartForward,
                "design", design,
                "ringProfile", ringProfile,
                "mode", mode,
                "lastSelection", lastSelection,
                "pathMode", pathMode,
                "heading", heading,
                "aim", aim == null ? null : aim.maintenanceState(),
                "stillSinceMs", stillSinceMs,
                "blocker", blocker,
                "heldForward", heldForward,
                "heldLeft", heldLeft,
                "heldRight", heldRight);
    }
    @Override protected java.util.Optional<FarmMaintenanceState> maintenanceProgress(){
        return java.util.Optional.of(maintenanceState());
    }
}
