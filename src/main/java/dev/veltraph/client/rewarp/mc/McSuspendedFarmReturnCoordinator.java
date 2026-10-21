package dev.veltraph.client.rewarp.mc;

import dev.veltraph.client.VeltraphBootstrap;
import dev.veltraph.client.config.*;
import dev.veltraph.client.core.AbstractModule;
import dev.veltraph.client.core.ModuleCategory;
import dev.veltraph.client.core.ModuleManager;
import dev.veltraph.client.farming.module.FarmModule;
import dev.veltraph.client.game.*;
import dev.veltraph.client.rewarp.*;
import dev.veltraph.client.scheduling.mc.NativeRestTransport;
import dev.veltraph.client.service.FarmServices;
import dev.veltraph.client.service.mc.McOwnedRotation;
import dev.veltraph.client.world.Vec3;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.Connection;
import java.util.*;

/** Root's explicit saved-START return. Public intent never supplies a target, pause token or native receipt. */
public final class McSuspendedFarmReturnCoordinator {
    private final Minecraft client;
    private final FarmServices services;
    private final ConfigService config;
    private final ModuleManager modules;
    private final GameContextService context;
    private final McFarmCycleCoordinator cycle;
    private final NativeRestTransport transport;
    private final SuspendedFarmReturnRuntime runtime=new SuspendedFarmReturnRuntime();
    private final McSavedAnchorRecovery returning;
    private long serial;
    private SourceHeader source;
    private PauseCall pauseCall;
    private ResumeCall resumeCall;
    private ArrivalStamp arrivalStamp;
    private ReturnCleanupCall returnCleanupCall;
    private MovementView movementView;
    private MovementView retainedMovementView;
    private RootDispatchObservation dispatchObservation;
    private TerminalDispatchObservation terminalDispatch;
    private NormalResumeWitness normalResumeWitness;
    private CommandCall commandCall;
    private LandingCall landingCall;
    private IssuedFarmAppliedLanding.Receipt appliedLanding;
    private long landingBaseline,acceptedTeleportSerial=-1,deadlineObservation=-1;
    private FarmModule.IssuedReturnCheckpoint checkpoint;
    private IssuedFarmCommandFence.Submitted submitted;
    private NativeTeleportSignal.Receipt beforeCommand;
    private JoinedScene joined;
    private JoinCall joinCall;
    private DepartureCall departureCall;
    private RejoinCall rejoinCall;
    private DepartureCall retainedDepartureCall;
    private RejoinCall retainedRejoinCall;
    private CloseCall closeCall;
    private MetadataCloseCall metadataCloseCall;
    private dev.veltraph.client.scheduling.mc.McScriptScheduler.TimerRetention timerRetention;
    private TimerCall timerCall;
    private DriverInputCall driverInputCall;
    private DriverEnableSource pendingDriverEnableSource;
    private VeltraphBootstrap.RootFarmReturnTick driverInputAttemptTick;
    private VeltraphBootstrap.RootFarmReturnTick activeTick;
    private VeltraphBootstrap.RootFarmReturnTick dispatchedTick;
    private long lastRootTick=-1;
    private int completedReturns;
    private boolean driverStarting,retiring;
    private ModuleManager.OwnEnableReceipt driverEnable;
    private ModuleManager.LifecycleSnapshot beforeDriver;
    private UserIntent pendingUserIntent,consumingUserIntent;
    private String diagnostic="Idle; authenticated SkyBlock profile UNKNOWN";
    private final AbstractModule driver=new AbstractModule("suspended_farm_return","Return to saved farm START",ModuleCategory.INTERMEDIARY){
        @Override public boolean requiresSkyBlock(){return true;}
        @Override public boolean hasTravelSetupPhase(){return active();}
        @Override public void onEnable(){
            driverEnable=modules.currentOwnEnableReceipt(this).orElseThrow(()->new IllegalStateException("No actual Core enable invocation"));
            if(!driverStarting||!registered()||source==null||checkpoint==null
                    ||!modules.ownEnableInvocationMatches(driverEnable,beforeDriver,this)||!source.currentRefusal())
                throw new IllegalStateException("No original Root return admission");
        }
        @Override public boolean ownsGameplayInput(){return active()&&!resumingOriginalFarm();}
        @Override public void onTick(){} // Root invokes coordinator.tick separately, exactly once.
        @Override public void onDisable(){if(!retiring&&active())cancel("Return driver was stopped");}
    };
    public McSuspendedFarmReturnCoordinator(Minecraft client,FarmServices services,ConfigService config,
            ModuleManager modules,GameContextService context,McFarmCycleCoordinator cycle){
        this.client=Objects.requireNonNull(client);this.services=Objects.requireNonNull(services);this.config=Objects.requireNonNull(config);
        this.modules=Objects.requireNonNull(modules);this.context=Objects.requireNonNull(context);this.cycle=Objects.requireNonNull(cycle);
        transport=new NativeRestTransport(client);returning=new McSavedAnchorRecovery(client,services,config);modules.register(driver);
    }
    private VeltraphBootstrap.RootFarmReturnRegistration registration(){return VeltraphBootstrap.farmReturnRegistrationFor(this);}
    private boolean registered(){var r=registration();return r!=null&&r.current(this,client,services,config,modules,context);}
    private void thread(){if(!client.isSameThread())throw new IllegalStateException("Farm return requires the client thread");}
    public AbstractModule driver(){return driver;}
    public boolean active(){return runtime.active();}
    public String diagnostics(){return diagnostic+" | "+runtime.state()+" | "+runtime.reason();}
    public SuspendedFarmReturnRuntime.State state(){return runtime.state();}
    public int completedReturns(){return completedReturns;}
    public boolean permitsDriverActivation(AbstractModule exact){return exact==driver&&driverStarting&&registered()&&source!=null
        &&checkpoint!=null&&source.current()&&source.farm.issuedReturnPausedCurrent(checkpoint)&&!driver.isEnabled();}
    private boolean resumingOriginalFarm(){return source!=null&&checkpoint!=null&&resumeCall!=null&&source.farm.issuedReturnResumingBy(checkpoint,resumeCall);}
    public ResumeCall currentResumeCall(){var call=resumeCall;return call!=null&&call.phaseCurrent()?call:null;}
    public LandingCall currentLandingCall(){var call=landingCall;return call!=null&&call.currentFinalNative()?call:null;}
    public boolean isPausedFarm(FarmModule exact){return source!=null&&source.farm==exact&&checkpoint!=null&&active()
        &&source.current()&&exact.issuedReturnPausedCurrent(checkpoint);}
    /** Local command intent only. It cannot supply native permission or survive actor/config replacement. */
    private final class UserIntent {
        private final FarmModule farm;
        private final String profile;
        private final Object player=client.player,world=client.level,listener=client.getConnection(),options=client.options;
        private final UUID session=client.getUser().getProfileId(),account=client.player.getGameProfile().id();
        private final ModuleManager.LifecycleSnapshot lifecycle=modules.lifecycleSnapshot();
        private final MapConfigService.StoredStateSnapshot settings;
        private final long requestedAt;
        private final FarmReturnIntentFence.Header header;
        private UserIntent(FarmModule farm,String profile,MapConfigService.StoredStateSnapshot settings,long requestedAt){this.farm=farm;this.profile=profile;this.settings=settings;this.requestedAt=requestedAt;
            header=new FarmReturnIntentFence.Header(modules,config,farm,player,world,listener,options,session,account,profile,requestedAt);
        }
        private boolean current(){return pendingUserIntent==this&&registered()&&!active()&&client.player==player&&client.level==world&&client.getConnection()==listener&&client.options==options
            &&client.player!=null&&client.player.isAlive()&&client.player.level()==client.level&&client.getUser().getProfileId().equals(session)
            &&client.player.getGameProfile().id().equals(account)&&session.equals(account)&&settings.current()&&lifecycle.known()&&modules.lifecycleCurrent(lifecycle)
            &&modules.get(farm.id()).orElse(null)==farm&&farm.isEnabled()&&!farm.isSuspended();}
    }
    /** Only the registered client-command executor should queue intent; original capture occurs on the next Root tick. */
    boolean queueUserIntent(){return queueUserIntent(null);}
    private boolean queueUserIntent(String selectedFarmId){
        thread();if(active()||pendingUserIntent!=null||!registered()||client.player==null||client.level==null||client.getConnection()==null)return false;
        var screen=client.gui.screen();if(screen!=null&&!(screen instanceof net.minecraft.client.gui.screens.ChatScreen))return false;
        var owner=modules.inputOwner().orElse(null);if(!(owner instanceof FarmModule farm)||!farm.isEnabled()||farm.isSuspended()||selectedFarmId!=null&&!selectedFarmId.equals(farm.id()))return false;
        String profile=FarmCycleProfileIdentity.of(farm);var settings=settingsSnapshot();long now=services.clock().nowMillis();
        if(settings==null||now<0||!registered())return false;
        var next=new UserIntent(farm,profile,settings,now);pendingUserIntent=next;
        if(!next.current()){pendingUserIntent=null;return false;}return true;
    }
    public boolean hasPendingUserIntent(){return pendingUserIntent!=null;}
    public boolean hasTerminalRootObservation(){return terminalDispatch!=null||normalResumeWitness!=null;}
    private void consumeUserIntent(VeltraphBootstrap.RootFarmReturnTick actual,VeltraphBootstrap.RootFarmReturnAdmission admission){
        var intent=pendingUserIntent;if(intent==null||actual==null||!actual.current(this)||admission==null||!admission.current(this,actual))return;
        long now=services.clock().nowMillis();
        var observed=new FarmReturnIntentFence.Header(modules,config,intent.farm,client.player,client.level,client.getConnection(),client.options,
            client.getUser().getProfileId(),client.player==null?null:client.player.getGameProfile().id(),intent.profile,intent.requestedAt);
        if(!FarmReturnIntentFence.current(intent.header,observed,now,intent.current())){
            pendingUserIntent=null;diagnostic="Queued return intent changed or expired";return;
        }
        if(client.gui.screen() instanceof net.minecraft.client.gui.screens.ChatScreen)return;
        if(client.gui.screen()!=null){pendingUserIntent=null;diagnostic="Queued return was interrupted by a menu";return;}
        if(!intent.profile.equals(FarmCycleProfileIdentity.of(intent.farm))||!intent.current()){
            pendingUserIntent=null;diagnostic="Queued farm profile changed";return;
        }
        consumingUserIntent=intent;
        try{if(!admit(intent.farm.id(),admission,actual))diagnostic="Configured return request was refused; "+diagnostic;}
        finally{if(pendingUserIntent==intent)pendingUserIntent=null;if(consumingUserIntent==intent)consumingUserIntent=null;}
    }
    boolean cancelUserIntentFromCommand(){
        thread();pendingUserIntent=null;
        if(!registered())return false;
        if(driver.isEnabled()){registration().stopDriver(driver);return true;}
        if(source!=null&&modules.get(source.farm.id()).orElse(null)==source.farm&&source.farm.isEnabled()){modules.disable(source.farm.id());return true;}
        return true;
    }
    /** Actual current registered farm is selected; no caller pose or raw suspension can be adopted. */
    public boolean request(){return queueUserIntent(null);}
    public boolean request(String selectedFarmId){return queueUserIntent(selectedFarmId);}
    private boolean admit(String selectedFarmId,VeltraphBootstrap.RootFarmReturnAdmission admission,VeltraphBootstrap.RootFarmReturnTick actual){
        thread();if(admission==null||actual==null||activeTick!=actual||!actual.current(this)||!admission.current(this,actual))return false;
        if(terminalDispatch!=null&&runtime.state()==SuspendedFarmReturnRuntime.State.COMPLETE){terminalDispatch.inventory.close();terminalDispatch=null;clear();}
        if(active()||serial==Long.MAX_VALUE||!registered()||!registration().higherPriorityIdle())return false;
        var intent=consumingUserIntent;if(intent!=null&&!intent.current())return false;
        var owner=modules.inputOwner().orElse(null);
        if(!(owner instanceof FarmModule farm)||modules.get(farm.id()).orElse(null)!=farm||!farm.isEnabled()||farm.isSuspended()
                ||selectedFarmId!=null&&!selectedFarmId.equals(farm.id())||driver.isEnabled()||cycle.active())return false;
        try{if(farm.issuedReturnReadinessError().isPresent())return false;}
        catch(RuntimeException unavailable){diagnostic="Original issued-return readiness unavailable";return false;}
        if(!registered()||modules.get(farm.id()).orElse(null)!=farm||modules.inputOwner().orElse(null)!=farm||!farm.isEnabled()||farm.isSuspended())return false;
        var profile=FarmCycleProfileIdentity.of(farm);
        if(intent!=null&&(farm!=intent.farm||!profile.equals(intent.profile)||!intent.current()))return false;
        var plan=cycle.plans().stream().filter(p->p.profileId().equals(profile)).findFirst().orElse(null);
        if(plan==null||!originalScene(plan.start())||!config.get(FarmReturnConfig.FARM_RETURN_ENABLED))return false;
        var mode=config.get(FarmReturnConfig.FARM_RETURN_MODE);
        var command=RewarpWarpExecutor.normalizeCommand(config.get(FarmingConfig.REWARP_COMMAND));
        if(!validCommand(command))return false;
        long now=services.clock().nowMillis(),maximum=config.get(FarmReturnConfig.FARM_RETURN_MAX_WAIT_MS);
        long delay=config.get(FarmReturnConfig.FARM_RETURN_RECONNECT_DELAY_MS);int settle=config.get(FarmReturnConfig.FARM_RETURN_SETTLE_TICKS);
        var settings=settingsSnapshot();if(settings==null)return false;
        var originalPublication=admission.originalInputPublication();
        if(originalPublication==null||!originalPublication.currentFor(farm)||!admission.current(this,actual))return false;
        if(mode==SuspendedFarmReturnRuntime.Mode.DISCONNECT_RECONNECT&&(client.hasSingleplayerServer()||!NativeRestTransport.cancellationAvailable()))return false;
        try{
            if(intent!=null&&(!intent.current()||farm!=intent.farm||!profile.equals(intent.profile)))return false;
            if(!admission.current(this,actual)||!originalPublication.currentFor(farm)||VeltraphBootstrap.originalRootPublicationFor(farm)!=originalPublication||farm.issuedReturnReadinessError().isPresent()||!originalScene(plan.start())||modules.get(farm.id()).orElse(null)!=farm
                    ||!profile.equals(FarmCycleProfileIdentity.of(farm))||!settings.current()||farm.isSuspended()||!farm.isEnabled())return false;
            if(!runtime.begin(mode,now,maximum,delay,settle))return refusal("Invalid configured return deadline");
            source=new SourceHeader(++serial,farm,profile,plan,command,settings,originalPublication);
            source.inventory=McIssuedReturnInventory.capture(client,source);
            if(source.originalInputPublication==null||!source.originalInputPublication.currentFor(farm)||!observeDeadline()||!source.current()||!source.protectedCurrent())return refusal("Original source capture changed");
            pauseCall=new PauseCall(source,admission);
            var scheduler=VeltraphBootstrap.scriptScheduler();
            if(scheduler==null)return refusal("Original timer scheduler is unavailable");
            timerRetention=scheduler.captureIssuedFarmTimer(pauseCall);
            if(timerRetention==null||!pauseCall.current(farm)||!admission.current(this,actual)||source.originalInputPublication==null||!source.originalInputPublication.currentFor(farm))return refusal("Original timer state changed before pause");
            checkpoint=registration().pauseOriginalFarm(pauseCall).orElse(null);
            if(checkpoint==null||!source.current()||!source.protectedCurrent()||!runtime.paused(farm.issuedReturnPausedCurrent(checkpoint)))return refusal("Original pause refused");
            if(mode==SuspendedFarmReturnRuntime.Mode.DISCONNECT_RECONNECT&&!transport.capture(()->cancel("Owned reconnect cancelled"),this::diagnostics))return refusal("Native transport capture refused");
            beforeDriver=modules.lifecycleSnapshot();driverStarting=true;
            pendingDriverEnableSource=new DriverEnableSource(source,beforeDriver,activeTick);
            boolean started;
            try{started=registration().activateDriver(driver);}finally{driverStarting=false;pendingDriverEnableSource=null;}
            if(!started||driverEnable==null||!modules.ownEnableMatches(driverEnable,beforeDriver,driver))return refusal("Actual return driver enable failed");
            source.driverLifecycle=modules.lifecycleSnapshot();
            if(!source.current())return refusal("Return driver transition changed original source");
            diagnostic="Original farm captured; authenticated SkyBlock profile UNKNOWN";return true;
        }catch(RuntimeException|Error failure){
            if(source==null&&!runtime.active()){diagnostic="Return admission failed before suspension";return false;}
            try{cancel("Return admission failed: "+failure.getClass().getSimpleName());}
            catch(RuntimeException|Error cleanup){if(cleanup!=failure)failure.addSuppressed(cleanup);throw failure;}
            return false;
        }
    }
    private boolean observeDeadline(){
        long now=services.clock().nowMillis();
        if(!runtime.active()||now<0||now>=runtime.deadline()||deadlineObservation>=0&&now<deadlineObservation)return false;
        deadlineObservation=now;return true;
    }
    static boolean validCommand(String command){return command!=null&&!command.isBlank()&&command.length()<=256&&!command.startsWith("/")
        &&command.chars().noneMatch(c->c<32||c==127);}
    private MapConfigService.StoredStateSnapshot settingsSnapshot(){return config instanceof MapConfigService m?m.storedStateSnapshot():config instanceof JsonConfigService j?j.storedStateSnapshot():null;}
    private boolean originalScene(FarmReturnPoint target){var p=client.player;var w=client.level;var listener=client.getConnection();
        if(p==null||w==null||listener==null||client.gameMode==null||p.level()!=w||!p.isAlive()||p.isPassenger()||p.isScoping()
            ||client.gui.screen()!=null||client.isPaused()||client.getCameraEntity()!=p||w.tickRateManager().isEntityFrozen(p)
            ||p.containerMenu!=p.inventoryMenu||!p.inventoryMenu.getCarried().isEmpty()||!p.getGameProfile().id().equals(client.getUser().getProfileId()))return false;
        var area=context.current();return registered()&&area.onHypixel()&&area.inSkyBlock()&&area.area()==GameArea.GARDEN
            &&target.area()==GameArea.GARDEN&&target.dimension().equals(w.dimension().identifier().toString());
    }
    /** Privately created original context; read-only getters cannot mint a pause or resume. */
    public final class SourceHeader {
        private final long serial;
        private final FarmModule farm;
        private final String profile,command;
        private final FarmCyclePlan plan;
        private final MapConfigService.StoredStateSnapshot settings;
        private final VeltraphBootstrap.RootOriginalInputPublication originalInputPublication;
        private final ModuleManager.LifecycleSnapshot originalLifecycle;
        private ModuleManager.LifecycleSnapshot driverLifecycle;
        private final Object player=client.player,world=client.level,listener=client.getConnection(),connection=client.getConnection().getConnection();
        private final UUID sessionId=client.getUser().getProfileId(),playerId=client.player.getGameProfile().id();
        private final ServerData server=client.getCurrentServer();
        private final String address=server==null?null:server.ip,name=server==null?null:server.name;
        private final int slot=client.player.getInventory().getSelectedSlot();
        private final Object options=client.options,gameMode=client.gameMode;
        private final dev.veltraph.client.game.mc.McGameContextService.GameplaySignalsSnapshot originalSignals;
        private McIssuedReturnInventory.Snapshot inventory;
        private SourceHeader(long serial,FarmModule farm,String profile,FarmCyclePlan plan,String command,MapConfigService.StoredStateSnapshot settings,VeltraphBootstrap.RootOriginalInputPublication originalPublication){
            this.serial=serial;this.farm=farm;this.profile=profile;this.plan=plan;this.command=command;this.settings=settings;originalInputPublication=originalPublication;originalLifecycle=modules.lifecycleSnapshot();
            originalSignals=context instanceof dev.veltraph.client.game.mc.McGameContextService nativeContext?nativeContext.gameplaySignalsSnapshot():null;
        }
        public VeltraphBootstrap.RootOriginalInputPublication originalInputPublication(){return originalInputPublication;}
        public long serial(){return serial;}public ModuleManager registryIdentity(){return modules;}public ConfigService configIdentity(){return config;}public FarmServices farmServicesIdentity(){return services;}
        public Object originalPlayer(){return player;}public Object originalWorld(){return world;}public Object originalConnection(){return listener;}
        public boolean current(){return currentRefusal();}
        public boolean currentRefusal(){
            if(source!=this||!client.isSameThread()||!registered()||!settings.current()||modules.get(farm.id()).orElse(null)!=farm||!farm.isEnabled()
                ||!client.getUser().getProfileId().equals(sessionId)||!sessionId.equals(playerId)||!originalLifecycle.known())return false;
            var lifecycle=driverLifecycle==null?originalLifecycle:driverLifecycle;
            boolean driverEntering=driverStarting&&driverEnable!=null&&beforeDriver!=null
                &&modules.ownEnableInvocationMatches(driverEnable,beforeDriver,driver);
            if(!driverEntering&&!modules.lifecycleCurrent(lifecycle))return false;
            if(cycle.plans().stream().filter(p->p.profileId().equals(profile)).findFirst().orElse(null)!=plan)return false;
            // The actual family checkpoint validates its profile/progress before each source operation.
            // This final native leaf invokes no virtual family getters.
            if(!settings.current()||!(driverEntering?modules.ownEnableInvocationMatches(driverEnable,beforeDriver,driver):modules.lifecycleCurrent(lifecycle)))return false;
            if(runtime.mode()!=SuspendedFarmReturnRuntime.Mode.DISCONNECT_RECONNECT||runtime.state()==SuspendedFarmReturnRuntime.State.PAUSING
                    ||runtime.state()==SuspendedFarmReturnRuntime.State.PAUSED)return originalNative();
            return ownedTransportScene()!=SuspendedFarmReturnRuntime.Scene.INVALID;
        }
        private boolean originalNative(){return client.player==player&&client.level==world&&client.getConnection()==listener
            &&client.getConnection()!=null&&client.getConnection().getConnection()==connection&&client.player.getGameProfile().id().equals(playerId)
            &&(server==null?client.getCurrentServer()==null:client.getCurrentServer()==server&&Objects.equals(server.ip,address)&&Objects.equals(server.name,name))
            &&originalSignals!=null&&originalSignals.current()&&originalSceneStoredFinal();}
        /** Stored/native identities after all gameplay header visitors. No text, semantic or role provider runs. */
        private boolean originalSceneStoredFinal(){return storedAffiliationFinal()&&client.player==player&&client.level==world&&client.getConnection()==listener
            &&client.getConnection()!=null&&client.getConnection().getConnection()==connection&&client.options==options&&client.gameMode==gameMode
            &&client.player.getGameProfile().id().equals(playerId)
            &&(server==null?client.getCurrentServer()==null:client.getCurrentServer()==server&&Objects.equals(server.ip,address)&&Objects.equals(server.name,name));}
        private boolean storedAffiliationFinal(){return source==this&&client.isSameThread()&&registered()&&settings.current()
            &&modules.get(farm.id()).orElse(null)==farm&&farm.isEnabled()&&client.getUser().getProfileId().equals(sessionId)&&sessionId.equals(playerId)
            &&originalLifecycle.known()&&(driverStarting&&driverEnable!=null&&beforeDriver!=null
                ?modules.ownEnableInvocationMatches(driverEnable,beforeDriver,driver):modules.lifecycleCurrent(driverLifecycle==null?originalLifecycle:driverLifecycle));}
        /** Raw protected content is checked last, after every source/header provider. */
        private boolean storedNativeSourceFinal(){return storedAffiliationFinal()
            &&(joined==null?originalSceneStoredFinal():joined.storedNativeFinal())
            &&inventory!=null&&inventory.retiredContentCurrent(client);}
        private boolean protectedCurrent(){return inventory!=null&&inventory.currentFinal()&&currentRefusal()&&storedNativeSourceFinal();}
        /** A real private issued device selection must validate the full existing source before capture. */
        public McIssuedReturnInventory.Snapshot.DeviceSlotCustody captureDeviceSlotCustody(McSavedAnchorRecovery.IssuedReturnTravelRequest.DeviceSelectionCall actual){
            if(actual==null||!actual.belongsTo(this)||!actual.nativeCurrent()||!protectedCurrent()||inventory==null)return null;
            return inventory.captureDeviceSlotCustody(actual);
        }
        /** Exact retained custody lookup only; it cannot mint a selection or continuation. */
        public McIssuedReturnInventory.Snapshot.DeviceSlotCustody retainedDeviceSlotCustody(McSavedAnchorRecovery.IssuedReturnTravelRequest.DeviceTransit exact){
            return exact!=null&&exact.sourceHeader()==this&&inventory!=null?inventory.retainedDeviceSlotCustody(exact):null;
        }
        private boolean originalDeviceSlotRestored(){return inventory!=null&&inventory.originalSelectedSlotRestored();}
    }

    public final class PauseCall {
        private final SourceHeader original;
        private final VeltraphBootstrap.RootFarmReturnTick rootTick=activeTick;
        private final VeltraphBootstrap.RootFarmReturnAdmission admission;
        private PauseCall(SourceHeader original,VeltraphBootstrap.RootFarmReturnAdmission admission){this.original=original;this.admission=admission;}
        public VeltraphBootstrap.RootFarmReturnAdmission admissionIdentity(){return admission;}
        public VeltraphBootstrap.RootFarmReturnTick rootTickIdentity(){return rootTick;}public FarmModule farm(){return original.farm;}public long serial(){return original.serial;}public String profile(){return original.profile;}public SourceHeader sourceHeader(){return original;}
        public boolean current(FarmModule exact){return rootTick!=null&&pauseCall==this&&source==original&&exact==original.farm&&original.current()
            &&(runtime.state()==SuspendedFarmReturnRuntime.State.PAUSING?activeTick==rootTick&&rootTick.current(McSuspendedFarmReturnCoordinator.this)&&admission!=null&&admission.current(McSuspendedFarmReturnCoordinator.this,rootTick):checkpoint!=null&&runtime.active());}
    }
    /** Stored original admission identity only; the private Root wrapper owns native pause observation. */
    public boolean selectedPauseForRoot(PauseCall exact){return exact!=null&&pauseCall==exact&&source==exact.original
        &&activeTick==exact.rootTick&&activeTick!=null&&runtime.state()==SuspendedFarmReturnRuntime.State.PAUSING
        &&checkpoint==null&&exact.admission!=null;}
    /** Original declaration selected before the actual driver enable; it cannot adopt an observed enable. */
    public final class DriverEnableSource {
        private final SourceHeader original;private final PauseCall pause;private final FarmModule.IssuedReturnCheckpoint cp;
        private final VeltraphBootstrap.RootOriginalInputPublication publication;
        private final ModuleManager.LifecycleSnapshot before;private final VeltraphBootstrap.RootFarmReturnTick tick;
        private final Object player,world,listener,connection,options,gameMode;
        private final Thread ownerThread=Thread.currentThread();
        private ModuleManager.ActualEnableStart start;private boolean claimed;
        private DriverEnableSource(SourceHeader original,ModuleManager.LifecycleSnapshot before,VeltraphBootstrap.RootFarmReturnTick tick){
            this.original=original;this.before=before;this.tick=tick;pause=pauseCall;cp=checkpoint;publication=original.originalInputPublication;
            player=client.player;world=client.level;listener=client.getConnection();connection=client.getConnection()==null?null:client.getConnection().getConnection();
            options=client.options;gameMode=client.gameMode;
        }
        public VeltraphBootstrap.RootOriginalInputPublication originalInputPublication(){return publication;}
        public FarmModule farm(){return original.farm;}public AbstractModule driverIdentity(){return driver;}
        public PauseCall originalPauseCall(){return pause;}public FarmModule.IssuedReturnCheckpoint checkpointIdentity(){return cp;}
        public ModuleManager.LifecycleSnapshot originalDisabledLifecycle(){return before;}
        public boolean claimOriginalEnable(ModuleManager.ActualEnableStart actual){
            if(claimed||actual==null||pendingDriverEnableSource!=this||source!=original||Thread.currentThread()!=ownerThread
                    ||actual.registryIdentity()!=modules||actual.parent()!=driver)return false;
            boolean namedRoot=StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(stream->{
                var caller=stream.filter(frame->frame.getDeclaringClass()!=DriverEnableSource.class).findFirst().orElse(null);
                return caller!=null&&caller.getDeclaringClass()==VeltraphBootstrap.class&&caller.getMethodName().equals("captureOriginalRootEnable");
            });
            if(!namedRoot)return false;claimed=true;start=actual;return beforeCurrent(actual);
        }
        public boolean beforeCurrent(ModuleManager.ActualEnableStart actual){
            return claimed&&start==actual&&pendingDriverEnableSource==this&&driverStarting&&source==original
                &&Thread.currentThread()==ownerThread&&registered()&&tick!=null&&tick.current(McSuspendedFarmReturnCoordinator.this)
                &&actual.registryIdentity()==modules&&actual.parent()==driver&&actual.startingCurrent()
                &&before!=null&&before.known()&&modules.lifecycleCurrent(before)
                &&actual.beforeSnapshot()!=null&&modules.lifecycleCurrent(actual.beforeSnapshot())
                &&pauseCall==pause&&checkpoint==cp&&cp!=null&&original.farm.issuedReturnPausedBound(cp,pause)
                &&publication!=null&&original.originalInputPublication==publication&&original.settings.current()
                &&modules.get(original.farm.id()).orElse(null)==original.farm&&original.farm.isEnabled()
                &&client.player==player&&client.level==world&&client.getConnection()==listener&&listener!=null
                &&client.getConnection().getConnection()==connection&&client.options==options&&client.gameMode==gameMode
                &&original.inventory!=null&&original.inventory.retiredContentCurrent(client);
        }
        /** Authentic normal enable proof; this advances only the declared original driver transition. */
        public boolean matchesCompletedEnable(ModuleManager.OwnEnableReceipt returned){
            return claimed&&start!=null&&returned!=null&&returned.actualStart()==start&&pendingDriverEnableSource==this
                &&source==original&&Thread.currentThread()==ownerThread&&registered()
                &&modules.ownEnableMatches(returned,before,driver)&&original.settings.current()
                &&checkpoint==cp&&pauseCall==pause&&original.farm.issuedReturnPausedBound(cp,pause)
                &&client.player==player&&client.level==world&&client.getConnection()==listener;
        }
    }
    /** Root before-enable observer may read only the declaration that already existed before guard.start. */
    public DriverEnableSource pendingDriverEnableForRoot(ModuleManager.ActualEnableStart actual){
        var declared=pendingDriverEnableSource;
        return declared!=null&&driverStarting&&actual!=null&&actual.registryIdentity()==modules&&actual.parent()==driver?declared:null;
    }

    /** Original Root pre-effect handoff instruction. It never adopts an InputLock snapshot as a publication. */
    public final class DriverInputCall {
        private final SourceHeader original;
        private final PauseCall pause;
        private final FarmModule.IssuedReturnCheckpoint cp;
        private final ModuleManager.OwnEnableReceipt enabled;
        private final ModuleManager.LifecycleSnapshot disabled;
        private final VeltraphBootstrap.RootFarmReturnTick tick;
        private final SuspendedFarmReturnRuntime.State phase;
        private final IssuedFarmExpectedObservation expected;
        private final Object player,world,listener,connection;
        private final Thread thread=Thread.currentThread();
        private VeltraphBootstrap.RootOriginalInputInvocation publicationInvocation;
        private DriverInputCall(SourceHeader original,VeltraphBootstrap.RootFarmReturnTick tick){
            this.original=original;this.tick=tick;pause=pauseCall;cp=checkpoint;enabled=driverEnable;disabled=beforeDriver;phase=runtime.state();
            player=client.player;world=client.level;listener=client.getConnection();connection=client.getConnection()==null?null:client.getConnection().getConnection();
            expected=original.originalNative()?null:IssuedFarmTransportOrigin.currentExpected(McSuspendedFarmReturnCoordinator.this);
        }
        public Minecraft client(){return client;}public McSuspendedFarmReturnCoordinator coordinatorIdentity(){return McSuspendedFarmReturnCoordinator.this;}
        public SourceHeader sourceHeader(){return original;}public PauseCall originalPauseCall(){return pause;}
        public FarmModule farm(){return original.farm;}public FarmModule.IssuedReturnCheckpoint checkpointIdentity(){return cp;}
        public AbstractModule driverIdentity(){return driver;}public ModuleManager.OwnEnableReceipt driverEnableReceipt(){return enabled;}
        public ModuleManager.LifecycleSnapshot originalDisabledLifecycle(){return disabled;}
        public VeltraphBootstrap.RootOriginalInputPublication originalInputPublication(){return original.originalInputPublication;}
        public boolean claimOriginalPublication(VeltraphBootstrap.RootOriginalInputInvocation invocation){
            if(publicationInvocation!=null||invocation==null||!invocation.matchesDriver(this)||driverInputCall!=this||source!=original||Thread.currentThread()!=thread)return false;
            boolean originalEntry=StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(stream->{
                var caller=stream.filter(frame->frame.getDeclaringClass()!=DriverInputCall.class).findFirst().orElse(null);
                return caller!=null&&caller.getDeclaringClass()==VeltraphBootstrap.class&&caller.getMethodName().equals("publishIssuedDriverOriginalInput");
            });
            if(!originalEntry)return false;publicationInvocation=invocation;return currentFinalNative();
        }

        public VeltraphBootstrap.RootFarmReturnTick rootTickIdentity(){return tick;}public long serial(){return original.serial;}
        public Object currentPlayer(){return player;}public Object currentWorld(){return world;}public Object currentConnection(){return listener;}
        public boolean loadedScene(){return player!=null&&world!=null&&listener!=null&&connection!=null&&(original.originalSceneStoredFinal()||joined!=null&&joined.storedNativeFinal());}
        public boolean current(){return original.farm.issuedReturnPausedCurrent(cp)&&original.currentRefusal()&&(!loadedScene()||original.protectedCurrent())&&currentFinalNative();}
        /** Refusal only: an authentic Root updater must independently provide its original publication lineage. */
        public boolean currentFinalNative(){return driverInputCall==this&&source==original&&pauseCall==pause&&checkpoint==cp&&driverEnable==enabled
            &&Thread.currentThread()==thread&&tick.current(McSuspendedFarmReturnCoordinator.this)&&runtime.active()&&runtime.state()==phase
            &&phase!=SuspendedFarmReturnRuntime.State.COMMITTING&&registered()&&original.storedAffiliationFinal()
            &&beforeDriver==disabled&&disabled!=null&&modules.ownEnableMatches(enabled,disabled,driver)&&original.farm.issuedReturnPausedBound(cp,pause)
            &&client.player==player&&client.level==world&&client.getConnection()==listener
            &&(listener==null?connection==null:client.getConnection().getConnection()==connection)
            &&(original.originalSceneStoredFinal()||expected!=null&&expected.currentScene(client,McSuspendedFarmReturnCoordinator.this))
            &&(loadedScene()?original.storedNativeSourceFinal():expected!=null&&expected.currentScene(client,McSuspendedFarmReturnCoordinator.this))
            &&original.storedAffiliationFinal()&&client.player==player&&client.level==world&&client.getConnection()==listener;}
    }
    /** The actual private Root dispatcher alone selects a pre-effect call, once per original callback. */
    public DriverInputCall driverInputForRoot(VeltraphBootstrap.RootFarmReturnTick exact){
        thread();if(exact==null||!exact.current(this)||source==null||checkpoint==null||pauseCall==null||!active()||resumingOriginalFarm())return null;
        boolean originalEntry=StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(stream->{
            var caller=stream.filter(frame->frame.getDeclaringClass()!=McSuspendedFarmReturnCoordinator.class).findFirst().orElse(null);
            return caller!=null&&caller.getDeclaringClass()==VeltraphBootstrap.class&&caller.getMethodName().equals("tickOwnedSuspendedFarmReturn");
        });
        if(!originalEntry)return null;
        if(driverInputAttemptTick==exact)return driverInputCall!=null&&driverInputCall.currentFinalNative()?driverInputCall:null;
        driverInputAttemptTick=exact;driverInputCall=null;
        var next=new DriverInputCall(source,exact);driverInputCall=next;
        if(!next.current()){if(driverInputCall==next)driverInputCall=null;return null;}return next;
    }
    /** Fixed accepted native arrival. Own producer retirement is separately proved by the resume guard. */
    private final class ArrivalStamp {
        private final Object player=client.player,world=client.level,listener=client.getConnection(),connection=client.getConnection().getConnection(),options=client.options,gameMode=client.gameMode;
        private final double x=client.player.getX(),y=client.player.getY(),z=client.player.getZ();
        private final float yaw=client.player.getYRot(),pitch=client.player.getXRot();
        private final net.minecraft.world.entity.Pose posture=client.player.getPose();
        private final int slot=client.player.getInventory().getSelectedSlot();
        private boolean terminalNativeCurrent(){return client.isSameThread()&&client.player==player&&client.level==world&&client.getConnection()==listener
            &&client.getConnection()!=null&&client.getConnection().getConnection()==connection&&client.options==options&&client.gameMode==gameMode
            &&client.player!=null&&client.player.isAlive()&&client.player.level()==client.level&&client.gui.screen()==null&&!client.isPaused()
            &&client.getCameraEntity()==client.player&&client.player.onGround()&&client.player.getDeltaMovement().length()<.1&&client.player.getPose()==posture
            &&client.player.getInventory().getSelectedSlot()==slot&&client.player.containerMenu==client.player.inventoryMenu&&client.player.inventoryMenu.getCarried().isEmpty()
            &&Double.doubleToLongBits(client.player.getX())==Double.doubleToLongBits(x)&&Double.doubleToLongBits(client.player.getY())==Double.doubleToLongBits(y)
            &&Double.doubleToLongBits(client.player.getZ())==Double.doubleToLongBits(z)&&Float.floatToIntBits(client.player.getYRot())==Float.floatToIntBits(yaw)
            &&Float.floatToIntBits(client.player.getXRot())==Float.floatToIntBits(pitch);}
        private boolean current(){return client.isSameThread()&&source!=null&&source.currentRefusal()&&client.player==player&&client.level==world&&client.getConnection()==listener
            &&client.getConnection()!=null&&client.getConnection().getConnection()==connection&&client.options==options&&client.gameMode==gameMode
            &&client.player!=null&&client.player.isAlive()&&client.player.level()==client.level&&client.gui.screen()==null&&!client.isPaused()
            &&client.getCameraEntity()==client.player&&!client.player.isPassenger()&&!client.player.isScoping()
            &&client.player.onGround()&&client.player.getDeltaMovement().length()<.1&&client.player.getPose()==posture
            &&client.player.getInventory().getSelectedSlot()==slot&&client.player.containerMenu==client.player.inventoryMenu&&client.player.inventoryMenu.getCarried().isEmpty()
            &&Double.doubleToLongBits(client.player.getX())==Double.doubleToLongBits(x)&&Double.doubleToLongBits(client.player.getY())==Double.doubleToLongBits(y)
            &&Double.doubleToLongBits(client.player.getZ())==Double.doubleToLongBits(z)&&Float.floatToIntBits(client.player.getYRot())==Float.floatToIntBits(yaw)
            &&Float.floatToIntBits(client.player.getXRot())==Float.floatToIntBits(pitch);}
    }
    public final class ResumeCall {
        private final SourceHeader original;
        private VeltraphBootstrap.RootOriginalInputInvocation publicationInvocation;
        private ResumeCall(SourceHeader original){this.original=original;}
        public FarmModule farm(){return original.farm;}public long serial(){return original.serial;}public String profile(){return original.profile;}public SourceHeader sourceHeader(){return original;}
        public FarmModule.IssuedReturnCheckpoint checkpoint(){return checkpoint;}
        public VeltraphBootstrap.RootOriginalInputPublication originalInputPublication(){return original.originalInputPublication;}
        public boolean claimOriginalPublication(VeltraphBootstrap.RootOriginalInputInvocation invocation){
            if(publicationInvocation!=null||invocation==null||!invocation.matchesResume(this)||resumeCall!=this||source!=original)return false;
            boolean originalEntry=StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(stream->{
                var caller=stream.filter(frame->frame.getDeclaringClass()!=ResumeCall.class).findFirst().orElse(null);
                return caller!=null&&caller.getDeclaringClass()==VeltraphBootstrap.class&&caller.getMethodName().equals("publishIssuedResumeOriginalInput");
            });
            if(!originalEntry)return false;publicationInvocation=invocation;return nativeCurrent();
        }

        public boolean phaseCurrent(){return resumeCall==this&&source==original&&runtime.state()==SuspendedFarmReturnRuntime.State.COMMITTING;}
        public boolean current(FarmModule exact,FarmModule.IssuedReturnCheckpoint expected){return exact==original.farm&&expected==checkpoint&&phaseCurrent()&&original.protectedCurrent()&&nativeCurrent();}
        public boolean nativeCurrent(){return phaseCurrent()&&deadlineObservation>=0&&deadlineObservation<runtime.deadline()&&arrivalStamp!=null&&arrivalStamp.terminalNativeCurrent()&&original.storedNativeSourceFinal()&&original.originalDeviceSlotRestored();}
    }
    /** Actual original saved-anchor operation, not a public travel reservation or capsule factory. */
    /** Same-checkpoint receipt join only. This capsule cannot issue movement or waive a source gate. */
    public final class DeviceAckTransit {
        private final McSavedAnchorRecovery.IssuedReturnTravelRequest.DeviceTransit transit;
        private final dev.veltraph.client.movement.device.DeviceUseReceipt.Issue issue;
        private final Object ownedSource;
        private final SourceHeader original=source;
        private final FarmModule.IssuedReturnCheckpoint cp=checkpoint;
        private final PauseCall pause=pauseCall;
        private final long baselineSignal=acceptedTeleportSerial;
        private final Object player=client.player,world=client.level,listener=client.getConnection();
        private final Object connection=client.getConnection().getConnection();
        private boolean credited,revoked;
        private dev.veltraph.client.movement.device.DeviceUseReceipt.Applied creditedApplied;
        private IssuedFarmAppliedLanding.DeviceAppliedAck creditedAckProof;
        private FinalEntryContinuation finalEntry;
        private DeviceAckTransit(McSavedAnchorRecovery.IssuedReturnTravelRequest.DeviceTransit transit,
                dev.veltraph.client.movement.device.DeviceUseReceipt.Issue issue,Object ownedSource){
            this.transit=transit;this.issue=issue;this.ownedSource=ownedSource;
        }
        public McSavedAnchorRecovery.IssuedReturnTravelRequest.DeviceTransit deviceTransitIdentity(){return transit;}
        public dev.veltraph.client.movement.device.DeviceUseReceipt.Issue issueIdentity(){return issue;}
        public Object sourceIdentity(){return ownedSource;}
        public long baselineSignalSerial(){return baselineSignal;}
        /** HEAD veto only: the genuine native receive observer still supplies its private receipt. */
        public boolean beforeReceiveCurrent(){return !credited&&!revoked&&sourceFieldsCurrent(true)
            &&baselineSignal>=0&&baselineSignal<Long.MAX_VALUE&&acceptedTeleportSerial==baselineSignal
            &&NativeTeleportSignal.read(client).serial()==baselineSignal;}
        private boolean sourceFieldsCurrent(boolean handed){
            var request=transit.requestIdentity();var view=transit.currentViewIdentity();
            return !revoked&&deviceAckTransit==this&&client.isSameThread()&&source==original&&checkpoint==cp&&pauseCall==pause
                &&runtime.state()==SuspendedFarmReturnRuntime.State.RETURNING&&original!=null&&cp!=null
                &&request!=null&&request.returningIdentity()==returning&&request.sourceHeader()==original
                &&request.checkpointIdentity()==cp&&request.driverIdentity()==driver
                &&request.ownsSharedChild(transit.sharedChildIdentity())
                &&transit.custodySourceCurrent()&&transit.nativeIssueCurrent()
                &&transit.issueIdentity()==issue&&transit.sourceIdentity()==ownedSource
                &&(!handed||transit.ackTransitIdentity()==this)
                &&view!=null&&view.original==original&&view.cp==cp&&view.deviceAckSourceCurrent()
                &&client.player==player&&client.level==world&&client.getConnection()==listener&&client.getConnection()!=null
                &&client.getConnection().getConnection()==connection&&issue.context().parent()==driver
                &&issue.context().player()==player&&issue.context().world()==world&&issue.context().connection()==listener
                &&issue.context().policy()==config&&issue.context().journey()>0;
        }
        /** Only a private genuine native application/ACK proof may advance this exact checkpoint. */
        public boolean creditNativeAck(IssuedFarmAppliedLanding.DeviceAppliedAck proof,
                dev.veltraph.client.movement.device.DeviceUseReceipt.Applied applied){
            if(proof==null||applied==null||credited||!sourceFieldsCurrent(true)||acceptedTeleportSerial!=baselineSignal
                ||baselineSignal<0||baselineSignal==Long.MAX_VALUE)return false;
            var signal=NativeTeleportSignal.read(client);
            if(signal.serial()!=baselineSignal+1||signal.position()==null
                ||!proof.matchesTransit(this,issue,ownedSource,baselineSignal,signal)||!proof.matchesApplied(applied)||!proof.current(client)
                ||!issue.context().same(applied.context())||applied.domain()!=issue.domain()||applied.nonce()!=issue.nonce()
                ||applied.serial()<=issue.baseline()||applied.receivedAt()<issue.issuedAt()||applied.receivedAt()>=issue.deadline()
                ||applied.position()==null||!Double.isFinite(applied.position().x())||!Double.isFinite(applied.position().y())
                ||!Double.isFinite(applied.position().z())||applied.position().distanceTo(issue.goal())>issue.tolerance()
                ||signal.position().distanceTo(applied.position())>.01||!sourceFieldsCurrent(true)
                ||acceptedTeleportSerial!=baselineSignal||NativeTeleportSignal.read(client).serial()!=signal.serial())return false;
            creditedApplied=applied;creditedAckProof=proof;acceptedTeleportSerial=signal.serial();credited=true;return true;
        }
        /** Only the actual shared stop captures terminal facts before its original Leg is retired. */
        public FinalEntryContinuation captureFinalEntryContinuation(McSavedAnchorRecovery.IssuedReturnTravelRequest.DeviceTransit exact,
                dev.veltraph.client.movement.device.McInstantTransmission.TerminalCleanupReceipt terminal,
                dev.veltraph.client.navigation.mc.LegacyTravelPublicationLease.Leg predecessor){
            if(exact!=transit||terminal==null||predecessor==null||finalEntry!=null||!credited||revoked
                ||creditedAckProof==null||creditedApplied==null||deviceAckTransit!=this
                ||!originalSharedStopCaller()||transit.originalLegIdentity()!=predecessor
                ||terminal.issue()!=issue||terminal.applied()!=creditedApplied||predecessor.retirementOutcome()!=null
                ||!terminal.current())return null;
            var next=new FinalEntryContinuation(this,terminal,predecessor);
            if(!next.sourceCurrent()||!terminal.current()||!next.sourceCurrent())return null;
            finalEntry=next;return next;
        }
        /** Read-only genuine completed ACK/entry retirement facts; it grants no old or new movement permission. */
        public boolean completedLoadedRetirementCurrent(McSavedAnchorRecovery.IssuedReturnTravelRequest.DeviceTransit exact,
                McSavedAnchorRecovery.IssuedReturnTravelRequest.LoadedSegment selected,
                dev.veltraph.client.navigation.mc.LegacyTravelPublicationLease.Leg retired,
                dev.veltraph.client.navigation.LegacyTravelRetirement.RetirementOutcome outcome){
            return exact==transit&&selected!=null&&transit.loadedSegmentIdentity()==selected&&finalEntry!=null
                &&!revoked&&credited&&creditedAckProof!=null&&creditedApplied!=null&&deviceAckTransit==this
                &&source==original&&checkpoint==cp&&pauseCall==pause&&client.isSameThread()
                &&runtime.state()==SuspendedFarmReturnRuntime.State.RETURNING
                &&transit.requestIdentity().sourceHeader()==original&&transit.requestIdentity().checkpointIdentity()==cp
                &&transit.requestIdentity().driverIdentity()==driver&&selected.requestIdentity()==transit.requestIdentity()
                &&transit.issueIdentity()==issue&&transit.sourceIdentity()==ownedSource
                &&original.originalDeviceSlotRestored()&&original.storedNativeSourceFinal()
                &&client.player==player&&client.level==world&&client.getConnection()==listener&&client.getConnection()!=null
                &&client.getConnection().getConnection()==connection&&acceptedTeleportSerial==baselineSignal+1
                &&NativeTeleportSignal.read(client).serial()==acceptedTeleportSerial
                &&finalEntry.completedLoadedRetirementCurrent(selected,retired,outcome);
        }
        /** Retirement never restores a previous accepted serial or an older receipt. */
        void revoke(){revoked=true;if(finalEntry!=null)finalEntry.revoke();}
    }
    /** One authentic terminal cleanup and real predecessor retirement can hand out one canonical entry Leg. */
    public final class FinalEntryContinuation {
        private final DeviceAckTransit ack;
        private final McSavedAnchorRecovery.IssuedReturnTravelRequest request;
        private final dev.veltraph.client.movement.device.McInstantTransmission.TerminalCleanupReceipt terminal;
        private final dev.veltraph.client.navigation.mc.LegacyTravelPublicationLease.Leg predecessor;
        private final SourceHeader original;
        private final FarmModule.IssuedReturnCheckpoint cp;
        private final Vec3 canonicalGoal,entryGoal;
        private final McSavedAnchorRecovery.IssuedReturnTravelRequest.LoadedSegment loadedSegment;
        private final double originalEntryRadius;
        private final FarmReturnPoint canonicalPoint;
        private final Object player,world,listener,connection;
        private dev.veltraph.client.navigation.LegacyTravelRetirement.RetirementOutcome retirement;
        private Object entryInstruction;
        private dev.veltraph.client.navigation.mc.McSharedMovementNavigator entryShared;
        private dev.veltraph.client.navigation.mc.LegacyTravelPublicationLease.Leg entryLeg;
        private double entryRadius;
        private boolean retirementRecorded,entryClaimed,entryBindAttempted,revoked;
        private FinalEntryContinuation(DeviceAckTransit ack,
                dev.veltraph.client.movement.device.McInstantTransmission.TerminalCleanupReceipt terminal,
                dev.veltraph.client.navigation.mc.LegacyTravelPublicationLease.Leg predecessor){
            this.ack=ack;this.terminal=terminal;this.predecessor=predecessor;request=ack.transit.requestIdentity();
            original=ack.original;cp=ack.cp;canonicalGoal=request.targetPosition();canonicalPoint=request.canonicalTargetPoint();
            loadedSegment=ack.transit.loadedSegmentIdentity();entryGoal=ack.transit.movementTargetPosition();originalEntryRadius=ack.transit.movementTerminalRadius();
            player=ack.player;world=ack.world;listener=ack.listener;connection=ack.connection;
        }
        public McSavedAnchorRecovery.IssuedReturnTravelRequest requestIdentity(){return request;}
        public SourceHeader sourceHeader(){return original;}
        public FarmModule.IssuedReturnCheckpoint checkpointIdentity(){return cp;}
        public AbstractModule driverIdentity(){return driver;}
        public FarmReturnPoint canonicalTargetPoint(){return canonicalPoint;}
        public McSavedAnchorRecovery.IssuedReturnTravelRequest.LoadedSegment loadedSegmentIdentity(){return loadedSegment;}
        public Vec3 movementTargetPosition(){return entryGoal;}
        public double movementTerminalRadius(){return originalEntryRadius;}
        private boolean movementSourceCurrent(){return Double.isFinite(originalEntryRadius)&&originalEntryRadius>0&&originalEntryRadius<=.35
            &&(loadedSegment==null?ack.transit.loadedSegmentIdentity()==null&&canonicalGoal.equals(entryGoal)
                :ack.transit.loadedSegmentIdentity()==loadedSegment&&loadedSegment.requestIdentity()==request
                    &&loadedSegment.sharedChildIdentity()==ack.transit.sharedChildIdentity()
                    &&request.currentLoadedSegmentFieldsCurrent(loadedSegment.sourceIdentity())
                    &&entryGoal.equals(loadedSegment.segmentPosition())
                    &&Double.doubleToLongBits(originalEntryRadius)==Double.doubleToLongBits(loadedSegment.terminalRadius()));}
        public dev.veltraph.client.navigation.mc.LegacyTravelPublicationLease.Leg predecessorIdentity(){return predecessor;}
        public dev.veltraph.client.movement.device.McInstantTransmission.TerminalCleanupReceipt terminalIdentity(){return terminal;}
        private boolean sourceCurrent(){
            var view=ack.transit.terminalViewIdentity(terminal);
            return !revoked&&!ack.revoked&&ack.credited&&ack.creditedAckProof!=null&&ack.creditedApplied!=null
                &&deviceAckTransit==ack&&source==original&&checkpoint==cp&&pauseCall==ack.pause&&client.isSameThread()
                &&ack.transit.requestIdentity()==request&&request.returningIdentity()==returning
                &&request.sourceHeader()==original&&request.checkpointIdentity()==cp&&request.driverIdentity()==driver
                &&request.canonicalTargetPoint()==canonicalPoint&&canonicalGoal.equals(request.targetPosition())&&movementSourceCurrent()
                &&ack.transit.originalLegIdentity()==predecessor&&ack.transit.ackTransitIdentity()==ack
                &&ack.transit.issueIdentity()==ack.issue&&ack.transit.sourceIdentity()==ack.ownedSource
                &&ack.transit.terminalSourceFieldsCurrent(terminal)
                &&terminal.issue()==ack.issue&&terminal.applied()==ack.creditedApplied
                &&view!=null&&view.original==original&&view.cp==cp&&(view.nativeCurrent()||view.retainedNativeCurrent())
                &&original.originalDeviceSlotRestored()&&original.storedNativeSourceFinal()
                &&client.player==player&&client.level==world&&client.getConnection()==listener&&client.getConnection()!=null
                &&client.getConnection().getConnection()==connection&&acceptedTeleportSerial==ack.baselineSignal+1
                &&NativeTeleportSignal.read(client).serial()==acceptedTeleportSerial;
        }
        private boolean actualRetiredContextCurrent(){
            if(!retirementRecorded||retirement==null||predecessor.retirementOutcome()!=retirement||!retirement.cleanSuccess()
                ||retirement.removal()!=dev.veltraph.client.navigation.LegacyTravelRetirement.Removal.REMOVED_OWN)return false;
            var context=predecessor.retirementContinuationContext();
            return context!=null&&context.activeLeg()==null&&context.root()==modules&&context.parent()==driver&&context.inputOwner()==driver
                &&context.cameraAuthority()==driver&&context.player()==player&&context.world()==world&&context.connection()==listener
                &&context.config()==config&&context.journey()==predecessor.journeyGeneration()
                &&context.epoch()==predecessor.cameraEpoch()&&context.requestGeneration()==predecessor.requestGeneration()
                &&sourceCurrent();
        }
        /** Supplied outcome must be the exact actual same-Leg normally returned object, never a copied record. */
        public boolean recordOriginalRetirement(dev.veltraph.client.navigation.LegacyTravelRetirement.RetirementOutcome outcome){
            if(retirementRecorded||entryClaimed||outcome==null||!originalSharedStopCaller()||!sourceCurrent()
                ||predecessor.retirementOutcome()!=outcome||!outcome.cleanSuccess()
                ||outcome.removal()!=dev.veltraph.client.navigation.LegacyTravelRetirement.Removal.REMOVED_OWN)return false;
            retirement=outcome;retirementRecorded=true;
            if(!actualRetiredContextCurrent()){revoked=true;return false;}return true;
        }
        /** Receipt-only preclaim veto. The request additionally verifies its private original TICK instruction/radius. */
        public boolean entryClaimSourceCurrent(McSavedAnchorRecovery.IssuedReturnTravelRequest exact,Object instruction,
                dev.veltraph.client.navigation.mc.McSharedMovementNavigator shared,Vec3 goal,double radius){
            return !entryClaimed&&entryLeg==null&&exact==request&&instruction!=null&&shared==ack.transit.sharedChildIdentity()
                &&request.ownsSharedChild(shared)&&entryGoal.equals(goal)&&Double.isFinite(radius)&&radius>0&&radius<=.35
                &&Double.doubleToLongBits(radius)==Double.doubleToLongBits(originalEntryRadius)
                &&sourceCurrent()&&request.nativeCurrent()&&request.publicationSourceSnapshotCurrent(instruction)
                &&actualRetiredContextCurrent();
        }
        /** Actual typed entry records the request's successful private claim before constructing its new Leg. */
        public boolean recordEntryInstruction(McSavedAnchorRecovery.IssuedReturnTravelRequest exact,Object instruction,
                dev.veltraph.client.navigation.mc.McSharedMovementNavigator shared,Vec3 goal,double radius){
            if(!originalFinalEntryCaller()||!entryClaimSourceCurrent(exact,instruction,shared,goal,radius)
                ||!request.currentFinalEntryInstructionCurrent(this,instruction,shared,goal,radius)
                ||!sourceCurrent()||!actualRetiredContextCurrent())return false;
            entryInstruction=instruction;entryShared=shared;entryRadius=radius;entryClaimed=true;return true;
        }
        /** Only an authentic private Leg with this exact typed source may complete the single admission. */
        public boolean bindEntryLeg(dev.veltraph.client.navigation.mc.LegacyTravelPublicationLease.Leg exact){
            if(!originalFinalEntryCaller()||!entryClaimed||entryBindAttempted||entryLeg!=null)return false;
            entryBindAttempted=true; // Consume the real named attempt before any source/provider reentry.
            if(exact==null||exact==predecessor||!sourceCurrent()
                ||!request.currentFinalEntryInstructionCurrent(this,entryInstruction,entryShared,entryGoal,entryRadius)
                ||!exact.issuedReturnEntrySourceFieldsCurrent(this,request,entryShared,entryGoal,entryRadius)
                ||!sourceCurrent()||NativeTeleportSignal.read(client).serial()!=acceptedTeleportSerial){revoked=true;return false;}
            entryLeg=exact;return true;
        }
        /** The genuine entry Leg still owns every later native publication and retirement guard. */
        public boolean entryCurrent(McSavedAnchorRecovery.IssuedReturnTravelRequest exact,Object instruction,
                dev.veltraph.client.navigation.mc.McSharedMovementNavigator shared,Vec3 goal,double radius){
            return entryClaimed&&entryLeg!=null&&exact==request&&instruction==entryInstruction&&shared==entryShared
                &&entryGoal.equals(goal)&&Double.doubleToLongBits(radius)==Double.doubleToLongBits(entryRadius)
                &&sourceCurrent()&&request.currentFinalEntryInstructionCurrent(this,entryInstruction,entryShared,entryGoal,entryRadius)
                &&entryLeg.issuedReturnEntrySourceFieldsCurrent(this,request,entryShared,entryGoal,entryRadius)
                &&NativeTeleportSignal.read(client).serial()==acceptedTeleportSerial;
        }
        private boolean completedLoadedRetirementCurrent(McSavedAnchorRecovery.IssuedReturnTravelRequest.LoadedSegment selected,
                dev.veltraph.client.navigation.mc.LegacyTravelPublicationLease.Leg retired,
                dev.veltraph.client.navigation.LegacyTravelRetirement.RetirementOutcome outcome){
            return !revoked&&loadedSegment==selected&&ack.finalEntry==this&&entryClaimed&&entryBindAttempted
                &&entryInstruction!=null&&entryShared==selected.sharedChildIdentity()&&entryLeg==retired&&retired!=null
                &&retirementRecorded&&retirement!=null&&predecessor.retirementOutcome()==retirement&&retirement.cleanSuccess()
                &&outcome!=null&&retired.retirementOutcome()==outcome&&outcome.cleanSuccess()
                &&outcome.removal()==dev.veltraph.client.navigation.LegacyTravelRetirement.Removal.REMOVED_OWN
                &&selected.sourceIdentity().actualRetiredLegIdentity()==retired
                &&selected.sourceIdentity().actualSegmentCleanupIdentity()==outcome&&selected.sourceIdentity().retiredLineageCurrent()
                &&request.sourceHeader()==original&&request.checkpointIdentity()==cp&&request.driverIdentity()==driver
                &&request.canonicalTargetPoint()==canonicalPoint&&canonicalGoal.equals(request.targetPosition())
                &&entryGoal.equals(selected.segmentPosition())&&Double.doubleToLongBits(entryRadius)==Double.doubleToLongBits(originalEntryRadius)
                &&terminal.issue()==ack.issue&&terminal.applied()==ack.creditedApplied;
        }
        private void revoke(){revoked=true;}
    }
    private static boolean originalSharedStopCaller(){return StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(stream->{
        var caller=stream.filter(frame->frame.getDeclaringClass()!=McSuspendedFarmReturnCoordinator.class
            &&frame.getDeclaringClass()!=DeviceAckTransit.class&&frame.getDeclaringClass()!=FinalEntryContinuation.class).findFirst().orElse(null);
        return caller!=null&&caller.getDeclaringClass()==dev.veltraph.client.navigation.mc.McSharedMovementNavigator.class
            &&java.util.Set.of("stopOwnedIssuedReturn","stopOwned").contains(caller.getMethodName());
    });}
    private static boolean originalFinalEntryCaller(){return StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(stream->{
        var caller=stream.filter(frame->frame.getDeclaringClass()!=McSuspendedFarmReturnCoordinator.class
            &&frame.getDeclaringClass()!=DeviceAckTransit.class&&frame.getDeclaringClass()!=FinalEntryContinuation.class).findFirst().orElse(null);
        return caller!=null&&caller.getDeclaringClass()==dev.veltraph.client.navigation.mc.LegacyTravelPublicationLease.class
            &&caller.getMethodName().equals("beginAfterIssuedReturnEntry");
    });}

    private DeviceAckTransit deviceAckTransit;
    /** Called only by the private selected request's real device-source capture. */
    DeviceAckTransit captureIssuedDeviceAck(McSavedAnchorRecovery.IssuedReturnTravelRequest.DeviceTransit transit,
            dev.veltraph.client.movement.device.DeviceUseReceipt.Issue issue,Object ownedSource){
        if(transit==null||issue==null||ownedSource==null||source==null||checkpoint==null||client.getConnection()==null
            ||deviceAckTransit!=null&&!deviceAckTransit.credited&&!deviceAckTransit.revoked)return null;
        var view=transit.currentViewIdentity();
        if(view==null||!view.nativeCurrent()||transit.requestIdentity()==null
            ||transit.requestIdentity().sourceHeader()!=source||transit.requestIdentity().checkpointIdentity()!=checkpoint)return null;
        var next=new DeviceAckTransit(transit,issue,ownedSource);deviceAckTransit=next;
        if(!next.sourceFieldsCurrent(false)||acceptedTeleportSerial!=next.baselineSignal
                ||next.baselineSignal<0||next.baselineSignal==Long.MAX_VALUE
                ||NativeTeleportSignal.read(client).serial()!=next.baselineSignal){next.revoke();deviceAckTransit=null;return null;}return next;
    }

    public final class MovementView {
        private final SourceHeader original;
        private final FarmModule.IssuedReturnCheckpoint cp;
        private final ReturnCleanupCall operation;
        private final VeltraphBootstrap.RootFarmReturnTick rootTick=activeTick;
        private final int nativeTick=client.player.tickCount;
        private boolean instructionReturned;
        private final Object player=client.player,world=client.level,listener=client.getConnection(),connection=client.getConnection().getConnection();
        private MovementView(SourceHeader original,FarmModule.IssuedReturnCheckpoint cp,ReturnCleanupCall operation){this.original=original;this.cp=cp;this.operation=operation;}
        public Minecraft client(){return client;}public McSuspendedFarmReturnCoordinator coordinatorIdentity(){return McSuspendedFarmReturnCoordinator.this;}
        public SourceHeader sourceHeader(){return original;}public FarmModule farm(){return original.farm;}public AbstractModule driver(){return McSuspendedFarmReturnCoordinator.this.driver;}
        public AbstractModule driverIdentity(){return driver();}public FarmModule.IssuedReturnCheckpoint checkpointIdentity(){return cp;}
        public VeltraphBootstrap.RootFarmReturnTick rootTickIdentity(){return rootTick;}
        public FarmModule.IssuedReturnCheckpoint checkpoint(){return cp;}public long serial(){return original.serial;}public String profile(){return original.profile;}
        public Vec3 targetPosition(){return original.plan.start().position();}public Vec3 canonicalTarget(){return targetPosition();}
        public FarmReturnPoint canonicalTargetPoint(){return original.plan.start();}
        public dev.veltraph.client.navigation.SharedMovementPolicy.Purpose purpose(){return dev.veltraph.client.navigation.SharedMovementPolicy.Purpose.EXACT_RETURN;}
        public McSavedAnchorRecovery returningIdentity(){return returning;}
        public ReturnCleanupCall originalOperation(){return operation;}
        /** Original runtime deadline is a refusal bound only, never a movement/camera/slot capability. */
        public long originalReturnDeadlineMillis(){
            long actual=runtime.deadline();
            return originalReturnDeadlineSourceCurrent(original,cp,actual)?actual:-1;
        }
        /** Actual same header/checkpoint/runtime pair; source permission must be checked independently. */
        public boolean originalReturnDeadlineSourceCurrent(SourceHeader exact,FarmModule.IssuedReturnCheckpoint expected,long deadline){
            return deadline>0&&source==original&&exact==original&&checkpoint==cp&&expected==cp&&cp!=null
                &&runtime.active()&&runtime.deadline()==deadline&&returning!=null
                &&original.farm.issuedReturnPausedBound(cp,pauseCall);
        }
        /** Genuine request capsule capture only; raw metadata or caller-supplied issue records cannot qualify. */
        public DeviceAckTransit captureDeviceAckTransit(McSavedAnchorRecovery.IssuedReturnTravelRequest.DeviceTransit transit){
            if(transit==null||transit.currentViewIdentity()!=this||!nativeCurrent()||!transit.custodySourceCurrent()
                ||!transit.nativeIssueCurrent()||transit.ackTransitIdentity()!=null)return null;
            return captureIssuedDeviceAck(transit,transit.issueIdentity(),transit.sourceIdentity());
        }
        /** Receipt-only source veto. No input/camera publication or serial adoption is authorized here. */
        private boolean deviceAckSourceCurrent(){
            boolean selected=movementView==this&&returnCleanupCall==operation&&activeTick==rootTick
                &&rootTick!=null&&rootTick.current(McSuspendedFarmReturnCoordinator.this)&&operation.nativeCurrent();
            boolean retained=retainedMovementView==this&&instructionReturned&&rootTick!=null&&rootTick.returnedNormal()
                &&rootTick.retainedRegistrationCurrent(McSuspendedFarmReturnCoordinator.this)
                &&client.player!=null&&(long)client.player.tickCount>=nativeTick&&(long)client.player.tickCount<=(long)nativeTick+1;
            return (selected||retained)&&source==original&&checkpoint==cp&&registered()&&original.storedNativeSourceFinal()
                &&client.player==player&&client.level==world&&client.getConnection()==listener&&client.getConnection()!=null
                &&client.getConnection().getConnection()==connection&&appliedLanding!=null&&appliedLanding.current(client)
                &&original.farm.issuedReturnPausedBound(cp,pauseCall);
        }
        public boolean current(){return movementView==this&&operation.current()&&original.farm.issuedReturnPausedCurrent(cp)&&nativeCurrent();}
        /** Refusal only. Actual private Journey/child request must independently bind this view. */
        public boolean nativeCurrent(){return currentFinalNative();}
        public boolean currentFinalNative(){return movementView==this&&returnCleanupCall==operation&&source==original&&checkpoint==cp
            &&rootTick!=null&&activeTick==rootTick&&rootTick.current(McSuspendedFarmReturnCoordinator.this)&&operation.nativeCurrent()&&appliedLanding!=null&&appliedLanding.current(client)
            &&acceptedTeleportSerial>=0&&NativeTeleportSignal.read(client).serial()==acceptedTeleportSerial
            &&client.player==player&&client.level==world&&client.getConnection()==listener&&client.getConnection()!=null
            &&client.getConnection().getConnection()==connection&&original.farm.issuedReturnPausedBound(cp,pauseCall);}
        /** Prior already-applied-frame validation only; it cannot publish or renew a native admission. */
        public boolean publicationObservationNativeCurrent(){return instructionReturned&&rootTick!=null
            &&rootTick.returnedNormal()&&rootTick.retainedRegistrationCurrent(McSuspendedFarmReturnCoordinator.this)
            &&source==original&&checkpoint==cp&&registered()&&original.storedNativeSourceFinal()
            &&client.player==player&&client.level==world&&client.getConnection()==listener&&client.getConnection()!=null
            &&client.getConnection().getConnection()==connection&&(long)client.player.tickCount>=nativeTick&&(long)client.player.tickCount<=(long)nativeTick+1
            &&appliedLanding!=null&&appliedLanding.current(client)&&acceptedTeleportSerial>=0&&NativeTeleportSignal.read(client).serial()==acceptedTeleportSerial
            &&original.farm.issuedReturnPausedBound(cp,pauseCall);}
        public boolean retainedNativeCurrent(){return retainedMovementView==this&&instructionReturned&&rootTick!=null
            &&rootTick.returnedNormal()&&rootTick.retainedRegistrationCurrent(McSuspendedFarmReturnCoordinator.this)
            &&source==original&&checkpoint==cp&&registered()&&original.storedNativeSourceFinal()
            &&client.player==player&&client.level==world&&client.getConnection()==listener&&client.getConnection()!=null
            &&client.getConnection().getConnection()==connection&&(long)client.player.tickCount>=nativeTick&&(long)client.player.tickCount<=(long)nativeTick+1
            &&appliedLanding!=null&&appliedLanding.current(client)&&acceptedTeleportSerial>=0&&NativeTeleportSignal.read(client).serial()==acceptedTeleportSerial
            &&original.farm.issuedReturnPausedBound(cp,pauseCall);}
    }
    public MovementView currentMovementView(){var view=movementView;return view!=null&&view.nativeCurrent()?view:null;}
    /** Identity only, including revoked validity; this can refuse downgrade but cannot grant an action. */
    public MovementView selectedMovementViewIdentity(McSavedAnchorRecovery exact){return exact==returning?movementView:null;}
    public boolean ownsReturningIdentity(McSavedAnchorRecovery exact){return exact==returning;}
    /** Only a real private saved-anchor instruction and original Root dispatch can retain this source view. */
    public MovementView retainedMovementView(){var view=retainedMovementView;return view!=null&&view.retainedNativeCurrent()?view:null;}

    /** Genuine return-step retirement request; only the selected issuer creates this call. */
    public final class ReturnCleanupCall {
        private final SourceHeader original;
        private final Object owner;
        private final ModuleManager.LifecycleSnapshot lifecycle;
        private final Object player,world,listener,connection,options,gameMode;
        private ReturnCleanupCall(SourceHeader original,Object actualOwner){
            this.original=original;owner=actualOwner;lifecycle=modules.lifecycleSnapshot();player=client.player;world=client.level;
            listener=client.getConnection();connection=client.getConnection().getConnection();options=client.options;gameMode=client.gameMode;
        }
        public Minecraft client(){return client;}public Object ownerIdentity(){return owner;}public AbstractModule driverIdentity(){return driver;}
        public SourceHeader sourceHeader(){return original;}public McSavedAnchorRecovery returningIdentity(){return returning;}
        public boolean current(){return returnCleanupCall==this&&currentGameplayOwner()
            &&original.farm.issuedReturnPausedCurrent(checkpoint)&&original.protectedCurrent()&&nativeCurrent();}
        /** No role/provider/semantic codec is invoked by this final retirement predicate. */
        public boolean nativeCurrent(){return returnCleanupCall==this&&source==original&&hasOwnedRootTick()&&owner==driver
            &&registered()&&original.storedNativeSourceFinal()&&lifecycle.known()&&modules.lifecycleCurrent(lifecycle)
            &&checkpoint!=null&&original.farm.issuedReturnPausedBound(checkpoint,pauseCall)
            &&client.player==player&&client.level==world&&client.getConnection()==listener&&client.getConnection()!=null
            &&client.getConnection().getConnection()==connection&&client.options==options&&client.gameMode==gameMode
            &&client.player!=null&&client.player.isAlive()&&client.player.level()==client.level&&client.gui.screen()==null&&!client.isPaused();}
    }
    public ReturnCleanupCall currentReturnCleanupCall(){var call=returnCleanupCall;return call!=null&&call.nativeCurrent()?call:null;}

    public final class CommandCall {
        private final SourceHeader original;
        private final ClientPacketListener listener=client.getConnection();
        private final Connection connection=listener.getConnection();
        private final ModuleManager.LifecycleSnapshot lifecycle=modules.lifecycleSnapshot();
        private final dev.veltraph.client.input.ScriptingInputLock.ControlSnapshot input=dev.veltraph.client.input.ScriptingInputLock.controlSnapshot();
        private final McOwnedRotation.InstallationSnapshot installation=McOwnedRotation.installationSnapshot(client);
        private final long epoch=McOwnedRotation.epoch(client),publication=McOwnedRotation.publicationSerial(client);
        private final Object player=client.player,world=client.level,options=client.options,gameMode=client.gameMode;
        private final double x=client.player.getX(),y=client.player.getY(),z=client.player.getZ();
        private final float yaw=client.player.getYRot(),pitch=client.player.getXRot();
        private final Object responseNonce=new Object();
        private final net.minecraft.client.KeyMapping[] manualKeys={client.options.keyUp,client.options.keyDown,client.options.keyLeft,client.options.keyRight,client.options.keyJump,client.options.keyShift,client.options.keySprint,client.options.keyAttack,client.options.keyUse};
        private final Object[] unownedKeySnapshots=new Object[9],bindingSnapshots=new Object[9];
        private final Object manualMouse=client.mouseHandler,mouseSnapshot;
        private CommandCall(SourceHeader original){this.original=original;
            for(int i=0;i<9;i++){
                if(manualKeys[i]!=null&&(manualKeys[i].getClass()==net.minecraft.client.KeyMapping.class||manualKeys[i].getClass()==net.minecraft.client.ToggleKeyMapping.class)&&manualKeys[i] instanceof dev.veltraph.client.service.mc.NormalIdleKeyCarrier carrier)unownedKeySnapshots[i]=carrier.normalIdleUnownedSnapshot();
                bindingSnapshots[i]=dev.veltraph.client.observation.nativeinput.NativeKeyBindingHistory.snapshot(manualKeys[i]);
            }
            mouseSnapshot=manualMouse!=null&&manualMouse.getClass()==net.minecraft.client.MouseHandler.class&&manualMouse instanceof dev.veltraph.client.service.mc.NormalIdleMouseCarrier carrier?carrier.normalIdleMouseSnapshot():null;
        }
        /** Negative native override history only; ordinary certified Root neutral-key writes do not rearm it. */
        private boolean manualInputCurrent(){
            var keys=new net.minecraft.client.KeyMapping[]{client.options.keyUp,client.options.keyDown,client.options.keyLeft,client.options.keyRight,client.options.keyJump,client.options.keyShift,client.options.keySprint,client.options.keyAttack,client.options.keyUse};
            if(client.mouseHandler!=manualMouse||mouseSnapshot==null||!(manualMouse instanceof dev.veltraph.client.service.mc.NormalIdleMouseCarrier mouse)||!mouse.normalIdleMouseCurrent(mouseSnapshot))return false;
            for(int i=0;i<9;i++)if(keys[i]!=manualKeys[i]||unownedKeySnapshots[i]==null||bindingSnapshots[i]==null
                ||keys[i].getClass()!=net.minecraft.client.KeyMapping.class&&keys[i].getClass()!=net.minecraft.client.ToggleKeyMapping.class
                ||!(keys[i] instanceof dev.veltraph.client.service.mc.NormalIdleKeyCarrier carrier)||!carrier.normalIdleUnownedCurrent(unownedKeySnapshots[i])
                ||!dev.veltraph.client.observation.nativeinput.NativeKeyBindingHistory.current(keys[i],bindingSnapshots[i]))return false;
            return true;
        }
        public ClientPacketListener listener(){return listener;}public Connection connection(){return connection;}public String command(){return original.command;}public long serial(){return original.serial;}
        public Minecraft client(){return client;}
        /** Semantic/provider checks occur before the stored native command leaf is used. */
        public boolean current(){return commandCall==this&&observeDeadline()&&original.farm.issuedReturnPausedCurrent(checkpoint)&&currentGameplayOwner()&&original.protectedCurrent()&&currentFinalNative();}
        public boolean nativeCurrent(){return currentFinalNative();}
        public boolean currentFinalNative(){return commandCall==this&&deadlineObservation>=0&&deadlineObservation<runtime.deadline()&&runtime.state()==SuspendedFarmReturnRuntime.State.COMMANDING&&baseNativeCurrent()
            &&Double.doubleToLongBits(client.player.getX())==Double.doubleToLongBits(x)&&Double.doubleToLongBits(client.player.getY())==Double.doubleToLongBits(y)
            &&Double.doubleToLongBits(client.player.getZ())==Double.doubleToLongBits(z)&&Float.floatToIntBits(client.player.getYRot())==Float.floatToIntBits(yaw)
            &&Float.floatToIntBits(client.player.getXRot())==Float.floatToIntBits(pitch);}
        private boolean baseNativeCurrent(){return source==original&&original.storedNativeSourceFinal()
            &&checkpoint!=null&&original.farm.issuedReturnPausedBound(checkpoint,pauseCall)&&lifecycle.known()&&modules.lifecycleCurrent(lifecycle)
            &&input!=null&&input.current()&&input.active()&&driver.id().equals(input.ownerModuleId())&&installation!=null&&installation.current()
            &&McOwnedRotation.epoch(client)==epoch&&McOwnedRotation.publicationSerial(client)==publication
            &&client.player==player&&client.level==world&&client.getConnection()==listener&&listener.getConnection()==connection&&client.options==options&&client.gameMode==gameMode
            &&client.player!=null&&client.player.isAlive()&&client.player.level()==client.level&&client.getCameraEntity()==player
            &&client.gui.screen()==null&&!client.isPaused()&&!client.player.isPassenger()&&!client.player.isScoping()
            &&!client.level.tickRateManager().isEntityFrozen(client.player)&&client.player.containerMenu==client.player.inventoryMenu&&client.player.inventoryMenu.getCarried().isEmpty();}
    }
    /** Privately sealed only after this actual command's native submission normally returned. */
    public final class LandingCall {
        private final CommandCall issued;
        private final IssuedFarmCommandFence.Submitted submission;
        private final long baseline,deadline;
        private boolean consumed;
        private LandingCall(CommandCall issued,IssuedFarmCommandFence.Submitted submission,long baseline){this.issued=issued;this.submission=submission;this.baseline=baseline;deadline=runtime.deadline();
            responseOrder=new dev.veltraph.client.rewarp.OwnCommandOperationalResponseOrder(submission.nativeIncomingBaseline());
            if(baseline<0||baseline==Long.MAX_VALUE)responseOrder.refuse();
        }
        /** Actual native ingress attempt. Its local nonce associates a source; it is not a server echo. */
        public final class OperationalAttempt {
            private final Object nonce=issued.responseNonce;
            private final long origin;
            private OperationalAttempt(long origin){this.origin=origin;}
        }
        private OperationalAttempt operationalAttempt;
        private OperationalResponse operationalResponse;
        private final dev.veltraph.client.rewarp.OwnCommandOperationalResponseOrder responseOrder;
        private boolean nativeResponseCaller(){return StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(stream->{
            var first=stream.filter(f->f.getDeclaringClass()!=LandingCall.class).findFirst().orElse(null);
            return first!=null&&first.getDeclaringClass()==IssuedFarmAppliedLanding.class;
        });}
        /** Only the installed genuine incoming factory may claim the first post-native-send response. */
        OperationalAttempt claimOriginalOperationalResponse(long origin){
            if(!nativeResponseCaller())return null;
            if(!currentFinalNative()||!responseOrder.begin(origin)){responseOrder.refuse();return null;}
            operationalAttempt=new OperationalAttempt(origin);return operationalAttempt;
        }
        /** Observation failure only: one-way loss; cannot mint, refresh, or publish a response. */
        void loseOriginalOperationalObservation(){responseOrder.refuse();}
        void refuseUnprovenOriginalIngress(){if(nativeResponseCaller())responseOrder.refuse();}
        void refuseOriginalOperationalResponse(OperationalAttempt exact){
            if(nativeResponseCaller()&&exact!=null&&operationalAttempt==exact)responseOrder.refuse();
        }
        OperationalResponse completeOriginalOperationalResponse(OperationalAttempt exact,IssuedFarmAppliedLanding.Receipt receipt){
            if(!nativeResponseCaller()||exact==null||exact!=operationalAttempt||receipt==null||!receipt.belongsTo(this,exact)
                ||operationalResponse!=null||!currentFinalNative()||!responseOrder.returned(exact.origin,true)){
                responseOrder.refuse();return null;
            }
            operationalResponse=new OperationalResponse(exact,receipt);return operationalResponse;
        }
        public boolean operationalResponseRefused(){return responseOrder.refused();}
        /** Only the actual full handler/ACK proof can mint this observed operational origin. */
        public final class OperationalResponse {
            private final OperationalAttempt originalAttempt;
            private final IssuedFarmAppliedLanding.Receipt receipt;
            private final SourceHeader original=issued.original;
            private final FarmModule.IssuedReturnCheckpoint originalCheckpoint=checkpoint;
            private final FarmCyclePlan canonicalPlan=original.plan;
            private final String profile=original.profile,dimension=original.plan.start().dimension();
            private final Vec3 origin;
            private final long nativeSignalSerial;
            private final Object player=client.player,world=client.level,listener=client.getConnection(),options=client.options,gameMode=client.gameMode;
            private OperationalResponse(OperationalAttempt originalAttempt,IssuedFarmAppliedLanding.Receipt receipt){
                this.originalAttempt=originalAttempt;this.receipt=receipt;origin=receipt.position();nativeSignalSerial=receipt.nativeSignalSerial();
            }
            public Vec3 observedOrigin(){return origin;}
            private boolean nativeProofCurrent(){
                if(operationalResponse!=this||operationalAttempt!=originalAttempt||originalAttempt.nonce!=issued.responseNonce
                    ||!responseOrder.complete()||source!=original||checkpoint!=originalCheckpoint||original.plan!=canonicalPlan
                    ||!receipt.belongsTo(LandingCall.this,originalAttempt)||!receipt.current(client)||receipt.originSerial()!=originalAttempt.origin
                    ||receipt.originSerial()<=submission.nativeIncomingBaseline()||submission.nativeIncomingBaseline()<0
                    ||receipt.serial()!=baseline+1||beforeCommand==null||beforeCommand.serial()==Long.MAX_VALUE
                    ||nativeSignalSerial!=beforeCommand.serial()+1||NativeTeleportSignal.read(client).serial()!=nativeSignalSerial
                    ||client.player!=player||client.level!=world||client.getConnection()!=listener||client.options!=options||client.gameMode!=gameMode
                    ||origin==null||!dev.veltraph.client.navigation.SharedMovementPolicy.finite(origin)
                    ||client.player==null||new Vec3(client.player.getX(),client.player.getY(),client.player.getZ()).distanceTo(origin)>.35
                    ||!currentFinalNative()||!issued.manualInputCurrent())return false;
                return original.storedNativeSourceFinal();
            }
            /** Parsed area/profile and loaded full native footprint are observers; all final facts follow them. */
            boolean admissionCurrent(){
                try{
                    if(!nativeProofCurrent()||!original.currentRefusal()||!original.farm.issuedReturnPausedCurrent(originalCheckpoint))return false;
                    var point=new Vec3(client.player.getX(),client.player.getY(),client.player.getZ());
                    var area=context.current();var resolvedProfile=FarmCycleProfileIdentity.of(original.farm);
                    var terrain=new dev.veltraph.client.navigation.mc.McNavigator(client,services.input(),config);
                    if(!area.onHypixel()||!area.inSkyBlock()||area.area()!=GameArea.GARDEN||area.area()!=canonicalPlan.start().area()
                        ||!dimension.equals(client.level.dimension().identifier().toString())||!profile.equals(resolvedProfile)
                        ||!original.farm.issuedReturnPausedCurrent(originalCheckpoint)||!original.currentRefusal())return false;
                    // Finish every family/area/profile/source visitor before the final native geometry queries.
                    if(!terrain.readOnlyNativeStandingFootprintCurrent(origin)||!terrain.readOnlyNativeStandingFootprintCurrent(point))return false;
                    return finalNativeCurrent(point);
                }catch(RuntimeException|Error unavailable){responseOrder.refuse();return false;}
            }
            private boolean finalNativeCurrent(Vec3 point){return nativeProofCurrent()&&point!=null
                &&Double.doubleToLongBits(client.player.getX())==Double.doubleToLongBits(point.x())
                &&Double.doubleToLongBits(client.player.getY())==Double.doubleToLongBits(point.y())
                &&Double.doubleToLongBits(client.player.getZ())==Double.doubleToLongBits(point.z());}
        }
        boolean originalOperationalAdmissionCurrent(IssuedFarmAppliedLanding.Receipt exact){
            var actual=operationalResponse;return actual!=null&&actual.receipt==exact&&actual.admissionCurrent();
        }
        /** Actual Root consumes the same observed native origin; no new serial or current version is adopted. */
        private boolean consumeOriginalOperationalResponse(IssuedFarmAppliedLanding.Receipt exact){
            var actual=operationalResponse;
            if(!hasOwnedRootTick()||actual==null||actual.receipt!=exact||!actual.admissionCurrent()
                ||!responseOrder.consume(actual.originalAttempt.origin))return false;
            appliedLanding=exact;acceptedTeleportSerial=actual.nativeSignalSerial;consumed=true;return true;
        }

        public Minecraft client(){return client;}public ClientPacketListener listener(){return issued.listener;}public Connection connection(){return issued.connection;}
        public long serial(){return issued.serial();}public Vec3 targetPosition(){return issued.original.plan.start().position();}
        public long baselineReceiptSerial(){return baseline;}public long deadlineMillis(){return deadline;}public boolean consumed(){return consumed;}
        /** Passive actual origin identity; it grants no native admission or final arrival. */
        public OperationalResponse originalOperationalResponseIdentity(){return operationalResponse;}
        public boolean currentFinalNative(){return landingCall==this&&!consumed&&!responseOrder.refused()&&commandCall==issued&&source==issued.original
            &&runtime.state()==SuspendedFarmReturnRuntime.State.WAITING_LANDING&&submission==submitted&&submission.belongs(issued)
            &&submission.normallyReturned()&&issued.baseNativeCurrent();}
        public boolean beforeTeleportNativeCurrent(){return currentFinalNative()&&issued.manualInputCurrent();}
    }
    /** Identity for authentic native ingress refusal only; never an effect permission or response factory. */
    LandingCall originalLandingIdentityForNativeIngress(ClientPacketListener exact){
        boolean nativeFactory=StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(stream->{
            var first=stream.filter(f->f.getDeclaringClass()!=McSuspendedFarmReturnCoordinator.class).findFirst().orElse(null);
            return first!=null&&first.getDeclaringClass()==IssuedFarmAppliedLanding.class;
        });
        var call=landingCall;return nativeFactory&&client.isSameThread()&&call!=null&&exact==call.listener()
            &&runtime.state()==SuspendedFarmReturnRuntime.State.WAITING_LANDING&&call.issued==commandCall&&source==call.issued.original?call:null;
    }
    /** Call is selected before every genuine private saved-anchor action, including its releases. */
    private <T>T originalReturnStep(java.util.function.Supplier<T> originalAction){
        // A failed/reentrant later instruction may never inherit the old Root normal-return view.
        retainedMovementView=null;
        if(returnCleanupCall!=null)throw new IllegalStateException("Return source action reentered");
        if(!hasOwnedRootTick()||!observeDeadline()||!currentGameplayOwner()||!source.protectedCurrent()
                ||!source.farm.issuedReturnPausedCurrent(checkpoint)||appliedLanding==null||!appliedLanding.current(client))
            throw new IllegalStateException("No original protected issued-return action");
        Object actualOwner=McOwnedRotation.authority(client);
        if(actualOwner!=driver||!source.currentRefusal()||!source.protectedCurrent())throw new IllegalStateException("Return role changed");
        var call=new ReturnCleanupCall(source,actualOwner);returnCleanupCall=call;movementView=new MovementView(source,checkpoint,call);
        var view=movementView;
        try{
            if(!call.nativeCurrent())throw new IllegalStateException("Original return action changed");
            T result=originalAction.get();
            if(movementView==view&&observeDeadline()&&call.nativeCurrent()){
                view.instructionReturned=true;retainedMovementView=view;
            }
            return result;
        }finally{if(returnCleanupCall==call){movementView=null;returnCleanupCall=null;}}
    }
    private final class JoinedScene {
        final Object player,world,listener,connection;
        final UUID playerId;
        final JoinCall admitted;
        private JoinedScene(JoinCall admitted){this.admitted=admitted;player=admitted.player;world=admitted.world;listener=admitted.listener;connection=admitted.connection;playerId=source.playerId;}
        boolean current(){return client.player==player&&client.level==world&&client.getConnection()==listener&&client.getConnection()!=null
            &&client.getConnection().getConnection()==connection&&playerId.equals(source.playerId)&&transport.matchingServer()
            &&admitted.signals!=null&&admitted.signals.current()&&storedNativeFinal();}
        boolean storedNativeFinal(){return source==admitted.original&&source.storedAffiliationFinal()&&client.player==player&&client.level==world
            &&client.getConnection()==listener&&client.getConnection()!=null&&client.getConnection().getConnection()==connection
            &&client.options==source.options&&client.gameMode==source.gameMode&&client.player.getGameProfile().id().equals(source.playerId);}
    }
    public final class JoinCall {
        private final SourceHeader original;
        private final Object player=client.player,world=client.level,listener=client.getConnection(),connection=client.getConnection().getConnection();
        private final dev.veltraph.client.game.mc.McGameContextService.GameplaySignalsSnapshot signals;
        private JoinCall(SourceHeader original){this.original=original;signals=context instanceof dev.veltraph.client.game.mc.McGameContextService nativeContext?nativeContext.gameplaySignalsSnapshot():null;}
        public Minecraft client(){return client;}public SourceHeader sourceHeader(){return original;}public long serial(){return original.serial;}
        public boolean currentRefusal(){return joinCall==this&&source==original
            &&(joined==null?hasOwnedRootTick()&&runtime.state()==SuspendedFarmReturnRuntime.State.CONNECTING
                :joined.admitted==this&&runtime.active())&&registered()&&original.settings.current()&&modules.lifecycleCurrent(original.driverLifecycle)
            &&client.player==player&&client.level==world&&client.getConnection()==listener&&client.getConnection()!=null
            &&client.getConnection().getConnection()==connection&&transport.matchingServer()&&client.player!=null&&client.level!=null
            &&client.player.getGameProfile().id().equals(original.playerId)&&client.getUser().getProfileId().equals(original.sessionId)
            &&original.sessionId.equals(original.playerId)&&client.gui.screen()==null&&client.player.isAlive()&&client.player.level()==client.level
            &&signals!=null&&signals.current()&&storedNativeFinal();}
        private boolean storedNativeFinal(){return joinCall==this&&source==original&&original.storedAffiliationFinal()
            &&client.player==player&&client.level==world&&client.getConnection()==listener&&client.getConnection()!=null
            &&client.getConnection().getConnection()==connection&&client.options==original.options&&client.gameMode==original.gameMode
            &&client.player.getGameProfile().id().equals(original.playerId);}
    }
    private void admitJoinedScene(){
        if(source==null||!source.currentRefusal()||client.player==null||client.level==null||client.getConnection()==null||client.gui.screen()!=null
                ||!transport.matchingServer())return;
        var area=context.current();
        if(!area.onHypixel()||!area.inSkyBlock()||!area.area().isSkyBlock()||area.area()==GameArea.UNKNOWN||area.area()==GameArea.LIMBO)return;
        joinCall=new JoinCall(source);
        if(!joinCall.currentRefusal()){cancel("Issued join source changed");return;}
        var original=source.inventory;
        McIssuedReturnInventory.Snapshot rebound=null;
        boolean transferred=false;
        try{
            rebound=original==null?null:original.semanticMatchesJoined(joinCall);
            if(rebound==null||!joinCall.currentRefusal()||!rebound.currentFinal()){cancel("Issued join protected inventory changed");return;}
            source.inventory=rebound;joined=new JoinedScene(joinCall);transferred=true;
            if(!joined.current()||!source.protectedCurrent()){cancel("Issued join could not bind original scene");return;}
        }finally{
            if(transferred&&original!=null)original.close();
            if(!transferred&&rebound!=null)rebound.close();
        }

    }
    private SuspendedFarmReturnRuntime.Scene ownedTransportScene(){
        if(source==null)return SuspendedFarmReturnRuntime.Scene.INVALID;
        if(source.originalNative())return SuspendedFarmReturnRuntime.Scene.ORIGINAL;
        if(client.player==null&&client.level==null&&client.getConnection()==null){
            var proof=IssuedFarmTransportOrigin.currentExpected(this);
            if(proof==null||!proof.currentScene(client,this))return SuspendedFarmReturnRuntime.Scene.INVALID;
            if(transport.restingScreenOwned())return SuspendedFarmReturnRuntime.Scene.OWN_DISCONNECTED;
            if(runtime.state()==SuspendedFarmReturnRuntime.State.CONNECTING&&transport.connectionScreenAllowed())return SuspendedFarmReturnRuntime.Scene.OWN_CONNECTING;
            return SuspendedFarmReturnRuntime.Scene.INVALID;
        }
        if(transport.matchingServer()&&client.player!=null&&client.level!=null&&client.getConnection()!=null
            &&client.player.getGameProfile().id().equals(source.playerId)&&client.player!=source.player&&client.level!=source.world&&client.getConnection()!=source.listener){
            if(joined==null){var proof=IssuedFarmTransportOrigin.currentExpected(this);
                if(proof==null||!proof.currentScene(client,this))return SuspendedFarmReturnRuntime.Scene.INVALID;
                return runtime.state()==SuspendedFarmReturnRuntime.State.CONNECTING&&transport.connectionScreenAllowed()
                ?SuspendedFarmReturnRuntime.Scene.OWN_CONNECTING:SuspendedFarmReturnRuntime.Scene.INVALID;}
            return joined.current()&&transport.connectionScreenAllowed(returning.active())?SuspendedFarmReturnRuntime.Scene.OWN_REJOINED:SuspendedFarmReturnRuntime.Scene.INVALID;
        }
        return SuspendedFarmReturnRuntime.Scene.INVALID;
    }
    private boolean currentGameplayOwner(){return source!=null&&registered()&&driver.isEnabled()&&modules.inputOwner().orElse(null)==driver
        &&McOwnedRotation.authority(client)==driver&&client.player!=null&&client.level!=null&&client.getConnection()!=null
        &&client.player.isAlive()&&!client.player.isPassenger()&&!client.player.isScoping()&&!client.isPaused()&&client.gui.screen()==null
        &&client.player.level()==client.level&&client.getCameraEntity()==client.player&&client.player.containerMenu==client.player.inventoryMenu
        &&client.player.inventoryMenu.getCarried().isEmpty()&&checkpoint!=null&&source.farm.issuedReturnPausedCurrent(checkpoint);}
    public void tick(VeltraphBootstrap.RootFarmReturnTick actual){tick(actual,null);}
    public void tick(VeltraphBootstrap.RootFarmReturnTick actual,VeltraphBootstrap.RootFarmReturnAdmission admission){
        thread();if(actual==null||!actual.current(this))return;
        if(normalResumeWitness!=null&&normalResumeWitness.tick!=actual)normalResumeWitness=null;
        if(terminalDispatch!=null&&terminalDispatch.tick!=actual){terminalDispatch.inventory.close();terminalDispatch=null;}
        if(activeTick!=null){cancel("Return tick reentered");return;}
        boolean admittedFromIntent=!active()&&pendingUserIntent!=null;
        activeTick=actual;dispatchedTick=actual;dispatchObservation=null;
        try{
            consumeUserIntent(actual,admission);if(!active())return;
            terminalDispatch=null;
            long tick=actual.clockTick();
            if(lastRootTick>=0&&tick==lastRootTick)return;
            if(lastRootTick>=0&&tick!=lastRootTick+1){cancel("Original Root return cadence changed");return;}
            lastRootTick=tick;
            // Admission captures the original farm only. Root publishes the real driver handoff before the next effect.
            if(!admittedFromIntent)tickOwned();
            captureDispatchObservation(actual);
        }catch(RuntimeException|Error failure){
            try{cancel("Native return callback failed: "+failure.getClass().getSimpleName());}
            catch(RuntimeException|Error cleanup){if(cleanup!=failure)failure.addSuppressed(cleanup);throw failure;}
        }finally{activeTick=null;}
    }
    /** Private actual normal-resume return, captured before any completion cleanup callback. No native permission. */
    private final class NormalResumeWitness {
        private final SourceHeader original;
        private final ResumeCall resumed;
        private final VeltraphBootstrap.RootFarmReturnTick tick;
        private final ModuleManager.LifecycleSnapshot lifecycle;
        private ModuleManager.OwnDisableReceipt driverStopped;
        private ModuleManager.LifecycleSnapshot stoppedLifecycle;
        private boolean refusalHandled;
        private NormalResumeWitness(SourceHeader original,ResumeCall resumed,VeltraphBootstrap.RootFarmReturnTick tick){
            this.original=original;this.resumed=resumed;this.tick=tick;lifecycle=modules.lifecycleSnapshot();
        }
        private boolean handled(VeltraphBootstrap.RootFarmReturnTick exact){return normalResumeWitness==this&&exact==tick&&tick!=null&&tick.current(McSuspendedFarmReturnCoordinator.this)
            &&registered()&&resumed!=null&&resumed.original==original;}
        private boolean unchangedLifecycle(){return lifecycle.known()&&modules.lifecycleCurrent(lifecycle)
            ||driverStopped!=null&&stoppedLifecycle!=null&&modules.ownDisableFromMatches(driverStopped,lifecycle,stoppedLifecycle,driver)&&modules.lifecycleCurrent(stoppedLifecycle);}
    }
    /** One actual normally resumed terminal frame; it cannot authorize a changed world or another tick. */
    private final class TerminalDispatchObservation {
        private final VeltraphBootstrap.RootFarmReturnTick tick;
        private final SourceHeader original;
        private final ResumeCall resumed;
        private final PauseCall originalPause;
        private final FarmModule.IssuedReturnCheckpoint originalCheckpoint;
        private final dev.veltraph.client.scheduling.mc.McScriptScheduler.TimerRetention originalTimer;
        private final FarmModule farm;
        private final ModuleManager.LifecycleSnapshot lifecycle;
        private final dev.veltraph.client.input.ScriptingInputLock.ControlSnapshot input;
        private final McOwnedRotation.InstallationSnapshot installation;
        private final McIssuedReturnInventory.Snapshot inventory;
        private final ArrivalStamp pose;
        private boolean refusalHandled;
        private final Object player=client.player,world=client.level,listener=client.getConnection(),options=client.options,gameMode=client.gameMode;
        private final UUID session=client.getUser().getProfileId(),account=client.player.getGameProfile().id();
        private TerminalDispatchObservation(SourceHeader original,ResumeCall resumed,VeltraphBootstrap.RootFarmReturnTick tick){
            this.original=original;this.resumed=resumed;this.tick=tick;farm=original.farm;lifecycle=modules.lifecycleSnapshot();
            originalPause=pauseCall;originalCheckpoint=checkpoint;originalTimer=timerRetention;
            input=dev.veltraph.client.input.ScriptingInputLock.controlSnapshot();installation=McOwnedRotation.installationSnapshot(client);
            inventory=original.inventory;pose=arrivalStamp;
        }
        private boolean current(VeltraphBootstrap.RootFarmReturnTick exact){return terminalDispatch==this&&exact==tick&&tick.current(McSuspendedFarmReturnCoordinator.this)
            &&registered()&&runtime.state()==SuspendedFarmReturnRuntime.State.COMPLETE&&resumed!=null&&resumed.farm()==farm
            &&modules.get(farm.id()).orElse(null)==farm&&farm.isEnabled()&&!farm.isSuspended()&&!driver.isEnabled()
            &&lifecycle.known()&&modules.lifecycleCurrent(lifecycle)&&original.settings.current()&&input!=null&&input.current()
            &&input.active()&&farm.id().equals(input.ownerModuleId())&&installation!=null&&installation.current()
            &&inventory!=null&&inventory.retiredContentCurrent(client)&&pose!=null&&pose.terminalNativeCurrent()
            &&client.player==player&&client.level==world&&client.getConnection()==listener&&client.options==options&&client.gameMode==gameMode
            &&client.player!=null&&client.player.isAlive()&&client.player.level()==client.level&&client.getCameraEntity()==player
            &&client.player.getGameProfile().id().equals(account)&&client.getUser().getProfileId().equals(session)&&account.equals(session)
            &&session.equals(original.sessionId)&&account.equals(original.playerId)
            &&client.gui.screen()==null&&!client.isPaused()&&client.player.onGround()&&client.player.containerMenu==client.player.inventoryMenu
            &&client.player.inventoryMenu.getCarried().isEmpty();}
    }
    /** Captured after the real Root dispatch; no active-window world-transition permission. */
    public final class RootDispatchObservation {
        private final SourceHeader original;
        private final TerminalDispatchObservation terminal;
        private final NormalResumeWitness failedCompletion;
        private final IssuedFarmExpectedObservation expected;
        private final VeltraphBootstrap.RootFarmReturnTick tick;
        private final long clockTick;
        private final Object player=client.player,world=client.level,listener=client.getConnection();
        private final SuspendedFarmReturnRuntime.State phase=runtime.state();
        private RootDispatchObservation(SourceHeader original,TerminalDispatchObservation terminal,IssuedFarmExpectedObservation expected,VeltraphBootstrap.RootFarmReturnTick tick){this.original=original;this.terminal=terminal;this.failedCompletion=null;this.expected=expected;this.tick=tick;clockTick=tick.clockTick();}
        private RootDispatchObservation(NormalResumeWitness failedCompletion){this.original=null;this.terminal=null;this.failedCompletion=failedCompletion;this.expected=null;this.tick=failedCompletion.tick;clockTick=tick.clockTick();}
        public boolean current(VeltraphBootstrap.RootFarmReturnTick exact){
            if(dispatchObservation!=this||exact!=tick||!tick.current(McSuspendedFarmReturnCoordinator.this)||tick.clockTick()!=clockTick)return false;
            if(failedCompletion!=null)return false;
            if(terminal!=null)return terminal.current(exact);
            return source==original&&active()&&runtime.state()==phase&&original.currentRefusal()
            &&client.player==player&&client.level==world&&client.getConnection()==listener
            &&(original.originalNative()||expected!=null&&expected.currentScene(client,McSuspendedFarmReturnCoordinator.this))
            &&checkpoint!=null&&original.farm.issuedReturnPausedBound(checkpoint,pauseCall)
            &&(client.player==null||joined==null&&!original.originalNative()
                ?expected!=null&&expected.currentScene(client,McSuspendedFarmReturnCoordinator.this):original.protectedCurrent())
            &&currentFinalNative(exact);}
        /** Called only after current's semantic/header observation. Native producer evidence remains required for changed scenes. */
        public boolean currentFinalNative(VeltraphBootstrap.RootFarmReturnTick exact){
            if(dispatchObservation!=this||exact!=tick||!tick.current(McSuspendedFarmReturnCoordinator.this)||tick.clockTick()!=clockTick)return false;
            if(failedCompletion!=null)return false;
            if(terminal!=null)return terminal.current(exact);
            if(source!=original||!active()||runtime.state()!=phase||!original.storedAffiliationFinal()
                ||checkpoint==null||!original.farm.issuedReturnPausedBound(checkpoint,pauseCall)
                ||client.player!=player||client.level!=world||client.getConnection()!=listener)return false;
            if(player!=null&&(original.originalSceneStoredFinal()||joined!=null&&joined.storedNativeFinal()))return original.storedNativeSourceFinal();
            return expected!=null&&expected.currentScene(client,McSuspendedFarmReturnCoordinator.this)
                &&original.storedAffiliationFinal()&&client.player==player&&client.level==world&&client.getConnection()==listener;
        }
    }
    /** Exact completed frame custody only. This never exempts a changed native scene. */
    public boolean handledTerminalFrame(RootDispatchObservation observation,VeltraphBootstrap.RootFarmReturnTick actual){
        if(observation==null||observation!=dispatchObservation||observation.tick!=actual)return false;
        if(observation.failedCompletion!=null)return observation.failedCompletion.handled(actual);
        if(observation.terminal==null)return false;
        var terminal=observation.terminal;
        return terminalDispatch==terminal&&terminal.tick==actual&&actual!=null&&actual.current(this)&&registered()
            &&runtime.state()==SuspendedFarmReturnRuntime.State.COMPLETE&&terminal.resumed!=null
            &&terminal.resumed.original==terminal.original&&terminal.resumed.farm()==terminal.farm;
    }
    /** Refused terminal proof stops only the unchanged original resumed lifecycle, never a replacement/restart. */
    public boolean stopOriginalTerminalFromRoot(RootDispatchObservation observation,VeltraphBootstrap.RootFarmReturnCloseCause cause,String reason){
        thread();if(observation==null||cause==null||!cause.current(this)||!handledTerminalFrame(observation,observation.tick))return false;
        boolean originalEntry=StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(stream->{
            var caller=stream.filter(frame->frame.getDeclaringClass()!=McSuspendedFarmReturnCoordinator.class).findFirst().orElse(null);
            return caller!=null&&caller.getDeclaringClass()==VeltraphBootstrap.class&&caller.getMethodName().equals("tickOwnedSuspendedFarmReturn");
        });
        if(!originalEntry)return false;
        var terminal=observation.terminal;var failed=observation.failedCompletion;
        boolean handled=terminal!=null?terminal.refusalHandled:failed.refusalHandled;if(handled)return true;
        if(terminal!=null)terminal.refusalHandled=true;else failed.refusalHandled=true;
        var farm=terminal!=null?terminal.farm:failed.original.farm;
        var lifecycle=terminal!=null?terminal.lifecycle:failed.lifecycle;
        // Capture once before manager callbacks. Invalid world/controls do not permit generic native clearing.
        boolean unchanged=(terminal!=null?lifecycle.known()&&modules.lifecycleCurrent(lifecycle):failed.unchangedLifecycle())
            &&modules.get(farm.id()).orElse(null)==farm&&farm.isEnabled();
        diagnostic=reason+"; original completed frame retired; authenticated SkyBlock profile UNKNOWN";
        if(unchanged&&cause.current(this)&&(terminal!=null?modules.lifecycleCurrent(lifecycle):failed.unchangedLifecycle()))modules.disable(farm.id());
        return true;
    }
    /** Returns only the actual completed frame's old identity, even after its native validation refused. */
    public RootDispatchObservation handledTerminalObservationForRoot(VeltraphBootstrap.RootFarmReturnTick actual){
        if(actual==null||!actual.current(this))return null;
        if(dispatchObservation!=null&&handledTerminalFrame(dispatchObservation,actual))return dispatchObservation;
        if(normalResumeWitness!=null&&normalResumeWitness.handled(actual)){
            dispatchObservation=new RootDispatchObservation(normalResumeWitness);return dispatchObservation;
        }
        return null;
    }
    private void captureDispatchObservation(VeltraphBootstrap.RootFarmReturnTick actual){
        if(terminalDispatch!=null&&terminalDispatch.current(actual)){
            dispatchObservation=new RootDispatchObservation(null,terminalDispatch,null,actual);return;
        }
        if(source==null||!active()||!source.currentRefusal()||checkpoint==null||!source.farm.issuedReturnPausedBound(checkpoint,pauseCall))return;
        if(source.originalNative()&&source.protectedCurrent()){
            dispatchObservation=new RootDispatchObservation(source,null,null,actual);return;
        }
        var expected=IssuedFarmTransportOrigin.currentExpected(this);
        if(expected!=null&&expected.currentScene(client,this)){
            // No raw scene or active window can replace the private native origin proof.
            if(client.player!=null&&joined!=null&&!source.protectedCurrent())return;
            dispatchObservation=new RootDispatchObservation(source,null,expected,actual);
        }
    }
    public final class TimerCall {
        private final SourceHeader original;
        private final PauseCall pause;
        private final FarmModule.IssuedReturnCheckpoint cp;
        private final dev.veltraph.client.scheduling.mc.McScriptScheduler.TimerRetention retention;
        private final RootDispatchObservation observation;
        private final VeltraphBootstrap.RootFarmReturnTick tick;
        private final TerminalDispatchObservation terminal;
        private final Object player=client.player,world=client.level,listener=client.getConnection();
        private TimerCall(RootDispatchObservation observation,VeltraphBootstrap.RootFarmReturnTick tick){
            this.observation=observation;this.tick=tick;terminal=observation.terminal;
            original=terminal==null?source:terminal.original;pause=terminal==null?pauseCall:terminal.originalPause;
            cp=terminal==null?checkpoint:terminal.originalCheckpoint;retention=terminal==null?timerRetention:terminal.originalTimer;
        }
        public Minecraft client(){return client;}public McSuspendedFarmReturnCoordinator coordinatorIdentity(){return McSuspendedFarmReturnCoordinator.this;}public SourceHeader sourceHeader(){return original;}public FarmModule farm(){return original.farm;}
        public PauseCall originalPauseCall(){return pause;}public FarmModule.IssuedReturnCheckpoint checkpointIdentity(){return cp;}
        public AbstractModule driverIdentity(){return driver;}public long serial(){return original.serial;}public VeltraphBootstrap.RootFarmReturnTick rootTickIdentity(){return tick;}
        public Object originalPlayer(){return original.player;}public Object originalWorld(){return original.world;}public Object originalConnection(){return original.listener;}
        public Object currentPlayer(){return player;}public Object currentWorld(){return world;}public Object currentConnection(){return listener;}
        public boolean resumeCommitted(){return terminal!=null&&terminal.current(tick);}
        public boolean currentFinalNative(){return timerCall==this&&retention!=null&&registered()&&tick.current(McSuspendedFarmReturnCoordinator.this)
            &&observation.currentFinalNative(tick)&&client.player==player&&client.level==world&&client.getConnection()==listener
            &&(terminal!=null?terminal.originalTimer==retention:timerRetention==retention&&source==original&&pauseCall==pause&&checkpoint==cp);}
    }
    public dev.veltraph.client.scheduling.mc.McScriptScheduler.TimerRetention timerRetention(){return terminalDispatch==null?timerRetention:terminalDispatch.originalTimer;}
    public TimerCall currentTimerCall(VeltraphBootstrap.RootFarmReturnTick exact){
        var observation=observationForRoot(exact);if(observation==null)return null;
        if(timerCall==null||timerCall.tick!=exact||timerCall.observation!=observation)timerCall=new TimerCall(observation,exact);
        return timerCall.currentFinalNative()?timerCall:null;
    }
    public RootDispatchObservation observationForRoot(VeltraphBootstrap.RootFarmReturnTick exact){var o=dispatchObservation;return o!=null&&o.current(exact)?o:null;}
    public boolean hasOwnedRootTick(){return activeTick!=null&&activeTick.current(this);}
    private void tickOwned(){
        if(runtime.mode()==SuspendedFarmReturnRuntime.Mode.DISCONNECT_RECONNECT&&runtime.state()==SuspendedFarmReturnRuntime.State.CONNECTING&&joined==null)
            admitJoinedScene();
        if(landingCall!=null&&landingCall.operationalResponseRefused()){cancel("Original native command operational response refused");return;}
        if(source==null||!source.current()||checkpoint==null||!source.farm.issuedReturnPausedCurrent(checkpoint)){cancel("Original farm source changed");return;}
        var scene=runtime.mode()==SuspendedFarmReturnRuntime.Mode.COMMAND_ONLY
            ?(source.originalNative()?SuspendedFarmReturnRuntime.Scene.ORIGINAL:SuspendedFarmReturnRuntime.Scene.INVALID):ownedTransportScene();
        if(scene==SuspendedFarmReturnRuntime.Scene.INVALID){cancel("Unexpected world, account, server or connector");return;}
        var landing=SuspendedFarmReturnRuntime.Landing.NONE;
        if(landingCall!=null&&!landingCall.consumed&&runtime.state()==SuspendedFarmReturnRuntime.State.WAITING_LANDING){
            var receipt=IssuedFarmAppliedLanding.consume(landingCall);
            if(receipt!=null&&receipt.current(client)&&receipt.serial()>landingCall.baselineReceiptSerial()&&observeDeadline()&&landingCall.currentFinalNative()&&landingCall.consumeOriginalOperationalResponse(receipt)){
                landing=SuspendedFarmReturnRuntime.Landing.MATCHED;
            }else if(beforeCommand!=null&&NativeTeleportSignal.read(client).serial()!=beforeCommand.serial())landing=SuspendedFarmReturnRuntime.Landing.FOREIGN;
        }
        if(appliedLanding!=null&&client.player!=null&&acceptedTeleportSerial>=0&&NativeTeleportSignal.read(client).serial()!=acceptedTeleportSerial)
            landing=SuspendedFarmReturnRuntime.Landing.FOREIGN;
        if(landing==SuspendedFarmReturnRuntime.Landing.FOREIGN){cancel("Unmatched native teleport after issued command");return;}
        if(returning.active())originalReturnStep(()->{returning.tick(context.current().area());return Boolean.TRUE;});
        boolean arrived=arrivalCurrent();long now=services.clock().nowMillis();
        var action=runtime.tick(new SuspendedFarmReturnRuntime.Observation(source.current(),scene,landing,arrived,arrived,now,client.player==null?-1:client.player.tickCount));
        switch(action){
            case DISCONNECT -> {departureCall=new DepartureCall(source);boolean done;try{
                    done=observeDeadline()&&departureCall.current()&&transport.disconnect()&&observeDeadline();
                    if(done&&departureCall.sourceNativeAffiliationCurrent()){departureCall.instructionReturned=true;retainedDepartureCall=departureCall;}
                }finally{departureCall=null;}runtime.disconnected(done&&client.player==null&&client.level==null&&client.getConnection()==null&&transport.restingScreenOwned(),now);}
            case REJOIN -> {
                rejoinCall=new RejoinCall(source);boolean connected;
                try{
                    connected=observeDeadline()&&rejoinCall.currentFinalNative()&&transport.rejoin()&&observeDeadline();
                    if(connected&&rejoinCall.sourceNativeAffiliationCurrent()){rejoinCall.instructionReturned=true;retainedRejoinCall=rejoinCall;}
                }finally{rejoinCall=null;}
                runtime.rejoinIssued(connected);
            }
            case SEND_COMMAND -> {if(!currentGameplayOwner()||!source.protectedCurrent()){cancel("Command scene or inventory changed");return;}
                beforeCommand=NativeTeleportSignal.read(client);landingBaseline=IssuedFarmAppliedLanding.serial(client);commandCall=new CommandCall(source);
                if(!commandCall.current()){cancel("Original command source changed");return;}
                submitted=IssuedFarmCommandFence.send(commandCall);
                boolean actual=submitted!=null&&submitted.belongs(commandCall)&&submitted.normallyReturned()&&observeDeadline()&&commandCall.currentFinalNative();
                if(runtime.commandSubmitted(actual))landingCall=new LandingCall(commandCall,submitted,landingBaseline);}
            case START_RETURN -> {if(!currentGameplayOwner()||!source.protectedCurrent()){cancel("Landing source changed");return;}
                runtime.returnStarted(originalReturnStep(()->returning.start(source.plan.start(),context.current().area())));}
            case COMMIT -> {if(!arrivalCurrent()){cancel("Accepted arrival changed before commit");return;}
                if(!observeDeadline()){cancel("Original resume deadline expired");return;}
                arrivalStamp=new ArrivalStamp();resumeCall=new ResumeCall(source);boolean resumed=resumeCall.nativeCurrent()&&source.farm.resumeIssuedReturn(checkpoint,resumeCall);
                if(!observeDeadline()){cancel("Original resume effect exceeded its deadline");return;}
                runtime.committed(resumed);if(resumed){normalResumeWitness=new NormalResumeWitness(source,resumeCall,activeTick);finish();}else cancel("Original once-resume failed");}
            case STOP -> cancel(runtime.reason());
            case NONE -> {}
        }
        if(runtime.state()==SuspendedFarmReturnRuntime.State.FAILED)cancel(runtime.reason());
    }
    private boolean arrivalCurrent(){return source!=null&&submitted!=null&&beforeCommand!=null&&checkpoint!=null&&source.current()
        &&currentGameplayOwner()&&returning.arrived(context.current().area())&&source.protectedCurrent()&&source.originalDeviceSlotRestored();}
    /** Root Hydra observer consumes only this actual submitted command's exact landing. */
    public boolean consumeExpectedTeleport(Vec3 position,long now){thread();return active()&&runtime.state()==SuspendedFarmReturnRuntime.State.WAITING_LANDING
        &&landingCall!=null&&landingCall.currentFinalNative()&&now>=0&&now<runtime.deadline()&&position!=null
        &&expectedAppliedPosition(position);}
    private boolean expectedAppliedPosition(Vec3 position){var actual=IssuedFarmAppliedLanding.currentFor(landingCall);
        return actual!=null&&actual.current(client)&&position.distanceTo(actual.position())<=.01&&landingCall.currentFinalNative();}
    /** Exact old metadata disposal only. No public header, role or native counter can mint this call. */
    public final class MetadataCloseCall {
        private final SourceHeader original;
        private final Thread thread=Thread.currentThread();private Object claimedJourney;
        private MetadataCloseCall(SourceHeader original){this.original=original;}
        public SourceHeader sourceHeader(){return original;}public McSavedAnchorRecovery returningIdentity(){return returning;}public Minecraft client(){return client;}
        public boolean claimOriginalJourney(McSavedAnchorRecovery helper,Object journey){
            if(claimedJourney!=null||helper!=returning||journey==null||!currentMetadata())return false;
            boolean original=StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(stream->{var first=stream.limit(32).filter(f->f.getDeclaringClass()!=MetadataCloseCall.class).findFirst().orElse(null);
                return first!=null&&first.getDeclaringClass()==McSavedAnchorRecovery.class&&first.getMethodName().equals("retireOriginalIssuedReturnMetadata");});
            if(!original)return false;claimedJourney=journey;return true;
        }
        public boolean originalJourneyClaimed(Object exact){return claimedJourney==exact&&exact!=null&&currentMetadata();}
        public boolean currentMetadata(){return metadataCloseCall==this&&source==original&&retiring&&registered()&&client.isSameThread()&&Thread.currentThread()==thread;}
    }
    public MetadataCloseCall currentMetadataCloseCall(){var call=metadataCloseCall;return call!=null&&call.currentMetadata()?call:null;}
    private boolean retireOriginalMetadata(SourceHeader exact){
        if(exact==null||source!=exact||!retiring||metadataCloseCall!=null)return false;
        var call=new MetadataCloseCall(exact);metadataCloseCall=call;
        try{
            var scheduler=VeltraphBootstrap.scriptScheduler();
            if(runtime.state()!=SuspendedFarmReturnRuntime.State.COMPLETE&&timerRetention!=null&&scheduler!=null&&!scheduler.retireIssuedFarmTimer(timerRetention,call))return false;
            if(!returning.hasOriginalIssuedReturn(exact))return true;
            return returning.retireOriginalIssuedReturnMetadata(call);
        }
        finally{if(metadataCloseCall==call)metadataCloseCall=null;}
    }
    /** A private original Root stop cause, never a public reason/actor flag or new native reservation. */
    public final class CloseCall {
        private final SourceHeader original;
        private final VeltraphBootstrap.RootFarmReturnCloseCause cause;
        private final Object player,world,listener,connection,options,gameMode;
        private final Thread thread=Thread.currentThread();
        private boolean invoked,claimed;
        private Object claimedJourney;
        private CloseCall(SourceHeader original,VeltraphBootstrap.RootFarmReturnCloseCause cause){
            this.original=original;this.cause=cause;player=client.player;world=client.level;listener=client.getConnection();
            connection=client.getConnection()==null?null:client.getConnection().getConnection();options=client.options;gameMode=client.gameMode;
        }
        public Minecraft client(){return client;}public SourceHeader sourceHeader(){return original;}
        public McSavedAnchorRecovery returningIdentity(){return returning;}public AbstractModule driverIdentity(){return driver;}
        public long serial(){return original.serial;}public VeltraphBootstrap.RootFarmReturnCloseCause rootCause(){return cause;}
        /** Claim is limited to the actual original helper entry; it grants no native control receipt. */
        public boolean claimOriginalJourney(McSavedAnchorRecovery exact,Object journey){
            if(!invoked||claimed||exact!=returning||journey==null||!current())return false;
            boolean original=StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(stream->{
                var first=stream.limit(32).filter(f->f.getDeclaringClass()!=CloseCall.class).findFirst().orElse(null);
                return first!=null&&first.getDeclaringClass()==McSavedAnchorRecovery.class&&first.getMethodName().equals("retireOriginalIssuedReturn");
            });
            if(!original)return false;claimed=true;claimedJourney=journey;return true;
        }
        public boolean originalJourneyClaimed(Object exact){return claimed&&claimedJourney==exact&&current();}
        public boolean current(){return closeCall==this&&source==original&&Thread.currentThread()==thread&&client.isSameThread()
            &&registered()&&cause!=null&&cause.current(McSuspendedFarmReturnCoordinator.this)
            &&modules.get(original.farm.id()).orElse(null)==original.farm&&modules.get(driver.id()).orElse(null)==driver;}
        /** Only native scene equality. Actual prior publication/key/service receipts remain mandatory. */
        public boolean nativeCurrent(){return current()&&client.player==player&&client.level==world&&client.getConnection()==listener
            &&listener!=null&&client.getConnection().getConnection()==connection&&client.options==options&&client.gameMode==gameMode
            &&cause.originalPlayer()==player&&cause.originalWorld()==world&&cause.originalConnection()==listener
            &&player!=null&&world!=null&&(joined==null?player==original.player&&world==original.world&&listener==original.listener
                :player==joined.player&&world==joined.world&&listener==joined.listener);}
    }
    public CloseCall currentCloseCall(){var call=closeCall;return call!=null&&call.current()?call:null;}
    public void closeFromRoot(VeltraphBootstrap.RootFarmReturnCloseCause cause,String reason){
        thread();pendingUserIntent=null;if(source==null){runtime.cancel(reason);return;}
        if(cause==null||!cause.current(this)){cancelWithoutNativeCause(reason);return;}
        if(retiring){runtime.cancel(reason);return;}
        var call=new CloseCall(source,cause);closeCall=call;retiring=true;
        Throwable failure=null;
        try{
            // The actual helper must capture its exact old Journey before any retirement callback.
            try{if(!call.current())throw new IllegalStateException("Original Root close cause changed");
                boolean hadOriginal=returning.hasOriginalIssuedReturn(call.sourceHeader());
                call.invoked=true;boolean retired=returning.retireOriginalIssuedReturn(call);
                if(hadOriginal&&!retired){retireOriginalMetadata(call.sourceHeader());
                    throw new IllegalStateException("Original Journey native retirement refused");}
            }catch(Throwable error){failure=error;}
            try{boolean hadTransport=IssuedFarmTransportOrigin.hasOriginalTransport(this,call.sourceHeader());
                if(hadTransport&&!IssuedFarmTransportOrigin.retireOwnedTransport(call))throw new IllegalStateException("Original issued transport retirement refused");}catch(Throwable error){if(failure==null)failure=error;else if(failure!=error)failure.addSuppressed(error);}
            try{var scheduler=VeltraphBootstrap.scriptScheduler();if(timerRetention!=null&&scheduler!=null&&!scheduler.retireIssuedFarmTimer(timerRetention,call))
                    throw new IllegalStateException("Original timer continuation retirement refused");}
                catch(Throwable error){if(failure==null)failure=error;else if(failure!=error)failure.addSuppressed(error);}
            runtime.cancel(reason);
            try{if(registered())registration().stopDriver(driver);}catch(Throwable error){if(failure==null)failure=error;else if(failure!=error)failure.addSuppressed(error);}
            try{if(registered()&&source!=null&&modules.get(source.farm.id()).orElse(null)==source.farm&&source.farm.isEnabled())modules.disable(source.farm.id());}
                catch(Throwable error){if(failure==null)failure=error;else if(failure!=error)failure.addSuppressed(error);}
            diagnostic=reason+"; authenticated SkyBlock profile UNKNOWN";
        }finally{retiring=false;clear();}
        if(failure!=null)throw new IllegalStateException("Original return close failed",failure);
    }
    public void onManualStop(dev.veltraph.client.core.Module owner,VeltraphBootstrap.RootFarmReturnCloseCause cause){
        thread();if(!retiring&&source!=null&&(owner==source.farm||owner==driver)&&cause!=null
                &&cause.affectedFromOriginalStop(owner))closeFromRoot(cause,"Manual stop");
    }
    public final class DepartureCall {
        private final SourceHeader original;
        private final Thread thread=Thread.currentThread();
        private final VeltraphBootstrap.RootFarmReturnTick rootTick=activeTick;
        private final long deadline=runtime.deadline();
        private boolean instructionReturned;
        private DepartureCall(SourceHeader original){this.original=original;}
        public Minecraft client(){return client;}public NativeRestTransport transport(){return transport;}public long serial(){return original.serial;}
        public SourceHeader sourceHeader(){return original;}public long deadlineMillis(){return deadline;}
        public boolean current(){return currentFinalNative();}
        public boolean afterDisconnectFinalNative(){return source==original&&instructionReturned&&retainedDepartureCall==this
            &&rootTick!=null&&(rootTick.current(McSuspendedFarmReturnCoordinator.this)||rootTick.returnedNormal()&&rootTick.retainedRegistrationCurrent(McSuspendedFarmReturnCoordinator.this))
            &&sourceNativeAffiliationCurrent()&&client.player==null&&client.level==null&&client.getConnection()==null;}
        public boolean sourceNativeAffiliationCurrent(){return (departureCall==this||retainedDepartureCall==this&&instructionReturned)&&source==original&&Thread.currentThread()==thread&&registered()
            &&original.settings.current()&&original.driverLifecycle!=null&&modules.lifecycleCurrent(original.driverLifecycle)
            &&checkpoint!=null&&original.farm.issuedReturnPausedBound(checkpoint,pauseCall)&&client.getUser().getProfileId().equals(original.sessionId);}
        public boolean currentFinalNative(){return departureCall==this&&source==original&&Thread.currentThread()==thread&&hasOwnedRootTick()
            &&runtime.mode()==SuspendedFarmReturnRuntime.Mode.DISCONNECT_RECONNECT&&runtime.state()==SuspendedFarmReturnRuntime.State.DISCONNECTING
            &&registered()&&original.settings.current()&&original.driverLifecycle!=null&&modules.lifecycleCurrent(original.driverLifecycle)
            &&checkpoint!=null&&original.farm.issuedReturnPausedBound(checkpoint,pauseCall)
            &&original.originalSceneStoredFinal()&&original.storedNativeSourceFinal();}
    }
    public DepartureCall currentDepartureCall(){var call=departureCall;return call!=null&&call.currentFinalNative()?call:null;}
    public final class RejoinCall {
        private final SourceHeader original;
        private final Thread thread=Thread.currentThread();
        private final VeltraphBootstrap.RootFarmReturnTick rootTick=activeTick;
        private final long deadline=runtime.deadline();
        private boolean instructionReturned;
        private RejoinCall(SourceHeader original){this.original=original;}
        public Minecraft client(){return client;}public NativeRestTransport transport(){return transport;}public long serial(){return original.serial;}
        public SourceHeader sourceHeader(){return original;}public long deadlineMillis(){return deadline;}
        public boolean afterIncomingFinalNative(){return retainedRejoinCall==this&&instructionReturned&&sourceNativeAffiliationCurrent()
            &&rootTick!=null&&rootTick.returnedNormal()&&registered();}
        public boolean sourceNativeAffiliationCurrent(){return (rejoinCall==this||retainedRejoinCall==this&&instructionReturned)&&source==original&&Thread.currentThread()==thread&&registered()
            &&original.settings.current()&&original.driverLifecycle!=null&&modules.lifecycleCurrent(original.driverLifecycle)
            &&checkpoint!=null&&original.farm.issuedReturnPausedBound(checkpoint,pauseCall)&&client.getUser().getProfileId().equals(original.sessionId);}
        public boolean currentFinalNative(){return rejoinCall==this&&source==original&&Thread.currentThread()==thread&&hasOwnedRootTick()
            &&runtime.mode()==SuspendedFarmReturnRuntime.Mode.DISCONNECT_RECONNECT&&runtime.state()==SuspendedFarmReturnRuntime.State.CONNECTING
            &&registered()&&original.settings.current()&&original.driverLifecycle!=null&&modules.lifecycleCurrent(original.driverLifecycle)
            &&checkpoint!=null&&original.farm.issuedReturnPausedBound(checkpoint,pauseCall)
            &&client.player==null&&client.level==null&&client.getConnection()==null&&client.getUser().getProfileId().equals(original.sessionId);}
    }
    public RejoinCall currentRejoinCall(){var call=rejoinCall;return call!=null&&call.currentFinalNative()?call:null;}
    public RejoinCall retainedRejoinCall(){var call=retainedRejoinCall;return call!=null&&call.afterIncomingFinalNative()?call:null;}
    public DepartureCall retainedDepartureCall(){var call=retainedDepartureCall;return call!=null&&call.afterDisconnectFinalNative()?call:null;}
    /** Native producer's private origin observation only; a pending request is never an expected scene. */
    public IssuedFarmExpectedObservation currentRootExpectedObservation(){
        thread();return IssuedFarmTransportOrigin.currentExpected(this);
    }
    public final class TransportObservation {
        private final SourceHeader original;
        private final Object player=client.player,world=client.level,listener=client.getConnection();
        private final SuspendedFarmReturnRuntime.State phase=runtime.state();
        private TransportObservation(SourceHeader original){this.original=original;}
        public boolean current(){return source==original&&active()&&registered()&&runtime.mode()==SuspendedFarmReturnRuntime.Mode.DISCONNECT_RECONNECT
            &&runtime.state()==phase&&original.currentRefusal()&&client.player==player&&client.level==world&&client.getConnection()==listener
            &&switch(phase){
                case DISCONNECTED -> player==null&&world==null&&listener==null&&transport.restingScreenOwned();
                case CONNECTING -> transport.connectionScreenAllowed()&&(listener==null||transport.matchingServer());
                case COMMANDING,WAITING_LANDING,RETURNING,SETTLING,COMMITTING -> joined!=null&&joined.current()&&transport.connectionScreenAllowed(returning.active());
                default -> false;
            };}
    }
    public TransportObservation transportObservation(){thread();if(source==null||!active())return null;var result=new TransportObservation(source);return result.current()?result:null;}
    public boolean permitsRunningLocation(FarmModule exact,GameContext area){return isPausedFarm(exact)&&area.onHypixel()&&area.inSkyBlock()
        &&area.area().isSkyBlock()&&area.area()!=GameArea.UNKNOWN&&area.area()!=GameArea.LIMBO;}
    public Object rotationAuthority(){if(source==null||!source.currentRefusal())return null;
        if(runtime.state()==SuspendedFarmReturnRuntime.State.PAUSING||resumingOriginalFarm())return source.farm;
        return active()&&driver.isEnabled()?driver:null;}
    public void onServerMessage(String line){thread();if(active()&&source!=null&&source.current())returning.onServerMessage(line);}
    public void onManualStop(dev.veltraph.client.core.Module owner){thread();if(!retiring&&source!=null&&(owner==source.farm||owner==driver))cancel("Manual stop");}
    private boolean refusal(String reason){cancel(reason);return false;}
    private void finish(){
        completedReturns=Math.addExact(completedReturns,1);retiring=true;Throwable failure=null;
        try{
            // Native controls were retired by the selected Journey before its accepted ARRIVED state.
            try{boolean hadOriginal=returning.hasOriginalIssuedReturn(source);
                if(hadOriginal&&!retireOriginalMetadata(source))throw new IllegalStateException("Completed original Journey metadata retirement refused");
            }catch(Throwable error){failure=error;}
            try{
                registration().stopDriver(driver);
                var witness=normalResumeWitness;
                if(witness!=null&&witness.original==source){
                    var receipt=modules.completedOwnDisableFrom(witness.lifecycle,driver).orElse(null);
                    var stopped=receipt==null?null:receipt.stoppedSnapshot();
                    if(receipt!=null&&stopped!=null&&modules.ownDisableFromMatches(receipt,witness.lifecycle,stopped,driver)){
                        witness.driverStopped=receipt;witness.stoppedLifecycle=stopped;
                    }
                }
            }catch(Throwable error){if(failure==null)failure=error;else if(failure!=error)failure.addSuppressed(error);}
            try{if(IssuedFarmTransportOrigin.hasOriginalTransport(this,source)&&!IssuedFarmTransportOrigin.retireTransportMetadata(this,source))
                    throw new IllegalStateException("Completed original transport metadata retirement refused");}
                catch(Throwable error){if(failure==null)failure=error;else if(failure!=error)failure.addSuppressed(error);}
            if(failure==null&&activeTick!=null&&activeTick.current(this))terminalDispatch=new TerminalDispatchObservation(source,resumeCall,activeTick);
            diagnostic="Original farm resumed once; authenticated SkyBlock profile UNKNOWN";
        }finally{retiring=false;clear();}
        if(failure!=null)throw new IllegalStateException("Completed return cleanup failed",failure);
    }
    public void cancel(String reason){
        thread();pendingUserIntent=null;if(retiring){runtime.cancel(reason);return;}
        var cause=activeTick==null?null:VeltraphBootstrap.farmReturnCloseCauseFor(activeTick,this);
        if(cause!=null){closeFromRoot(cause,reason);return;}
        // Actual manager disable invokes the private Root manual-stop observer before revocation.
        if(source!=null&&registered()){
            if(driver.isEnabled())registration().stopDriver(driver);
            else if(source.farm.isEnabled()&&modules.get(source.farm.id()).orElse(null)==source.farm)modules.disable(source.farm.id());
            if(source==null)return;
        }
        cancelWithoutNativeCause(reason);
    }
    private void cancelWithoutNativeCause(String reason){
        runtime.cancel(reason);retiring=true;Throwable failure=null;
        try{
            // Public cancellation lacks native cleanup authority; helper can retire only its exact old metadata.
            try{if(source!=null){boolean hadOriginal=returning.hasOriginalIssuedReturn(source);
                    if((hadOriginal||timerRetention!=null)&&!retireOriginalMetadata(source))throw new IllegalStateException("Original Journey/timer metadata retirement refused");}}
                catch(Throwable error){failure=error;}
            try{if(registered())registration().stopDriver(driver);}catch(Throwable error){if(failure==null)failure=error;else if(failure!=error)failure.addSuppressed(error);}
            try{if(registered()&&source!=null&&modules.get(source.farm.id()).orElse(null)==source.farm&&source.farm.isEnabled())modules.disable(source.farm.id());}
                catch(Throwable error){if(failure==null)failure=error;else if(failure!=error)failure.addSuppressed(error);}
            try{if(source!=null&&IssuedFarmTransportOrigin.hasOriginalTransport(this,source)&&!IssuedFarmTransportOrigin.retireTransportMetadata(this,source))
                    throw new IllegalStateException("Original issued transport metadata retirement refused");}catch(Throwable error){if(failure==null)failure=error;else if(failure!=error)failure.addSuppressed(error);}
            diagnostic=reason+"; authenticated SkyBlock profile UNKNOWN";
        }finally{retiring=false;clear();}
        if(failure!=null)throw new IllegalStateException("Return stop failed",failure);
    }
    private void clear(){
        if(source!=null&&source.inventory!=null&&terminalDispatch==null)source.inventory.close();
        source=null;pauseCall=null;resumeCall=null;arrivalStamp=null;
        returnCleanupCall=null;movementView=null;retainedMovementView=null;dispatchObservation=null;
        if(deviceAckTransit!=null)deviceAckTransit.revoke();deviceAckTransit=null;
        commandCall=null;landingCall=null;appliedLanding=null;acceptedTeleportSerial=-1;
        checkpoint=null;submitted=null;beforeCommand=null;joined=null;driverEnable=null;beforeDriver=null;pendingDriverEnableSource=null;
        dispatchedTick=null;timerRetention=null;timerCall=null;driverInputCall=null;driverInputAttemptTick=null;joinCall=null;
        departureCall=null;rejoinCall=null;retainedDepartureCall=null;retainedRejoinCall=null;
        closeCall=null;metadataCloseCall=null;lastRootTick=-1;deadlineObservation=-1;
    }
}
