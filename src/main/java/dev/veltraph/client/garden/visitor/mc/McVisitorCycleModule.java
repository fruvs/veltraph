package dev.veltraph.client.garden.visitor.mc;

import dev.veltraph.client.config.*;
import dev.veltraph.client.core.*;
import dev.veltraph.client.game.*;
import dev.veltraph.client.garden.loadout.NamedLoadoutFlow;
import dev.veltraph.client.garden.visitor.*;
import dev.veltraph.client.service.FarmServices;
import dev.veltraph.client.world.Vec3;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.*;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** Exact registered helper lease; immutable ordered NPC identities and named loadout restoration. */
public final class McVisitorCycleModule extends AbstractModule implements dev.veltraph.client.rewarp.mc.FarmMaintenanceCoordinator.Participant {
    public static final String ID="garden_visitor_cycle";
    /** Injected shared maintenance service: never substitutes a rewarp suspension or restarts onEnable. */
    public interface LeasePort {
        Optional<String> readiness();
        boolean acquire(McVisitorCycleModule helper);
        boolean valid(McVisitorCycleModule helper);
        boolean beginReturn(McVisitorCycleModule helper,BooleanSupplier restoredLoadout);
        VisitorCycleRuntime.Progress tickReturn(McVisitorCycleModule helper);
        boolean resume(McVisitorCycleModule helper,BooleanSupplier restoredLoadout);
        void cancel(McVisitorCycleModule helper);
        /** Existing selling worker is borrowed under this exact helper lease; no new controller or farm capture. */
        default VisitorCycleRuntime.Progress beginPostQueueAutoSell(McVisitorCycleModule helper,BooleanSupplier restoredLoadout){return VisitorCycleRuntime.Progress.FAILED;}
        default VisitorCycleRuntime.Progress tickPostQueueAutoSell(McVisitorCycleModule helper){return VisitorCycleRuntime.Progress.FAILED;}
        default boolean allowsPostQueueAutoSellScreen(McVisitorCycleModule helper,String title){return false;}
        /** Frozen only for explicitly selected post-queue selling. */
        default String postQueueAutoSellSettings(){return "Unbound post-queue seller";}
        default boolean hasPostQueueCleanup(McVisitorCycleModule helper){return false;}
        default void maintenanceTickPostQueueCleanup(McVisitorCycleModule helper){}
        default List<dev.veltraph.client.inventory.selling.SellingAcknowledgment> acknowledgedPostQueueSales(McVisitorCycleModule helper){return List.of();}
        default Optional<dev.veltraph.client.inventory.selling.BazaarCustodyModule.Acknowledgment> acknowledgedPostQueueBazaarSale(McVisitorCycleModule helper){return Optional.empty();}
        default boolean completing(McVisitorCycleModule helper){return false;}
        default void message(String line){}
        default boolean consumeExpectedTeleport(Vec3 position,long now){return false;}
        default String status(){return "Maintenance lease";}
    }
    private final Minecraft client;private final FarmServices services;private final ConfigService config;
    private final GameContextService context;private final LeasePort lease;
    private final NamedLoadoutFlow loadouts;private final NamedLoadoutFlow.Port loadoutPort;private final Supplier<NamedLoadoutFlow.Settings> loadoutSettings;
    private final McVisitorServeModule transaction;private final VisitorCycleRuntime runtime;
    private Map<String,NamedLoadoutFlow.Gear> originalFarmGear=Map.of();private NamedLoadoutFlow.Gear originalFarmTool;private int originalFarmToolSlot=-1;
    private Object player,world,connection;private List<VisitorQueue.Entry> queue=List.of();private Map<UUID,Entity> targets=Map.of();
    private List<Object> configuration=List.of();private boolean pending,started,swap,cancelled;private String lastError="Idle";
    public McVisitorCycleModule(Minecraft client,FarmServices services,ConfigService config,GameContextService context,
            LeasePort lease,NamedLoadoutFlow loadouts,NamedLoadoutFlow.Port loadoutPort,Supplier<NamedLoadoutFlow.Settings> loadoutSettings){
        super(ID,"Ordered garden visitors",ModuleCategory.INTERMEDIARY);this.client=client;this.services=services;this.config=config;this.context=context;this.lease=Objects.requireNonNull(lease);
        this.loadouts=loadouts;this.loadoutPort=loadoutPort;this.loadoutSettings=loadoutSettings;transaction=new McVisitorServeModule(client,services,config);
        runtime=new VisitorCycleRuntime(new VisitorCycleRuntime.Actions(){
            public boolean acquire(){return McVisitorCycleModule.this.lease.acquire(McVisitorCycleModule.this);}
            public boolean valid(){return scopeValid()&&McVisitorCycleModule.this.lease.valid(McVisitorCycleModule.this);}
            public boolean beginLoadout(String name){
                if(loadouts==null||loadoutPort==null||loadoutSettings==null)return false;
                loadouts.start(name,loadoutSettings.get(),tick());return loadouts.active()||loadouts.state()==NamedLoadoutFlow.State.APPLIED;
            }
            public VisitorCycleRuntime.Progress loadout(){loadouts.tick(tick());return loadoutProgress(false);}
            public boolean startVisitor(VisitorQueue.Entry entry,long remaining){
                Entity target=targets.get(entry.identity());
                if(!targetValid(entry,target)||transaction.isEnabled())return false;
                long currentRemaining=config.get(VisitorConfig.VISITOR_CYCLE_MAX_COST)-runtime.spent();
                if(currentRemaining<0)return false;
                transaction.target(target,Math.min(remaining,currentRemaining));transaction.setEnabled(true);return transaction.isEnabled();
            }
            public VisitorCycleRuntime.Result visitor(){
                if(transaction.isEnabled())transaction.onTick();
                var state=transaction.isEnabled()?VisitorCycleRuntime.Progress.ACTIVE:switch(transaction.outcome()){
                    case COMPLETE->VisitorCycleRuntime.Progress.COMPLETE;case SKIPPED->VisitorCycleRuntime.Progress.SKIPPED;
                    case CANCELLED->VisitorCycleRuntime.Progress.CANCELLED;default->VisitorCycleRuntime.Progress.FAILED;
                };
                return new VisitorCycleRuntime.Result(state,transaction.coinsSpent());
            }
            public boolean restoreLoadout(){loadouts.restore(tick());return loadouts.active()||loadouts.state()==NamedLoadoutFlow.State.RESTORED;}
            public VisitorCycleRuntime.Progress restoredLoadout(){loadouts.tick(tick());return loadoutProgress(true);}
            public VisitorCycleRuntime.Progress beginPostQueueAutoSell(){return McVisitorCycleModule.this.lease.beginPostQueueAutoSell(McVisitorCycleModule.this,McVisitorCycleModule.this::restoredLoadoutWitness);}
            public VisitorCycleRuntime.Progress postQueueAutoSell(){return McVisitorCycleModule.this.lease.tickPostQueueAutoSell(McVisitorCycleModule.this);}
            public boolean startReturn(){return McVisitorCycleModule.this.lease.beginReturn(McVisitorCycleModule.this,McVisitorCycleModule.this::restoredLoadoutWitness);}
            public VisitorCycleRuntime.Progress returned(){return McVisitorCycleModule.this.lease.tickReturn(McVisitorCycleModule.this);}
            public boolean resume(){boolean resumed=McVisitorCycleModule.this.lease.resume(McVisitorCycleModule.this,McVisitorCycleModule.this::restoredLoadoutWitness);
                if(resumed)clearScope();return resumed;}
            public void cancel(){cancelOwned();}
        });
    }
    @Override public boolean requiresSkyBlock(){return true;}
    @Override public Optional<GameArea> requiredArea(){return Optional.of(GameArea.GARDEN);}
    private long now(){return services.clock().nowMillis();}private long tick(){return services.clock().currentTick();}
    private List<Object> configuration(){return List.of(config.get(VisitorConfig.VISITOR_NPC_NAMES),config.get(VisitorConfig.VISITOR_NPC_NAME),
            config.get(VisitorConfig.VISITOR_ORDER),config.get(VisitorConfig.VISITOR_ORDER_ORIGIN),config.get(VisitorConfig.VISITOR_MAX_VISITORS),config.get(VisitorConfig.VISITOR_LOADOUT_SWAP),config.get(VisitorConfig.VISITOR_LOADOUT_NAME),
            config.get(VisitorConfig.VISITOR_AUTO_SELL_AFTER),config.get(VisitorConfig.VISITOR_AUTO_SELL_AFTER)?lease.postQueueAutoSellSettings():"Post-queue selling off");}
    private boolean enabledPolicy(){return config.get(VisitorConfig.VISITOR_MULTI_SERVE)&&config.get(VisitorConfig.VISITOR_AUTO_SERVE)
            &&config.get(VisitorConfig.VISITOR_HAVE_MAX_PRICES)&&config.get(VisitorConfig.VISITOR_CYCLE_MAX_COST)>0
            &&config.get(VisitorConfig.VISITOR_MAX_COST)>0&&config.get(VisitorConfig.VISITOR_MAX_SERVE_QUANTITY)>0;}
    @Override public Optional<String> preStartCheck(){
        if(!enabledPolicy())return Optional.of("Enable ordered visitors, Auto Serve and positive enforced cycle/offer guards");
        if(client.player==null||client.level==null||client.getConnection()==null||!client.player.isAlive()||client.gui.screen()!=null)return Optional.of("Ordered visitors require a live Garden farm with closed menus");
        if(context.current().area()!=GameArea.GARDEN||!context.current().inSkyBlock())return Optional.of("Ordered visitors require the Garden");
        if(hasPendingCleanup())return Optional.of("Original named loadout restoration is still pending");
        var readiness=lease.readiness();if(readiness.isPresent())return readiness;
        try{captureTargets();
            if(config.get(VisitorConfig.VISITOR_LOADOUT_SWAP)){
                if(loadouts==null||loadoutPort==null||loadoutSettings==null)return Optional.of("Configure exact named loadout profiles and native menu controls first");
                var settings=loadoutSettings.get();if(settings==null)return Optional.of("Named loadout profiles are unavailable");
                String wanted=config.get(VisitorConfig.VISITOR_LOADOUT_NAME).strip();
                if(settings.profiles().stream().noneMatch(p->p.name().equalsIgnoreCase(wanted)))return Optional.of("Requested visitor loadout has no exact configured gear witness");
                var current=dev.veltraph.client.garden.loadout.mc.McNamedLoadoutPort.equipped(client.player);
                if(settings.profiles().stream().noneMatch(p->p.gear().equals(current)))return Optional.of("Record the current farming loadout's complete armor/offhand before starting visitors");
            }
        }catch(IllegalArgumentException invalid){return Optional.of(invalid.getMessage());}
        return Optional.empty();
    }
    private void captureTargets(){
        var names=VisitorQueue.names(config.get(VisitorConfig.VISITOR_NPC_NAMES),config.get(VisitorConfig.VISITOR_NPC_NAME),config.get(VisitorConfig.VISITOR_MAX_VISITORS));
        var entries=new ArrayList<VisitorQueue.Entry>();var found=new LinkedHashMap<UUID,Entity>();
        var origin=VisitorQueue.origin(config.get(VisitorConfig.VISITOR_ORDER_ORIGIN),new Vec3(client.player.getX(),client.player.getY(),client.player.getZ()));
        for(String wanted:names){Entity selected=null;
            for(var entity:client.level.entitiesForRendering())if(entity!=client.player&&entity instanceof LivingEntity&&entity.isAlive()
                    &&!(entity instanceof net.minecraft.world.entity.decoration.ArmorStand stand&&stand.isMarker())&&entity.distanceToSqr(client.player)<64*64
                    &&LocationText.stripColorCodes(entity.getName().getString()).trim().equalsIgnoreCase(wanted)){
                if(selected!=null)throw new IllegalArgumentException("Multiple loaded NPCs match visitor "+wanted);selected=entity;
            }
            if(selected==null)throw new IllegalArgumentException("Configured visitor NPC is not loaded: "+wanted);
            String name=LocationText.stripColorCodes(selected.getName().getString()).trim();
            var position=new Vec3(selected.getX(),selected.getY(),selected.getZ());
            if(position.distanceTo(origin)>=20)throw new IllegalArgumentException("Configured visitor is outside 20 blocks of the order reference: "+name);
            entries.add(new VisitorQueue.Entry(selected.getUUID(),name,McVisitorIdentity.rarity(name,List.of(),selected.getDisplayName()),position.distanceTo(origin)));found.put(selected.getUUID(),selected);
        }
        queue=VisitorQueue.ordered(entries,config.get(VisitorConfig.VISITOR_ORDER));targets=Map.copyOf(found);
    }
    /** Defer every menu/travel/inventory request until the shared service commits this helper's lease. */
    @Override public void onEnable(){
        player=client.player;world=client.level;connection=client.getConnection();configuration=configuration();swap=config.get(VisitorConfig.VISITOR_LOADOUT_SWAP);
        originalFarmGear=client.player==null?Map.of():dev.veltraph.client.garden.loadout.mc.McNamedLoadoutPort.equipped(client.player);
        originalFarmToolSlot=client.player==null?-1:client.player.getInventory().getSelectedSlot();
        originalFarmTool=client.player==null?null:dev.veltraph.client.garden.loadout.mc.McNamedLoadoutPort.fingerprint(client.player.getInventory().getItem(originalFarmToolSlot));
        pending=true;started=false;cancelled=false;lastError="Awaiting committed maintenance lease";
        try{captureTargets();}catch(RuntimeException invalid){lastError=invalid.getMessage();setEnabled(false);}
    }
    private boolean scopeValid(){return isEnabled()&&enabledPolicy()&&client.player==player&&client.level==world&&client.getConnection()==connection
            &&client.player!=null&&client.player.isAlive()&&context.current().inSkyBlock()&&context.current().area()==GameArea.GARDEN
            &&configuration.equals(configuration())&&runtime.spent()<=config.get(VisitorConfig.VISITOR_CYCLE_MAX_COST);}
    private boolean targetValid(VisitorQueue.Entry entry,Entity target){return target!=null&&client.level!=null&&target.isAlive()
            &&client.level.getEntity(target.getId())==target&&target.getUUID().equals(entry.identity())
            &&LocationText.stripColorCodes(target.getName().getString()).trim().equals(entry.name());}
    @Override public void onTick(){
        if(pending){
            pending=false;
            if(!scopeValid()){lastError="Visitor context/configuration changed before lease takeover";setEnabled(false);return;}
            started=true;
            if(!runtime.start(queue,new VisitorCycleRuntime.Settings(enabledPolicy(),swap,config.get(VisitorConfig.VISITOR_LOADOUT_NAME),
                    config.get(VisitorConfig.VISITOR_CYCLE_MAX_COST),Math.multiplyExact(120_000L,queue.size()),config.get(VisitorConfig.VISITOR_AUTO_SELL_AFTER)),now())){lastError=runtime.status();setEnabled(false);return;}
        }else runtime.tick(enabledPolicy(),now());
        if(!runtime.active())setEnabled(false);
    }
    private VisitorCycleRuntime.Progress loadoutProgress(boolean restoration){
        if(loadouts.active())return VisitorCycleRuntime.Progress.ACTIVE;
        if(loadouts.state()==(restoration?NamedLoadoutFlow.State.RESTORED:NamedLoadoutFlow.State.APPLIED))return VisitorCycleRuntime.Progress.COMPLETE;
        return loadouts.state()==NamedLoadoutFlow.State.CANCELLED?VisitorCycleRuntime.Progress.CANCELLED:VisitorCycleRuntime.Progress.FAILED;
    }
    /** Context-bound fresh server receipt plus a current full actual-equipment re-read. */
    public boolean restoredLoadoutWitness(){
        if(!swap)return true;if(loadouts==null||loadoutPort==null||loadouts.state()!=NamedLoadoutFlow.State.RESTORED)return false;
        var receipt=loadouts.receipt();var original=loadouts.original();var current=loadoutPort.read();
        return receipt!=null&&original!=null&&receipt.restoring()&&receipt.name().equals(original.name())&&receipt.gear().equals(original.gear())
                &&receipt.player()==player&&receipt.world()==world&&receipt.connection()==connection&&current.player()==player&&current.world()==world&&current.connection()==connection
                &&current.snapshot()==null&&current.cursorEmpty()&&current.gear().equals(original.gear());
    }
    /** Original named gear and context-bound restoration receipt remain fresh during owned seller menus. */
    public boolean restoredLoadoutEquipmentWitness(){
        if(client.player==null||!client.player.isAlive()||originalFarmToolSlot<0||originalFarmToolSlot>8)return false;
        if(swap&&(loadouts==null||loadouts.state()!=NamedLoadoutFlow.State.RESTORED))return false;
        var gear=dev.veltraph.client.garden.loadout.mc.McNamedLoadoutPort.equipped(client.player);
        var current=loadoutPort==null?new NamedLoadoutFlow.Observation(client.player,client.level,client.getConnection(),client.player.containerMenu,null,gear,
                client.player.containerMenu.getCarried().isEmpty(),false,false):loadoutPort.read();
        boolean ownedMenu=client.gui.screen()==null||runtime.state()==VisitorCycleRuntime.State.AUTO_SELL&&lease.allowsPostQueueAutoSellScreen(this,client.gui.screen().getTitle().getString());
        var currentTool=dev.veltraph.client.garden.loadout.mc.McNamedLoadoutPort.fingerprint(client.player.getInventory().getItem(originalFarmToolSlot));
        return VisitorPostQueueWitness.equipment(current,player,world,connection,originalFarmGear,originalFarmTool,currentTool,
                configuration.equals(configuration())&&enabledPolicy()&&runtime.spent()<=config.get(VisitorConfig.VISITOR_CYCLE_MAX_COST),ownedMenu,swap,swap?loadouts.receipt():null,swap?loadouts.original():null);
    }
    private void cancelOwned(){
        if(cancelled)return;cancelled=true;
        try{transaction.setEnabled(false);}finally{try{if(swap&&loadouts!=null)loadouts.cancel(tick());}finally{try{lease.cancel(this);}finally{clearScope();}}}
    }
    @Override public void onDisable(){
        // Shared success handoff disables the helper before resuming farm input. Keep the fresh
        // restoration witness alive through its final post-cleanup revalidation; do not cancel it.
        if(lease.completing(this)){pending=false;transaction.setEnabled(false);return;}
        try{if(!started)cancelOwned();else runtime.cancel();}finally{clearScope();}
    }
    private void clearScope(){pending=false;player=world=connection=null;queue=List.of();targets=Map.of();configuration=List.of();originalFarmGear=Map.of();originalFarmTool=null;originalFarmToolSlot=-1;}
    @Override public boolean allowsScreen(String title){return transaction.isEnabled()&&transaction.allowsScreen(title)
            ||loadouts!=null&&loadouts.active()&&loadouts.titleAllowed(title)
            ||runtime.state()==VisitorCycleRuntime.State.AUTO_SELL&&lease.allowsPostQueueAutoSellScreen(this,title);}
    @Override public void onServerMessage(String line){if(transaction.isEnabled())transaction.onServerMessage(line);lease.message(line);}
    @Override public boolean consumeExpectedTeleport(Vec3 position,long now){return isEnabled()&&(transaction.isEnabled()&&transaction.consumeExpectedTeleport(position,now)||lease.consumeExpectedTeleport(position,now));}
    @Override public boolean hasPendingCleanup(){return !isEnabled()&&(swap&&loadouts!=null&&loadouts.cleanupActive()||lease.hasPostQueueCleanup(this));}
    @Override public void maintenanceTick(){
        if(isEnabled())return;
        if(swap&&loadouts!=null&&loadouts.cleanupActive())loadouts.tick(tick());
        if(lease.hasPostQueueCleanup(this))lease.maintenanceTickPostQueueCleanup(this);
    }
    /** Individually acknowledged outcomes survive a later cancellation; the Root owner deduplicates each outcome ID. */
    public List<dev.veltraph.client.inventory.selling.SellingAcknowledgment> acknowledgedPostQueueSales(){return lease.acknowledgedPostQueueSales(this);}
    public Optional<dev.veltraph.client.inventory.selling.BazaarCustodyModule.Acknowledgment> acknowledgedPostQueueBazaarSale(){return lease.acknowledgedPostQueueBazaarSale(this);}
    public VisitorCycleRuntime.State cycleState(){return runtime.state();}public int completedVisitors(){return runtime.completed();}public long coinsSpent(){return runtime.spent();}
    public List<VisitorQueue.Entry> scheduled(){return queue;}
    /** Exact helper settings to bind in the shared policy; no blanket farm/menu exemption. */
    public String maintenanceSettings(){return configuration().toString()+" | "+(config.get(VisitorConfig.VISITOR_LOADOUT_SWAP)&&loadoutSettings!=null?String.valueOf(loadoutSettings.get()):"no loadout swap");}
    private record GearBinding(Object player,Object world,Object connection,Map<String,NamedLoadoutFlow.Gear> gear){}
    /** Root registers this exact Garden-only policy and routes activation through startTransaction. */
    /** Actual policy-creation provenance; generic supplied settings closures are never accepted by OWNED_USE. */
    public final class GameplaySettings implements java.util.function.Supplier<String> {
        private dev.veltraph.client.rewarp.mc.FarmMaintenanceCoordinator.Policy issuedPolicy;
        private GameplaySettings(){}
        @Override public String get(){return maintenanceSettings();}
        private final class Snapshot {
            final GameplaySettings issued=GameplaySettings.this;final Object policy=issuedPolicy;final Object namedSource=McVisitorCycleModule.this.loadoutSettings;final boolean namedRequired=config.get(VisitorConfig.VISITOR_LOADOUT_SWAP);
            final dev.veltraph.client.config.MapConfigService.StoredStateSnapshot settings;
            private Snapshot(dev.veltraph.client.config.MapConfigService.StoredStateSnapshot settings){this.settings=settings;}
        }
        public Object snapshot(Object expectedHelper,dev.veltraph.client.config.ConfigService expectedStore,Object expectedPolicy){
            if(expectedHelper!=McVisitorCycleModule.this||expectedStore!=config||expectedPolicy==null||expectedPolicy!=issuedPolicy||!client.isSameThread())return null;
            var stored=config instanceof dev.veltraph.client.config.MapConfigService m?m.storedStateSnapshot():config instanceof dev.veltraph.client.config.JsonConfigService j?j.storedStateSnapshot():null;
            if(stored==null||config.get(VisitorConfig.VISITOR_LOADOUT_SWAP)&&!knownNamedSourceCurrent(loadoutSettings))return null;
            var actual=new Snapshot(stored);return snapshotCurrent(actual)?actual:null;
        }
        private boolean knownNamedSourceCurrent(Object source){return source instanceof dev.veltraph.client.VeltraphBootstrap.RootNamedLoadoutSettingsSource actual&&actual.affiliates(config);}
        /** Private known backing state only; never calls the settings/loadout supplier. */
        public boolean snapshotCurrent(Object exact){return exact instanceof McVisitorCycleModule.GameplaySettings.Snapshot s&&s.issued==this&&s.policy==issuedPolicy&&client.isSameThread()&&s.settings.current()&&s.namedSource==loadoutSettings&&(!s.namedRequired||knownNamedSourceCurrent(s.namedSource));}
    }
    public dev.veltraph.client.rewarp.mc.FarmMaintenanceCoordinator.Policy maintenancePolicy(){
        var issuedSettings=new GameplaySettings();var issuedPolicy=new dev.veltraph.client.rewarp.mc.FarmMaintenanceCoordinator.Policy(Set.of(GameArea.GARDEN),
                screen->(screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>
                        ||screen instanceof net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen)&&allowsScreen(screen.getTitle().getString()),
                issuedSettings,new dev.veltraph.client.rewarp.mc.FarmMaintenanceCoordinator.LoadoutProof(){
            public Object captureBinding(){return client.player==null||client.level==null||client.getConnection()==null?null:new GearBinding(client.player,client.level,client.getConnection(),
                    dev.veltraph.client.garden.loadout.mc.McNamedLoadoutPort.equipped(client.player));}
            public boolean restored(Object binding,Object witness){return binding instanceof GearBinding original&&witness instanceof BooleanSupplier proof
                    &&original.player()==client.player&&original.world()==client.level&&original.connection()==client.getConnection()
                    &&client.player!=null&&original.gear().equals(dev.veltraph.client.garden.loadout.mc.McNamedLoadoutPort.equipped(client.player))&&proof.getAsBoolean();}
        },600_000);
    issuedSettings.issuedPolicy=issuedPolicy;return issuedPolicy;}
    @Override public String diagnostics(){return (pending||!started?lastError:runtime.status())+" | "+lease.status()+(transaction.isEnabled()?" | "+transaction.diagnostics():"")
            +(loadouts!=null&&loadouts.active()?" | loadout "+loadouts.state()+": "+loadouts.reason():"");}
}
