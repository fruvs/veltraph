package dev.veltraph.client.farming.module;

import dev.veltraph.client.config.ConfigService;
import dev.veltraph.client.config.FarmingConfig;
import dev.veltraph.client.config.PumpkinFarmDesign;
import dev.veltraph.client.farming.domain.FarmLayoutProfile;
import dev.veltraph.client.farming.domain.FarmLayoutRegistry;
import dev.veltraph.client.farming.movement.Angles;
import dev.veltraph.client.farming.movement.GourdCropTarget;
import dev.veltraph.client.farming.movement.GourdSourceGeometry;
import dev.veltraph.client.farming.movement.FarmAimController;
import dev.veltraph.client.farming.movement.TurnDirection;
import dev.veltraph.client.farming.movement.planner.PumpkinOnEndRowPlanner;
import dev.veltraph.client.farming.movement.planner.CapturedGourdWallPlanner;
import dev.veltraph.client.farming.movement.planner.PumpkinSourceRowPlanner;
import dev.veltraph.client.farming.movement.planner.MelonKingDeWaterPlanner;
import dev.veltraph.client.farming.movement.planner.MelonKingDeCornerPlanner;
import dev.veltraph.client.log.VeltraphLog;
import dev.veltraph.client.service.FarmServices;
import dev.veltraph.client.world.Direction;
import dev.veltraph.client.world.BlockPos;
import dev.veltraph.client.world.Vec3;

import java.util.Random;

/**
 * Pumpkin/melon input families: decoded CLASSICAL alternating lateral/108–112-degree turn sequence,
 * explicit captured-default forward corridor adaptation, and actual-map creator water planners.
 * All camera targets use gradual aiming and all carved crop attacks require a loaded soil/stem bed.
 * The classical side probe/corner gate were recovered from original bytecode; its intState update
 * rhythm remains a local tick interpretation. See docs/FARMING_CAPTURED_GOURD_SOURCE_AUDIT.md.
 */
public final class PumpkinMelonFarmModule extends FarmModule {

    public static final String ID = "pumpkin_melon";

    /**
     * inferred: anti-ghost-block break cadence period (ticks). The recovered {@code PUMPKIN_ANTI_GHOST_BLOCK}
     * flag exists in config but its exact cadence was not recovered to a salted number; when enabled we
     * release the attack key for one tick every N ticks so the client re-issues a fresh break swing
     * instead of a held break that can leave a "ghost" (visually-broken but server-present) pumpkin.
     * Chosen to align with the project's continuous-break feel without inventing a salted constant.
     */
    private static final int ANTI_GHOST_RELEASE_PERIOD_TICKS = 4;

    private final FarmLayoutProfile profile;
    private final PumpkinOnEndRowPlanner planner;
    private final MelonKingDeWaterPlanner waterPlanner;
    private final PumpkinSourceRowPlanner sourcePlanner;
    private final CapturedGourdWallPlanner capturedWallPlanner;
    private final MelonKingDeCornerPlanner cornerPlanner;
    private float sourceYaw;
    private float sourcePitch;
    private float compactPitch=-59;
    private Vec3 previousWaterPosition;
    private BlockPos nativeEntryWater;
    private MelonKingDeCornerPlanner.CourseStrip terminalStrip;
    private boolean terminalInward=true;
    private int terminalTicks;
    private WaterTick waterTick;
    private final class WaterTick {
        final FarmAimController originalAim=aim;final Direction originalHeading=heading;
        boolean cut;
        boolean current(){return !cut&&waterTick==this&&originalAim!=null&&aim==originalAim
                &&heading==originalHeading&&design==PumpkinFarmDesign.MELONKINGDE_WATER&&isEnabled()&&!isSuspended();}
    }
    private boolean waterTickCurrent(){return waterTick==null||waterTick.current();}
    private void pauseWater(String reason){if(waterTickCurrent())waterPlanner.pause(reason);}
    private void releaseWaterMovement() {
        if(waterTick==null){services.input().releaseAllMovement();return;}
        if(!waterTickCurrent())return;
        services.input().setForwardHeld(false);if(!waterTickCurrent())return;
        services.input().setBackHeld(false);if(!waterTickCurrent())return;
        services.input().setLeftHeld(false);if(!waterTickCurrent())return;
        services.input().setRightHeld(false);if(!waterTickCurrent())return;
        services.input().setSneakHeld(false);if(!waterTickCurrent())return;
        services.input().setJumpHeld(false);if(!waterTickCurrent())return;
        services.input().setSprintHeld(false);
    }
    private PumpkinFarmDesign design = PumpkinFarmDesign.CLASSICAL;

    /** Macro-owned heading captured on enable; flipped 180° on the row-end turn (never the live facing). */
    private Direction heading = Direction.NORTH;
    private FarmAimController aim;

    // Diagnostics snapshot (last tick).
    private boolean lastGrounded;
    private boolean lastToolReady;
    private boolean lastBreakSuppressed;
    private float lastAimErr;
    private String lastBlocker = "none";

    public PumpkinMelonFarmModule(FarmServices services, ConfigService config, FarmLayoutRegistry registry) {
        super(ID, "Pumpkin/Melon", services, config, registry);
        this.profile = registry.byId(FarmLayoutRegistry.PUMPKIN_MELON).orElseThrow();
        PumpkinOnEndRowPlanner.World world = new PumpkinOnEndRowPlanner.World() {
            @Override
            public boolean atRowEnd() {
                return !classicalLaneWalkable(heading);
            }

            @Override
            public boolean nextLaneStandable() {
                return classicalLaneWalkable(heading.rotateYCounterclockwise()) || classicalLaneWalkable(heading.rotateYClockwise());
            }

            @Override
            public TurnDirection openLaneSide() {
                if (classicalLaneWalkable(heading.rotateYClockwise())) {
                    return TurnDirection.RIGHT;
                }
                if (classicalLaneWalkable(heading.rotateYCounterclockwise())) {
                    return TurnDirection.LEFT;
                }
                return TurnDirection.FORWARD;
            }

            @Override
            public float currentYaw() {
                return services.player().yaw();
            }

            @Override public java.util.Optional<Vec3> nextLaneCenter(TurnDirection side) {
                if(design!=PumpkinFarmDesign.CAPTURED_DEFAULT)return java.util.Optional.empty();
                Direction physical=side.resolve(heading);
                if(!classicalLaneWalkable(physical))return java.util.Optional.empty();
                BlockPos adjacent=services.player().blockPos().offset(physical);
                Vec3 current=services.player().position();
                return java.util.Optional.of(new Vec3(physical.dx()==0?current.x():adjacent.x()+.5,
                        current.y(),physical.dz()==0?current.z():adjacent.z()+.5));
            }
        };
        // The lateral lane step waits until the smooth aim has panned to the (flipped) heading.
        this.planner = new PumpkinOnEndRowPlanner(services, world, this::aimAligned);
        this.waterPlanner = new MelonKingDeWaterPlanner(services, new WaterDesignWorld());
        this.cornerPlanner = new MelonKingDeCornerPlanner(services);
        this.sourcePlanner = new PumpkinSourceRowPlanner(services,new PumpkinSourceRowPlanner.World(){
            public boolean atRowEnd(TurnDirection side){return GourdSourceGeometry.endAir(services.world(),services.player().position(),services.rotation().yaw(),side);}
            public boolean atTurnCorner(Vec3 current,Vec3 previous){return GourdSourceGeometry.corner(current,previous);}
            public boolean movementSupported(TurnDirection side){
                Direction facing=Direction.fromYaw(services.rotation().yaw());
                return sourceLoadedSupport(facing)&&sourceLoadedSupport(side.resolve(facing));
            }
        },this::aimAligned,yaw->sourceYaw=(float)yaw,new Random());
        this.capturedWallPlanner=new CapturedGourdWallPlanner(services,new CapturedWallWorld(),this::aimAligned);
    }

    @Override protected java.util.Optional<String> configurationReadinessError() {
        if(config.get(FarmingConfig.PUMPKIN_FARM_DESIGN)==PumpkinFarmDesign.MELONKINGDE_WATER) {
            if(dev.veltraph.client.farming.movement.planner.MelonKingDeCompactAim.target(services.rotation().pitch(),
                    config.get(FarmingConfig.ROTATION_USE_MOUSE_GCD),config.get(FarmingConfig.ROTATION_MOUSE_SENSITIVITY)).isEmpty())
                return java.util.Optional.of("Melon King compact view requires a mouse step inside its native ladder/fruit clearance window; adjust farming mouse sensitivity or disable mouse quantization");
            return java.util.Optional.empty();
        }
        if(config.get(FarmingConfig.PUMPKIN_FARM_DESIGN)!=PumpkinFarmDesign.CAPTURED_DEFAULT
                && (!isEnabled()||design!=PumpkinFarmDesign.CAPTURED_DEFAULT))return java.util.Optional.empty();
        double pitch=config.get(FarmingConfig.PUMPKIN_CAPTURED_PITCH);
        return !Double.isFinite(pitch)||pitch < -90||pitch > 90
                ?java.util.Optional.of("Captured Default Gourd Pitch must be finite and between -90 and 90 degrees")
                :java.util.Optional.empty();
    }

    @Override
    protected java.util.List<String> toolKeywords() {
        return java.util.List.of("dicer", "cleaver");
    }

    @Override
    public FarmLayoutProfile activeProfile() {
        if(config.get(FarmingConfig.PUMPKIN_FARM_DESIGN)==PumpkinFarmDesign.CAPTURED_DEFAULT)
            return registry.byId("pumpkin_melon_captured_default").orElse(profile);
        if (config.get(FarmingConfig.PUMPKIN_FARM_DESIGN) == PumpkinFarmDesign.SAIGO_CURRENT)
            return registry.byId("pumpkin_melon_saigo_current").orElse(profile);
        if (config.get(FarmingConfig.PUMPKIN_FARM_DESIGN) == PumpkinFarmDesign.MELONKINGDE_WATER)
            return registry.byId("pumpkin_melon_melonkingde_water").orElse(profile);
        return profile;
    }

    @Override
    public void onEnable() {
        this.design=config.get(FarmingConfig.PUMPKIN_FARM_DESIGN);
        var invalid=configurationReadinessError();
        if(invalid.isPresent())throw new IllegalArgumentException(invalid.get());
        this.aim = buildAim();
        // Capture the macro-owned heading once from the initial camera (snapped to a cardinal), exactly
        // like SShapeRowEngine.start(). All row geometry/aim use this heading, never the live facing.
        this.heading = Direction.fromYaw(Angles.snapToCardinal(services.rotation().yaw()));
        this.sourceYaw = services.rotation().yaw();
        this.sourcePitch = services.rotation().pitch();
        this.previousWaterPosition=services.player().position();
        this.nativeEntryWater=null;this.terminalStrip=null;this.terminalInward=true;this.terminalTicks=0;
        this.cornerPlanner.reset();
        if(design==PumpkinFarmDesign.MELONKINGDE_WATER)compactPitch=(float)dev.veltraph.client.farming.movement.planner.MelonKingDeCompactAim.target(sourcePitch,
                config.get(FarmingConfig.ROTATION_USE_MOUSE_GCD),config.get(FarmingConfig.ROTATION_MOUSE_SENSITIVITY)).orElseThrow();
        if(design==PumpkinFarmDesign.CAPTURED_DEFAULT)capturedWallPlanner.start(heading);
        this.aim.setTarget(headingYaw(), farmPitch());
        if (isWaterDesign()) waterPlanner.start(waterStartingSide());
        else if(design==PumpkinFarmDesign.CLASSICAL) sourcePlanner.start(sourceYaw);
        else if(design!=PumpkinFarmDesign.CAPTURED_DEFAULT)planner.start();
        VeltraphLog.details(displayName(),
                "enabled: captured heading=" + heading + " (" + headingYaw() + "°)");
        installFarmTargetGuard( this::harvestableInteractionTarget);
    }

    @Override
    protected void onDisableFarming() {
        clearFarmTargetGuard();
        planner.stop();
        waterPlanner.stop();
        sourcePlanner.stop();
        capturedWallPlanner.stop();
        cornerPlanner.reset();
        super.onDisableFarming();
    }

    @Override protected void onResumeAfterRewarp() {
        if (isWaterDesign()) waterPlanner.resumeAtInterruptedPose();
        // A legitimate return may replace the player/world connection. Rebind only at the explicit
        // module resume seam, retaining its owner identity and crop policy.
        installFarmTargetGuard( this::harvestableInteractionTarget);
    }

    private TurnDirection waterStartingSide() {
        if (design == PumpkinFarmDesign.SAIGO_CURRENT)
            return TurnDirection.fromLaneSide(config.get(FarmingConfig.PUMPKIN_SAIGO_START_SIDE));
        return TurnDirection.fromLaneSide(config.get(FarmingConfig.PUMPKIN_MELONKING_START_SIDE));
    }

    private boolean isWaterDesign() { return design == PumpkinFarmDesign.SAIGO_CURRENT
            || design == PumpkinFarmDesign.MELONKINGDE_WATER; }

    private java.util.Optional<BlockPos> walkingSoulWaterBelow(Vec3 position) {
        BlockPos body=GourdSourceGeometry.body(position);
        for(int down=0;down<=3;down++) {
            BlockPos water=body.down(down);
            if(!services.world().isLoaded(water)||!services.world().isLoaded(water.down()))return java.util.Optional.empty();
            if(dev.veltraph.client.farming.movement.planner.MelonKingDeWalkingLaneMarker.walkingWater(services.world(),water,water.y()))return java.util.Optional.of(water);
        }
        return java.util.Optional.empty();
    }
    private void detectNativeCapEntry(Vec3 position,Direction side) {
        if(nativeEntryWater!=null)return;
        BlockPos body=GourdSourceGeometry.body(position);
        if(!services.world().isLoaded(body.down())||!services.world().isBlock(body.down(),"minecraft:polished_andesite"))return;
        for(double distance:new double[]{.65,1.65,2.65}) {
            var ahead=walkingSoulWaterBelow(new Vec3(position.x()+side.dx()*distance,position.y(),position.z()+side.dz()*distance));
            if(ahead.isEmpty()||Math.abs(position.y()-(ahead.get().y()+1))>.001)continue;
            boolean supported=true;
            for(double step=0;step<=distance;step+=.25) {
                BlockPos floor=new Vec3(position.x()+side.dx()*step,ahead.get().y()-1,position.z()+side.dz()*step).toBlockPos();
                if(!services.world().isLoaded(floor)||!services.world().isBlock(floor,"minecraft:soul_sand")){supported=false;break;}
            }
            if(supported&&waterTickCurrent()){nativeEntryWater=ahead.get();return;}
        }
    }
    private boolean nativeEntryBankContact(Vec3 position) {
        FarmAimController originalAim=aim;Direction originalHeading=heading;BlockPos originalWater=nativeEntryWater;
        if(originalWater==null||!new WaterDesignWorld().transferSourceCurrent(position,originalHeading,originalAim))return false;
        BlockPos bank=new Vec3(position.x(),originalWater.y(),position.z()).toBlockPos().offset(originalHeading);
        if(!services.world().isLoaded(bank))return false;
        String soil=services.world().blockId(bank);
        if(!soil.equals("minecraft:dirt")&&!soil.equals("minecraft:farmland"))return false;
        java.util.function.BooleanSupplier current=()->nativeEntryWater==originalWater
                &&new WaterDesignWorld().transferSourceCurrent(position,originalHeading,originalAim);
        if(services.world() instanceof dev.veltraph.client.service.mc.McWorldService nativeWorld)
            return nativeWorld.melonKingEntryBankContact(position,originalHeading,bank,current);
        Vec3 step=new Vec3(position.x()+originalHeading.dx()*.0001,position.y(),position.z()+originalHeading.dz()*.0001);
        if(!services.world().bodyClear(position,.3,1.8)||!current.getAsBoolean())return false;
        if(!services.world().hasGroundSupport(position,.3)||!current.getAsBoolean())return false;
        double top=bank.y()+(soil.equals("minecraft:farmland")?.9375:1);
        boolean bankContact=step.x()+.3-1e-7>bank.x()&&step.x()-.3+1e-7<bank.x()+1
                &&step.z()+.3-1e-7>bank.z()&&step.z()-.3+1e-7<bank.z()+1
                &&step.y()+1.8>bank.y()&&step.y()+.001<top;
        return bankContact&&!services.world().bodyClear(step,.3,1.8)&&current.getAsBoolean();
    }
    private boolean tickTerminalStrip(Vec3 position) {
        FarmAimController originalAim=aim;Direction originalHeading=heading;
        var current=cornerPlanner.courseStrip();
        if(current.isEmpty())return false;
        var strip=current.get();
        boolean changed=terminalStrip==null||!terminalStrip.floor().equals(strip.floor())||terminalStrip.inward()!=strip.inward()
                ||terminalStrip.heading()!=strip.heading();
        boolean nextInward=changed||terminalInward;int nextTicks=changed?1:terminalTicks+1;
        if(nextTicks>600) {
            pauseWater("terminal bank sweep timed out");if(!waterTickCurrent())return true;lastBlocker="terminal bank sweep timed out";return true;
        }
        double coordinate=(position.x()-strip.floor().x()-.5)*strip.inward().dx()
                +(position.z()-strip.floor().z()-.5)*strip.inward().dz();
        var velocity=services.player().horizontalVelocity();if(!waterTickCurrent())return true;
        double along=velocity.map(v->v.x()*strip.inward().dx()+v.z()*strip.inward().dz()).orElse(0d);
        // A continuous three-column bank pass, with momentum-aware turns at supported
        // bounds. No individual fruit is selected, stopped at or used as a camera target.
        if(coordinate+along*1.5>=2.2)nextInward=false;
        if(coordinate+along*1.5<=-.15)nextInward=true;
        if(strip.cleared())nextInward=coordinate<1;
        Direction direction=nextInward?strip.inward():strip.inward().opposite();
        var world=new WaterDesignWorld();
        if(!world.transferSourceCurrent(position,originalHeading,originalAim)
                ||!terminalStepClear(position,direction,originalHeading,originalAim)) {
            pauseWater("terminal bank direction unsupported or obstructed");if(!waterTickCurrent())return true;lastBlocker="terminal bank direction unsupported or obstructed";return true;
        }
        if(!world.transferSourceCurrent(position,originalHeading,originalAim))return true;
        if(strip.cleared()&&Math.abs(coordinate-1)<.25&&Math.abs(along)<.05) {
            releaseWaterMovement();if(!waterTickCurrent())return true;
            services.breaking().setBreaking(false);if(!waterTickCurrent())return true;
            lastBlocker="cleared terminal bank at supported END";return true;
        }
        boolean left=direction==originalHeading.rotateYCounterclockwise();
        boolean forward=world.forwardWhileFarming();if(!waterTickCurrent())return true;
        boolean attack=cropTargeted();
        if(!world.transferSourceCurrent(position,originalHeading,originalAim))return true;
        terminalStrip=strip;terminalInward=nextInward;terminalTicks=nextTicks;
        services.input().setForwardHeld(forward);if(!waterTickCurrent())return true;
        services.input().setBackHeld(false);if(!waterTickCurrent())return true;
        services.input().setLeftHeld(left);if(!waterTickCurrent())return true;
        services.input().setRightHeld(!left);if(!waterTickCurrent())return true;
        services.input().setJumpHeld(false);if(!waterTickCurrent())return true;
        services.input().setSneakHeld(false);if(!waterTickCurrent())return true;
        services.input().setSprintHeld(false);if(!waterTickCurrent())return true;
        services.breaking().setBreaking(attack);if(!waterTickCurrent())return true;
        lastBlocker="fixed-view terminal bank "+(terminalInward?"inward":"outward");return true;
    }
    private boolean terminalStepClear(Vec3 position,Direction direction,Direction originalHeading,FarmAimController originalAim) {
        var world=new WaterDesignWorld();java.util.function.BooleanSupplier current=()->world.transferSourceCurrent(position,originalHeading,originalAim);
        if(services.world() instanceof dev.veltraph.client.service.mc.McWorldService nativeWorld)
            return nativeWorld.melonKingTransferClear(position,direction,current);
        Vec3 probe=new Vec3(position.x()+direction.dx()*.35,position.y(),position.z()+direction.dz()*.35);
        if(!services.world().bodyClear(probe,.3,1.8)||!current.getAsBoolean())return false;
        return services.world().hasGroundSupport(probe,.3)&&current.getAsBoolean();
    }
    private boolean tickNativeEntry(Vec3 position,boolean grounded,double lateralProgress,double forwardProgress) {
        if(nativeEntryWater==null)return false;
        BlockPos floor=new Vec3(position.x(),nativeEntryWater.y()-1,position.z()).toBlockPos();
        if(!services.world().isLoaded(floor)||!services.world().isBlock(floor,"minecraft:soul_sand")) {
            pauseWater("native entry left loaded SoulSand support");return true;
        }
        double forward=position.x()*heading.dx()+position.z()*heading.dz()
                -nativeEntryWater.x()*heading.dx()-nativeEntryWater.z()*heading.dz();
        boolean walkingHeight=grounded&&Math.abs(position.y()-(nativeEntryWater.y()-.125))<.01;
        if(walkingHeight) {
            FarmAimController originalAim=aim;Direction originalHeading=heading;BlockPos originalWater=nativeEntryWater;
            var source=new WaterDesignWorld();BlockPos bank=new Vec3(position.x(),originalWater.y(),position.z()).toBlockPos().offset(originalHeading);
            if(!services.world().isLoaded(bank)||!source.transferSourceCurrent(position,originalHeading,originalAim)) {
                pauseWater("entry soil bank unloaded or source changed");return true;
            }
            String soil=services.world().blockId(bank);
            if((!soil.equals("minecraft:dirt")&&!soil.equals("minecraft:farmland"))
                    ||!services.world().bodyClear(position,.3,1.8)||!source.transferSourceCurrent(position,originalHeading,originalAim)
                    ||!services.world().hasGroundSupport(position,.3)||!source.transferSourceCurrent(position,originalHeading,originalAim)
                    ||nativeEntryWater!=originalWater) {
                pauseWater("entry soil bank or supported pose changed");return true;
            }
        }
        if(walkingHeight&&forward>=.6&&nativeEntryBankContact(position)) {nativeEntryWater=null;return false;}
        pauseWater(walkingHeight?"returning to native crop-bank contact":"settling native cap entry at walking-floor height");
        boolean forwardHeld=false,backHeld=false;TurnDirection lateral=null;
        if(walkingHeight)forwardHeld=true;
        else {
            double predictedForward=forward+forwardProgress*(grounded?1.25:4);
            backHeld=predictedForward>.62;forwardHeld=predictedForward<.30;
            if(grounded){if(lateralProgress<.08)lateral=waterPlanner.side();}
            else if(lateralProgress>.005)lateral=waterPlanner.side().opposite();

        }
        if(!waterTickCurrent())return true;
        services.input().setForwardHeld(forwardHeld);if(!waterTickCurrent())return true;
        services.input().setBackHeld(backHeld);if(!waterTickCurrent())return true;
        services.input().setLeftHeld(lateral==TurnDirection.LEFT);if(!waterTickCurrent())return true;
        services.input().setRightHeld(lateral==TurnDirection.RIGHT);
        return true;
    }

    @Override
    protected void onTickFarming() {
        if(design!=PumpkinFarmDesign.MELONKINGDE_WATER){onTickFarmingBody();return;}
        if(waterTick!=null){waterTick.cut=true;return;}
        WaterTick invocation=new WaterTick();waterTick=invocation;
        try{if(invocation.current())onTickFarmingBody();}
        finally{if(waterTick==invocation)waterTick=null;}
    }
    private void onTickFarmingBody() {
        var invalid=configurationReadinessError();
        if(!waterTickCurrent())return;
        if(invalid.isPresent()) {
            releaseWaterMovement();if(!waterTickCurrent())return;
            services.breaking().setBreaking(false);if(!waterTickCurrent())return;
            lastBlocker=invalid.get();return;
        }
        if (aim == null) {
            aim = buildAim();
        }
        boolean grounded = services.player().onGround();if(!waterTickCurrent())return;
        boolean toolReady = toolReady();if(!waterTickCurrent())return;
        lastGrounded = grounded;
        lastToolReady = toolReady;
        FarmAimController waterAim=aim;Direction waterHeading=heading;Vec3 originalWaterPosition=null;
        if(design==PumpkinFarmDesign.MELONKINGDE_WATER) {
            originalWaterPosition=services.player().position();
            if(!new WaterDesignWorld().transferSourceCurrent(originalWaterPosition,waterHeading,waterAim)) {
                pauseWater("creator course source changed before geometry");return;
            }
        }

        if(design==PumpkinFarmDesign.MELONKINGDE_WATER && toolReady && nativeEntryWater==null
                && waterPlanner.state()==MelonKingDeWaterPlanner.State.FARMING && grounded) {
            var position=services.player().position();
            var lower=walkingSoulWaterBelow(position);
            if(lower.isPresent()&&Math.abs(position.y()-(lower.get().y()-.125))<.01)
                cornerPlanner.observeCourse(lower.get(),heading,this::waterTickCurrent);
            else cornerPlanner.observeCourse(null,heading,this::waterTickCurrent);
        } else if(design==PumpkinFarmDesign.MELONKINGDE_WATER)cornerPlanner.observeCourse(null,heading,this::waterTickCurrent);

        // Always step the smooth aim toward the macro heading so the camera keeps panning (incl. through
        // the 180° flip) even while motion is gated. Yaw = macro heading; pitch = flat farm pitch.
        if(!waterTickCurrent())return;
        retargetAndAim();if(!waterTickCurrent())return;

        if (isWaterDesign()) {
            if(design==PumpkinFarmDesign.MELONKINGDE_WATER
                    &&!new WaterDesignWorld().transferSourceCurrent(originalWaterPosition,waterHeading,waterAim)) {
                pauseWater("creator course source changed during geometry");return;
            }
            Vec3 waterPosition=originalWaterPosition!=null?originalWaterPosition:services.player().position();
            Direction travelSide=waterPlanner.side().resolve(heading);
            double lateralProgress=previousWaterPosition==null?0:(waterPosition.x()-previousWaterPosition.x())*travelSide.dx()
                    +(waterPosition.z()-previousWaterPosition.z())*travelSide.dz();
            double forwardProgress=previousWaterPosition==null?0:(waterPosition.x()-previousWaterPosition.x())*heading.dx()+(waterPosition.z()-previousWaterPosition.z())*heading.dz();
            previousWaterPosition=waterPosition;
            if(design==PumpkinFarmDesign.MELONKINGDE_WATER)detectNativeCapEntry(waterPosition,travelSide);
            if (!toolReady || aim.isLargeError(services.rotation().yaw(), services.rotation().pitch()))
                pauseWater(!toolReady ? "tool not selected" : "aligning creator water-lane aim");
            else if(design==PumpkinFarmDesign.MELONKINGDE_WATER&&!dev.veltraph.client.farming.movement.planner.MelonKingDeCompactAim.ready(services.rotation().pitch()))
                pauseWater("aligning precise native compact ladder/fruit view");
            else if(design==PumpkinFarmDesign.MELONKINGDE_WATER&&tickNativeEntry(waterPosition,grounded,lateralProgress,forwardProgress)) {}
            else if(design==PumpkinFarmDesign.MELONKINGDE_WATER&&tickTerminalStrip(waterPosition)) {return;}
            else waterPlanner.tick();
            if(!waterTickCurrent())return;
            lastBlocker = waterPlanner.blocker();
            return;
        }

        if (!grounded || !toolReady) {
            lastBlocker = !grounded ? "not on ground" : "tool not selected";
            // Hold movement/break released while gated; the planner is not advanced so it cannot
            // re-assert inputs. The aim keeps tracking the heading above.
            releaseWaterMovement();
            services.breaking().setBreaking(false);
            VeltraphLog.verbose(displayName() + ".tick", () -> "gated: " + lastBlocker);
            return;
        }
        lastBlocker = "none";

        if(design==PumpkinFarmDesign.CAPTURED_DEFAULT){
            capturedWallPlanner.tick();lastBlocker=capturedWallPlanner.blocker();lastBreakSuppressed=false;
            if(config.get(FarmingConfig.PUMPKIN_ANTI_GHOST_BLOCK)&&services.breaking().isBreaking()
                    &&services.clock().currentTick()%ANTI_GHOST_RELEASE_PERIOD_TICKS==0){
                services.breaking().setBreaking(false);lastBreakSuppressed=true;
            }
            return;
        }

        if(design==PumpkinFarmDesign.CLASSICAL) {
            sourcePlanner.tick();
            if(!cropTargeted()) services.breaking().setBreaking(false);
            if(config.get(FarmingConfig.PUMPKIN_ANTI_GHOST_BLOCK)
                    && services.clock().currentTick()%ANTI_GHOST_RELEASE_PERIOD_TICKS==0) services.breaking().setBreaking(false);
            lastBlocker=sourcePlanner.state()==PumpkinSourceRowPlanner.State.ON_END_ROW?"gradual decoded gourd turn":"none";
            return;
        }

        PumpkinOnEndRowPlanner.PumpkinRowState before = planner.state();
        planner.tick();

        // Pick up a row-end heading-flip signal and flip the macro-owned heading 180° (gated). The
        // controller then eases the camera to it across the next ticks (no snap), mirroring
        // SShapeRowEngine's SSHAPE_FLIP_HEADING_ON_TURN boustrophedon turn.
        if (planner.consumeHeadingFlip()) {
            if (config.get(FarmingConfig.SSHAPE_FLIP_HEADING_ON_TURN)) {
                Direction prev = heading;
                heading = heading.opposite();
                VeltraphLog.details(displayName(),
                        "row-end heading flip " + prev + " -> " + heading + " (" + headingYaw()
                                + "°); smooth aim will pan");
            } else {
                VeltraphLog.details(displayName(),
                        "row-end flip signalled but SSHAPE_FLIP_HEADING_ON_TURN=false; heading kept "
                                + heading);
            }
        }

        // Full crop cubes can share IDs with decoration. A carved head only authorizes attack
        // when the real crosshair position is a loaded soil/stem crop bed from the captured plot.
        if (services.breaking().isBreaking() && !cropTargeted()) services.breaking().setBreaking(false);

        // PUMPKIN_ANTI_GHOST_BLOCK: avoid rapid ghost breaks by releasing the attack for one tick on a
        // fixed period when enabled. The planner asserts break ON each FARMING/step tick; this overrides
        // it down on the release tick. // inferred: cadence period (see ANTI_GHOST_RELEASE_PERIOD_TICKS).
        lastBreakSuppressed = false;
        if (config.get(FarmingConfig.PUMPKIN_ANTI_GHOST_BLOCK) && services.breaking().isBreaking()) {
            long tick = services.clock().currentTick();
            if (tick % ANTI_GHOST_RELEASE_PERIOD_TICKS == 0L) {
                services.breaking().setBreaking(false);
                lastBreakSuppressed = true;
            }
        }

        if (planner.state() != before) {
            VeltraphLog.details(displayName(),
                    before + " -> " + planner.state() + " heading=" + heading);
        }
    }

    // ---- Smooth aim (mirrors SShapeRowEngine) ----

    private FarmAimController buildAim() {
        FarmAimController.Settings settings = new FarmAimController.Settings(
                config.get(FarmingConfig.ROTATION_MAX_YAW_DEG_PER_TICK).floatValue(),
                config.get(FarmingConfig.ROTATION_MAX_PITCH_DEG_PER_TICK).floatValue(),
                config.get(FarmingConfig.ROTATION_ACCELERATION),
                config.get(FarmingConfig.ROTATION_TOLERANCE_DEG).floatValue(),
                config.get(FarmingConfig.ROTATION_PAUSE_ERROR_DEG).floatValue(),
                config.get(FarmingConfig.ROTATION_USE_MOUSE_GCD),
                config.get(FarmingConfig.ROTATION_MOUSE_SENSITIVITY),
                // Creator water layouts require a held cardinal view. Their probes use this
                // fixed heading, so intentional camera jitter must not change input direction.
                config.get(FarmingConfig.ROTATION_JITTER)
                        && config.get(FarmingConfig.PUMPKIN_FARM_DESIGN)!=PumpkinFarmDesign.SAIGO_CURRENT
                        && config.get(FarmingConfig.PUMPKIN_FARM_DESIGN)!=PumpkinFarmDesign.MELONKINGDE_WATER
                        && config.get(FarmingConfig.PUMPKIN_FARM_DESIGN)!=PumpkinFarmDesign.CAPTURED_DEFAULT,
                config.get(FarmingConfig.ROTATION_JITTER_MAX_DEG).floatValue(),
                config.get(FarmingConfig.ROTATION_JITTER_MIN_INTERVAL_TICKS),
                config.get(FarmingConfig.ROTATION_JITTER_MAX_INTERVAL_TICKS),
                config.get(FarmingConfig.ROTATION_JITTER_HOLD_TICKS));
        return config.get(FarmingConfig.PUMPKIN_FARM_DESIGN)==PumpkinFarmDesign.MELONKINGDE_WATER
                ?new FarmAimController(settings,new Random(),.05f)
                :new FarmAimController(settings,new Random());
    }

    /** Track the macro heading continuously (no ramp reset) and step the smooth aim one tick. */
    private void retargetAndAim() {
        FarmAimController controller=aim;
        if(!waterTickCurrent())return;
        controller.retarget(headingYaw(), farmPitch());
        float curYaw = services.rotation().yaw();if(!waterTickCurrent())return;
        float curPitch = services.rotation().pitch();if(!waterTickCurrent())return;
        float[] next = controller.step(curYaw, curPitch);if(!waterTickCurrent())return;
        services.rotation().aim(next[0], next[1]);if(!waterTickCurrent())return;
        lastAimErr = controller.aimError(curYaw, curPitch);
        VeltraphLog.verbose(displayName() + ".aim", () -> String.format(
                "target=%.1f/%.1f current=%.1f/%.1f err=%.1f", headingYaw(), farmPitch(),
                curYaw, curPitch, lastAimErr));
    }

    /** Whether the smooth aim has reached the macro heading (used to gate the lane step in the planner). */
    private boolean aimAligned() {
        if (aim == null) {
            return false;
        }
        return aim.withinTolerance(services.rotation().yaw(), services.rotation().pitch());
    }

    private float headingYaw() {
        if(design==PumpkinFarmDesign.CLASSICAL)return sourceYaw;
        if(design==PumpkinFarmDesign.CAPTURED_DEFAULT)return capturedWallPlanner.yaw();
        return (float)heading.toYaw();
    }

    /**
     * Design-specific pitch: decoded original view, calibrated captured crop-bed setting, or the
     * explicit signed view supplied by the creator reference.
     */
    private float farmPitch() {
        if(design==PumpkinFarmDesign.CLASSICAL)return sourcePitch;
        if(design==PumpkinFarmDesign.CAPTURED_DEFAULT)return capturedWallPlanner.focusPitch().orElse(config.get(FarmingConfig.PUMPKIN_CAPTURED_PITCH).floatValue());
        // Creator's 1.8.9 HUD reads -58.51° UP. The actual 26.3 north ladder is 3/16
        // thick: native saved-world rays at -58.5 hit it, while the source COMPACT
        // -59 view reaches the third-tier fruit within unmodified 4.5-block reach.
        if (design == PumpkinFarmDesign.MELONKINGDE_WATER) return compactPitch;
        if (design == PumpkinFarmDesign.SAIGO_CURRENT) return 25.8f;
        return config.get(FarmingConfig.FARM_AIM_PITCH).floatValue();
    }

    private boolean classicalLaneWalkable(Direction direction) {
        BlockPos body = services.player().blockPos().offset(direction);
        BlockPos head = body.up();
        BlockPos floor = body.down();
        if (!services.world().isLoaded(body) || !services.world().isLoaded(head)
                || !services.world().isLoaded(floor) || !services.world().isPassable(head)) return false;
        // A full gourd cube is an obstruction that harvesting will clear; it is not an end marker.
        // Decorative heads and unrelated solid blocks keep their normal collision meaning.
        boolean workingFruit = design==PumpkinFarmDesign.CAPTURED_DEFAULT
                ? capturedWorkingFruit(body) : GourdCropTarget.harvestable(services.world(), body);
        if (!services.world().isPassable(body) && !workingFruit) return false;
        return services.world().isSolidFloor(floor)
                || (config.get(FarmingConfig.ALLOW_IRREGULAR_CORNERS)
                && services.world().isLoaded(floor.down()) && services.world().isSolidFloor(floor.down()));
    }

    private boolean sourceLoadedSupport(Direction direction) {
        BlockPos body=GourdSourceGeometry.body(services.player().position()).offset(direction);
        if(!services.world().isLoaded(body)||!services.world().isLoaded(body.up())
                ||!services.world().isLoaded(body.down()))return false;
        // Loaded collisions constrain literal keys naturally, including lower slab paths. They
        // are not themselves the recovered AIR/carpet end predicate.
        return services.world().isSolidFloor(body)||services.world().isSolidFloor(body.down())
                ||(config.get(FarmingConfig.ALLOW_IRREGULAR_CORNERS)
                &&services.world().isLoaded(body.down(2))&&services.world().isSolidFloor(body.down(2)));
    }

    /** The detected two-column bed remains valid when a protected outside stem is replaced. */
    private boolean capturedWorkingFruit(BlockPos position) {
        if(design!=PumpkinFarmDesign.CAPTURED_DEFAULT||!isEnabled()||isSuspended()
                ||config.get(FarmingConfig.PUMPKIN_FARM_DESIGN)!=design||position==null
                ||!services.world().isLoaded(position)||!services.world().isLoaded(position.down()))return false;
        var band=capturedWallPlanner.band();
        if(band==null||!band.contains(position))return false;
        String soil=services.world().blockId(position.down());
        if(!soil.equals("minecraft:dirt")&&!soil.equals("minecraft:farmland")&&!soil.equals("minecraft:grass_block"))return false;
        String id=services.world().blockId(position);
        return id.equals("minecraft:melon")||id.equals("minecraft:pumpkin")||id.equals("minecraft:carved_pumpkin");
    }

    private boolean harvestableInteractionTarget(BlockPos position) {
        if(position==null||!services.world().isLoaded(position))return false;
        if(design==PumpkinFarmDesign.CAPTURED_DEFAULT){
            if(!isEnabled()||isSuspended()||config.get(FarmingConfig.PUMPKIN_FARM_DESIGN)!=design
                    ||capturedWallPlanner.state()!=CapturedGourdWallPlanner.State.FARMING)return false;
            return capturedWorkingFruit(position);
        }
        String id=services.world().blockId(position);
        return id.equals("minecraft:melon")||id.equals("minecraft:pumpkin")
                ||(id.equals("minecraft:carved_pumpkin")&&GourdCropTarget.harvestable(services.world(),position));
    }

    private boolean cropTargeted() {
        if(design==PumpkinFarmDesign.CAPTURED_DEFAULT)return services.breaking().targetBlockPos().filter(this::harvestableInteractionTarget).isPresent();
        String id = services.breaking().targetBlockId();
        if (id.equals("minecraft:melon") || id.equals("minecraft:pumpkin")) return true;
        return id.equals("minecraft:carved_pumpkin") && services.breaking().targetBlockPos()
                .filter(position -> GourdCropTarget.harvestable(services.world(), position)).isPresent();
    }

    // ---- State views ----

    @Override
    public String debugState() {
        if (isWaterDesign())
            return "Pumpkin/Melon/" + design + "/" + waterPlanner.state() + "/" + waterPlanner.side();
        if(design==PumpkinFarmDesign.CLASSICAL)return "Pumpkin/Melon/CLASSICAL/"+sourcePlanner.state()+"/"+sourcePlanner.side();
        if(design==PumpkinFarmDesign.CAPTURED_DEFAULT)return "Pumpkin/Melon/CAPTURED_DEFAULT/"+capturedWallPlanner.state()+"/"+capturedWallPlanner.side();
        return "Pumpkin/Melon/" + planner.state();
    }

    public int capturedPasses(){return capturedWallPlanner.passes();}

    public PumpkinOnEndRowPlanner.PumpkinRowState rowState() {
        if(design==PumpkinFarmDesign.CAPTURED_DEFAULT)return capturedWallPlanner.state()==CapturedGourdWallPlanner.State.FARMING
                ?PumpkinOnEndRowPlanner.PumpkinRowState.FARMING:PumpkinOnEndRowPlanner.PumpkinRowState.ON_END_ROW;
        if(design==PumpkinFarmDesign.CLASSICAL)return sourcePlanner.state()==PumpkinSourceRowPlanner.State.FARMING
                ?PumpkinOnEndRowPlanner.PumpkinRowState.FARMING:PumpkinOnEndRowPlanner.PumpkinRowState.ON_END_ROW;
        return planner.state();
    }

    /** The macro-owned heading currently driving the smooth aim (test/diagnostic view). */
    public Direction heading() {
        if(design==PumpkinFarmDesign.CAPTURED_DEFAULT)return capturedWallPlanner.forward();
        return heading;
    }

    /** The lane side selected for the active row-end step (FORWARD = none). */
    public CapturedGourdWallPlanner.State capturedPhase(){return capturedWallPlanner.state();}
    public CapturedGourdWallPlanner.Band capturedBand(){return capturedWallPlanner.band();}
    public float capturedRequestedYaw(){return capturedWallPlanner.yaw();}
    public TurnDirection laneSide() {
        if (isWaterDesign()) return waterPlanner.side();
        return design==PumpkinFarmDesign.CAPTURED_DEFAULT?capturedWallPlanner.side():design==PumpkinFarmDesign.CLASSICAL?sourcePlanner.side():planner.stepSide();
    }

    /** MKD holds ground/water crop-course keys; Shared travel owns its separate terrain intents. */
    public boolean groundedCropWorkActive(){
        return isEnabled()&&!isSuspended()&&design==PumpkinFarmDesign.MELONKINGDE_WATER
                &&aim!=null&&waterPlanner.state()!=MelonKingDeWaterPlanner.State.BLOCKED;
    }

    public PumpkinFarmDesign design() { return design; }
    public MelonKingDeWaterPlanner.State waterState() { return waterPlanner.state(); }
    @Override public boolean cycleEndReady() {
        return design!=PumpkinFarmDesign.MELONKINGDE_WATER || cornerPlanner.endReady();
    }

    @Override
    public String diagnostics() {
        if(design==PumpkinFarmDesign.CAPTURED_DEFAULT)return "Captured gourd wall="+capturedWallPlanner.band()+" state="+capturedWallPlanner.state()
                +" travel="+capturedWallPlanner.forward()+" literal="+capturedWallPlanner.side()+" yaw="+capturedWallPlanner.yaw()+" pitch="+farmPitch()
                +" passes="+capturedWallPlanner.passes()+" blocker="+capturedWallPlanner.blocker()
                    +" next="+capturedWallPlanner.nextGoal()+" horizontalSpeed="+services.player().horizontalSpeed();
        if(design==PumpkinFarmDesign.CLASSICAL)return "Pumpkin/Melon design=CLASSICAL sourceState="+sourcePlanner.state()
                +" literalSide="+sourcePlanner.side()+" gradualTarget="+sourceYaw+"/"+sourcePitch+" blocker="+lastBlocker;
        if (isWaterDesign())
            return "Pumpkin/Melon design=" + design + " state=" + waterPlanner.state()
                    + " side=" + waterPlanner.side() + " target=" + headingYaw() + "/" + farmPitch() + " aimError=" + lastAimErr
                    + " cropTarget=" + services.breaking().targetBlockId() + " corner=" + cornerPlanner.state()
                    + " cornerDetail=" + cornerPlanner.blocker()
                    + " terminal=" + (terminalStrip==null?"none":terminalInward?"inward":"outward")
                    + " blocker=" + (cornerPlanner.active()?cornerPlanner.blocker():waterPlanner.blocker());
        return String.format(
                "Pumpkin/Melon row=%s heading=%s head=%.0f tgt=%.1f/%.1f err=%.1f%n"
                + "  lane=%s stepping=%b ground=%b tool=%b antiGhost=%b breakSuppressed=%b%n"
                + "  toolSlot=%d blocker=%s",
                planner.state(), heading, headingYaw(),
                aim == null ? headingYaw() : aim.targetYaw(),
                aim == null ? farmPitch() : aim.targetPitch(), lastAimErr,
                planner.stepSide(), planner.stepping(), lastGrounded, lastToolReady,
                config.get(FarmingConfig.PUMPKIN_ANTI_GHOST_BLOCK), lastBreakSuppressed,
                chosenToolSlot(), lastBlocker);
    }

    private final class CapturedWallWorld implements CapturedGourdWallPlanner.World {
        private boolean wall(BlockPos position){
            return services.world().isLoaded(position)&&services.world().isLoaded(position.up())
                    &&!GourdCropTarget.harvestable(services.world(),position)
                    &&(!services.world().isPassable(position)||!services.world().isPassable(position.up()));
        }
        private boolean fruitBed(BlockPos cell){
            return GourdCropTarget.harvestable(services.world(),cell)
                    ||(services.world().isBlock(cell,"minecraft:air")&&GourdCropTarget.cropBed(services.world(),cell,"pumpkin"));
        }
        public java.util.Optional<CapturedGourdWallPlanner.Band> detect(Vec3 position,Direction forward){
            BlockPos base=GourdSourceGeometry.body(position);
            CapturedGourdWallPlanner.Band best=null;int score=Integer.MIN_VALUE;
            for(Direction side:java.util.List.of(forward.rotateYClockwise(),forward.rotateYCounterclockwise()))for(int distance=1;distance<=3;distance++){
                int walls=0,crops=0,own=0;int cropY=base.y();
                for(int depth=0;depth<=5;depth++){
                    BlockPos ahead=base.offset(forward,depth);
                    if(wall(ahead.offset(side,distance)))walls++;
                    for(int y=-1;y<=0;y++){
                        BlockPos ownCell=ahead.add(0,y,0);
                        if(fruitBed(ownCell)){
                            own++;crops++;cropY=ownCell.y();
                            // Only the contiguous other fruit column; never count through a stem/slab wall.
                            BlockPos adjacent=ownCell.offset(side.opposite());
                            if(fruitBed(adjacent))crops++;
                        }
                    }
                }
                int candidate=crops*10+walls-distance*100;
                if(walls>=2&&own>=2&&distance<=2&&candidate>score){
                    int ownAxis=side.dx()!=0?base.x():base.z(),farAxis=ownAxis+(side.dx()!=0?-side.dx():-side.dz());
                    String key=(side.dx()!=0?"X":"Z")+":"+Math.min(ownAxis,farAxis)+":"+Math.max(ownAxis,farAxis)+":"+cropY;
                    score=candidate;best=new CapturedGourdWallPlanner.Band(side,side.opposite(),distance,key,Math.min(ownAxis,farAxis),Math.max(ownAxis,farAxis),cropY);
                }
            }
            return java.util.Optional.ofNullable(best);
        }
        public boolean supported(){
            Vec3 feet=services.player().position();
            return services.world().hasGroundSupport(feet,.3)&&services.world().bodyClear(feet,.3,1.8);
        }
        public boolean forwardLoaded(Direction forward){
            BlockPos body=GourdSourceGeometry.body(services.player().position()).offset(forward);
            return services.world().isLoaded(body)&&services.world().isLoaded(body.up())&&services.world().isLoaded(body.down());
        }
        public boolean forwardBlocked(Direction direction){return !classicalLaneWalkable(direction);}
        public boolean cropTargeted(){return PumpkinMelonFarmModule.this.cropTargeted();}
        public java.util.Optional<CapturedGourdWallPlanner.Focus> nextFocus(CapturedGourdWallPlanner.Band band,Direction forward){
            // User requested only the two working crop columns, with no detached boundary focus.
            return java.util.Optional.empty();
        }
        public boolean focusLoaded(CapturedGourdWallPlanner.Focus focus){return services.world().isLoaded(focus.position());}
        public boolean focusRemoved(CapturedGourdWallPlanner.Focus focus){
            if(!focusLoaded(focus))return false;String id=services.world().blockId(focus.position());
            return !id.equals("minecraft:melon")&&!id.equals("minecraft:pumpkin")&&!id.equals("minecraft:carved_pumpkin");
        }
        public boolean wallGapAhead(CapturedGourdWallPlanner.Band band,Direction forward){
            BlockPos base=GourdSourceGeometry.body(services.player().position());
            for(int depth=0;depth<=3;depth++){
                BlockPos side=base.offset(forward,depth).offset(band.wall(),band.wallDistance());
                if(!services.world().isLoaded(side)||!services.world().isLoaded(side.up()))return false;
                int axis=band.wall().dx()!=0?side.x():side.z();
                boolean knownOutsideColumns=band.minCropAxis()!=Integer.MIN_VALUE
                        &&(axis<band.minCropAxis()||axis>band.maxCropAxis());
                // This probe already has the detected wall role. A solid protected fruit
                // replacement keeps that role; true AIR/head gaps retain their angle handling.
                boolean structuralWall=knownOutsideColumns
                        &&(!services.world().isPassable(side)||!services.world().isPassable(side.up()));
                if(!wall(side)&&!structuralWall)return true;
            }
            return false;
        }
        public boolean requireLeftStart(){return true;}
        public boolean leftStartAlreadySatisfied(CapturedGourdWallPlanner.Band band,Direction forward,Vec3 position){
            Direction left=forward.rotateYCounterclockwise();
            if(band.wall()!=left||band.minCropAxis()==Integer.MIN_VALUE)return false;
            double desired=(left.dx()+left.dz()>0?band.maxCropAxis():band.minCropAxis())+.5;
            double actual=left.dx()!=0?position.x():position.z();
            return Math.abs(actual-desired)<=.20;
        }
        public java.util.Optional<Vec3> leftStartEntry(CapturedGourdWallPlanner.Band band,Direction forward,Vec3 from){
            Direction left=forward.rotateYCounterclockwise();
            if(band.minCropAxis()==Integer.MIN_VALUE)return java.util.Optional.empty();
            double axis=(left.dx()+left.dz()>0?band.maxCropAxis():band.minCropAxis())+.5;
            Vec3 goal=left.dx()!=0?new Vec3(axis,from.y(),from.z()):new Vec3(from.x(),from.y(),axis);
            var candidate=detect(goal,forward);
            return candidate.isPresent()&&candidate.get().wall()==left&&candidate.get().key().equals(band.key())
                    &&dev.veltraph.client.farming.movement.CapturedGourdSweptWalk.clear(services.world(),from,goal)
                    ?java.util.Optional.of(goal):java.util.Optional.empty();
        }
        public boolean turningMovementClear(Vec3 from,Vec3 proposed){
            return dev.veltraph.client.farming.movement.CapturedGourdSweptWalk.momentumClear(services.world(),from,proposed,services.player().horizontalVelocity());
        }
        public boolean movementClear(Vec3 from,Vec3 goal,Direction actualDirection){
            Vec3 projected = actualDirection.dx()!=0
                    ? new Vec3(goal.x(),from.y(),from.z())
                    : new Vec3(from.x(),from.y(),goal.z());
            return dev.veltraph.client.farming.movement.CapturedGourdApproachSafety.clear(services.world(),from,projected,actualDirection);
        }
        public boolean approachClear(CapturedGourdWallPlanner.Next next){
            return next.approach()==null||dev.veltraph.client.farming.movement.CapturedGourdApproachSafety.clear(services.world(),services.player().position(),next.approach(),next.approachDirection());
        }
        public boolean connectorClear(CapturedGourdWallPlanner.Next next){
            Vec3 p=services.player().position();
            if(!services.world().bodyClear(p,.3,1.8)||!services.world().hasGroundSupport(p,.3))return false;
            double distance=next.transfer().dx()!=0?Math.abs(next.entry().x()-p.x()):Math.abs(next.entry().z()-p.z());
            int samples=Math.max(1,(int)Math.ceil(distance*2));
            for(int sample=1;sample<=samples;sample++){
                double along=distance*sample/samples;
                Vec3 probe=new Vec3(p.x()+next.transfer().dx()*along,p.y(),p.z()+next.transfer().dz()*along);
                if(!services.world().bodyClear(probe,.3,1.8)||!services.world().hasGroundSupport(probe,.3))return false;
            }
            return true;
        }
        public java.util.Optional<CapturedGourdWallPlanner.Next> next(CapturedGourdWallPlanner.Band current,Direction forward,java.util.Set<String> visited){
            Vec3 p=services.player().position();BlockPos base=GourdSourceGeometry.body(p);
            // The actor may finish against an outer endcap past the parallel bridge. Find
            // its actual loaded empty walkway plane, including a short supported inward approach.
            for(int plane=-1;plane<=2;plane++){
                BlockPos leadBody=base.offset(forward.opposite(),Math.max(0,plane));
                Vec3 lead=plane<0?p:new Vec3(forward.dx()!=0?leadBody.x()+.5:p.x(),p.y(),forward.dz()!=0?leadBody.z()+.5:p.z());
                double shift=forward.dx()!=0?lead.x()-p.x():lead.z()-p.z();
                Direction approachDirection=forward.dx()!=0?(shift>=0?Direction.EAST:Direction.WEST):(shift>=0?Direction.SOUTH:Direction.NORTH);
                boolean approachClear=true;
                int samples=(int)Math.ceil(Math.abs(shift)*2);
                for(int sample=1;sample<=samples;sample++){
                    double fraction=(double)sample/samples;
                    Vec3 probe=new Vec3(p.x()+(lead.x()-p.x())*fraction,p.y(),p.z()+(lead.z()-p.z())*fraction);
                    if(!services.world().bodyClear(probe,.3,1.8)||!services.world().hasGroundSupport(probe,.3)){approachClear=false;break;}
                }
                if(!approachClear)continue;
                for(Direction move:java.util.List.of(current.wall().opposite(),current.wall()))for(int distance=3;distance<=12;distance++){
                    BlockPos candidate=leadBody.offset(move,distance);boolean clear=true;
                    for(int step=1;step<=distance;step++){
                        Vec3 probe=new Vec3(lead.x()+move.dx()*step,lead.y(),lead.z()+move.dz()*step);
                        if(!services.world().bodyClear(probe,.3,1.8)||!services.world().hasGroundSupport(probe,.3)){clear=false;break;}
                    }
                    if(!clear)break;
                    Vec3 point=new Vec3(move.dx()!=0?candidate.x()+.5:lead.x(),lead.y(),move.dz()!=0?candidate.z()+.5:lead.z());
                    var layout=detect(point,forward.opposite());
                    if(layout.isPresent()&&!visited.contains(layout.get().key()))return java.util.Optional.of(new CapturedGourdWallPlanner.Next(layout.get(),point,move,Math.abs(shift)<.001?null:lead,Math.abs(shift)<.001?null:approachDirection));
                }
            }
            return java.util.Optional.empty();
        }
    }

    /** Bounded source-design adapter. Generic unsupported air never authorizes fluid/climb movement. */
    private final class WaterDesignWorld implements MelonKingDeWaterPlanner.World {
        private BlockPos bodyAt(Vec3 position) {
            return new Vec3(position.x(), position.y() + 0.15, position.z()).toBlockPos();
        }
        private boolean supportBelow(BlockPos body) {
            for (int down = 1; down <= 4; down++) {
                BlockPos p = body.down(down);
                if (!services.world().isLoaded(p)) return false;
                String id = services.world().blockId(p);
                if (design == PumpkinFarmDesign.SAIGO_CURRENT && services.player().onGround()
                        && (id.equals("minecraft:melon") || id.equals("minecraft:pumpkin")
                        || (id.equals("minecraft:carved_pumpkin") && GourdCropTarget.harvestable(services.world(),p)))) {
                    BlockPos originalBed = p.down();
                    return services.world().isLoaded(originalBed) && services.world().isSolidFloor(originalBed)
                            && (services.world().isBlock(originalBed,"minecraft:dirt")
                            || services.world().isBlock(originalBed,"minecraft:farmland"));
                }
                if (design == PumpkinFarmDesign.SAIGO_CURRENT && (id.equals("minecraft:white_wool")
                        || id.equals("minecraft:chiseled_quartz_block") || id.equals("minecraft:farmland")
                        || id.equals("minecraft:quartz_slab"))) return services.world().isSolidFloor(p);
                if (id.equals("minecraft:soul_sand") || id.equals("minecraft:snow")
                        || id.endsWith("_trapdoor") || id.equals("minecraft:polished_andesite")
                        || id.equals("minecraft:dirt")) return services.world().isSolidFloor(p);
                if (!services.world().isPassable(p)) return false;
            }
            return false;
        }
        @Override public boolean currentForTick(){return waterTickCurrent();}
        @Override public boolean fencedRelease(){return design==PumpkinFarmDesign.MELONKINGDE_WATER&&waterTick!=null;}
        @Override public boolean supportedDesignPosition() {
            BlockPos body = bodyAt(services.player().position());
            if (!services.world().isLoaded(body) || !services.world().isLoaded(body.up())
                    || !supportBelow(body)) return false;
            String id = services.world().blockId(body);
            if (id.equals("minecraft:bubble_column")) return false;
            return services.player().onGround() || waterAtFeet() || id.equals("minecraft:ladder");
        }
        private boolean waterAtFeet() {
            BlockPos actualFoot = services.player().position().toBlockPos();
            BlockPos body = bodyAt(services.player().position());
            return services.world().isWater(body) || (services.world().isLoaded(actualFoot)
                    && services.world().isWater(actualFoot)
                    && !services.world().isBlock(actualFoot,"minecraft:bubble_column"));
        }
        private boolean sideProbe(TurnDirection side, boolean obstructed) {
            Vec3 position = services.player().position();
            Direction direction = side.resolve(heading);
            boolean nativeBlocked=false;
            if(design==PumpkinFarmDesign.MELONKINGDE_WATER) {
                Vec3 probe=new Vec3(position.x()+direction.dx()*.35,position.y(),position.z()+direction.dz()*.35);
                // A nonempty voxel can contain a thin panel entirely behind the actual body.
                // Establish complete loaded bounds before interpreting a failed shape query
                // as a known wall; unknown space cannot take the grounded-cap exception.
                for(int x=(int)Math.floor(probe.x()-.3);x<=(int)Math.floor(probe.x()+.3-1e-7);x++)
                    for(int y=(int)Math.floor(probe.y()+.001);y<=(int)Math.floor(probe.y()+1.8-1e-7);y++)
                        for(int z=(int)Math.floor(probe.z()-.3);z<=(int)Math.floor(probe.z()+.3-1e-7);z++)
                            if(!services.world().isLoaded(new BlockPos(x,y,z)))return false;
                nativeBlocked=!services.world().bodyClear(probe,.3,1.8);
            }
            boolean collision = false;
            for (double x : new double[]{-0.29, 0.29}) for (double z : new double[]{-0.29, 0.29}) {
                BlockPos body = bodyAt(new Vec3(position.x() + direction.dx() * 0.35 + x,
                        position.y(), position.z() + direction.dz() * 0.35 + z));
                if (!services.world().isLoaded(body) || !services.world().isLoaded(body.up())) return false;
                boolean blocked = design==PumpkinFarmDesign.MELONKINGDE_WATER?nativeBlocked
                        :!services.world().isPassable(body)||!services.world().isPassable(body.up());
                // A loaded outer cap is an obstruction, not a destination. MK's inner snow
                // strip supports the actor while the retained outer dirt may have no working
                // floor behind its face. Unknown/passable unsupported space still fails closed.
                boolean groundedCap = design == PumpkinFarmDesign.MELONKINGDE_WATER && blocked
                        && services.world().hasGroundSupport(position, .3);
                if (!supportBelow(body) && !groundedCap) return false;
                collision |= blocked;
            }
            return !obstructed || collision;
        }
        @Override public boolean sideLoaded(TurnDirection side) { return sideProbe(side, false); }
        @Override public boolean sideObstructed(TurnDirection side) { return sideProbe(side, true); }
        private boolean transferSourceCurrent(Vec3 original,Direction originalHeading,FarmAimController originalAim) {
            if(!waterTickCurrent()||original==null||!Double.isFinite(original.x())||!Double.isFinite(original.y())||!Double.isFinite(original.z())
                    ||originalAim==null||aim!=originalAim||design!=PumpkinFarmDesign.MELONKINGDE_WATER
                    ||heading!=originalHeading||!isEnabled()||isSuspended())return false;
            if(config.get(FarmingConfig.PUMPKIN_FARM_DESIGN)!=PumpkinFarmDesign.MELONKINGDE_WATER
                    ||aim!=originalAim||design!=PumpkinFarmDesign.MELONKINGDE_WATER
                    ||heading!=originalHeading||!isEnabled()||isSuspended())return false;
            if(!waterTickCurrent())return false;
            Vec3 actual=services.player().position();
            // onEnable always creates a fresh private aim. Never adopt a replacement
            // activation even when a callback restores the same design/view/position.
            return waterTickCurrent()&&original.equals(actual)&&aim==originalAim&&design==PumpkinFarmDesign.MELONKINGDE_WATER
                    &&heading==originalHeading&&isEnabled()&&!isSuspended();
        }
        private boolean shapeTransferClear(Vec3 original,Direction originalHeading,FarmAimController originalAim) {
            if(!waterTickCurrent()||original==null||!Double.isFinite(original.x())||!Double.isFinite(original.y())||!Double.isFinite(original.z())
                    ||Math.abs(original.x())>30_000_000||Math.abs(original.z())>30_000_000||Math.abs(original.y())>4096
                    ||!transferSourceCurrent(original,originalHeading,originalAim))return false;
            if(services.world() instanceof dev.veltraph.client.service.mc.McWorldService nativeWorld)
                return nativeWorld.melonKingTransferClear(original,originalHeading,()->transferSourceCurrent(original,originalHeading,originalAim));
            Vec3 probe=new Vec3(original.x()+originalHeading.dx()*.35,original.y(),original.z()+originalHeading.dz()*.35);
            for(int x=(int)Math.floor(probe.x()-.3);x<=(int)Math.floor(probe.x()+.3-1e-7);x++)
                for(int y=(int)Math.floor(probe.y()-.025);y<=(int)Math.floor(probe.y()+1.8-1e-7);y++)
                    for(int z=(int)Math.floor(probe.z()-.3);z<=(int)Math.floor(probe.z()+.3-1e-7);z++) {
                        if(!services.world().isLoaded(new BlockPos(x,y,z))||!transferSourceCurrent(original,originalHeading,originalAim))return false;
                    }
            if(!services.world().bodyClear(probe,.3,1.8)||!transferSourceCurrent(original,originalHeading,originalAim))return false;
            return services.world().hasGroundSupport(probe,.3)&&transferSourceCurrent(original,originalHeading,originalAim);
        }
        @Override public boolean transferClear() {
            if(design==PumpkinFarmDesign.MELONKINGDE_WATER) {
                // Capture the existing per-enable negative witness before any provider.
                FarmAimController originalAim=aim;Direction originalHeading=heading;
                Vec3 original=services.player().position();
                return shapeTransferClear(original,originalHeading,originalAim);
            }
            Vec3 p = services.player().position();
            for (double x : new double[]{-0.29, 0.29}) for (double z : new double[]{-0.29, 0.29}) {
                BlockPos body = bodyAt(new Vec3(p.x()+heading.dx()*0.35+x,p.y(),p.z()+heading.dz()*0.35+z));
                boolean breakableBody = cropBlock(body) && cropTargeted();
                boolean breakableHead = cropBlock(body.up()) && cropTargeted();
                // Actual SAIGO corner drops one block, then climbs from the falling-water column
                // onto the next wool-backed working water row. Keeping W into this precise loaded
                // water ledge lets ordinary vanilla fluid collision climb; no jump/position writes.
                boolean waterLedge = design == PumpkinFarmDesign.SAIGO_CURRENT && waterAtFeet()
                        && services.world().isBlock(body,"minecraft:white_wool")
                        && services.world().isWater(body.up()) && services.world().isLoaded(body.up(2))
                        && services.world().isPassable(body.up(2));
                if (!services.world().isLoaded(body) || !services.world().isLoaded(body.up())
                        || !supportBelow(body) || (!services.world().isPassable(body) && !breakableBody && !waterLedge)
                        || (!services.world().isPassable(body.up()) && !breakableHead)) return false;
            }
            return true;
        }
        @Override public int nextLaneDistance(TurnDirection side) {
            BlockPos current = bodyAt(services.player().position());
            Direction inward = side.opposite().resolve(heading);
            if(design==PumpkinFarmDesign.MELONKINGDE_WATER)
                return dev.veltraph.client.farming.movement.planner.MelonKingDeWalkingLaneMarker.nextDistance(services.world(),current,heading,inward);
            int maxSpacing = design == PumpkinFarmDesign.SAIGO_CURRENT ? 6 : 4;
            int maxInward = design == PumpkinFarmDesign.SAIGO_CURRENT ? 5 : 1;
            for (int distance = 1; distance <= maxSpacing; distance++) for (int depth=1;depth<=maxInward;depth++) {
                BlockPos candidate = current.offset(heading,distance).offset(inward,depth);
                for (int height = -1; height <= 3; height++) {
                    BlockPos probe = candidate.add(0,height,0);
                    if (!services.world().isLoaded(probe)) return -1;
                    boolean workingFloor = design != PumpkinFarmDesign.SAIGO_CURRENT
                            || services.world().isBlock(probe.down(),"minecraft:white_wool");
                    if (workingFloor && services.world().isWater(probe)
                            && !services.world().isBlock(probe,"minecraft:bubble_column") && supportBelow(probe))
                        return distance;
                }
            }
            return -1;
        }
        @Override public boolean cropTargeted() {
            return PumpkinMelonFarmModule.this.cropTargeted();
        }
        private boolean cropBlock(BlockPos position) {
            String id = services.world().blockId(position);
            return id.equals("minecraft:melon") || id.equals("minecraft:pumpkin")
                    || (id.equals("minecraft:carved_pumpkin") && GourdCropTarget.harvestable(services.world(),position));
        }
        @Override public double forwardCoordinate(Vec3 position) {
            return position.x()*heading.dx()+position.z()*heading.dz();
        }
        @Override public boolean forwardWhileFarming() {
            if(design!=PumpkinFarmDesign.MELONKINGDE_WATER)return true;
            Vec3 position=services.player().position();
            BlockPos front=bodyAt(position).offset(heading);
            if(!services.world().isLoaded(front)||services.world().isPassable(front))return false;
            // FarmHelper's cardinal contact windows expressed in the held forward axis.
            // Floor-based fractions also preserve the source windows at negative coordinates.
            double coordinate=forwardCoordinate(position);
            double fraction=coordinate-Math.floor(coordinate);
            return fraction>.10&&fraction<.65;
        }
        @Override public boolean sprintWhileFarming() { return design == PumpkinFarmDesign.SAIGO_CURRENT; }
        @Override public boolean reverseBeforeTransfer() { return design == PumpkinFarmDesign.SAIGO_CURRENT; }
        @Override public double obstructionMotion(Vec3 current,Vec3 previous) {
            // MK retains W into a transverse opening. Forward progress must not hide the
            // observed stopped side axis, or the first connector is crossed before detection.
            if(design==PumpkinFarmDesign.MELONKINGDE_WATER)
                return Math.abs((current.x()-previous.x())*heading.dz()-(current.z()-previous.z())*heading.dx());
            return current.horizontalDistanceTo(previous);
        }
    }

    /** Passive exact maintenance checkpoint. No services, geometry, clocks or planner callbacks run. */
    public dev.veltraph.client.farming.module.FarmMaintenanceState maintenanceState() {
        if(waterTick!=null)throw new IllegalStateException("Active MKD invocation cannot be checkpointed");
        return dev.veltraph.client.farming.module.FarmMaintenanceState.of(this,
                "profile", profile,
                "planner", planner == null ? null : planner.maintenanceState(),
                "waterPlanner", waterPlanner == null ? null : waterPlanner.maintenanceState(),
                "sourcePlanner", sourcePlanner == null ? null : sourcePlanner.maintenanceState(),
                "capturedWallPlanner", capturedWallPlanner == null ? null : capturedWallPlanner.maintenanceState(),
                "cornerPlanner", cornerPlanner == null ? null : cornerPlanner.maintenanceState(),
                "sourceYaw", sourceYaw,
                "sourcePitch", sourcePitch,
                "compactPitch", compactPitch,
                "previousWaterPosition", previousWaterPosition,
                "nativeEntryWater", nativeEntryWater,
                "terminalStrip", terminalStrip,
                "terminalInward", terminalInward,
                "terminalTicks", terminalTicks,
                "waterTick", waterTick,
                "design", design,
                "heading", heading,
                "aim", aim == null ? null : aim.maintenanceState(),
                "lastGrounded", lastGrounded,
                "lastToolReady", lastToolReady,
                "lastBreakSuppressed", lastBreakSuppressed,
                "lastAimErr", lastAimErr,
                "lastBlocker", lastBlocker);
    }
    @Override protected java.util.Optional<FarmMaintenanceState> maintenanceProgress(){
        return waterTick!=null?java.util.Optional.empty():java.util.Optional.of(maintenanceState());
    }
}
