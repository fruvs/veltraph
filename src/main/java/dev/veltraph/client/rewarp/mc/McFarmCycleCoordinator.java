package dev.veltraph.client.rewarp.mc;

import dev.veltraph.client.config.*;
import dev.veltraph.client.core.ModuleManager;
import dev.veltraph.client.farming.module.*;
import dev.veltraph.client.game.*;
import dev.veltraph.client.rewarp.*;
import dev.veltraph.client.service.FarmServices;
import dev.veltraph.client.service.mc.McServerCommandService;
import dev.veltraph.client.world.Vec3;
import net.minecraft.client.Minecraft;
import java.nio.file.Path;
import java.io.IOException;
import java.util.*;

/** Client-thread integration of explicit normal START/END plans and observed reset completion. */
public final class McFarmCycleCoordinator {
    private final Minecraft client;private final FarmServices services;private final ConfigService config;
    private final ModuleManager modules;private final GameContextService context;private final FarmCycleStore store;
    private final FarmCycleResetRuntime runtime;private FarmModule owner;
    private net.minecraft.client.player.LocalPlayer playerOwner;
    private long lastRewarpMillis=-1,cycleGeneration;
    private Runnable revokeUnexpectedLanding=()->{};
    public McFarmCycleCoordinator(Minecraft client,FarmServices services,ConfigService config,ModuleManager modules,GameContextService context,Path file) {
        this.client=client;this.services=services;this.config=config;this.modules=modules;this.context=context;store=new FarmCycleStore(file);
        try{store.load();}catch(IOException error){dev.veltraph.client.log.VeltraphLog.basic("FarmCycle",error.getMessage());}
        var commands=new RewarpWarpExecutor(new McServerCommandService(client));
        runtime=new FarmCycleResetRuntime(new FarmCycleResetRuntime.Actions(){
            public boolean suspend(Object candidate){
                if(!(candidate instanceof FarmModule farm)||!farm.isEnabled()||owner!=null||pauseSource!=null)return false;
                var source=new PauseCameraSource(farm);var handoff=cameraHandoff.begin(source,farm);
                if(handoff==null)return false;
                pauseSource=source;
                try {
                    if(!source.current())return false;
                    boolean suspended;
                    source.suspendingFarm=true;
                    try{suspended=farm.suspendForCycleReset();}finally{source.suspendingFarm=false;}
                    if(!suspended||!source.current()||!releasedCycleInputs()||!source.current())return false;
                    if(dev.veltraph.client.service.mc.McOwnedRotation.authority(client)!=farm||!source.current())return false;
                    source.cleaningCamera=true;
                    try{services.rotation().cancelRotation();}finally{source.cleaningCamera=false;}
                    if(!source.current()||!cameraHandoff.complete(handoff))return false;
                    owner=farm;ownerPauseSource=source;playerOwner=client.player;cycleGeneration++;pauseSource=null;return true;
                } finally {
                    if(owner==null)failedPauseSource=source;
                    cameraHandoff.retire(handoff);
                    if(pauseSource==source)pauseSource=null;
                }
            }
            public boolean sendRewarp(Object candidate){
                var farm=owner;var start=issuedStart(candidate);long now=services.clock().nowMillis();
                if(candidate!=farm||farm==null||!farm.isEnabled()||!farm.isSuspendedForCycleReset()||start==null)return false;
                var nativePlayer=client.player;var nativeWorld=client.level;var nativeConnection=client.getConnection();
                var plan=store.get(profileId(farm)).orElse(null);String command=config.get(FarmingConfig.REWARP_COMMAND);
                long generation=cycleGeneration;long maximum=config.get(FarmingConfig.REWARP_MAX_WAIT_MS);
                double marker=config.get(FarmingConfig.FARM_CYCLE_MARKER_RADIUS);long delay=config.get(FarmingConfig.REWARP_WAIT_TIME_MS);
                long ttl=Math.min(5000,requestWindow(now));
                if(ttl<=0||modules.get(farm.id()).orElse(null)!=farm||modules.inputOwner().orElse(null)!=farm)return false;
                revokeUnexpectedLanding.run();var issued=new java.util.concurrent.atomic.AtomicBoolean();
                revokeUnexpectedLanding=dev.veltraph.client.observation.unexpected.UnexpectedActions.expectOwnedTeleport(client,start.position(),.35,ttl,()->
                        issued.get()&&cycleGeneration==generation&&owner==farm&&farm.isEnabled()&&farm.isSuspendedForCycleReset()
                        &&active()&&modules.get(farm.id()).orElse(null)==farm&&modules.inputOwner().orElse(null)==farm
                        &&client.player==nativePlayer&&client.level==nativeWorld&&client.getConnection()==nativeConnection
                        &&config.get(FarmingConfig.FARM_CYCLE_RESET_ENABLED)&&Objects.equals(config.get(FarmingConfig.REWARP_COMMAND),command)
                        &&store.get(profileId(farm)).orElse(null)==plan&&Objects.equals(config.get(FarmingConfig.REWARP_MAX_WAIT_MS),maximum)
                        &&Objects.equals(config.get(FarmingConfig.FARM_CYCLE_MARKER_RADIUS),marker)&&Objects.equals(config.get(FarmingConfig.REWARP_WAIT_TIME_MS),delay));
                try{boolean sent=commands.execute(command);issued.set(sent);if(sent)lastRewarpMillis=now;else revokeUnexpectedLanding.run();return sent;}
                catch(RuntimeException failure){revokeUnexpectedLanding.run();throw failure;}
            }
            public boolean aimAtStart(Object candidate,FarmReturnPoint start){
                if(candidate!=owner||!owner.isEnabled()||!owner.isSuspendedForCycleReset())return false;
                services.rotation().track(start.yaw(),start.pitch());return true;
            }
            public boolean restartAfterCycleReset(Object candidate){
                return restartRetainedFarm(candidate);
            }
            public void stop(Object candidate,String reason){
                var failed=failedPauseSource!=null?failedPauseSource:ownerPauseSource;
                if(failed!=null&&candidate==failed.farm){
                    if(failedPauseSource==failed)failedPauseSource=null;
                    if(ownerPauseSource==failed)ownerPauseSource=null;
                    failed.retirement.retire();
                }else if(candidate instanceof FarmModule farm&&cycleTickSource!=null)
                    CycleCameraHandoff.retirement(modules,cycleTickSource.originalBeforeLifecycle(),farm).retire();
                dev.veltraph.client.log.VeltraphLog.basic("FarmCycle",reason);
            }
            public void cancelAim(Object candidate){if(candidate==owner){
                cycleGeneration++;revokeUnexpectedLanding.run();revokeUnexpectedLanding=()->{};
                // A successful restart has already handed rotation back to the fresh farm engine.
                try{if(owner.isSuspendedForCycleReset())services.rotation().cancelRotation();}
                finally{owner=null;playerOwner=null;}
            }}
        });
    }
    private final CycleCameraHandoff cameraHandoff=new CycleCameraHandoff();
    private PauseCameraSource pauseSource,failedPauseSource,ownerPauseSource;
    private dev.veltraph.client.VeltraphBootstrap.RootFarmCycleTick cycleTickSource;
    private boolean cycleTickRevoked;
    /** Only this installed coordinator's actual suspension retains the original farm camera owner. */
    private final class PauseCameraSource {
        private final FarmModule farm;
        private boolean cleaningCamera,suspendingFarm;
        private final Object player=client.player,world=client.level,connection=client.getConnection(),options=client.options,gameMode=client.gameMode;
        private final ModuleManager.LifecycleSnapshot lifecycle=modules.lifecycleSnapshot();
        private final dev.veltraph.client.input.ScriptingInputLock.ControlSnapshot control=dev.veltraph.client.input.ScriptingInputLock.controlSnapshot();
        private final dev.veltraph.client.service.mc.McOwnedRotation.InstallationSnapshot installation=dev.veltraph.client.service.mc.McOwnedRotation.installationSnapshot(client);
        private final CycleCameraHandoff.Retirement retirement;
        private final long generation=cycleGeneration;
        private final dev.veltraph.client.VeltraphBootstrap.RootFarmCycleTick rootTick=cycleTickSource;
        private final Thread thread=Thread.currentThread();
        private PauseCameraSource(FarmModule farm){this.farm=farm;retirement=CycleCameraHandoff.retirement(modules,lifecycle,farm);}
        private boolean current(){
            return pauseSource==this&&cameraHandoff.pendingSource()==this&&owner==null&&cycleGeneration==generation
                    &&Thread.currentThread()==thread&&client.isSameThread()&&rootTick!=null&&!cycleTickRevoked&&cycleTickSource==rootTick&&rootTick.currentFor(McFarmCycleCoordinator.this)
                    &&runtime.state()==FarmCycleResetRuntime.State.REQUEST_DELAY
                    &&dev.veltraph.client.VeltraphBootstrap.actualFarmToolCoordinator(client,McFarmCycleCoordinator.this)
                    &&modules==dev.veltraph.client.VeltraphBootstrap.moduleManager()&&config==dev.veltraph.client.VeltraphBootstrap.config()
                    &&player!=null&&world!=null&&connection!=null&&options!=null&&gameMode!=null
                    &&client.player==player&&client.level==world&&client.getConnection()==connection&&client.options==options&&client.gameMode==gameMode
                    &&lifecycle.known()&&modules.lifecycleCurrent(lifecycle)&&modules.get(farm.id()).orElse(null)==farm&&farm.isEnabled()
                    &&!farm.isSuspendedForRewarp()&&!farm.isSuspendedForMaintenance()
                    &&control!=null&&control.current()&&control.active()&&farm.id().equals(control.ownerModuleId())
                    &&installation!=null&&installation.current();
        }
    }
    /** Observation-only source for existing normally returned cleanup records; cannot construct a pause. */
    public FarmModule pausedInputForOriginalRoot(dev.veltraph.client.VeltraphBootstrap.RootFarmCycleTick exact,dev.veltraph.client.service.mc.McInputService input){
        var source=pauseSource;var negative=dev.veltraph.client.service.mc.McInputService.pendingNegativeObservation(input);
        return source!=null&&source.rootTick==exact&&services.input()==input&&source.suspendingFarm
                &&negative!=null&&negative.currentFor(input)&&negative.matchesOriginalFarm(source.farm)&&source.current()?source.farm:null;
    }
    public FarmModule pausedCameraForOriginalRoot(dev.veltraph.client.VeltraphBootstrap.RootFarmCycleTick exact,dev.veltraph.client.service.mc.McRotationService rotation){
        var source=pauseSource;return source!=null&&source.rootTick==exact&&services.rotation()==rotation&&source.cleaningCamera
                &&originalCancelMethodActive()&&source.current()?source.farm:null;
    }
    private static boolean originalCancelMethodActive(){
        return StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(frames->{
            var methods=frames.filter(frame->frame.getDeclaringClass()==dev.veltraph.client.service.mc.McRotationService.class).toList();
            for(var frame:methods){
                var method=frame.getMethodName();
                if(method.equals("cancelRotation"))return true;
                if(!java.util.Set.of("enterCameraMethod","enterRootNativeCall","observeRootNativeCancel","originalCameraEntry5").contains(method))return false;
            }
            return false;
        });
    }
    private boolean releasedCycleInputs(){
        var o=client.options;return o!=null&&!o.keyUp.isDown()&&!o.keyDown.isDown()&&!o.keyLeft.isDown()&&!o.keyRight.isDown()
                &&!o.keyJump.isDown()&&!o.keyShift.isDown()&&!o.keySprint.isDown()&&!o.keyAttack.isDown();
    }
    /** Read-only actual handoff; a public token lookup cannot create a pause or adopt another owner. */
    public Object rotationAuthorityToken(){
        var source=pauseSource;
        if(source!=null)return source.current()?source.farm:null;
        return runtime.active()&&owner!=null?this:null;
    }
    private ToolSelectionSource selectingTool;
    /** Closed source from this actual issued coordinator invocation; public scopes/predicates cannot create it. */
    public final class ToolSelectionSource {
        private final FarmModule farm;private final Object player,world,connection,inventory;
        private final ModuleManager.LifecycleSnapshot lifecycle;
        private final dev.veltraph.client.input.ScriptingInputLock.ControlSnapshot control;
        private final dev.veltraph.client.service.mc.McOwnedRotation.InstallationSnapshot installation;
        private final String profile;private final int tick;private final double x,y,z;private final float yaw,pitch;
        private boolean claimed,restarted;
        private final long generation;private final FarmCycleResetRuntime.State phase;
        private ToolSelectionSource(FarmModule farm) {
            this.farm=farm;player=client.player;world=client.level;connection=client.getConnection();inventory=client.player.getInventory();
            lifecycle=modules.lifecycleSnapshot();control=dev.veltraph.client.input.ScriptingInputLock.controlSnapshot();
            installation=dev.veltraph.client.service.mc.McOwnedRotation.installationSnapshot(client);profile=McFarmCycleCoordinator.profileId(farm);
            tick=client.player.tickCount;x=client.player.getX();y=client.player.getY();z=client.player.getZ();yaw=client.player.getYRot();pitch=client.player.getXRot();
            generation=cycleGeneration;phase=runtime.state();
        }
        private boolean nativeCurrent(FarmModule expected,dev.veltraph.client.service.mc.McInventoryService expectedInventory) {
            return selectingTool==this&&cycleTickSource!=null&&!cycleTickRevoked&&cycleTickSource.currentFor(McFarmCycleCoordinator.this)
                    &&dev.veltraph.client.VeltraphBootstrap.actualFarmToolCoordinator(client,McFarmCycleCoordinator.this)
                    &&owner==farm&&cycleGeneration==generation&&runtime.active()&&runtime.state()==phase
                    &&playerOwner==player&&(farm.isSuspendedForCycleReset()||restarted&&!farm.isSuspended())&&!farm.isSuspendedForRewarp()
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
            return nativeCurrent(expected,expectedInventory)&&config.get(FarmingConfig.FARM_CYCLE_RESET_ENABLED)
                    &&modules.inputOwner().orElse(null)==farm&&dev.veltraph.client.service.mc.McOwnedRotation.authority(client)==McFarmCycleCoordinator.this
                    &&profile.equals(McFarmCycleCoordinator.profileId(farm))&&nativeCurrent(expected,expectedInventory);
        }
        public boolean claim(FarmModule expected,dev.veltraph.client.service.mc.McInventoryService expectedInventory) {
            if(claimed||!current(expected,expectedInventory))return false;claimed=true;return true;
        }
    }
    private boolean issuedToolReady(FarmModule farm) {
        if(!dev.veltraph.client.VeltraphBootstrap.actualFarmToolCoordinator(client,this))return false;
        if(!runtime.active())return farm.selectedFarmingToolReady();
        if(selectingTool!=null||farm!=owner||client.player==null||client.level==null||client.getConnection()==null)return false;
        var source=new ToolSelectionSource(farm);selectingTool=source;
        try{return dev.veltraph.client.service.mc.McFarmToolRecoveryContext.withSource(source,farm::toolReadyForRewarp);}
        finally{selectingTool=null;}
    }
    private boolean restartRetainedFarm(Object candidate) {
        if(candidate!=owner||owner==null||!owner.isEnabled()||!owner.isSuspendedForCycleReset()
                ||runtime.state()!=FarmCycleResetRuntime.State.RESTARTING
                ||!dev.veltraph.client.VeltraphBootstrap.actualFarmToolCoordinator(client,this))return false;
        var farm=owner;var source=new ToolSelectionSource(farm);selectingTool=source;
        try {
            if(!(services.inventory() instanceof dev.veltraph.client.service.mc.McInventoryService inventory)
                    ||!source.current(farm,inventory)||!farm.retainedCycleToolReady()||!source.current(farm,inventory)
                    ||!farm.restartAfterCycleReset())return false;
            source.restarted=true; // Only the genuine original restart return admits its own suspension transition.
            return farm.selectedFarmingToolReady()&&source.current(farm,inventory);
        }
        finally{selectingTool=null;}
    }

    private FarmReturnPoint issuedStart(Object candidate){return runtime.issuedStart(candidate);}
    private long requestWindow(long now){return runtime.remainingRequestWindow(now,config.get(FarmingConfig.REWARP_MAX_WAIT_MS));}
    public static String profileId(FarmModule farm){
        return dev.veltraph.client.rewarp.FarmCycleProfileIdentity.of(farm);
    }
    private List<FarmModule> farms(){return modules.enabledModules().stream().filter(FarmModule.class::isInstance).map(FarmModule.class::cast).toList();}
    public void tickFromOriginalRoot(dev.veltraph.client.VeltraphBootstrap.RootFarmCycleTick source){
        checkThread();
        if(source==null||!source.claim(this))return;
        if(cycleTickSource!=null){cameraHandoff.cancel();return;}
        cycleTickSource=source;cycleTickRevoked=false;
        try{tickOriginal();}finally{if(cycleTickSource==source){cycleTickSource=null;cycleTickRevoked=true;}}
    }
    /** Public calls never borrow an issued Root body; reentry only revokes the original instruction. */
    public void tick(){
        checkThread();
        if(cycleTickSource!=null){cycleTickRevoked=true;cameraHandoff.cancel();}
    }
    private void tickOriginal(){
        checkThread();
        if(cycleTickSource==null||cycleTickRevoked||!cycleTickSource.currentFor(this))return;
        if(!config.get(FarmingConfig.FARM_CYCLE_RESET_ENABLED)){cancel(true);return;}
        var running=farms();var farm=running.size()==1?running.getFirst():null;
        var originalFarmRetirement=CycleCameraHandoff.retirement(modules,cycleTickSource.originalBeforeLifecycle(),farm);
        if(farm==null&&!runtime.active())return;
        var p=client.player;var w=client.level;var ctx=context.current();
        boolean valid=p!=null&&w!=null&&p.isAlive()&&client.gui.screen()==null&&ctx.inSkyBlock()
                &&(!runtime.active()||p==playerOwner);
        var observation=new FarmCycleResetRuntime.Observation(valid,w,ctx.area(),w==null?null:w.dimension().identifier().toString(),p==null?null:new Vec3(p.getX(),p.getY(),p.getZ()),p==null?Double.POSITIVE_INFINITY:p.getDeltaMovement().length(),p!=null&&p.onGround(),p==null?0:p.getYRot(),p==null?0:p.getXRot());
        String profile=farm==null?null:profileId(farm);
        try {
            if(!runtime.active()&&valid&&farm!=null&&!farm.cycleEndReady())return;
            var settings=new FarmCycleResetRuntime.Settings(config.get(FarmingConfig.FARM_CYCLE_MARKER_RADIUS),config.get(FarmingConfig.REWARP_WAIT_TIME_MS),250,config.get(FarmingConfig.REWARP_MAX_WAIT_MS),.35,1,.1);
            var input=new FarmCycleResetRuntime.Inputs(farm!=null&&farm.isEnabled()&&!farm.isSuspendedForRewarp()&&!farm.isSuspendedForMaintenance(),farm,profile,profile==null?null:store.get(profile).orElse(null),observation,valid&&farm!=null&&issuedToolReady(farm),services.clock().currentTick(),services.clock().nowMillis());
            if(cycleTickRevoked||cycleTickSource==null||!cycleTickSource.currentFor(this))return;
            runtime.tick(input,settings);
        } catch(RuntimeException error) {
            CycleCameraHandoff.retireAfterFailure(originalFarmRetirement,()->cancel(true));
            dev.veltraph.client.log.VeltraphLog.basic("FarmCycle","Stopped after invalid cycle configuration/action: "+error.getMessage());
        }
    }
    public void cancel(boolean stopOwner){checkThread();
        if(pauseSource!=null){cameraHandoff.cancel();return;}
        var old=owner;
        var original=ownerPauseSource;
        try{runtime.cancel();}finally{
            cameraHandoff.cancel();pauseSource=null;
            if(stopOwner&&old!=null){
                if(original!=null&&original.farm==old){if(ownerPauseSource==original)ownerPauseSource=null;original.retirement.retire();}
                // Unknown original activation receives no current-owner fallback stop.

            }
        }}
    public boolean active(){return runtime.active();}
    public int completedCycles(){return runtime.completedCycles();}
    /** Successful native dispatch timestamp only; -1 denotes no observed cycle rewarp, not arrival. */
    public long lastRewarpMillis(){return lastRewarpMillis;}
    public boolean consumeExpectedTeleport(Vec3 position,long now){
        checkThread();var p=client.player;var ctx=context.current();
        return owner!=null&&owner.isEnabled()&&owner.isSuspendedForCycleReset()&&p==playerOwner
                &&p!=null&&p.isAlive()&&client.level!=null&&client.gui.screen()==null&&ctx.inSkyBlock()&&ctx.area()==GameArea.GARDEN
                &&runtime.consumeExpectedTeleport(owner,client.level,position,now);
    }
    public String status(){return runtime.statusLine();}
    public List<FarmCyclePlan> plans(){return store.list();}
    public List<String> profileKeys(){return modules.all().stream().filter(FarmModule.class::isInstance).map(FarmModule.class::cast).map(McFarmCycleCoordinator::profileId).toList();}
    private FarmReturnPoint capture(String profile){
        checkThread();
        if(!profileKeys().contains(profile))throw new IllegalArgumentException("Choose a currently selected farm profile: "+profileKeys());
        if(!modules.enabledModules().isEmpty())throw new IllegalStateException("Stop farming before recording cycle points");
        var p=client.player;var w=client.level;var ctx=context.current();
        // Fabric executes a typed client command before ChatScreen closes. Allow that read-only
        // capture entry point while retaining the inventory/other-menu prohibition.
        boolean captureScreen=client.gui.screen()==null||client.gui.screen() instanceof net.minecraft.client.gui.screens.ChatScreen;
        if(p==null||w==null||!p.isAlive()||!captureScreen||!ctx.inSkyBlock()||ctx.area()!=GameArea.GARDEN)throw new IllegalStateException("Cycle points require a live Garden player with no open inventory/menu");
        return new FarmReturnPoint(w.dimension().identifier().toString(),ctx.area(),new Vec3(p.getX(),p.getY(),p.getZ()),p.getYRot(),p.getXRot());
    }
    public void captureStart(String profile)throws IOException{
        var pose=capture(profile);var p=client.player;
        if(!p.onGround()||p.getDeltaMovement().length()>.1||p.isInWater()||p.isInLava())
            throw new IllegalStateException("Record START on stable dry supported ground after releasing movement controls");
        store.updateAndSave(()->store.setStart(profile,pose));
    }
    public void captureEnd(String profile)throws IOException{var pose=capture(profile);store.updateAndSave(()->store.addEnd(profile,pose.area(),pose.dimension(),pose.position()));}
    public void clear(String profile)throws IOException{checkThread();if(active())throw new IllegalStateException("Stop cycle reset first");store.updateAndSave(()->store.clear(profile));}
    private void checkThread(){if(!client.isSameThread())throw new IllegalStateException("Farm cycle reset requires client thread");}
}
