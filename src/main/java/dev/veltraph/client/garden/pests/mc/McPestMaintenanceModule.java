package dev.veltraph.client.garden.pests.mc;

import dev.veltraph.client.config.*;
import dev.veltraph.client.core.*;
import dev.veltraph.client.game.*;
import dev.veltraph.client.garden.loadout.NamedLoadoutFlow;
import dev.veltraph.client.garden.loadout.mc.McNamedLoadoutPort;
import dev.veltraph.client.garden.pests.PestMaintenanceRuntime;
import dev.veltraph.client.garden.pests.PestBindings;
import dev.veltraph.client.garden.pests.PestBoxBindings;
import dev.veltraph.client.rewarp.FarmMaintenanceLease;
import dev.veltraph.client.rewarp.AssignedPlotTravelPort;
import dev.veltraph.client.rewarp.AssignedPlotTravelRuntime;
import dev.veltraph.client.rewarp.mc.FarmMaintenanceCoordinator;
import dev.veltraph.client.service.FarmServices;
import dev.veltraph.client.world.Vec3;
import net.minecraft.client.Minecraft;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** Complete current-plot vacuum batch with exact engine-preserving farm interruption and return. */
public final class McPestMaintenanceModule extends AbstractModule implements FarmMaintenanceCoordinator.Participant {
    public static final String ID="garden_pest_maintenance";
    private final Minecraft client;private final FarmServices services;private final ConfigService config;
    private final FarmMaintenanceCoordinator maintenance;private final NamedLoadoutFlow loadouts;private final NamedLoadoutFlow.Port loadoutPort;
    private final Supplier<NamedLoadoutFlow.Settings> settings;private final McPestVacuumModule cleaner;private final PestMaintenanceRuntime runtime;
    private final AssignedPlotTravelPort assignedTravel;private final McPestBoxAction box;private boolean boxMode,boxActive,boxRecovery;
    private AssignedPlotTravelPort.Target boxTarget;
    private FarmMaintenanceLease.Token token;private Object player,world,connection;private String frozen;private boolean pending,swap,cancelled;
    public McPestMaintenanceModule(Minecraft client,FarmServices services,ConfigService config,FarmMaintenanceCoordinator maintenance,
                                  NamedLoadoutFlow loadouts,NamedLoadoutFlow.Port port,Supplier<NamedLoadoutFlow.Settings> settings){
        this(client,services,config,maintenance,loadouts,port,settings,null);
    }
    public McPestMaintenanceModule(Minecraft client,FarmServices services,ConfigService config,FarmMaintenanceCoordinator maintenance,
                                  NamedLoadoutFlow loadouts,NamedLoadoutFlow.Port port,Supplier<NamedLoadoutFlow.Settings> settings,AssignedPlotTravelPort assignedTravel){
        super(ID,"Leased current-plot pest cleaning",ModuleCategory.INTERMEDIARY);this.client=client;this.services=services;this.config=config;
        this.maintenance=Objects.requireNonNull(maintenance);this.loadouts=loadouts;this.loadoutPort=port;this.settings=settings;cleaner=new McPestVacuumModule(client,services,config);
        this.assignedTravel=assignedTravel;box=assignedTravel==null?null:new McPestBoxAction(client,services,config,assignedTravel);
        runtime=new PestMaintenanceRuntime(new PestMaintenanceRuntime.Actions(){
            public boolean acquire(){token=McPestMaintenanceModule.this.maintenance.tokenFor(McPestMaintenanceModule.this);return token!=null&&valid();}
            public boolean valid(){return scopeValid()&&McPestMaintenanceModule.this.maintenance.valid(token);}
            public boolean apply(String name){loadouts.start(name,settings.get(),tick());return loadouts.active();}
            public PestMaintenanceRuntime.Progress loadout(boolean restore){loadouts.tick(tick());if(loadouts.active())return PestMaintenanceRuntime.Progress.ACTIVE;
                if(!restore&&(loadouts.original()==null||!loadouts.original().name().equalsIgnoreCase(dev.veltraph.client.gui.MenuSnapshot.clean(config.get(PestLoadoutConfig.PEST_FARMING_LOADOUT)))))return PestMaintenanceRuntime.Progress.FAILED;
                return loadouts.state()==(restore?NamedLoadoutFlow.State.RESTORED:NamedLoadoutFlow.State.APPLIED)?PestMaintenanceRuntime.Progress.COMPLETE:PestMaintenanceRuntime.Progress.FAILED;}
            public boolean clean(){if(boxMode){boxActive=box.start();return boxActive;}return startCleaner();}
            public PestMaintenanceRuntime.Progress cleaned(){return tickCleaning();}
            public boolean restore(){loadouts.restore(tick());return loadouts.active();}
            public boolean beginReturn(){return McPestMaintenanceModule.this.maintenance.beginRestore(token)&&McPestMaintenanceModule.this.maintenance.beginReturn(token,(BooleanSupplier)McPestMaintenanceModule.this::restored);}
            public PestMaintenanceRuntime.Progress returned(){return switch(McPestMaintenanceModule.this.maintenance.tickReturn(token)){
                case ACTIVE->PestMaintenanceRuntime.Progress.ACTIVE;case READY->PestMaintenanceRuntime.Progress.COMPLETE;case FAILED->PestMaintenanceRuntime.Progress.FAILED;};}
            public boolean resume(){return McPestMaintenanceModule.this.maintenance.resume(token,(BooleanSupplier)McPestMaintenanceModule.this::restored);}
            public void cancel(){cancelOwned();}
        });
    }
    private boolean startCleaner(){if(cleaner.preStartCheck().isPresent())return false;cleaner.setEnabled(true);return cleaner.isEnabled();}
    private PestMaintenanceRuntime.Progress tickCleaning(){
        if(boxActive){box.tick();if(box.active())return PestMaintenanceRuntime.Progress.ACTIVE;boxActive=false;if(!box.complete())return PestMaintenanceRuntime.Progress.FAILED;
            if(!box.hasPlotModels())return PestMaintenanceRuntime.Progress.COMPLETE;
            // Source nonempty plot continuation returns to the assigned spawn before regular search.
            if(!assignedTravel.start(boxTarget,GameArea.GARDEN))return PestMaintenanceRuntime.Progress.FAILED;boxRecovery=true;return PestMaintenanceRuntime.Progress.ACTIVE;}
        if(boxRecovery){var state=assignedTravel.tick(boxTarget,GameArea.GARDEN);if(state==AssignedPlotTravelRuntime.State.WAITING_ARRIVAL)return PestMaintenanceRuntime.Progress.ACTIVE;
            if(state!=AssignedPlotTravelRuntime.State.ARRIVED)return PestMaintenanceRuntime.Progress.FAILED;assignedTravel.cancel();boxRecovery=false;if(!startCleaner())return PestMaintenanceRuntime.Progress.FAILED;}
        if(cleaner.isEnabled())cleaner.onTick();return cleaner.isEnabled()?PestMaintenanceRuntime.Progress.ACTIVE:
            cleaner.workflowPhase()==GardenActionModule.Phase.COMPLETE?PestMaintenanceRuntime.Progress.COMPLETE:PestMaintenanceRuntime.Progress.FAILED;
    }
    private long tick(){return services.clock().currentTick();}private long now(){return services.clock().nowMillis();}
    private String configuration(){var keys=new TreeMap<String,String>();try{for(var holder:List.of(PestConfig.class,PestLoadoutConfig.class))for(var field:holder.getFields())
        if(field.get(null) instanceof ConfigKey<?> key)keys.put(key.name(),String.valueOf(config.get(key)));}catch(IllegalAccessException bad){throw new IllegalStateException(bad);}
        String binding="no loadout swap";if(config.get(PestLoadoutConfig.PEST_LOADOUT_SWAP)&&settings!=null){try{binding=String.valueOf(settings.get());}
            catch(RuntimeException invalid){binding="invalid named bindings: "+invalid.getClass().getName()+": "+invalid.getMessage();}}
        return keys+" | "+binding;}
    private boolean scopeValid(){return isEnabled()&&config.get(PestLoadoutConfig.PEST_MAINTENANCE_ENABLED)&&client.player==player&&client.level==world&&client.getConnection()==connection
        &&client.player!=null&&client.player.isAlive()&&Objects.equals(frozen,configuration());}
    private String targetName(){return config.get(PestLoadoutConfig.PEST_LOADOUT_SWAP_KILLING)?config.get(PestLoadoutConfig.PEST_LOADOUT_SWAP_KILLING_LOADOUT):config.get(PestLoadoutConfig.PEST_PEST_LOADOUT);}
    @Override public Optional<String> preStartCheck(){
        if(!config.get(PestLoadoutConfig.PEST_MAINTENANCE_ENABLED))return Optional.of("Enable current-plot farm maintenance explicitly");
        if(hasPendingCleanup())return Optional.of("Named original loadout cleanup remains active");
        if(config.get(PestLoadoutConfig.PEST_MAINTENANCE_TIMEOUT_MS)<1000)return Optional.of("Invalid bounded pest maintenance deadline");
        var common=config.get(PestLoadoutConfig.PESTS_USE_BOX_2)?box==null?Optional.of("Source Box2 requires coordinated assigned-plot travel"):box.ready():cleaner.preStartCheck();if(common.isPresent())return common;
        try{if(config.get(PestLoadoutConfig.PEST_LOADOUT_SWAP)){
            if(loadouts==null||loadoutPort==null||settings==null||settings.get()==null)return Optional.of("Exact named menu and complete gear profiles required");
            var profiles=settings.get().profiles();for(String wanted:List.of(config.get(PestLoadoutConfig.PEST_FARMING_LOADOUT),targetName()))
                if(profiles.stream().noneMatch(p->p.name().equalsIgnoreCase(dev.veltraph.client.gui.MenuSnapshot.clean(wanted))))return Optional.of("Configured farming and pest/killing names must have exact complete gear profiles");}}
        catch(RuntimeException bad){return Optional.of("Invalid named loadout bindings: "+bad.getMessage());}
        return maintenance.state()==FarmMaintenanceLease.State.PREPARED?Optional.empty():maintenance.startEligibility(this);
    }
    /** Only captures identity/settings; root must commit guarded startTransaction before first tick. */
    @Override public void onEnable(){player=client.player;world=client.level;connection=client.getConnection();frozen=configuration();swap=config.get(PestLoadoutConfig.PEST_LOADOUT_SWAP);pending=true;cancelled=false;token=null;
        boxMode=config.get(PestLoadoutConfig.PESTS_USE_BOX_2);boxActive=boxRecovery=false;boxTarget=null;
        if(boxMode){var selected=PestBindings.read(config.get(PestLoadoutConfig.PEST_BOX_BINDINGS),PestBoxBindings.class).selected();boxTarget=new AssignedPlotTravelPort.Target(selected.plotId(),selected.spawn(),selected.yaw(),selected.pitch());}}
    /** Optional central registration callback; ownership is still re-read on the first action tick. */
    public void committed(FarmMaintenanceLease.Token committed){if(!isEnabled()||!pending||committed==null||maintenance.tokenFor(this)!=committed)throw new IllegalStateException("Pest maintenance commit owner changed");token=committed;}
    @Override public void onTick(){
        if(pending){pending=false;runtime.start(new PestMaintenanceRuntime.Settings(swap,targetName(),config.get(PestLoadoutConfig.PEST_MAINTENANCE_TIMEOUT_MS)),now());}
        else runtime.tick(now());if(!runtime.active()&&isEnabled())setEnabled(false);
    }
    public boolean restored(){if(!swap)return true;var receipt=loadouts.receipt();var original=loadouts.original();var current=loadoutPort.read();
        return loadouts.state()==NamedLoadoutFlow.State.RESTORED&&receipt!=null&&original!=null&&receipt.restoring()&&receipt.name().equals(original.name())&&receipt.gear().equals(original.gear())
            &&receipt.player()==player&&receipt.world()==world&&receipt.connection()==connection&&current.player()==player&&current.world()==world&&current.connection()==connection
            &&current.snapshot()==null&&current.cursorEmpty()&&current.gear().equals(original.gear());}
    private record Binding(Object player,Object world,Object connection,Map<String,NamedLoadoutFlow.Gear> gear){}
    /** Actual policy-creation provenance; generic supplied settings closures are never accepted by OWNED_USE. */
    public final class GameplaySettings implements java.util.function.Supplier<String> {
        private dev.veltraph.client.rewarp.mc.FarmMaintenanceCoordinator.Policy issuedPolicy;
        private GameplaySettings(){}
        @Override public String get(){return configuration();}
        private final class Snapshot {
            final GameplaySettings issued=GameplaySettings.this;final Object policy=issuedPolicy;final Object namedSource=McPestMaintenanceModule.this.settings;final boolean namedRequired=config.get(PestLoadoutConfig.PEST_LOADOUT_SWAP);
            final dev.veltraph.client.config.MapConfigService.StoredStateSnapshot settings;
            private Snapshot(dev.veltraph.client.config.MapConfigService.StoredStateSnapshot settings){this.settings=settings;}
        }
        public Object snapshot(Object expectedHelper,dev.veltraph.client.config.ConfigService expectedStore,Object expectedPolicy){
            if(expectedHelper!=McPestMaintenanceModule.this||expectedStore!=config||expectedPolicy==null||expectedPolicy!=issuedPolicy||!client.isSameThread())return null;
            var stored=config instanceof dev.veltraph.client.config.MapConfigService m?m.storedStateSnapshot():config instanceof dev.veltraph.client.config.JsonConfigService j?j.storedStateSnapshot():null;
            if(stored==null||config.get(PestLoadoutConfig.PEST_LOADOUT_SWAP)&&!knownNamedSourceCurrent(settings))return null;
            var actual=new Snapshot(stored);return snapshotCurrent(actual)?actual:null;
        }
        private boolean knownNamedSourceCurrent(Object source){return source instanceof dev.veltraph.client.VeltraphBootstrap.RootNamedLoadoutSettingsSource actual&&actual.affiliates(config);}
        /** Private known backing state only; never calls the settings/loadout supplier. */
        public boolean snapshotCurrent(Object exact){return exact instanceof McPestMaintenanceModule.GameplaySettings.Snapshot s&&s.issued==this&&s.policy==issuedPolicy&&client.isSameThread()&&s.settings.current()&&s.namedSource==settings&&(!s.namedRequired||knownNamedSourceCurrent(s.namedSource));}
    }
    public FarmMaintenanceCoordinator.Policy maintenancePolicy(){var issuedSettings=new GameplaySettings();var issuedPolicy=new FarmMaintenanceCoordinator.Policy(Set.of(GameArea.GARDEN),
        screen->screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> &&allowsScreen(screen.getTitle().getString()),issuedSettings,new FarmMaintenanceCoordinator.LoadoutProof(){
            public Object captureBinding(){return client.player==null||client.level==null||client.getConnection()==null?null:new Binding(client.player,client.level,client.getConnection(),McNamedLoadoutPort.equipped(client.player));}
            public boolean restored(Object binding,Object witness){return binding instanceof Binding b&&witness instanceof BooleanSupplier proof&&b.player()==client.player&&b.world()==client.level&&b.connection()==client.getConnection()
                &&client.player!=null&&b.gear().equals(McNamedLoadoutPort.equipped(client.player))&&proof.getAsBoolean();}
        },config.get(PestLoadoutConfig.PEST_MAINTENANCE_TIMEOUT_MS));issuedSettings.issuedPolicy=issuedPolicy;return issuedPolicy;}
    private void cancelOwned(){if(cancelled)return;cancelled=true;cleaner.setEnabled(false);if(box!=null)box.cancel();if(boxRecovery)assignedTravel.cancel();if(swap&&loadouts!=null)loadouts.cancel(tick());
        if(token==null)token=maintenance.tokenFor(this);maintenance.cancel(token,"Pest maintenance stopped; no farm resume");}
    @Override public void onDisable(){if(maintenance.isCompleting(this)){pending=false;cleaner.setEnabled(false);return;}runtime.cancel();cancelOwned();pending=false;}
    @Override public boolean hasPendingCleanup(){return !isEnabled()&&swap&&loadouts!=null&&loadouts.cleanupActive();}
    @Override public void maintenanceTick(){if(hasPendingCleanup())loadouts.tick(tick());}
    @Override public boolean requiresSkyBlock(){return true;}
    @Override public Optional<GameArea> requiredArea(){return Optional.of(GameArea.GARDEN);}
    @Override public boolean allowsScreen(String title){return loadouts!=null&&loadouts.active()&&loadouts.titleAllowed(title);}
    @Override public void onServerMessage(String line){if(cleaner.isEnabled())cleaner.onServerMessage(line);if(boxActive)box.message(line);if(boxRecovery)assignedTravel.onServerMessage(line);maintenance.onServerMessage(line);}
    @Override public boolean consumeExpectedTeleport(Vec3 position,long now){return isEnabled()&&(boxActive&&box.consumeExpectedTeleport(position,now)||boxRecovery&&assignedTravel.consumeExpectedTeleport(position,now)||maintenance.consumeExpectedTeleport(position,now));}
    public PestMaintenanceRuntime.State workflowState(){return runtime.state();}
    @Override public String diagnostics(){return runtime.status()+" | "+maintenance.status()+" | "+cleaner.diagnostics()+(boxMode&&box!=null?" | Box "+box.status():"");}
}
