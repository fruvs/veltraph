package dev.veltraph.client.rewarp.mc;

import dev.veltraph.client.config.*;
import dev.veltraph.client.core.Module;
import dev.veltraph.client.core.ModuleManager;
import dev.veltraph.client.farming.module.FarmModule;
import dev.veltraph.client.game.*;
import dev.veltraph.client.rewarp.*;
import dev.veltraph.client.service.FarmServices;
import dev.veltraph.client.world.Vec3;
import net.minecraft.client.Minecraft;
import java.util.List;

/** Same-world owned automatic recovery, including the handoff before running location enforcement. */
public final class McFarmAutoRecoveryCoordinator {
    private final Minecraft client;private final FarmServices services;private final ConfigService config;
    private final ModuleManager modules;private final GameContextService context;private final RewarpService rewarps;
    private final McSavedAnchorRecovery returning;private final AutoRewarpRuntime runtime;
    private final StuckTracker stuck=new StuckTracker();
    private record Scope(Object player,Object world,Object connection,List<FarmModule> farms,List<String> profiles,FarmReturnPoint pose){}
    private Scope healthy,pinned;
    private boolean preGuardAdvanced;
    public McFarmAutoRecoveryCoordinator(Minecraft client,FarmServices services,ConfigService config,
                                        ModuleManager modules,GameContextService context,RewarpService rewarps,RewarpWarpExecutor commands){
        this.client=client;this.services=services;this.config=config;this.modules=modules;this.context=context;this.rewarps=rewarps;
        returning=new McSavedAnchorRecovery(client,services,config);
        runtime=new AutoRewarpRuntime(new AutoRewarpRuntime.Actions(){
            public void suspendFarming(){
                if(!matches(healthy,false))throw new IllegalStateException("No healthy same-player/world/connection farm capture");
                pinned=healthy;pinned.farms().forEach(FarmModule::suspendForRewarp);services.rotation().cancelRotation();
            }
            public boolean sendRewarp(){
                if(!owned())return false;
                rewarps.clearPending();return commands.execute(config.get(FarmingConfig.REWARP_COMMAND));
            }
            public boolean startFarmReturn(){return owned()&&returning.start(pinned.pose(),context.current().area());}
            public AutoRewarpRuntime.Return tickFarmReturn(){
                if(!owned())return AutoRewarpRuntime.Return.CANCELLED;
                returning.tick(context.current().area());
                return returning.arrived(context.current().area())?AutoRewarpRuntime.Return.ARRIVED
                        :returning.active()?AutoRewarpRuntime.Return.ACTIVE:AutoRewarpRuntime.Return.FAILED;
            }
            public void resumeFarming(){
                if(!owned()||context.current().area()!=GameArea.GARDEN||!returning.arrived(GameArea.GARDEN))
                    throw new IllegalStateException("Captured farm changed before exact-pose resume");
                pinned.farms().forEach(FarmModule::resumeAfterRewarp);stuck.reset();
            }
            public void cancelFarmReturn(){returning.cancel();}
            public void stopMacro(){stopCaptured();}
        });
    }
    private void checkThread(){if(!client.isSameThread())throw new IllegalStateException("Farm recovery requires client thread");}
    private List<FarmModule> farms(){return modules.enabledControllers().stream().filter(FarmModule.class::isInstance).map(FarmModule.class::cast).toList();}
    private boolean known(GameContext ctx){return ctx.onHypixel()&&ctx.inSkyBlock()&&ctx.area().isSkyBlock()&&ctx.area()!=GameArea.UNKNOWN&&ctx.area()!=GameArea.LIMBO;}
    private boolean valid(GameContext ctx){return known(ctx)&&client.player!=null&&client.level!=null&&client.getConnection()!=null
            &&client.player.isAlive()&&client.gui.screen()==null;}
    private List<String> profiles(List<FarmModule> owners){return owners.stream().map(McFarmCycleCoordinator::profileId).toList();}
    private boolean matches(Scope scope,boolean suspended){
        var owners=farms();
        if(scope==null||scope.player()!=client.player||scope.world()!=client.level||scope.connection()!=client.getConnection()
                ||owners.size()!=scope.farms().size()||owners.isEmpty()||modules.enabledControllers().size()!=owners.size()
                ||!profiles(owners).equals(scope.profiles()))return false;
        for(int i=0;i<owners.size();i++)if(owners.get(i)!=scope.farms().get(i)||!owners.get(i).isEnabled()
                ||owners.get(i).isSuspendedForCycleReset()||owners.get(i).isSuspendedForRewarp()!=suspended
                ||owners.get(i).isSuspendedForMaintenance())return false;
        // Identity/custody validation completes for every owner before any recovery selection.
        // Idle/healthy observations only read; an issued pinned recovery retains its tool repair.
        boolean ownsIssuedReturn=suspended&&scope==pinned&&enabled()&&runtime.state()!=AutoRewarpState.IDLE;
        return owners.stream().allMatch(farm->dev.veltraph.client.rewarp.FarmRecoveryToolObservation.ready(
                ownsIssuedReturn,farm::selectedFarmingToolReady,()->issuedToolReady(farm)));
    }
    private ToolSelectionSource selectingTool;
    /** Closed source from this actual issued coordinator invocation; public scopes/predicates cannot create it. */
    public final class ToolSelectionSource {
        private final FarmModule farm;private final Object player,world,connection,inventory;
        private final ModuleManager.LifecycleSnapshot lifecycle;
        private final dev.veltraph.client.input.ScriptingInputLock.ControlSnapshot control;
        private final dev.veltraph.client.service.mc.McOwnedRotation.InstallationSnapshot installation;
        private final String profile;private final int tick;private final double x,y,z;private final float yaw,pitch;
        private boolean claimed;
        private final Scope original;private final AutoRewarpState phase;
        private ToolSelectionSource(FarmModule farm) {
            this.farm=farm;player=client.player;world=client.level;connection=client.getConnection();inventory=client.player.getInventory();
            lifecycle=modules.lifecycleSnapshot();control=dev.veltraph.client.input.ScriptingInputLock.controlSnapshot();
            installation=dev.veltraph.client.service.mc.McOwnedRotation.installationSnapshot(client);profile=McFarmCycleCoordinator.profileId(farm);
            tick=client.player.tickCount;x=client.player.getX();y=client.player.getY();z=client.player.getZ();yaw=client.player.getYRot();pitch=client.player.getXRot();
            original=pinned;phase=runtime.state();
        }
        private boolean nativeCurrent(FarmModule expected,dev.veltraph.client.service.mc.McInventoryService expectedInventory) {
            return selectingTool==this&&dev.veltraph.client.VeltraphBootstrap.actualFarmToolCoordinator(client,McFarmAutoRecoveryCoordinator.this)
                    &&pinned==original&&original!=null&&original.player()==player&&original.world()==world&&original.connection()==connection
                    &&original.farms().stream().anyMatch(v->v==farm)&&runtime.state()==phase&&phase!=AutoRewarpState.IDLE
                    &&farm.isSuspendedForRewarp()&&!farm.isSuspendedForCycleReset()
                    &&modules==dev.veltraph.client.VeltraphBootstrap.moduleManager()&&config==dev.veltraph.client.VeltraphBootstrap.config()
                    &&expected==farm&&expectedInventory==services.inventory()&&client.isSameThread()
                    &&client.player==player&&client.level==world&&client.getConnection()==connection&&client.player!=null
                    &&client.player.getInventory()==inventory&&client.player.tickCount==tick
                    &&client.player.getX()==x&&client.player.getY()==y&&client.player.getZ()==z
                    &&client.player.getYRot()==yaw&&client.player.getXRot()==pitch
                    &&lifecycle!=null&&lifecycle.known()&&modules.lifecycleCurrent(lifecycle)
                    &&control!=null&&control.current()&&control.active()&&farm.id().equals(control.ownerModuleId())
                    &&installation!=null&&installation.current()&&farm.isEnabled()&&!farm.isSuspendedForMaintenance();
        }
        public boolean current(FarmModule expected,dev.veltraph.client.service.mc.McInventoryService expectedInventory) {
            return nativeCurrent(expected,expectedInventory)&&config.get(FarmingConfig.AUTO_REWARP_ENABLED)
                    &&(context.current().area()==GameArea.GARDEN||config.get(FarmingConfig.AUTO_REWARP_ON_LEAVE_AREA))
                    &&modules.inputOwner().orElse(null)==farm
                    &&dev.veltraph.client.service.mc.McOwnedRotation.authority(client)==(phase==AutoRewarpState.RETURNING?McFarmAutoRecoveryCoordinator.this:null)
                    &&profile.equals(McFarmCycleCoordinator.profileId(farm))&&nativeCurrent(expected,expectedInventory);
        }
        public boolean claim(FarmModule expected,dev.veltraph.client.service.mc.McInventoryService expectedInventory) {
            if(claimed||!current(expected,expectedInventory))return false;claimed=true;return true;
        }
    }
    private boolean issuedToolReady(FarmModule farm) {
        if(!dev.veltraph.client.VeltraphBootstrap.actualFarmToolCoordinator(client,this))return false;
        if(selectingTool!=null||client.player==null||client.level==null||client.getConnection()==null)return false;
        var source=new ToolSelectionSource(farm);selectingTool=source;
        try{return dev.veltraph.client.service.mc.McFarmToolRecoveryContext.withSource(source,farm::toolReadyForRewarp);}
        finally{selectingTool=null;}
    }

    private boolean enabled(){return config.get(FarmingConfig.AUTO_REWARP_ENABLED);}
    private boolean leaveEnabled(){return enabled()&&config.get(FarmingConfig.AUTO_REWARP_ON_LEAVE_AREA);}
    private boolean owned(){return valid(context.current())&&enabled()&&matches(pinned,true);}
    /** Called before the activation guard and any module/controller tick. No inactive wrong-area farming is exempt. */
    public void beforeLocationGuard(){
        checkThread();preGuardAdvanced=false;var ctx=context.current();
        if(runtime.state()!=AutoRewarpState.IDLE){
            if(!owned()||(ctx.area()!=GameArea.GARDEN&&!leaveEnabled())){abort();return;}
            return;
        }
        if(client.player==null||client.level==null||client.getConnection()==null||!client.player.isAlive()){
            healthy=null;farms().forEach(farm->farm.setEnabled(false));return;
        }
        if(client.gui.screen()!=null){healthy=null;return;}
        if(ctx.area()==GameArea.GARDEN||!leaveEnabled()||!valid(ctx)||!matches(healthy,false))return;
        advance(false);preGuardAdvanced=true;
    }
    public boolean permitsRunningLocation(Module module,GameContext ctx){
        checkThread();return runtime.state()!=AutoRewarpState.IDLE&&leaveEnabled()&&known(ctx)
                &&owned()&&module instanceof FarmModule farm&&pinned.farms().stream().anyMatch(owner->owner==farm);
    }
    /** Called after module updates. Healthy snapshots never come from a suspended or non-Garden engine. */
    public void tick(){
        checkThread();var ctx=context.current();
        if(preGuardAdvanced){preGuardAdvanced=false;return;}
        if(runtime.state()!=AutoRewarpState.IDLE&&!owned()){abort();return;}
        if(farms().isEmpty()){cancel();return;}
        if(!enabled()){cancel();return;} // Disabled recovery observes no loadout and retains no stale capture.
        advance(true);
    }
    private void advance(boolean sampleHealthy){
        var ctx=context.current();var p=client.player;boolean valid=valid(ctx);var owners=farms();
        boolean tools=valid&&!owners.isEmpty()&&owners.stream().allMatch(FarmModule::selectedFarmingToolReady);
        if(sampleHealthy&&runtime.state()==AutoRewarpState.IDLE&&valid&&ctx.area()==GameArea.GARDEN&&p.onGround()
                &&tools&&owners.stream().noneMatch(FarmModule::isSuspended)&&modules.enabledControllers().size()==owners.size()) {
            healthy=new Scope(p,client.level,client.getConnection(),List.copyOf(owners),profiles(owners),
                    new FarmReturnPoint(client.level.dimension().identifier().toString(),GameArea.GARDEN,position(),p.getYRot(),p.getXRot()));
        }
        boolean tracking=valid&&ctx.area()==GameArea.GARDEN&&runtime.state()==AutoRewarpState.IDLE
                &&owners.stream().noneMatch(FarmModule::isSuspended);
        if(tracking)stuck.sample(true,p.getX(),p.getY(),p.getZ(),services.clock().nowMillis());else stuck.reset();
        boolean applicable=runtime.state()!=AutoRewarpState.IDLE||matches(healthy,false);
        try{
            runtime.tick(new AutoRewarpRuntime.Inputs(!owners.isEmpty(),enabled()&&applicable,
                    config.get(FarmingConfig.AUTO_REWARP_ON_LEAVE_AREA),config.get(FarmingConfig.AUTO_REWARP_ON_STUCK),
                    ctx.area()==GameArea.GARDEN,known(ctx),valid&&p.onGround(),tools,stuck.isStuck(),stuck.stuckSinceMs(),services.clock().nowMillis(),
                    new AutoRewarpRuntime.Observation(valid,client.level,client.level==null?null:client.level.dimension().identifier().toString(),
                            p==null?null:position(),p==null?Double.POSITIVE_INFINITY:p.getDeltaMovement().length())),
                    new AutoRewarpRuntime.Settings(config.get(FarmingConfig.REWARP_WAIT_TIME_MS),config.get(FarmingConfig.REWARP_MAX_WAIT_MS),config.get(FarmingConfig.AUTO_REWARP_STUCK_MS)));
            if(runtime.state()==AutoRewarpState.IDLE)pinned=null;
        }catch(RuntimeException failure){abort();dev.veltraph.client.log.VeltraphLog.basic("AutoRewarp","Owned recovery failed: "+failure);}
    }
    private Vec3 position(){return new Vec3(client.player.getX(),client.player.getY(),client.player.getZ());}
    private void stopCaptured(){
        var failed=pinned;pinned=null;healthy=null;
        if(failed!=null)failed.farms().forEach(farm->{if(farm.isEnabled())farm.setEnabled(false);});
        services.input().releaseAllMovement();services.breaking().setBreaking(false);stuck.reset();
    }
    private void abort(){var failed=pinned;runtime.cancel();pinned=failed;stopCaptured();}
    public void cancel(){checkThread();runtime.cancel();healthy=pinned=null;preGuardAdvanced=false;stuck.reset();}
    public void resetProgress(){checkThread();stuck.reset();}
    public AutoRewarpRuntime runtime(){return runtime;}
    public void onServerMessage(String message){checkThread();if(owned())returning.onServerMessage(message);}
    public boolean consumeExpectedTeleport(Vec3 position,long now){checkThread();return owned()
            &&(runtime.consumeExpectedTeleport(client.level,position,now)||returning.consumeExpectedTeleport(position,now));}
    public String status(){return runtime.statusLine(enabled())+" | "+returning.status();}
}
