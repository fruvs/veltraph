package dev.veltraph.client.mining.module;

import dev.veltraph.client.config.*;
import dev.veltraph.client.core.*;
import dev.veltraph.client.game.GameArea;
import dev.veltraph.client.mining.*;
import java.util.Optional;

public final class MiningModule extends AbstractModule {
    public static final String COBBLESTONE_ID="cobblestone", MINE_DOWN_ID="mine_down", ECHO_ID="echo";
    private final MiningController controller;
    private final MiningServices services;
    private final ConfigService config;
    private final MiningMode mode;
    private Object scheduledTicket;
    private String pausedSettings;
    private int enableRuns;
    private final dev.veltraph.client.mining.crafting.CobbleCraftStorePort craftStore;
    private String craftProfile;
    private boolean craftStarted;
    private Object craftTicket;
    private int craftAdmissionTicks;
    private Object dueRestCause;
    private int dueRestActivation;
    private String dueRestSettings,dueRestWorld;
    private final dev.veltraph.client.mining.recorded.RecordedEchoDelegate recorded;
    private boolean recordedStarted;
    public MiningModule(String id,String name,MiningMode mode,MiningServices services,ConfigService config) {
        this(id,name,mode,services,config,null);
    }
    /** Optional source-recorded Echo adapter; the empty profile retains the original engine. */
    public MiningModule(String id,String name,MiningMode mode,MiningServices services,ConfigService config,
            dev.veltraph.client.mining.recorded.RecordedEchoPort recordedPort) {
        super(id,name,ModuleCategory.MINING);this.mode=mode;this.services=services;this.config=config;
        controller=new MiningController(mode,services,config);craftStore=services.craftStorePort().orElse(null);
        if(recordedPort!=null&&recordedPort.mining()!=services)throw new IllegalArgumentException("Recorded Echo must share the same mining services");
        recorded=mode==MiningMode.ECHO&&recordedPort!=null?new dev.veltraph.client.mining.recorded.RecordedEchoDelegate(config,recordedPort):null;
    }
    private boolean recordedConfigured(){return mode==MiningMode.ECHO&&!config.get(dev.veltraph.client.mining.recorded.RecordedEchoConfig.PROFILE).isBlank();}
    @Override public Optional<GameArea> requiredArea(){return mode==MiningMode.COBBLESTONE?Optional.of(GameArea.ISLAND):Optional.empty();}
    @Override public boolean requiresSkyBlock(){return true;}
    @Override public Optional<String> preStartCheck() {
        if(!services.available())return Optional.of("Mining needs a loaded world and closed screen");
        if(recordedConfigured())return recorded==null?Optional.of("Recorded Echo native adapter is unavailable"):recorded.preStartCheck();
        dev.veltraph.client.world.BlockPos target;
        if(mode==MiningMode.ECHO)try {
            var route=EchoCheckpoint.parse(config.get(MiningConfig.ECHO_ROUTE));
            if(route.isEmpty())return Optional.of("Configure standing x,y,z>target x,y,z Echo checkpoints");
            target=route.getFirst().target();
        }catch(IllegalArgumentException ex){return Optional.of("Invalid Echo route: "+ex.getMessage());}
        else if(mode==MiningMode.MINE_DOWN)target=services.farm().player().blockPos().down();
        else {
            target=services.farm().breaking().targetBlockPos().orElse(null);
            if(target==null || !services.farm().world().isBlock(target,"minecraft:cobblestone"))return Optional.of("Aim at generated cobblestone");
        }
        String issue=services.selectTool(target,Math.max(1,config.get(MiningConfig.MINING_DURABILITY_RESERVE)));
        if(!issue.isEmpty()){services.cleanup();return Optional.of(issue);}
        if(!services.hasCapacity(target)){services.cleanup();return Optional.of("Inventory has no drop capacity");}
        return Optional.empty();
    }
    @Override public void onEnable(){dueRestCause=null;enableRuns++;controller.bindUseActivation(this,enableRuns);craftStarted=false;craftTicket=null;craftAdmissionTicks=0;craftProfile=null;recordedStarted=recordedConfigured();if(recordedStarted){if(recorded==null){setEnabled(false);return;}try{recorded.start(this);}catch(RuntimeException invalid){setEnabled(false);}return;}try{controller.start();services.farm().breaking().installTargetGuard(this,controller::allowsTarget);}catch(IllegalArgumentException ex){setEnabled(false);}}
    @Override public void onDisable(){dueRestCause=null;if(recordedStarted&&recorded!=null)recorded.stop();recordedStarted=false;if(craftStore!=null&&craftStore.active())craftStore.stop();controller.endOwnedSubflow(craftTicket);craftTicket=null;Object old=scheduledTicket;scheduledTicket=null;pausedSettings=null;services.cancelScheduledPause(old);controller.cancelScheduledRest(old);controller.stop();services.farm().breaking().clearTargetGuard(this);services.cleanup();}
    @Override public void onTick(){
        if(!isEnabled()||scheduledTicket!=null)return;
        if(dueRestCause!=null){if(!dueRestCurrent()){setEnabled(false);return;}return;}
        if(recordedStarted){if(!recorded.tick()){setEnabled(false);return;}setState(recorded.engine().state()==dev.veltraph.client.mining.recorded.RecordedEchoController.State.PAUSED?MacroState.PAUSED:MacroState.RUNNING);return;}
        if(recordedConfigured()){setEnabled(false);return;} // A new design requires a new explicit start.
        if(craftTicket!=null&&!craftStarted){
            // An inventory-triggered loan stops movement first. It never relaxes
            // the native full readiness/start gate while actual speed settles.
            if(!controller.resourceLoanCurrent(craftTicket)||!config.get(MiningConfig.COBBLE_CRAFT_STORE_ENABLED)||!java.util.Objects.equals(craftProfile,config.get(MiningConfig.COBBLE_CRAFT_STORE_PROFILE))||++craftAdmissionTicks>40){setEnabled(false);return;}
            try {var profile=dev.veltraph.client.mining.crafting.CobbleCraftStoreProfile.parse(craftProfile);
                if(craftStore.readiness(profile).isEmpty()){craftStarted=craftStore.start(profile);if(!craftStarted){setEnabled(false);return;}}
            }catch(RuntimeException invalid){setEnabled(false);return;}
            setState(MacroState.RUNNING);return;
        }
        if(craftStore!=null&&craftStore.active()){
            if(!config.get(MiningConfig.COBBLE_CRAFT_STORE_ENABLED)||!java.util.Objects.equals(craftProfile,config.get(MiningConfig.COBBLE_CRAFT_STORE_PROFILE))){setEnabled(false);return;}
            craftStore.tick();if(craftStore.failed()){setEnabled(false);return;}if(!craftStore.active()){controller.endOwnedSubflow(craftTicket);craftTicket=null;}setState(MacroState.RUNNING);return;
        }
        if(mode==MiningMode.COBBLESTONE)controller.observeCobbleInventoryBoundary();
        if(mode==MiningMode.COBBLESTONE&&craftStore!=null&&config.get(MiningConfig.COBBLE_CRAFT_STORE_ENABLED)&&controller.ownedSubflowReady())try{
            var profile=dev.veltraph.client.mining.crafting.CobbleCraftStoreProfile.parse(config.get(MiningConfig.COBBLE_CRAFT_STORE_PROFILE));
            boolean stationaryReady=craftStore.readiness(profile).isEmpty();
            if(stationaryReady||craftStore.materialsReadyToStop(profile)){
                craftTicket=new Object();if(!controller.beginOwnedSubflow(craftTicket)){craftTicket=null;return;}
                craftProfile=config.get(MiningConfig.COBBLE_CRAFT_STORE_PROFILE);craftAdmissionTicks=0;craftStarted=false;
                if(stationaryReady){craftStarted=craftStore.start(profile);if(!craftStarted){setEnabled(false);return;}}
                setState(MacroState.RUNNING);return;
            }
        }catch(RuntimeException invalid){setEnabled(false);return;}
        controller.tick();if(controller.state()==MiningController.State.COMPLETE){setEnabled(false);return;}setState(controller.state()==MiningController.State.PAUSED?MacroState.PAUSED:MacroState.RUNNING);
    }
    @Override public boolean allowsScreen(String title){return scheduledTicket==null&&craftStore!=null&&craftStore.allowsScreen(title);}
    public java.util.Optional<dev.veltraph.client.mining.crafting.CobbleCraftStorePort> craftStore(){return java.util.Optional.ofNullable(craftStore);}

    /** Called only for the registered input owner before an exclusive Root response takes over. */
    public void suspendOwnedUse(){if(isEnabled()&&scheduledTicket==null)controller.suspendOwnedUse();}
    @Override public boolean ownsGameplayInput(){return scheduledTicket==null;}
    public MiningMode miningMode(){return mode;}
    public int enableRuns(){return enableRuns;}
    public String scheduledSettingsSignature(){return mode+"|"+config.get(MiningConfig.AUTO_BREAK_MINING)+"|"+config.get(MiningConfig.MINING_MIN_Y)+"|"+config.get(MiningConfig.MINING_BREAK_TIMEOUT_MS)+"|"+config.get(MiningConfig.MINING_DURABILITY_RESERVE)+"|"+config.get(MiningConfig.COBBLE_AUTO_WALK)+"|"+config.get(MiningConfig.COBBLE_AUTO_SPRINT)+"|"+config.get(MiningConfig.COBBLE_AUTO_CHANGE_DIR)+"|"+config.get(MiningConfig.COBBLE_HOLD_RIGHT_CLICK)+"|"+config.get(MiningConfig.ECHO_ROUTE)+"|"+config.get(MiningConfig.ECHO_ALLOW_CHECKPOINT_SKIPPING)+"|"+config.get(MiningConfig.ECHO_REMEMBER_LAST_SESSION)+"|"+config.get(MiningConfig.ECHO_RESET_AT_REWARP)+"|"+config.get(MiningConfig.COBBLE_CRAFT_STORE_ENABLED)+"|"+config.get(MiningConfig.COBBLE_CRAFT_STORE_PROFILE);}
    public Optional<String> scheduledRestReadiness(){
        if(recordedStarted)return recorded.scheduledRestReadiness();
        if(recordedConfigured())return Optional.of("Recorded Echo design changed; restart after inspection");
        if(craftTicket!=null||craftStore!=null&&craftStore.active())return Optional.of("Craft/store holds a menu/resource/navigation loan");
        if(!isEnabled()||!config.get(MiningConfig.AUTO_BREAK_MINING)||!controller.scheduledBoundary()||!controller.scheduledGeometryReady())return Optional.of("Mining needs a healthy between-operation boundary");
        try{if(!controller.configuredProfileMatches())return Optional.of("Loaded mining profile differs from configured route");}
        catch(IllegalArgumentException error){return Optional.of("Configured mining route is invalid");}
        int reserve=Math.max(1,config.get(MiningConfig.MINING_DURABILITY_RESERVE));
        if(scheduledTicket!=null){
            if(!scheduledSettingsSignature().equals(pausedSettings)||!services.scheduledArrivalReady(scheduledTicket,reserve))return Optional.of("Saved mining pose/tool/menu readiness is not restored");
        }else if(!services.scheduledPauseReady(reserve))return Optional.of("Mining holds a navigation/resource/menu loan or is not safely supported");
        return Optional.empty();
    }
    public boolean scheduledPauseOwnedBy(Object ticket){return ticket!=null&&isEnabled()&&scheduledTicket==ticket&&controller.scheduledOwnedBy(ticket);}
    /** Only the scheduler's actual due-policy path calls this optional stopping port.
     * No rest ticket, transport or menu permission is created here. */
    public boolean prepareForDueRest(Object cause){
        if(cause==null||mode!=MiningMode.COBBLESTONE||!isEnabled()||!config.get(MiningConfig.AUTO_BREAK_MINING)||scheduledTicket!=null||craftTicket!=null||craftStore!=null&&craftStore.active())return false;
        if(dueRestCause!=null)return dueRestCause==cause&&dueRestCurrent();
        controller.observeCobbleInventoryBoundary();
        if(!controller.ownedSubflowReady()||!controller.scheduledBoundary())return false;
        dueRestCause=cause;dueRestActivation=enableRuns;dueRestSettings=scheduledSettingsSignature();dueRestWorld=services.worldIdentity();
        controller.stop();
        if(!dueRestCurrent()){dueRestCause=null;return false;}return true;
    }
    private boolean dueRestCurrent(){return dueRestCause!=null&&isEnabled()&&enableRuns==dueRestActivation&&services.available()
            &&java.util.Objects.equals(dueRestWorld,services.worldIdentity())&&java.util.Objects.equals(dueRestSettings,scheduledSettingsSignature())&&controller.ownedSubflowReady();}
    public void cancelDueRestPreparation(Object cause){if(cause!=null&&dueRestCause==cause){dueRestCause=null;}}
    public boolean pauseForScheduledRest(Object ticket){
        if(scheduledPauseOwnedBy(ticket))return true;
        if(ticket==null||scheduledTicket!=null||scheduledRestReadiness().isPresent())return false;
        int reserve=Math.max(1,config.get(MiningConfig.MINING_DURABILITY_RESERVE));
        if(!services.captureScheduledPause(ticket,reserve))return false;
        if(!controller.pauseForScheduledRest(ticket)){services.cancelScheduledPause(ticket);return false;}
        scheduledTicket=ticket;dueRestCause=null;pausedSettings=scheduledSettingsSignature();
        services.releaseScheduledPause(ticket);setState(MacroState.PAUSED);return true;
    }
    /** Parent has proved its matched join/saved-pose return; no enable/start/reset hook is invoked. */
    public boolean rebindAfterScheduledArrival(Object ticket){
        if(!scheduledPauseOwnedBy(ticket)||scheduledRestReadiness().isPresent())return false;
        if(!services.rebindScheduledPause(ticket,Math.max(1,config.get(MiningConfig.MINING_DURABILITY_RESERVE))))return false;
        if(!controller.rebindForScheduledRest(ticket))return false;
        services.farm().breaking().installTargetGuard(this,controller::allowsTarget);return scheduledPauseOwnedBy(ticket);
    }
    public boolean resumeAfterScheduledRest(Object ticket){
        if(!scheduledPauseOwnedBy(ticket)||scheduledRestReadiness().isPresent())return false;
        // Readiness/rebind has pinned context before either gate is cleared.
        if(!services.resumeScheduledPause(ticket,Math.max(1,config.get(MiningConfig.MINING_DURABILITY_RESERVE))))return false;
        if(!controller.resumeScheduledRest(ticket)){setEnabled(false);return false;}
        scheduledTicket=null;pausedSettings=null;setState(MacroState.RUNNING);return true;
    }
    public void resetAtRewarp(){if(recordedStarted){recorded.resetAtRewarp();return;}controller.resetAtRewarp();}
    public MiningController controller(){return controller;}
    public boolean recordedPlayback(){return recordedStarted;}
    public Optional<dev.veltraph.client.mining.recorded.RecordedEchoController> recordedController(){return recorded==null?Optional.empty():Optional.of(recorded.engine());}
    @Override public String diagnostics(){if(recordedStarted)return id()+" "+recorded.diagnostics();return id()+" "+(scheduledTicket!=null?"SCHEDULED_PAUSE / ":"")+controller.state()+" | "+controller.reason()+" | removed="+controller.removed()+" checkpoint="+controller.checkpoint()+(craftStarted?" | "+craftStore.status():"");}
}
