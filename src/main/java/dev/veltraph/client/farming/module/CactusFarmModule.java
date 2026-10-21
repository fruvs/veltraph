package dev.veltraph.client.farming.module;

import dev.veltraph.client.config.ConfigService;
import dev.veltraph.client.config.ConfigKey;
import dev.veltraph.client.config.LaneSide;
import dev.veltraph.client.farming.movement.FarmAimController;
import dev.veltraph.client.farming.movement.planner.CactusAcePlanner;
import dev.veltraph.client.farming.movement.planner.CactusAceRecentering;
import dev.veltraph.client.farming.movement.planner.CactusAceDeferredHarvest;
import dev.veltraph.client.farming.domain.*;
import dev.veltraph.client.config.FarmingConfig;
import dev.veltraph.client.farming.domain.FarmLayoutProfile;
import dev.veltraph.client.farming.domain.FarmLayoutRegistry;
import dev.veltraph.client.farming.movement.MovementDirectionEngine;
import dev.veltraph.client.farming.movement.MovementState;
import dev.veltraph.client.farming.movement.RowGeometry;
import dev.veltraph.client.farming.movement.WorldRowGeometry;
import dev.veltraph.client.service.FarmServices;

import java.util.List;

/**
 * Cactus farming module using the decoded shared row engine. Cactus inherits that engine wholesale
 * (see {@code CactusPumpkinCrossCheck.md}), so it is implemented directly on
 * {@link MovementDirectionEngine} with a deterministic block-ahead row geometry. This mechanism does not establish full
 * five-plot author-design/reset compatibility.
 */
public final class CactusFarmModule extends FarmModule {

    public static final String ID = "cactus";

    public static final ConfigKey<Boolean> ACE_PTP=ConfigKey.ofBool("CACTUS_ACE_PTP",false);
    public static final ConfigKey<LaneSide> ACE_START_SIDE=ConfigKey.ofEnum("CACTUS_ACE_START_SIDE",LaneSide.RIGHT);
    private static final FarmLayoutProfile ACE_PROFILE=new FarmLayoutProfile("cactus_ace_ptp","Creator Ace Cactus PTP 5 plot",FarmCard.CACTUS,
            CropType.CACTUS,FarmMovementMode.STRAIGHT,RecoveryStatus.LOCAL_CUSTOM,List.of("CACTUS_ACE_PTP","CACTUS_ACE_START_SIDE","FARM_CYCLE_RESET_ENABLED","REWARP_COMMAND"),
            "Creator463/DepthStriderIII, fixed cardinal/0, A/D. Record actual source /tptoplot START/END; reset must be observed before resuming.");
    private final CactusAcePlanner acePlanner=new CactusAcePlanner();
    private final CactusAceRecentering aceRecentering=new CactusAceRecentering();
    private final CactusAceDeferredHarvest aceDeferredHarvest=new CactusAceDeferredHarvest();
    private final java.util.Set<dev.veltraph.client.world.BlockPos> aceInterruptedTargets=new java.util.HashSet<>();
    private boolean ace;
    private boolean aceTerminalClear;
    private boolean aceReceiptKnown;
    private String aceRecoveryFailure="none";
    private java.util.Set<dev.veltraph.client.world.BlockPos> acePendingTargets=java.util.Set.of();
    private java.util.Optional<dev.veltraph.client.world.BlockPos> aceFreshUpper=java.util.Optional.empty();
    private FarmAimController aceAim;
    public void setAcePtp(boolean enabled){if(isEnabled())throw new IllegalStateException("Stop cactus before changing layout");config.set(ACE_PTP,enabled);}
    public boolean acePtp(){return config.get(ACE_PTP);}
    public boolean atAceEnd(){return ace&&acePlanner.state()==CactusAcePlanner.State.AT_END&&aceTerminalClear;}
    @Override public boolean cycleEndReady(){return !config.get(ACE_PTP)||atAceEnd();}
    public int aceReversals(){return acePlanner.reversals();}
    public boolean aceRecenteringActive(){return ace&&aceRecentering.active();}
    public CactusAceRecentering.State aceRecenteringState(){return aceRecentering.state();}
    public boolean aceDeferredHarvestActive(){return ace&&aceDeferredHarvest.active();}
    public CactusAceDeferredHarvest.State aceDeferredHarvestState(){return aceDeferredHarvest.state();}
    private final FarmLayoutProfile profile;
    private final MovementDirectionEngine engine;

    public CactusFarmModule(FarmServices services, ConfigService config, FarmLayoutRegistry registry) {
        super(ID, "Cactus", services, config, registry);
        this.profile = registry.byId(FarmLayoutRegistry.CACTUS_STRAIGHT).orElseThrow();
        RowGeometry geometry = new WorldRowGeometry(config, services.player(), services.world());
        this.engine = new MovementDirectionEngine(config, services, geometry,
                FarmingConfig.SSHAPE_HOLD_FORWARD_POLICY, this::toolReady);
    }

    @Override
    protected List<String> toolKeywords() {
        return List.of("cactus", "knife");
    }

    @Override
    public FarmLayoutProfile activeProfile() {
        return config.get(ACE_PTP)?ACE_PROFILE:profile;
    }

    @Override
    public void onEnable() {
        ace=config.get(ACE_PTP);aceRecoveryFailure="none";aceRecentering.reset();aceDeferredHarvest.reset();aceInterruptedTargets.clear();aceTerminalClear=false;aceReceiptKnown=false;acePendingTargets=java.util.Set.of();aceFreshUpper=java.util.Optional.empty();
        if(ace){
            acePlanner.start(services.rotation().yaw(),config.get(ACE_START_SIDE));
            aceAim=new FarmAimController(new FarmAimController.Settings(config.get(FarmingConfig.ROTATION_MAX_YAW_DEG_PER_TICK).floatValue(),
                    config.get(FarmingConfig.ROTATION_MAX_PITCH_DEG_PER_TICK).floatValue(),config.get(FarmingConfig.ROTATION_ACCELERATION),
                    config.get(FarmingConfig.ROTATION_TOLERANCE_DEG).floatValue(),config.get(FarmingConfig.ROTATION_PAUSE_ERROR_DEG).floatValue(),
                    config.get(FarmingConfig.ROTATION_USE_MOUSE_GCD),config.get(FarmingConfig.ROTATION_MOUSE_SENSITIVITY),false,0,1,1,0),new java.util.Random(0));
            aceAim.setTarget(acePlanner.yaw(),0);
        }else engine.start();
        installCropGuard();
    }

    private void installCropGuard() {
        installFarmTargetGuard(position->isEnabled()
                && services.world().isLoaded(position) && services.world().isLoaded(position.down())
                && services.world().isBlock(position,"minecraft:cactus")
                && services.world().isBlock(position.down(),"minecraft:cactus"));
    }
    @Override protected void onResumeAfterRewarp() { aceRecentering.reset();aceDeferredHarvest.reset();aceInterruptedTargets.clear();installCropGuard(); }

    @Override
    protected void onDisableFarming() {
        clearFarmTargetGuard();
        aceRecentering.reset();aceDeferredHarvest.reset();aceInterruptedTargets.clear();acePlanner.stop();engine.stop();
        super.onDisableFarming();
    }

    @Override
    protected void onTickFarming() {
        if(ace){tickAce();return;}
        engine.tick();
        // The actual crosshair must be an upper cactus segment. Preserve planted bases and supports.
        if (services.breaking().isBreaking()) {
            boolean upper=services.breaking().targetBlockId().equals("minecraft:cactus")
                    && services.breaking().targetBlockPos().filter(p->services.world().isLoaded(p.down())
                            && services.world().isBlock(p.down(),"minecraft:cactus")).isPresent();
            services.breaking().setBreaking(upper);
        }
    }

    @Override
    public String debugState() {
        return ace?"Cactus/Ace/"+acePlanner.state()+"/"+acePlanner.side()+"/recenter="+aceRecentering.state()+"/deferred="+aceDeferredHarvest.state():"Cactus/"+engine.state();
    }

    @Override
    public String diagnostics() {
        return ace?"Cactus/Ace yaw="+acePlanner.yaw()+" side="+acePlanner.side()+" reversals="+acePlanner.reversals()+" "+acePlanner.reason()+" receiptKnown="+aceReceiptKnown+" pending="+acePendingTargets+" freshUpper="+aceFreshUpper+" recenter="+aceRecentering.state()+"/"+aceRecentering.reason()+" deferred="+aceDeferredHarvest.state()+"/"+aceDeferredHarvest.reason()+" deferredTargets="+aceDeferredHarvest.targets()+" recoveryFailure="+aceRecoveryFailure:"Cactus toolSlot="+chosenToolSlot()+" "+engine.aimDebug();
    }

    public MovementState movementState() {
        return ace?acePlanner.state()==CactusAcePlanner.State.STOPPED?MovementState.STOPPED:MovementState.FARMING:engine.state();
    }
    private void tickAce(){
        aceAim.retarget(acePlanner.yaw(),0);float[] view=aceAim.step(services.rotation().yaw(),services.rotation().pitch());services.rotation().aim(view[0],view[1]);
        if(!toolReady()||aceAim.aimError(services.rotation().yaw(),services.rotation().pitch())>1){releaseInputs();return;}
        var receipt=services.breaking().pendingBlockChanges(this);
        aceReceiptKnown=receipt.isPresent();
        if(receipt.isEmpty()){aceTerminalClear=false;releaseInputs();if(aceRecentering.active()||aceDeferredHarvest.active()){aceRecentering.cancel("native owner/world/cursor receipt unavailable");aceRecoveryFailure=aceRecentering.reason();setEnabled(false);}return;}
        // Track the actual native attempted coordinates, even while prediction renders them AIR.
        // The native snapshot is bound to this exact guard owner/player/world/interaction context.
        acePendingTargets=receipt.get();
        aceFreshUpper=services.breaking().freshTargetBlockPos().filter(p->services.world().isLoaded(p)
                &&services.world().isLoaded(p.down())&&services.world().isBlock(p,"minecraft:cactus")
                &&services.world().isBlock(p.down(),"minecraft:cactus"));
        if(aceRecentering.active()){
            releaseInputs();
            var keys=aceRecentering.tick(services.world(),services.player().position(),services.player().onGround(),services.clock().currentTick(),acePendingTargets.isEmpty());
            if(aceRecentering.state()==CactusAceRecentering.State.CANCELLED){aceRecoveryFailure=aceRecentering.reason();setEnabled(false);return;}
            if(aceRecentering.active()){services.input().setSneakHeld(keys.sneak());services.input().setForwardHeld(keys.forward());services.input().setBackHeld(keys.back());return;}
            // Preserve and finish actual upper targets bypassed by passive longitudinal coasting.
            if(!aceInterruptedTargets.isEmpty()&&!aceDeferredHarvest.begin(services.world(),services.player().position(),dev.veltraph.client.world.Direction.fromYaw(acePlanner.yaw()),aceInterruptedTargets,services.clock().currentTick())){aceRecoveryFailure=aceDeferredHarvest.reason();setEnabled(false);return;}
            aceInterruptedTargets.clear();
            // COMPLETE is an actual observed restored footing; ordinary crop actions remain guarded.
        }
        if(aceDeferredHarvest.active()){
            releaseInputs();
            var keys=aceDeferredHarvest.tick(services.world(),services.player().position(),services.player().eyePosition(),services.player().blockInteractionRange(),services.clock().currentTick(),acePendingTargets,aceFreshUpper);
            if(aceDeferredHarvest.state()==CactusAceDeferredHarvest.State.CANCELLED){aceRecoveryFailure=aceDeferredHarvest.reason();setEnabled(false);return;}
            if(aceDeferredHarvest.active()){services.input().setLeftHeld(keys.left());services.input().setRightHeld(keys.right());services.input().setSneakHeld(keys.sneak());services.breaking().setBreaking(keys.attack());return;}
        }
        if(!acePlanner.tick(services.world(),services.player().position(),aceFreshUpper.isPresent()||!acePendingTargets.isEmpty())){
            releaseInputs();
            if(acePlanner.reason().contains("feet raised above authored bottom-slab")){
                aceFreshUpper.ifPresent(aceInterruptedTargets::add);aceInterruptedTargets.addAll(acePendingTargets);
                if(!aceRecentering.begin(services.world(),services.player().position(),dev.veltraph.client.world.Direction.fromYaw(acePlanner.yaw()),services.clock().currentTick())){aceRecoveryFailure=aceRecentering.reason();setEnabled(false);}
                return;
            }
            if(acePlanner.state()==CactusAcePlanner.State.AT_END){
                var feet=services.player().position();
                if(feet==null||!services.player().isAvailable()||!services.world().isLoaded(feet.toBlockPos())
                        ||!services.world().bodyClear(feet,.3,1.8)||!services.world().hasGroundSupport(feet,.3)){aceTerminalClear=false;return;}
                // Finish the genuinely reachable upper target at the native fence before reset.
                // A read-only fresh pick avoids the rendered just-destroyed-cell lag; vanilla
                // still owns the actual attack and its upper-only target guard.
                boolean upper=aceFreshUpper.isPresent();
                services.breaking().setBreaking(upper);aceTerminalClear=!upper&&acePendingTargets.isEmpty();
            }
            return;
        }
        services.input().setForwardHeld(false);services.input().setBackHeld(false);services.input().setSprintHeld(false);
        boolean moving=acePlanner.state()!=CactusAcePlanner.State.HARVESTING_FENCE;
        services.input().setLeftHeld(moving&&acePlanner.side()==LaneSide.LEFT);services.input().setRightHeld(moving&&acePlanner.side()==LaneSide.RIGHT);
        // Creator holds attack through gaps. The rendered hit result may still point to the
        // cactus just predicted as air; gating here drops the next staggered column. The
        // installed guard checks every actual vanilla start/continue target and preserves bases.
        services.breaking().setBreaking(true);
    }


    /** Passive exact maintenance checkpoint. No services, geometry, clocks or planner callbacks run. */
    public dev.veltraph.client.farming.module.FarmMaintenanceState maintenanceState() {
        return dev.veltraph.client.farming.module.FarmMaintenanceState.of(this,
                "acePlanner", acePlanner == null ? null : acePlanner.maintenanceState(),
                "aceRecentering", aceRecentering == null ? null : aceRecentering.maintenanceState(),
                "aceDeferredHarvest", aceDeferredHarvest == null ? null : aceDeferredHarvest.maintenanceState(),
                "aceInterruptedTargets", aceInterruptedTargets,
                "ace", ace,
                "aceTerminalClear", aceTerminalClear,
                "aceReceiptKnown", aceReceiptKnown,
                "aceRecoveryFailure", aceRecoveryFailure,
                "acePendingTargets", acePendingTargets,
                "aceFreshUpper", aceFreshUpper,
                "aceAim", aceAim == null ? null : aceAim.maintenanceState(),
                "profile", profile,
                "engine", engine == null ? null : engine.maintenanceState());
    }
    @Override protected java.util.Optional<FarmMaintenanceState> maintenanceProgress(){
        return java.util.Optional.of(maintenanceState());
    }
}
