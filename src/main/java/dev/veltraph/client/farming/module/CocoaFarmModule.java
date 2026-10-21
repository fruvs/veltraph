package dev.veltraph.client.farming.module;

import dev.veltraph.client.config.ConfigService;
import dev.veltraph.client.config.CocoaFarmMode;
import dev.veltraph.client.config.FarmingConfig;
import dev.veltraph.client.config.LaneSide;
import dev.veltraph.client.farming.domain.FarmLayoutProfile;
import dev.veltraph.client.farming.domain.FarmLayoutRegistry;
import dev.veltraph.client.farming.movement.Angles;
import dev.veltraph.client.farming.movement.TurnDirection;
import dev.veltraph.client.farming.movement.FarmAimController;
import dev.veltraph.client.farming.movement.planner.CocoaPlanner;
import dev.veltraph.client.farming.movement.planner.TwoKeyCocoaPlanner;
import dev.veltraph.client.farming.movement.planner.CocoaBoundaryHarvest;
import dev.veltraph.client.navigation.TravelHeading;
import dev.veltraph.client.log.VeltraphLog;
import dev.veltraph.client.service.FarmServices;
import dev.veltraph.client.world.BlockPos;
import dev.veltraph.client.world.Direction;

import java.util.List;
import java.util.Random;

/** Cocoa world probes and smooth fixed-pitch aim. See docs/FARMING_COCOA_SOURCE_AUDIT.md. */
public final class CocoaFarmModule extends FarmModule {

    public static final String ID = "cocoa";

    /** Fixed cocoa aim pitch (CONFIRMED -45.0, §5b). Negative = looking up at the hanging pods. */
    private static final float COCOA_AIM_PITCH = -45.0f;

    /** Cocoa block id (MC 1.21). */
    private static final String COCOA_BLOCK = "minecraft:cocoa";

    /** Recovered row-fraction alignment band (§5b / FarmingCocoaLayout.md). */
    private static final double ROW_FRACTION_MIN = 0.35;
    private static final double ROW_FRACTION_MAX = 0.65;

    /** Vertical scan span for the cocoa cross-check ({@code player.up(2)} + [0..3], §5b). */
    private static final int COCOA_SCAN_VERTICAL = 3;

    /** Side-probe distances for lane detection ({@code CocoaConstants}: immediate side + side+5). */
    private static final int SIDE_PROBE_NEAR = 1;
    private static final int SIDE_PROBE_FAR = 5;

    /**
     * Return-lane distance to the aligned cross-check target ({@code player.up(2)} offset by forward/side
     * distance 2, §5b / FarmingCocoaLayout.md:31).
     */
    private static final int ROW_END_FORWARD = 2;

    private final FarmLayoutProfile profile;
    private final CocoaPlanner planner;
    private final CocoaWorld world;

    private final TwoKeyCocoaPlanner twoKey=new TwoKeyCocoaPlanner();
    private final CocoaBoundaryHarvest boundaryHarvest=new CocoaBoundaryHarvest();
    private final TravelHeading currentYaw=new TravelHeading(),currentPitch=new TravelHeading();
    private CocoaFarmMode runningMode=CocoaFarmMode.CLASSICAL;
    private String currentStopReason="";
    private FarmAimController aim;
    private Direction heading = Direction.NORTH;

    public CocoaFarmModule(FarmServices services, ConfigService config, FarmLayoutRegistry registry) {
        super(ID, "Cocoa", services, config, registry);
        this.profile = registry.byId(FarmLayoutRegistry.COCOA).orElseThrow();
        this.world = new CocoaWorld();
        this.planner = new CocoaPlanner(config, services, world);
    }

    @Override
    protected List<String> toolKeywords() {
        return List.of("axe", "cocoa");
    }

    @Override
    public FarmLayoutProfile activeProfile() {
        var mode=isEnabled()?runningMode:config.get(FarmingConfig.COCOA_FARM_MODE);
        return mode==CocoaFarmMode.CURRENT_TWO_KEY_LOOP?registry.byId("cocoa_two_key_current").orElseThrow():profile;
    }

    @Override
    public void onEnable() {
        try {
            startCocoa();
            installCropGuard();
        } catch (RuntimeException | Error failure) {
            clearFarmTargetGuard();
            releaseInputs();
            throw failure;
        }
    }

    private void startCocoa() {
        // Macro-owned heading captured once (never the live player.facing()), mirroring
        // SShapeRowEngine.start(): snap the current yaw to the nearest cardinal.
        runningMode=config.get(FarmingConfig.COCOA_FARM_MODE);
        currentStopReason="";
        boundaryHarvest.reset();currentYaw.reset();currentPitch.reset();
        this.heading = Direction.fromYaw(Angles.snapToCardinal(services.rotation().yaw()));
        this.aim = buildAim();
        this.aim.setTarget(headingYaw(), aimPitch());
        world.switchOrigin = null;
        if(runningMode==CocoaFarmMode.CURRENT_TWO_KEY_LOOP)
            twoKey.start(config.get(FarmingConfig.COCOA_TWO_KEY_START_SIDE),services.player().position(),services.clock().nowMillis());
        else planner.start();
        VeltraphLog.details("CocoaFarmModule.onEnable", "captured heading=" + heading + " ("
                + headingYaw() + "°) pitch=" + COCOA_AIM_PITCH);
    }

    @Override
    protected void onDisableFarming() {
        twoKey.stop();
        boundaryHarvest.reset();
        planner.stop();
        super.onDisableFarming();
        clearFarmTargetGuard();
    }

    @Override
    protected void onResumeAfterRewarp() {
        world.switchOrigin = null;
        boundaryHarvest.reset();currentYaw.reset();currentPitch.reset();
        if(runningMode==CocoaFarmMode.CURRENT_TWO_KEY_LOOP)
            twoKey.start(twoKey.side(),services.player().position(),services.clock().nowMillis());
        else planner.start();
        // The confirmed arrival may replace ClientLevel/player while this module stays enabled.
        installCropGuard();
    }

    private void installCropGuard() {
        // Geometry probes still observe all pods; only mature native age 2 may be harvested.
        installFarmTargetGuard(this::matureCocoa);
    }

    private boolean matureCocoa(BlockPos target) {
        return services.world().isLoaded(target)&&services.world().isBlock(target,COCOA_BLOCK)
                && services.world().blockProperty(target,"age").filter("2"::equals).isPresent();
    }

    @Override
    protected void onTickFarming() {
        if(runningMode==CocoaFarmMode.CURRENT_TWO_KEY_LOOP){tickCurrentTwoKey();return;}
        if (aim == null) {
            aim = buildAim();
            aim.setTarget(headingYaw(), COCOA_AIM_PITCH);
        }
        // Smoothly hold the cocoa aim target every tick (retarget + step + aim), exactly like
        // SShapeRowEngine.retargetAndAim() — never a one-tick snap.
        retargetAndAim();
        planner.observeHeight();

        // Ground + tool gating, mirroring the S-shape engine. Off-ground/tool-not-ready -> hold
        // position (release movement, keep aiming) rather than drive the lane machine.
        if (!grounded()) {
            VeltraphLog.verbose("CocoaFarmModule.tick", "blocked: not on ground");
            services.input().releaseAllMovement();
            services.breaking().setBreaking(false);
            return;
        }
        if (!toolReady()) {
            VeltraphLog.verbose("CocoaFarmModule.tick", "blocked: tool not ready");
            services.input().releaseAllMovement();
            services.breaking().setBreaking(false);
            return;
        }
        planner.tick();
    }

    @Override
    public String debugState() {
        if(runningMode==CocoaFarmMode.CURRENT_TWO_KEY_LOOP)return "Cocoa current/"+twoKey.status();
        return "Cocoa/" + planner.state() + "/" + planner.heading();
    }

    @Override
    public String diagnostics() {
        if(runningMode==CocoaFarmMode.CURRENT_TWO_KEY_LOOP)return "Current 2KeyCocoa1Plot | "+twoKey.status()
                +" | posted aim="+headingYaw()+"/-79 | source speed="+(heading==Direction.NORTH?394:397)
                +" (player setting) | target="+services.breaking().targetBlockId()
                +(boundaryHarvest.state()==CocoaBoundaryHarvest.State.IDLE?"":" | boundary="+boundaryHarvest.status())
                +(currentStopReason.isEmpty()?"":" | stopped: "+currentStopReason);
        return String.format(
                "Cocoa state=%s heading=%s head=%.0f laneDir=%s laneSide=%s sneak=%b%n"
                + "  aim tgt=%.0f/%.0f err=%.1f onGround=%b toolSlot=%d%n"
                + "  blocker=%s",
                planner.state(), heading, headingYaw(), planner.heading(), planner.laneSide(),
                planner.sneakLatched(), headingYaw(), aimTargetPitch(), aimError(),
                grounded(), chosenToolSlot(), planner.blocker());
    }

    public CocoaPlanner.CocoaState cocoaState() {
        return planner.state();
    }

    public TwoKeyCocoaPlanner.State currentTwoKeyState(){return twoKey.state();}
    public int currentTwoKeyEnds(){return twoKey.completedEnds();}
    public LaneSide currentTwoKeySide(){return twoKey.side();}
    public CocoaBoundaryHarvest.State currentBoundaryState(){return boundaryHarvest.state();}
    private void tickCurrentTwoKey(){
        if(!services.player().isAvailable()){setEnabled(false);return;}
        if(aim==null){aim=buildAim();aim.setTarget(headingYaw(),aimPitch());}
        var pos=services.player().position();var feet=pos.toBlockPos();
        boolean loaded=true;
        for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)for(int y=0;y<=3;y++)
            loaded&=services.world().isLoaded(feet.add(x,y,z));
        double fraction=pos.y()-Math.floor(pos.y());String floor=services.world().blockId(feet);
        boolean supported=(floor.equals("minecraft:snow")&&Math.abs(fraction-.375)<.08)
                ||(floor.equals("minecraft:smooth_stone_slab")&&Math.abs(fraction-.5)<.08);
        boolean ready=loaded&&supported&&grounded()&&toolReady();
        boolean rowAligned=Math.abs(Angles.signedDelta(services.rotation().yaw(),headingYaw()))<.25
                &&Math.abs(services.rotation().pitch()-aimPitch())<.25;
        var focus=boundaryHarvest.observe(services.world(),pos,services.player().eyePosition(),
                services.player().blockInteractionRange(),heading,
                ready&&(rowAligned||boundaryHarvest.state()!=CocoaBoundaryHarvest.State.IDLE),services.clock().nowMillis());
        if(boundaryHarvest.state()!=CocoaBoundaryHarvest.State.IDLE){
            services.input().releaseAllMovement();services.input().setSprintHeld(false);services.breaking().setBreaking(false);
            var paused=twoKey.tick(new TwoKeyCocoaPlanner.Facts(loaded,supported,grounded(),false,pos,
                    services.player().horizontalSpeed(),false,false,services.clock().nowMillis()));
            if(paused.state()==TwoKeyCocoaPlanner.State.BLOCKED){currentStopReason=twoKey.status();setEnabled(false);return;}
            if(boundaryHarvest.state()==CocoaBoundaryHarvest.State.BLOCKED){
                currentStopReason=boundaryHarvest.status();setEnabled(false);return;
            }
            if(focus.isPresent()){
                var target=focus.get();var eye=services.player().eyePosition();var point=target.point();
                double dx=point.x()-eye.x(),dy=point.y()-eye.y(),dz=point.z()-eye.z();
                float yaw=(float)(Math.toDegrees(Math.atan2(dz,dx))-90);
                float pitch=(float)-Math.toDegrees(Math.atan2(dy,Math.hypot(dx,dz)));
                applyCurrentAim(yaw,pitch);
                boolean settled=Math.abs(Angles.signedDelta(services.rotation().yaw(),yaw))<.15
                        &&Math.abs(services.rotation().pitch()-pitch)<.15;
                boolean actualTarget=services.breaking().targetBlockId().equals(COCOA_BLOCK)
                        &&services.breaking().targetBlockPos().filter(target.target()::equals).filter(this::matureCocoa).isPresent();
                services.breaking().setBreaking(settled&&actualTarget);
            }else{
                applyCurrentAim(headingYaw(),aimPitch());
                if(Math.abs(Angles.signedDelta(services.rotation().yaw(),headingYaw()))<.15
                        &&Math.abs(services.rotation().pitch()-aimPitch())<.15)boundaryHarvest.restored();
            }
            return;
        }
        retargetAndAim();
        boolean aligned=aim.aimError(services.rotation().yaw(),services.rotation().pitch())<1.0&&toolReady();
        boolean cropTarget=services.breaking().targetBlockId().equals(COCOA_BLOCK)
                && services.breaking().targetBlockPos().filter(this::matureCocoa).isPresent();
        // The renderer's cached crosshair can lag the current native pose by one frame.
        // A fresh read only pauses movement; attack still requires the cached actual target.
        boolean harvestHold=cropTarget
                || services.breaking().freshTargetBlockPos().filter(this::matureCocoa).isPresent();
        var out=twoKey.tick(new TwoKeyCocoaPlanner.Facts(loaded,supported,grounded(),aligned,pos,
                services.player().horizontalSpeed(),cropTarget,harvestHold,services.clock().nowMillis()));
        services.input().releaseAllMovement();services.input().setSprintHeld(false);
        services.input().setLeftHeld(out.left());services.input().setRightHeld(out.right());
        services.breaking().setBreaking(out.attack());
        if(out.state()==TwoKeyCocoaPlanner.State.BLOCKED){
            currentStopReason=twoKey.status();
            setEnabled(false);
        }
    }

    // ---- Aim (mirrors SShapeRowEngine.buildAim/retargetAndAim) ----

    private FarmAimController buildAim() {
        FarmAimController.Settings settings = new FarmAimController.Settings(
                runningMode==CocoaFarmMode.CURRENT_TWO_KEY_LOOP?Math.min(6f,config.get(FarmingConfig.ROTATION_MAX_YAW_DEG_PER_TICK).floatValue()):config.get(FarmingConfig.ROTATION_MAX_YAW_DEG_PER_TICK).floatValue(),
                runningMode==CocoaFarmMode.CURRENT_TWO_KEY_LOOP?Math.min(6f,config.get(FarmingConfig.ROTATION_MAX_PITCH_DEG_PER_TICK).floatValue()):config.get(FarmingConfig.ROTATION_MAX_PITCH_DEG_PER_TICK).floatValue(),
                config.get(FarmingConfig.ROTATION_ACCELERATION),
                config.get(FarmingConfig.ROTATION_TOLERANCE_DEG).floatValue(),
                config.get(FarmingConfig.ROTATION_PAUSE_ERROR_DEG).floatValue(),
                config.get(FarmingConfig.ROTATION_USE_MOUSE_GCD),
                config.get(FarmingConfig.ROTATION_MOUSE_SENSITIVITY),
                runningMode!=CocoaFarmMode.CURRENT_TWO_KEY_LOOP&&config.get(FarmingConfig.ROTATION_JITTER),
                config.get(FarmingConfig.ROTATION_JITTER_MAX_DEG).floatValue(),
                config.get(FarmingConfig.ROTATION_JITTER_MIN_INTERVAL_TICKS),
                config.get(FarmingConfig.ROTATION_JITTER_MAX_INTERVAL_TICKS),
                config.get(FarmingConfig.ROTATION_JITTER_HOLD_TICKS));
        return new FarmAimController(settings, new Random());
    }

    private void retargetAndAim() {
        if(runningMode==CocoaFarmMode.CURRENT_TWO_KEY_LOOP){applyCurrentAim(headingYaw(),aimPitch());return;}
        aim.retarget(headingYaw(), aimPitch());
        float curYaw = services.rotation().yaw();
        float curPitch = services.rotation().pitch();
        float[] next = aim.step(curYaw, curPitch);
        services.rotation().aim(next[0], next[1]);
        VeltraphLog.details("CocoaFarmModule.aim", () -> String.format(
                "target=%.1f/%.1f current=%.1f/%.1f err=%.1f", headingYaw(), COCOA_AIM_PITCH,
                curYaw, curPitch, aim.aimError(curYaw, curPitch)));
    }

    private void applyCurrentAim(float yaw,float pitch){
        aim.retarget(yaw,pitch);
        services.rotation().aim(Angles.wrapDegrees(currentYaw.step(services.rotation().yaw(),yaw,1)),
                currentPitch.stepLinear(services.rotation().pitch(),pitch,1));
    }

    private float headingYaw() {
        if(runningMode==CocoaFarmMode.CURRENT_TWO_KEY_LOOP)return switch(heading){
            case NORTH->-166.4f;case SOUTH->13.5f;case EAST->-76.5f;case WEST->103.5f;default->throw new IllegalStateException("Horizontal Cocoa heading required");
        };
        return (float) heading.toYaw();
    }

    private float aimPitch(){return runningMode==CocoaFarmMode.CURRENT_TWO_KEY_LOOP?-79f:COCOA_AIM_PITCH;}
    private float aimTargetPitch() {
        return aim == null ? aimPitch() : aim.targetPitch();
    }

    private float aimError() {
        return aim == null ? 0f
                : aim.aimError(services.rotation().yaw(), services.rotation().pitch());
    }

    private boolean grounded() {
        return services.player().onGround();
    }

    /**
     * Real {@link CocoaPlanner.World} probes composed from the existing world/player services
     * (no new service methods). All geometry uses the macro-owned {@link #heading}, never the live
     * facing.
     */
    private final class CocoaWorld implements CocoaPlanner.World {
    /** Passive exact maintenance checkpoint. No services, geometry, clocks or planner callbacks run. */
    public dev.veltraph.client.farming.module.FarmMaintenanceState maintenanceState() {
        return dev.veltraph.client.farming.module.FarmMaintenanceState.of(this,
                "switchOrigin", switchOrigin);
    }


        @Override
        public LaneSide detectedLaneSide() {
            // CocoaConstants.getType40: probe the cocoa block around player.up(2) on each lateral —
            // immediate side and side+5 — to decide whether cocoa is on the left or right.
            Direction left = heading.rotateYCounterclockwise();
            Direction right = heading.rotateYClockwise();
            if (cocoaOnSide(left)) {
                return LaneSide.LEFT;
            }
            if (cocoaOnSide(right)) {
                return LaneSide.RIGHT;
            }
            return null; // no side detected -> planner falls back to FORWARD heading
        }

        private BlockPos switchOrigin;

        private BlockPos heightProbe() {
            var position = services.player().position();
            return new dev.veltraph.client.world.Vec3(position.x(), position.y() - 0.001,
                    position.z()).toBlockPos();
        }

        @Override public boolean heightProbeLoaded() {
            BlockPos probe = heightProbe();
            return services.world().isLoaded(probe) && services.world().isLoaded(probe.up());
        }

        @Override public boolean heightProbeClear() {
            BlockPos probe = heightProbe();
            return services.world().isPassable(probe) && services.world().isPassable(probe.up());
        }

        @Override public void clearSwitch() { switchOrigin = null; }

        @Override
        public boolean atRowEnd() {
            Direction travel = planner.heading().resolve(heading);
            BlockPos ahead = services.player().blockPos().offset(travel, 1);
            // The decoded base enters SWITCHING_ROW at a body obstruction. Missing chunks
            // and unsupported ground stop input rather than inventing a row boundary.
            return services.world().isLoaded(ahead) && services.world().isLoaded(ahead.up())
                    && (!services.world().isPassable(ahead) || !services.world().isPassable(ahead.up()));
        }

        @Override
        public void beginSwitch() {
            switchOrigin = services.player().blockPos();
        }

        @Override
        public boolean laneAligned() {
            if (switchOrigin == null || !rowFractionAligned()) return false;
            BlockPos current = services.player().blockPos();
            boolean changedLane = heading.dx() == 0
                    ? current.x() != switchOrigin.x() : current.z() != switchOrigin.z();
            if (!changedLane) return false;
            // Actual cocoa override :179-208 probes opposite the old longitudinal travel,
            // UP from player.up(2). It is the switch-completion predicate, not row end.
            Direction returnDirection = planner.heading().opposite().resolve(heading);
            return cocoaWithinVerticalSpan(current.up(2).offset(returnDirection, ROW_END_FORWARD));
        }

        @Override
        public boolean canMove(TurnDirection movement) {
            Direction direction = movement.resolve(heading);
            BlockPos next = services.player().blockPos().offset(direction, 1);
            return services.world().isLoaded(next) && services.world().isLoaded(next.up())
                    && services.world().isLoaded(next.down())
                    && services.world().isPassable(next) && services.world().isPassable(next.up())
                    && services.world().isSolidFloor(next.down());
        }

        /** Cocoa present at player.up(2) offset to {@code side} by the near or far probe distance. */
        private boolean cocoaOnSide(Direction side) {
            BlockPos base = services.player().blockPos().up(2);
            return isCocoa(base.offset(side, SIDE_PROBE_NEAR))
                    || isCocoa(base.offset(side, SIDE_PROBE_FAR));
        }

        /** Scan vertical offsets 0..3 at {@code base} for the cocoa block. */
        private boolean cocoaWithinVerticalSpan(BlockPos base) {
            for (int dy = 0; dy <= COCOA_SCAN_VERTICAL; dy++) {
                if (isCocoa(base.up(dy))) {
                    return true;
                }
            }
            return false;
        }

        private boolean isCocoa(BlockPos pos) {
            return services.world().isLoaded(pos) && services.world().isBlock(pos, COCOA_BLOCK);
        }

        /**
         * The fractional coordinate on the axis perpendicular to travel is within the recovered band.
         * The checked axis depends on the heading axis (FarmingCocoaLayout.md:29): along an X-axis
         * heading the Z fraction is the lane offset, and vice-versa.
         */
        private boolean rowFractionAligned() {
            double frac = heading.dx() != 0
                    ? frac(services.player().position().z())
                    : frac(services.player().position().x());
            return inBand(frac);
        }
    }

    private static boolean inBand(double f) {
        return f >= ROW_FRACTION_MIN && f <= ROW_FRACTION_MAX;
    }

    private static double frac(double v) {
        return v - Math.floor(v);
    }

    /** Passive exact maintenance checkpoint. No services, geometry, clocks or planner callbacks run. */
    public dev.veltraph.client.farming.module.FarmMaintenanceState maintenanceState() {
        return dev.veltraph.client.farming.module.FarmMaintenanceState.of(this,
                "profile", profile,
                "planner", planner == null ? null : planner.maintenanceState(),
                "world", world == null ? null : world.maintenanceState(),
                "twoKey", twoKey == null ? null : twoKey.maintenanceState(),
                "boundaryHarvest", boundaryHarvest == null ? null : boundaryHarvest.maintenanceState(),
                "currentYaw", currentYaw == null ? null : currentYaw.maintenanceState(),
                "currentPitch", currentPitch == null ? null : currentPitch.maintenanceState(),
                "runningMode", runningMode,
                "currentStopReason", currentStopReason,
                "aim", aim == null ? null : aim.maintenanceState(),
                "heading", heading);
    }
    @Override protected java.util.Optional<FarmMaintenanceState> maintenanceProgress(){
        return java.util.Optional.of(maintenanceState());
    }
}
