package dev.veltraph.client.foraging.replant;

import dev.veltraph.client.config.*;
import dev.veltraph.client.core.*;
import dev.veltraph.client.game.GameArea;
import dev.veltraph.client.service.FarmServices;
import java.util.Optional;
import net.minecraft.client.Minecraft;

/** Explicit private-island source Dark Oak plant/grow/harvest/reset continuation. */
public final class ForagingReplantModule extends AbstractModule implements dev.veltraph.client.foraging.scheduling.ForagingRestOwner {
    private final Minecraft client;private final ConfigService config;private final FarmServices services;
    private McForagingReplant runtime;private ForagingReplantCycle cycle;private OwnedReplantSupply supply;
    private final java.util.List<dev.veltraph.client.metrics.FinancialActionReceipt> supplyReceipts=new java.util.ArrayList<>();
    private int supplyResumes;
    public java.util.List<dev.veltraph.client.metrics.FinancialActionReceipt> acknowledgedFinancialActions(){var all=new java.util.ArrayList<>(supplyReceipts);if(supply!=null)all.addAll(supply.receipts());return java.util.List.copyOf(all);}
    public int supplyResumes(){return supplyResumes;}
    private boolean supplyAllowed(){return config.get(dev.veltraph.client.foraging.supply.ForagingSupplyConfig.FORAGING_CHEST_FILL_ENABLED);}
    private final dev.veltraph.client.foraging.scheduling.ForagingPauseGate rest=new dev.veltraph.client.foraging.scheduling.ForagingPauseGate();
    public ForagingReplantModule(String id,String name,Minecraft client,ConfigService config,FarmServices services){super(id,name,ModuleCategory.FORAGING);this.client=client;this.config=config;this.services=services;}
    @Override public boolean requiresSkyBlock(){return true;}@Override public Optional<GameArea> requiredArea(){return Optional.of(GameArea.ISLAND);}
    @Override public Optional<String> preStartCheck(){if(hasPendingCleanup())return Optional.of("Finish owned inventory restoration first.");var anchor=McForagingReplant.parseAnchor(config.get(ForagingReplantConfig.ISLAND_REPLANT_ANCHOR));if(anchor.isEmpty())return Optional.of("Configure the explicit 2x2 Dark Oak planting anchor (x,y,z).");runtime=new McForagingReplant(client,config,services,anchor.get(),()->this);return runtime.preStart(supplyAllowed());}
    @Override public void onEnable(){rest.clear();supply=null;supplyReceipts.clear();supplyResumes=0;var invalid=runtime==null?preStartCheck():runtime.preStart(supplyAllowed());if(invalid.isPresent()){setEnabled(false);return;}cycle=new ForagingReplantCycle();retainedNormalReturnSource=null;runtime.start();}
    private ForagingReplantCycle.Settings settings(){return new ForagingReplantCycle.Settings(Math.max(1000,Math.min(60000,config.get(ForagingReplantConfig.ISLAND_REPLANT_STAGE_TIMEOUT_MS))),Math.max(100,Math.min(5000,config.get(ForagingReplantConfig.ISLAND_REPLANT_ACK_MS))),Math.max(100,Math.min(5000,config.get(ForagingReplantConfig.ISLAND_REPLANT_BONEMEAL_INTERVAL_MS))),Math.max(1,Math.min(64,config.get(ForagingReplantConfig.ISLAND_REPLANT_MAX_BONEMEAL_USES))),Math.max(0,config.get(ForagingReplantConfig.ISLAND_REPLANT_CYCLES)),config.get(ForagingReplantConfig.FORAGING_RESET_ON_LONG_CHANGE));}
    @Override public void onTick(){if(rest.paused()||!isEnabled()||runtime==null||cycle==null)return;if(supply!=null){
            supply.tick();if(supply.terminal()){supplyReceipts.addAll(supply.receipts());if(supply.resumed())supplyResumes++;supply=null;}
        }else if(cycle.supplyRequest().isPresent()){
            if(!supplyAllowed())cycle.rejectSupply("Resource shortage; explicit Foraging chest supply is disabled",runtime);
            else {supply=new OwnedReplantSupply(client,config,services,this,runtime,cycle);supply.tick();}
        }else cycle.tick(runtime.read(),settings(),runtime);
        if(cycle.terminal())setEnabled(false);}
    @Override public void onDisable(){rest.clear();if(supply!=null){supply.cancel();supplyReceipts.addAll(supply.receipts());supply=null;}if(runtime!=null){if(cycle!=null&&!cycle.terminal())cycle.cancel(runtime);runtime.beginCleanup();runtime.cleanupTick();}}
    @Override public void maintenanceTick(){if(!rest.paused()&&!isEnabled()&&runtime!=null&&runtime.pendingCleanup())runtime.cleanupTick();}
    @Override public boolean hasPendingCleanup(){return runtime!=null&&runtime.pendingCleanup();}
    @Override public boolean allowsScreen(String title){return runtime!=null&&runtime.ownsInventoryScreen()||supply!=null&&supply.allowsScreen(title);}
    @Override public void onServerMessage(String line){if(!rest.paused()&&runtime!=null)runtime.feedback(line);}
    @Override public boolean consumeExpectedTeleport(dev.veltraph.client.world.Vec3 position,long now){
        return isEnabled()&&!rest.paused()&&runtime!=null&&cycle!=null&&!cycle.terminal()
                &&(supply!=null&&supply.consumeExpectedTeleport(position,now)||runtime.consumeExpectedTeleport(position,now));
    }
    private NativeNormalReturnSource retainedNormalReturnSource;
    /** Actual private replant return source; no overridden role Boolean can construct it. */
    public final class NativeNormalReturnSource {
        private final ForagingReplantCycle originalCycle=cycle;
        private final McForagingReplant originalRuntime=runtime;
        private final OwnedReplantSupply originalSupply=supply;
        private final dev.veltraph.client.core.ModuleManager registry=dev.veltraph.client.VeltraphBootstrap.moduleManager();
        private final dev.veltraph.client.core.ModuleManager.ModuleTickDeclaration declaration;
        private final dev.veltraph.client.VeltraphBootstrap.RootOriginalInputPublication publication;
        private final McForagingReplant.NativeNormalReturnSource travel;
        private final dev.veltraph.client.core.ModuleManager.LifecycleSnapshot enabledLifecycle;
        private final dev.veltraph.client.config.MapConfigService.StoredStateSnapshot settings;
        private NativeNormalReturnSource(dev.veltraph.client.core.ModuleManager.ModuleTickDeclaration declaration,
                dev.veltraph.client.VeltraphBootstrap.RootOriginalInputPublication publication,dev.veltraph.client.navigation.mc.LegacyTravelPublicationLease.Leg exact){
            this.declaration=declaration;this.publication=publication;enabledLifecycle=registry.lifecycleSnapshot();
            settings=config instanceof dev.veltraph.client.config.MapConfigService m?m.storedStateSnapshot():config instanceof dev.veltraph.client.config.JsonConfigService j?j.storedStateSnapshot():null;
            travel=originalRuntime.captureNativeNormalReturnSource(exact,ForagingReplantModule.this);
        }
        public boolean nativeCurrent(){
            // Semantic callbacks run first; every retained Module field is vetoed afterwards.
            if(travel==null||!travel.semanticCurrent()||settings==null||!settings.current()
                ||!dev.veltraph.client.VeltraphBootstrap.originalNativeGuiAffiliationCurrent(publication,client))return false;
            return travel.rawCurrent()&&declaration!=null&&(declaration.issuingCurrent(ForagingReplantModule.this)||declaration.normalReturnedCurrent(ForagingReplantModule.this))
                &&registry==dev.veltraph.client.VeltraphBootstrap.moduleManager()&&registry.get(id()).orElse(null)==ForagingReplantModule.this
                &&config==dev.veltraph.client.VeltraphBootstrap.config()&&registry.lifecycleCurrent(enabledLifecycle)
                &&isEnabled()&&cycle==originalCycle&&runtime==originalRuntime&&supply==originalSupply&&originalSupply==null
                &&originalCycle!=null&&originalCycle.stage()==ForagingReplantCycle.Stage.RETURNING&&!rest.paused();
        }
    }
    /** Capture before and after the original getter, only inside the canonical ordinary Leg factory. */
    public NativeNormalReturnSource captureNativeNormalReturnSource(dev.veltraph.client.navigation.mc.LegacyTravelPublicationLease.Leg exact){
        boolean originalCaller=StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(stream->{
            var first=stream.filter(f->f.getDeclaringClass()!=ForagingReplantModule.class).findFirst().orElse(null);
            return first!=null&&first.getDeclaringClass()==dev.veltraph.client.navigation.mc.LegacyTravelPublicationLease.Leg.class&&first.getMethodName().equals("ownedUseSnapshot");
        });
        var root=dev.veltraph.client.VeltraphBootstrap.moduleManager();
        if(!originalCaller||root==null||!client.isSameThread()||!isEnabled()||rest.paused()||cycle==null||cycle.stage()!=ForagingReplantCycle.Stage.RETURNING
            ||supply!=null||runtime==null||exact==null||exact.parent()!=this||config!=dev.veltraph.client.VeltraphBootstrap.config())return null;
        var declaration=root.currentModuleTickDeclaration(this);var publication=dev.veltraph.client.VeltraphBootstrap.originalRootPublicationFor(this);
        if(declaration==null||!declaration.issuingCurrent(this)||publication==null||!publication.currentFor(this))return null;
        // Pin Module runtime/cycle before travel's component comparisons can re-enter preStartCheck.
        var actual=new NativeNormalReturnSource(declaration,publication,exact);if(!actual.nativeCurrent())return null;
        boolean originalRole=ownsGameplayInput();boolean originalPassive=isPassiveObserver();
        if(!originalRole||originalPassive||!declaration.issuingCurrent(this)||!publication.currentFor(this)||!actual.nativeCurrent())return null;
        retainedNormalReturnSource=actual;return actual;
    }
    /** Private invocation provenance. It has no gameplay/native-use permission surface. */
    final class NativeNormalReturnCleanupContext {
        final ForagingReplantCycle originalCycle=cycle;
        final McForagingReplant originalRuntime=runtime;
        final OwnedReplantSupply originalSupply=supply;
        final ForagingReplantCycle.Stage originalStage=cycle.stage();
        final dev.veltraph.client.core.ModuleManager registry=dev.veltraph.client.VeltraphBootstrap.moduleManager();
        final dev.veltraph.client.core.ModuleManager.ModuleTickDeclaration tick;
        final dev.veltraph.client.core.ModuleManager.PendingOwnDisable disable;
        final dev.veltraph.client.core.ModuleManager.LifecycleSnapshot enabledLifecycle;
        final dev.veltraph.client.VeltraphBootstrap.RootOriginalInputPublication publication;
        final dev.veltraph.client.config.MapConfigService.StoredStateSnapshot settings;
        private NativeNormalReturnCleanupContext(NativeNormalReturnSource retained){
            disable=registry.pendingActualOwnDisable(ForagingReplantModule.this).orElse(null);
            tick=disable==null?registry.currentModuleTickDeclaration(ForagingReplantModule.this):null;
            enabledLifecycle=retained==null?registry.lifecycleSnapshot():retained.enabledLifecycle;
            publication=retained==null?dev.veltraph.client.VeltraphBootstrap.originalRootPublicationFor(ForagingReplantModule.this):retained.publication;
            settings=retained==null?(config instanceof dev.veltraph.client.config.MapConfigService m?m.storedStateSnapshot():config instanceof dev.veltraph.client.config.JsonConfigService j?j.storedStateSnapshot():null):retained.settings;
        }
        boolean semanticCurrent(){return settings!=null&&settings.current()&&dev.veltraph.client.VeltraphBootstrap.originalNormalReturnCleanupAffiliationCurrent(
            publication,ForagingReplantModule.this,client,enabledLifecycle,tick,disable);}
        boolean rawCurrent(){return registry==dev.veltraph.client.VeltraphBootstrap.moduleManager()&&registry.get(id()).orElse(null)==ForagingReplantModule.this
                &&config==dev.veltraph.client.VeltraphBootstrap.config()&&!rest.paused()
                &&cycle==originalCycle&&runtime==originalRuntime&&supply==originalSupply&&originalSupply==null
                &&originalCycle!=null&&originalCycle.stage()==originalStage;
        }
        boolean nativeCurrent(){return semanticCurrent()&&rawCurrent();}
    }
    /** Package-private, reachable only by the original private runtime window opener. */
    NativeNormalReturnCleanupContext openNativeNormalReturnCleanupContext(McForagingReplant actualRuntime){
        boolean canonical=StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(stream->{
            var first=stream.filter(f->f.getDeclaringClass()!=ForagingReplantModule.class).findFirst().orElse(null);
            return first!=null&&first.getDeclaringClass()==McForagingReplant.class&&first.getMethodName().equals("openNormalReturnCleanupWindow");
        });
        var root=dev.veltraph.client.VeltraphBootstrap.moduleManager();
        if(!canonical||!client.isSameThread()||root==null||actualRuntime==null||runtime!=actualRuntime||cycle==null||supply!=null||rest.paused())return null;
        var retained=retainedNormalReturnSource;
        if(retained!=null&&(retained.originalRuntime!=runtime||retained.originalCycle!=cycle||retained.originalSupply!=supply))return null;
        if(!isEnabled()&&retained==null)return null;
        var actual=new NativeNormalReturnCleanupContext(retained);return actual.nativeCurrent()?actual:null;
    }
    /** Sole public cleanup ABI consumed only by Leg's genuine original selected-slot restore. */
    public final class NativeNormalReturnCleanupSource {
        private final dev.veltraph.client.navigation.mc.LegacyTravelPublicationLease.Leg leg;
        private final NativeNormalReturnCleanupContext context;
        private final McForagingReplant.NativeNormalReturnCleanupWindow travel;
        private NativeNormalReturnCleanupSource(dev.veltraph.client.navigation.mc.LegacyTravelPublicationLease.Leg leg,
                NativeNormalReturnCleanupContext context,McForagingReplant.NativeNormalReturnCleanupWindow travel){
            this.leg=leg;this.context=context;this.travel=travel;
        }
        public boolean belongsTo(dev.veltraph.client.navigation.mc.LegacyTravelPublicationLease.Leg exact){return exact!=null&&exact==leg&&exact.parent()==ForagingReplantModule.this&&travel.belongsTo(exact);}
        public boolean nativeCurrent(){return travel.semanticCurrent()&&context.semanticCurrent()&&travel.rawCurrent()&&context.rawCurrent();}
    }
    public NativeNormalReturnCleanupSource captureNativeNormalReturnCleanupSource(dev.veltraph.client.navigation.mc.LegacyTravelPublicationLease.Leg exact){
        boolean canonical=StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(stream->{
            var first=stream.filter(f->f.getDeclaringClass()!=ForagingReplantModule.class).findFirst().orElse(null);
            return first!=null&&first.getDeclaringClass()==dev.veltraph.client.navigation.mc.LegacyTravelPublicationLease.Leg.class&&first.getMethodName().equals("normalReturnCleanupSnapshot");
        });
        if(!canonical||!client.isSameThread()||runtime==null||exact==null||exact.parent()!=this)return null;
        var travel=runtime.currentNativeNormalReturnCleanupWindow(exact);if(travel==null)return null;
        var actual=new NativeNormalReturnCleanupSource(exact,travel.context,travel);
        return actual.belongsTo(exact)&&actual.nativeCurrent()?actual:null;
    }
    @Override public boolean ownsGameplayInput(){return !rest.paused();}
    @Override public Optional<String> scheduledRestReadiness(){if(!isEnabled()||cycle==null||runtime==null||!cycle.atScheduledRestBoundary()||supply!=null||cycle.supplyRequest().isPresent())return Optional.of("Replant requires its empty between-cycle planting boundary");if(!rest.paused()&&!runtime.nativeOwnerBound())return Optional.of("Replant original native owner changed");return runtime.scheduledRestReadiness();}
    @Override public boolean pauseForScheduledRest(Object ticket){return rest.pause(ticket,scheduledRestReadiness().isEmpty(),System.currentTimeMillis(),()->runtime.release());}
    @Override public boolean scheduledRestOwnedBy(Object ticket){return isEnabled()&&rest.owns(ticket);}
    @Override public boolean rebindForScheduledRest(Object ticket){return scheduledRestOwnedBy(ticket)&&cycle.atScheduledRestBoundary()&&runtime.rebindForScheduledRest()&&rest.rebound(ticket);}
    @Override public boolean resumeFromScheduledRest(Object ticket){if(!scheduledRestOwnedBy(ticket))return false;long elapsed=rest.resume(ticket,System.currentTimeMillis());if(elapsed<0)return false;cycle.rebaseAfterScheduledRest(elapsed);return true;}
    public Object scheduledEngineIdentity(){return cycle;}
    public ForagingReplantCycle.Stage stage(){return cycle==null?ForagingReplantCycle.Stage.CANCELLED:cycle.stage();}public int completedCycles(){return cycle==null?0:cycle.cycles();}public int harvestedLogs(){return runtime==null?0:runtime.harvestedLogs();}public int collectedItems(){return runtime==null?0:runtime.collectedItems();}
    @Override public String diagnostics(){return id()+" | "+stage()+" | cycles="+completedCycles()+" | harvested="+harvestedLogs()+" | collected="+collectedItems()+" | cleanup="+hasPendingCleanup()+" | "+(cycle==null?"":cycle.progress()+" | "+cycle.reason())+" | "+(runtime==null?"":runtime.problem())+" | supply="+(supply==null?"idle":supply.diagnostics())+" | supplyResumes="+supplyResumes;}
}
