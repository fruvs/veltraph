package dev.veltraph.client.garden.pests.mc;

import dev.veltraph.client.config.*;
import dev.veltraph.client.core.*;
import dev.veltraph.client.game.*;
import dev.veltraph.client.garden.loadout.*;
import dev.veltraph.client.garden.loadout.mc.McNamedLoadoutPort;
import dev.veltraph.client.garden.pests.*;
import dev.veltraph.client.garden.pests.tracker.PestTrackedBindings;
import dev.veltraph.client.rewarp.*;
import dev.veltraph.client.rewarp.mc.FarmMaintenanceCoordinator;
import dev.veltraph.client.service.FarmServices;
import dev.veltraph.client.world.Vec3;
import net.minecraft.client.Minecraft;
import java.util.*;
import java.util.function.*;

/** Autonomous source particle/plot workflow, retaining the exact same farm lease through return. */
public final class McPestTrackedMaintenanceModule extends AbstractModule implements FarmMaintenanceCoordinator.Participant {
    public static final String ID="garden_pest_tracker_cycle";
    private final Minecraft client;private final FarmServices services;private final ConfigService config;private final FarmMaintenanceCoordinator maintenance;
    private final NamedLoadoutFlow loadouts;private final NamedLoadoutFlow.Port loadoutPort;private final Supplier<NamedLoadoutFlow.Settings> loadoutSettings;
    private final McTrackedPestAction action;private final PestMaintenanceRuntime runtime;private PestTrackedBindings bindings;private FarmMaintenanceLease.Token token;
    private McNearbyPestAdmission.Admission nearby;
    private Object player,world,connection;private String frozen;private boolean pending,swap,cancelled;
    public McPestTrackedMaintenanceModule(Minecraft client,FarmServices services,ConfigService config,FarmMaintenanceCoordinator maintenance,AssignedPlotTravelPort travel,
                                         NamedLoadoutFlow loadouts,NamedLoadoutFlow.Port port,Supplier<NamedLoadoutFlow.Settings> loadoutSettings){
        super(ID,"Particle-guided multi-plot pests",ModuleCategory.INTERMEDIARY);this.client=client;this.services=services;this.config=config;this.maintenance=maintenance;
        this.loadouts=loadouts;this.loadoutPort=port;this.loadoutSettings=loadoutSettings;action=new McTrackedPestAction(client,services,config,travel,this::owned);
        runtime=new PestMaintenanceRuntime(new PestMaintenanceRuntime.Actions(){
            public boolean acquire(){token=maintenance.tokenFor(McPestTrackedMaintenanceModule.this);return token!=null&&owned();}
            public boolean valid(){return owned();}
            public boolean apply(String name){loadouts.start(name,loadoutSettings.get(),tick());return loadouts.active();}
            public PestMaintenanceRuntime.Progress loadout(boolean restore){loadouts.tick(tick());if(loadouts.active())return PestMaintenanceRuntime.Progress.ACTIVE;
                if(!restore&&(loadouts.original()==null||!loadouts.original().name().equalsIgnoreCase(dev.veltraph.client.gui.MenuSnapshot.clean(config.get(PestLoadoutConfig.PEST_FARMING_LOADOUT)))))return PestMaintenanceRuntime.Progress.FAILED;
                return loadouts.state()==(restore?NamedLoadoutFlow.State.RESTORED:NamedLoadoutFlow.State.APPLIED)?PestMaintenanceRuntime.Progress.COMPLETE:PestMaintenanceRuntime.Progress.FAILED;}
            public boolean clean(){return nearby==null?action.start(bindings):action.startNearby(bindings,nearby);}
            public PestMaintenanceRuntime.Progress cleaned(){action.tickAction();return action.active()?PestMaintenanceRuntime.Progress.ACTIVE:action.complete()?PestMaintenanceRuntime.Progress.COMPLETE:PestMaintenanceRuntime.Progress.FAILED;}
            public boolean restore(){loadouts.restore(tick());return loadouts.active();}
            public boolean beginReturn(){return maintenance.beginRestore(token)&&maintenance.beginReturn(token,(BooleanSupplier)McPestTrackedMaintenanceModule.this::restored);}
            public PestMaintenanceRuntime.Progress returned(){return switch(maintenance.tickReturn(token)){case ACTIVE->PestMaintenanceRuntime.Progress.ACTIVE;case READY->PestMaintenanceRuntime.Progress.COMPLETE;case FAILED->PestMaintenanceRuntime.Progress.FAILED;};}
            public boolean resume(){return maintenance.resume(token,(BooleanSupplier)McPestTrackedMaintenanceModule.this::restored);}
            public void cancel(){cancelOwned();}
        });
    }
    private long tick(){return services.clock().currentTick();}private long now(){return services.clock().nowMillis();}
    private String signature(){var values=new TreeMap<String,String>();try{for(var holder:List.of(PestConfig.class,PestLoadoutConfig.class,PestTrackerConfig.class))for(var field:holder.getFields())if(field.get(null) instanceof ConfigKey<?> key)values.put(key.name(),String.valueOf(config.get(key)));}
        catch(IllegalAccessException bad){throw new IllegalStateException(bad);}String gear="no loadout";if(config.get(PestLoadoutConfig.PEST_LOADOUT_SWAP)&&loadoutSettings!=null){try{gear=String.valueOf(loadoutSettings.get());}catch(RuntimeException invalid){gear="invalid "+invalid.getMessage();}}return values+" | "+gear;}
    private boolean contest(){if(!config.get(PestTrackerConfig.PEST_TRACKED_CONTEST_ONLY))return true;var location=new dev.veltraph.client.game.mc.McGameContextService(client);location.resolution();return GardenContestSignal.active(location.signals().sidebarRowsRaw().stream().map(LocationText::stripColorCodes).toList());}
    private boolean owned(){return isEnabled()&&client.player==player&&client.level==world&&client.getConnection()==connection&&client.player!=null&&client.player.isAlive()
        &&config.get(PestTrackerConfig.PEST_TRACKED_MAINTENANCE_ENABLED)&&Objects.equals(frozen,signature())&&contest()&&maintenance.valid(token);}
    /** Read-only intent; only the genuine Root nearby observer can construct it. */
    boolean prepareNearby(McNearbyPestAdmission.Admission intent){
        clearAbandonedNearby(); // Retire only an abandoned logical intent, never another owner's cleanup.
        if(isEnabled()||nearby!=null||intent==null||!intent.preflight(this))return false;nearby=intent;return true;}
    private void clearAbandonedNearby(){if(!isEnabled()&&nearby!=null&&nearby.attempted()&&maintenance.tokenFor(this)==null)nearby=null;}
    void discardNearby(McNearbyPestAdmission.Admission intent){if(!isEnabled()&&nearby==intent)nearby=null;}
    /** Roof permission requires a fresh native tool/contract check before any farm reservation. */
    @Override public Optional<String> preReservationReadiness(){
        clearAbandonedNearby();
        if(nearby!=null&&!nearby.preflight(this))return Optional.of("Nearby native target changed before reservation");
        if(!config.get(PestTrackerConfig.PESTS_USE_ROOF_ETHERWARPING))return Optional.empty();
        if(!config.get(PestTrackerConfig.PEST_TRACKED_MAINTENANCE_ENABLED))return Optional.of("Enable particle-guided pest maintenance explicitly");
        try{return action.ready(PestBindings.read(config.get(PestTrackerConfig.PEST_TRACKED_BINDINGS),PestTrackedBindings.class));}
        catch(RuntimeException invalid){return Optional.of("Invalid source tracker bindings: "+invalid.getMessage());}
    }
    @Override public Optional<String> preStartCheck(){clearAbandonedNearby();if(!config.get(PestTrackerConfig.PEST_TRACKED_MAINTENANCE_ENABLED))return Optional.of("Enable particle-guided pest maintenance explicitly");
        if(nearby!=null&&!nearby.preflight(this))return Optional.of("Nearby native target changed before activation");
        if(hasPendingCleanup())return Optional.of("Original named loadout cleanup remains active");if(config.get(PestTrackerConfig.PEST_TRACKED_TIMEOUT_MS)<1000||!contest())return Optional.of("Bounded time and configured source contest guard required");
        try{bindings=PestBindings.read(config.get(PestTrackerConfig.PEST_TRACKED_BINDINGS),PestTrackedBindings.class);var ready=action.ready(bindings);if(ready.isPresent())return ready;
            if(config.get(PestLoadoutConfig.PEST_LOADOUT_SWAP)){if(loadouts==null||loadoutPort==null||loadoutSettings==null||loadoutSettings.get()==null)return Optional.of("Complete named loadout contracts required");
                for(String name:List.of(config.get(PestLoadoutConfig.PEST_FARMING_LOADOUT),targetName()))if(loadoutSettings.get().profiles().stream().noneMatch(p->p.name().equalsIgnoreCase(dev.veltraph.client.gui.MenuSnapshot.clean(name))))return Optional.of("Exact configured farming and pest/killing gear profiles required");}
        }catch(RuntimeException invalid){return Optional.of("Invalid source tracker bindings: "+invalid.getMessage());}
        return maintenance.state()==FarmMaintenanceLease.State.PREPARED?Optional.empty():maintenance.startEligibility(this);
    }
    private String targetName(){return config.get(PestLoadoutConfig.PEST_LOADOUT_SWAP_KILLING)?config.get(PestLoadoutConfig.PEST_LOADOUT_SWAP_KILLING_LOADOUT):config.get(PestLoadoutConfig.PEST_PEST_LOADOUT);}
    /** Deferred until the concrete guarded transaction commits; no native packets or input changes. */
    @Override public void onEnable(){player=client.player;world=client.level;connection=client.getConnection();frozen=signature();swap=config.get(PestLoadoutConfig.PEST_LOADOUT_SWAP);pending=true;cancelled=false;token=null;}
    public void committed(FarmMaintenanceLease.Token committed){if(!isEnabled()||!pending||committed==null||maintenance.tokenFor(this)!=committed)throw new IllegalStateException("Tracker lease commit owner changed");token=committed;}
    @Override public void onTick(){if(pending){pending=false;runtime.start(new PestMaintenanceRuntime.Settings(swap,targetName(),config.get(PestTrackerConfig.PEST_TRACKED_TIMEOUT_MS)),now());}else runtime.tick(now());if(!runtime.active()&&isEnabled())setEnabled(false);}
    private record Binding(Object player,Object world,Object connection,Map<String,NamedLoadoutFlow.Gear> gear){}
    /** Actual policy-creation provenance; generic supplied settings closures are never accepted by OWNED_USE. */
    public final class GameplaySettings implements java.util.function.Supplier<String> {
        private dev.veltraph.client.rewarp.mc.FarmMaintenanceCoordinator.Policy issuedPolicy;
        private GameplaySettings(){}
        @Override public String get(){return signature();}
        private final class Snapshot {
            final GameplaySettings issued=GameplaySettings.this;final Object policy=issuedPolicy;final Object namedSource=McPestTrackedMaintenanceModule.this.loadoutSettings;final boolean namedRequired=config.get(PestLoadoutConfig.PEST_LOADOUT_SWAP);
            final dev.veltraph.client.config.MapConfigService.StoredStateSnapshot settings;
            private Snapshot(dev.veltraph.client.config.MapConfigService.StoredStateSnapshot settings){this.settings=settings;}
        }
        public Object snapshot(Object expectedHelper,dev.veltraph.client.config.ConfigService expectedStore,Object expectedPolicy){
            if(expectedHelper!=McPestTrackedMaintenanceModule.this||expectedStore!=config||expectedPolicy==null||expectedPolicy!=issuedPolicy||!client.isSameThread())return null;
            var stored=config instanceof dev.veltraph.client.config.MapConfigService m?m.storedStateSnapshot():config instanceof dev.veltraph.client.config.JsonConfigService j?j.storedStateSnapshot():null;
            if(stored==null||config.get(PestLoadoutConfig.PEST_LOADOUT_SWAP)&&!knownNamedSourceCurrent(loadoutSettings))return null;
            var actual=new Snapshot(stored);return snapshotCurrent(actual)?actual:null;
        }
        private boolean knownNamedSourceCurrent(Object source){return source instanceof dev.veltraph.client.VeltraphBootstrap.RootNamedLoadoutSettingsSource actual&&actual.affiliates(config);}
        /** Private known backing state only; never calls the settings/loadout supplier. */
        public boolean snapshotCurrent(Object exact){return exact instanceof McPestTrackedMaintenanceModule.GameplaySettings.Snapshot s&&s.issued==this&&s.policy==issuedPolicy&&client.isSameThread()&&s.settings.current()&&s.namedSource==loadoutSettings&&(!s.namedRequired||knownNamedSourceCurrent(s.namedSource));}
    }
    public FarmMaintenanceCoordinator.Policy maintenancePolicy(){var issuedSettings=new GameplaySettings();var issuedPolicy=new FarmMaintenanceCoordinator.Policy(Set.of(GameArea.GARDEN),screen->screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> &&allowsScreen(screen.getTitle().getString()),issuedSettings,new FarmMaintenanceCoordinator.LoadoutProof(){
        public Object captureBinding(){return client.player==null||client.level==null||client.getConnection()==null?null:new Binding(client.player,client.level,client.getConnection(),McNamedLoadoutPort.equipped(client.player));}
        public boolean restored(Object binding,Object witness){return binding instanceof Binding b&&witness instanceof BooleanSupplier proof&&client.player==b.player()&&client.level==b.world()&&client.getConnection()==b.connection()&&client.player!=null&&b.gear().equals(McNamedLoadoutPort.equipped(client.player))&&proof.getAsBoolean();}
    },config.get(PestTrackerConfig.PEST_TRACKED_TIMEOUT_MS));issuedSettings.issuedPolicy=issuedPolicy;return issuedPolicy;}
    private boolean restored(){if(!swap)return true;var receipt=loadouts.receipt();var original=loadouts.original();var current=loadoutPort.read();return loadouts.state()==NamedLoadoutFlow.State.RESTORED&&receipt!=null&&original!=null&&receipt.restoring()
        &&receipt.name().equals(original.name())&&receipt.gear().equals(original.gear())&&receipt.player()==player&&receipt.world()==world&&receipt.connection()==connection&&current.player()==player&&current.world()==world&&current.connection()==connection
        &&current.snapshot()==null&&current.cursorEmpty()&&current.gear().equals(original.gear());}
    private void cancelOwned(){if(cancelled)return;cancelled=true;action.cancel();if(swap&&loadouts!=null)loadouts.cancel(tick());if(token==null)token=maintenance.tokenFor(this);maintenance.cancel(token,"Source tracker workflow stopped; no farm resume");}
    @Override public void onDisable(){if(maintenance.isCompleting(this)){pending=false;action.cancel();nearby=null;return;}runtime.cancel();cancelOwned();pending=false;nearby=null;}
    @Override public boolean hasPendingCleanup(){return !isEnabled()&&swap&&loadouts!=null&&loadouts.cleanupActive();}
    @Override public void maintenanceTick(){if(hasPendingCleanup())loadouts.tick(tick());}
    @Override public boolean requiresSkyBlock(){return true;}@Override public Optional<GameArea> requiredArea(){return Optional.of(GameArea.GARDEN);}
    @Override public boolean allowsScreen(String title){return loadouts!=null&&loadouts.active()&&loadouts.titleAllowed(title);}
    @Override public void onServerMessage(String line){if(action.active())action.message(line);maintenance.onServerMessage(line);}
    @Override public boolean consumeExpectedTeleport(Vec3 position,long now){return isEnabled()&&(action.consumeExpectedTeleport(position,now)||maintenance.consumeExpectedTeleport(position,now));}
    public PestMaintenanceRuntime.State workflowState(){return runtime.state();}public int removed(){return action.removed();}public int trackerRequests(){return action.requests();}
    @Override public String diagnostics(){return runtime.status()+" | "+action.status()+" | "+maintenance.status();}
}
