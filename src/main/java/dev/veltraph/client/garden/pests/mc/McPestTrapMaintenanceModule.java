package dev.veltraph.client.garden.pests.mc;

import dev.veltraph.client.config.*;
import dev.veltraph.client.core.*;
import dev.veltraph.client.game.GameArea;
import dev.veltraph.client.garden.jacob.JacobContestTracker;
import dev.veltraph.client.garden.loadout.NamedLoadoutFlow;
import dev.veltraph.client.garden.loadout.mc.McNamedLoadoutPort;
import dev.veltraph.client.garden.pests.*;
import dev.veltraph.client.rewarp.*;
import dev.veltraph.client.rewarp.mc.FarmMaintenanceCoordinator;
import dev.veltraph.client.service.FarmServices;
import dev.veltraph.client.world.Vec3;
import net.minecraft.client.Minecraft;
import java.util.*;
import java.util.function.*;

/** Source contest-triggered configured trap transaction and exact same-farm continuation. */
public final class McPestTrapMaintenanceModule extends AbstractModule implements FarmMaintenanceCoordinator.Participant {
    public static final String ID="garden_pest_trap_cycle";
    public record Receipt(UUID transaction,Object player,Object world,Object connection,String profile,int plot,
                          PestTrapReadiness.Observation before,PestTrapReadiness.Observation after,
                          int releasedPests,int placedBait,boolean cursorEmpty) {}
    private final Minecraft client;private final FarmServices services;private final ConfigService config;
    private final FarmMaintenanceCoordinator maintenance;private final Supplier<List<String>> actualTabRows;
    private final McPestTrapModule child;private final PestMaintenanceRuntime runtime;
    private FarmMaintenanceLease.Token token;private JacobContestTracker.Scope startedScope,armedScope;
    private JacobContestTracker armedTracker,preparedTracker;private PreparedTrapActivation preparedActivation;private PestTrapReadiness.Observation preparedBefore;
    private Object player,world,connection;private String frozen;private boolean pending,cancelled,armed;
    private PestTrapReadiness.Observation before;private Receipt receipt;private UUID transaction;
    public McPestTrapMaintenanceModule(Minecraft client,FarmServices services,ConfigService config,
                                      FarmMaintenanceCoordinator maintenance,Supplier<List<String>> actualTabRows){
        super(ID,"Contest-triggered trap maintenance",ModuleCategory.INTERMEDIARY);
        this.client=client;this.services=services;this.config=config;this.maintenance=Objects.requireNonNull(maintenance);
        this.actualTabRows=Objects.requireNonNull(actualTabRows);child=new McPestTrapModule(client,services,config);
        runtime=new PestMaintenanceRuntime(new PestMaintenanceRuntime.Actions(){
            public boolean acquire(){token=maintenance.tokenFor(McPestTrapMaintenanceModule.this);return token!=null&&owned();}
            public boolean valid(){return owned();}
            public boolean apply(String name){return false;}public PestMaintenanceRuntime.Progress loadout(boolean restoring){return PestMaintenanceRuntime.Progress.FAILED;}public boolean restore(){return false;}
            public boolean clean(){if(child.preStartCheck().isPresent())return false;child.setEnabled(true);return child.isEnabled();}
            public PestMaintenanceRuntime.Progress cleaned(){
                if(child.isEnabled())child.onTick();
                if(child.isEnabled())return PestMaintenanceRuntime.Progress.ACTIVE;
                if(child.workflowPhase()!=GardenActionModule.Phase.COMPLETE||child.releasedPests()<1)return PestMaintenanceRuntime.Progress.FAILED;
                var after=readReady();
                // One configured loaded trap was released; fresh native ready markers must acknowledge it.
                if(!after.known()||after.readyTraps()!=before.readyTraps()-1)return PestMaintenanceRuntime.Progress.ACTIVE;
                if(!inventoryClosed())return PestMaintenanceRuntime.Progress.FAILED;
                receipt=new Receipt(transaction,player,world,connection,startedScope.profile(),config.get(PestConfig.PEST_ACTION_PLOT),before,after,child.releasedPests(),child.placedBait(),true);
                return PestMaintenanceRuntime.Progress.COMPLETE;
            }
            public boolean beginReturn(){return maintenance.beginRestore(token)&&maintenance.beginReturn(token,(BooleanSupplier)McPestTrapMaintenanceModule.this::restored);}
            public PestMaintenanceRuntime.Progress returned(){return switch(maintenance.tickReturn(token)){case ACTIVE->PestMaintenanceRuntime.Progress.ACTIVE;case READY->PestMaintenanceRuntime.Progress.COMPLETE;case FAILED->PestMaintenanceRuntime.Progress.FAILED;};}
            public boolean resume(){return maintenance.resume(token,(BooleanSupplier)McPestTrapMaintenanceModule.this::restored);}
            public void cancel(){cancelOwned();}
        });
    }
    private PestTrapReadiness.Observation readReady(){return PestTrapReadiness.read(actualTabRows.get(),config.get(PestTrapMaintenanceConfig.PEST_TRAP_READY_TAB_PREFIX));}
    private String signature(){var values=new TreeMap<String,String>();try{for(var holder:List.of(PestConfig.class,PestTrapMaintenanceConfig.class))for(var field:holder.getFields())if(field.get(null) instanceof ConfigKey<?> key)values.put(key.name(),String.valueOf(config.get(key)));}
        catch(IllegalAccessException bad){throw new IllegalStateException(bad);}return values.toString();}
    private boolean sameNative(JacobContestTracker.Scope scope){return scope!=null&&client.player==scope.player()&&client.level==scope.world()&&client.getConnection()==scope.connection();}
    private Optional<String> nativeReady(){
        if(!config.get(PestTrapMaintenanceConfig.PEST_TRAP_MAINTENANCE_ENABLED)||!config.get(PestConfig.COLLECT_PEST_TRAPS)||!config.get(PestTrapMaintenanceConfig.PEST_TRAP_CONTEST_ONLY))return Optional.of("Enable collection and explicit contest-only trap maintenance");
        if(config.get(PestTrapMaintenanceConfig.PEST_TRAP_MAINTENANCE_TIMEOUT_MS)<1000)return Optional.of("Bounded trap maintenance deadline required");
        if(config.get(PestTrapMaintenanceConfig.PEST_TRAP_READY_TAB_PREFIX).isBlank()||config.get(PestTrapMaintenanceConfig.PEST_TRAP_CONTEST_CUTOFF_SECONDS)<0)return Optional.of("Exact observed ready-trap tab prefix and explicit contest cutoff required");
        var childReady=child.preStartCheck();if(childReady.isPresent())return childReady;
        return maintenance.state()==FarmMaintenanceLease.State.PREPARED?Optional.empty():maintenance.startEligibility(this);
    }
    /** Root preview uses actual tab data and its source-sampled tracker; this never consumes a latch. */
    public boolean readyForContest(JacobContestTracker tracker,JacobContestTracker.Scope scope,long tick,List<String> freshTabRows){
        return !isEnabled()&&maintenance.tokenFor(this)==null&&sameNative(scope)&&nativeReady().isEmpty()&&PestTrapContestDispatch.ready(tracker,scope,tick,config.get(PestConfig.COLLECT_PEST_TRAPS),config.get(PestTrapMaintenanceConfig.PEST_TRAP_CONTEST_ONLY),freshTabRows,config.get(PestTrapMaintenanceConfig.PEST_TRAP_READY_TAB_PREFIX),config.get(PestTrapMaintenanceConfig.PEST_TRAP_CONTEST_CUTOFF_SECONDS));
    }
    /** Calls Root's actual guarded start once, after consuming the source sampled-record window. */
    public boolean startForContest(JacobContestTracker tracker,JacobContestTracker.Scope scope,long tick,List<String> freshTabRows,BooleanSupplier guardedActivation){
        if(!readyForContest(tracker,scope,tick,freshTabRows))return false;
        return PestTrapContestDispatch.start(tracker,scope,tick,true,true,freshTabRows,config.get(PestTrapMaintenanceConfig.PEST_TRAP_READY_TAB_PREFIX),config.get(PestTrapMaintenanceConfig.PEST_TRAP_CONTEST_CUTOFF_SECONDS),()->sameNative(scope)&&nativeReady().isEmpty(),()->{
            armed=true;armedScope=scope;armedTracker=tracker;before=PestTrapReadiness.read(freshTabRows,config.get(PestTrapMaintenanceConfig.PEST_TRAP_READY_TAB_PREFIX));
            try{return guardedActivation.getAsBoolean();}finally{armed=false;armedScope=null;armedTracker=null;}
        });
    }
    @Override public boolean prepareActivation(FarmMaintenanceLease.Token prepared){
        if(!armed||armedScope==null||armedTracker==null||!armedTracker.trapConsumed()||preparedActivation!=null
                ||maintenance.tokenFor(this)!=prepared||!maintenance.preparedActivationValid(this,prepared))return false;
        var window=armedTracker.observation(armedScope,services.clock().currentTick());if(!window.clockKnown())return false;
        preparedActivation=new PreparedTrapActivation(prepared,armedScope,window);preparedTracker=armedTracker;preparedBefore=before;return true;
    }
    @Override public boolean activatePrepared(FarmMaintenanceLease.Token prepared,BooleanSupplier activation){
        var authorization=preparedActivation;var tracker=preparedTracker;
        if(authorization==null||tracker==null)return false;var scope=authorization.scope();
        boolean valid=authorization.consume(prepared,scope,tracker.observation(scope,services.clock().currentTick()),
                sameNative(scope)&&tracker.trapConsumed()&&maintenance.preparedActivationValid(this,prepared));
        if(!valid){cancelPrepared(prepared);return false;}
        armed=true;armedScope=scope;before=preparedBefore;
        try{return activation.getAsBoolean();}finally{armed=false;armedScope=null;preparedActivation=null;preparedTracker=null;preparedBefore=null;}
    }
    @Override public void cancelPrepared(FarmMaintenanceLease.Token prepared){
        if(preparedActivation!=null&&preparedActivation.boundTo(prepared)){preparedActivation.cancel(prepared);preparedActivation=null;preparedTracker=null;preparedBefore=null;}
    }
    @Override public Optional<String> preStartCheck(){return !armed||!sameNative(armedScope)?Optional.of("A fresh source contest window must authorize trap maintenance"):nativeReady();}
    /** Capture only: no input release, inventory action, interaction or movement before lease commit. */
    @Override public void onEnable(){player=client.player;world=client.level;connection=client.getConnection();startedScope=armedScope;frozen=signature();transaction=UUID.randomUUID();receipt=null;token=null;pending=true;cancelled=false;}
    public void committed(FarmMaintenanceLease.Token committed){if(!isEnabled()||!pending||committed==null||maintenance.tokenFor(this)!=committed)throw new IllegalStateException("Trap maintenance commit owner changed");token=committed;}
    private boolean owned(){return isEnabled()&&sameNative(startedScope)&&client.player==player&&client.level==world&&client.getConnection()==connection&&client.player!=null&&client.player.isAlive()&&Objects.equals(frozen,signature())&&maintenance.valid(token);}
    private boolean inventoryClosed(){return client.player!=null&&client.gui.screen()==null&&client.player.containerMenu==client.player.inventoryMenu&&client.player.containerMenu.getCarried().isEmpty();}
    private boolean restored(){return receipt!=null&&receipt.transaction().equals(transaction)&&receipt.player()==player&&receipt.world()==world&&receipt.connection()==connection&&child.workflowPhase()==GardenActionModule.Phase.COMPLETE&&receipt.releasedPests()>0&&receipt.cursorEmpty()&&inventoryClosed();}
    @Override public void onTick(){if(pending){pending=false;runtime.start(new PestMaintenanceRuntime.Settings(false,"",config.get(PestTrapMaintenanceConfig.PEST_TRAP_MAINTENANCE_TIMEOUT_MS)),services.clock().nowMillis());}else runtime.tick(services.clock().nowMillis());if(!runtime.active()&&isEnabled())setEnabled(false);}
    private record Binding(Object player,Object world,Object connection,Map<String,NamedLoadoutFlow.Gear> gear){}
    /** Actual policy-creation provenance; generic supplied settings closures are never accepted by OWNED_USE. */
    public final class GameplaySettings implements java.util.function.Supplier<String> {
        private dev.veltraph.client.rewarp.mc.FarmMaintenanceCoordinator.Policy issuedPolicy;
        private GameplaySettings(){}
        @Override public String get(){return signature();}
        private final class Snapshot {
            final GameplaySettings issued=GameplaySettings.this;final Object policy=issuedPolicy;
            final dev.veltraph.client.config.MapConfigService.StoredStateSnapshot settings;
            private Snapshot(dev.veltraph.client.config.MapConfigService.StoredStateSnapshot settings){this.settings=settings;}
        }
        public Object snapshot(Object expectedHelper,dev.veltraph.client.config.ConfigService expectedStore,Object expectedPolicy){
            if(expectedHelper!=McPestTrapMaintenanceModule.this||expectedStore!=config||expectedPolicy==null||expectedPolicy!=issuedPolicy||!client.isSameThread())return null;
            var stored=config instanceof dev.veltraph.client.config.MapConfigService m?m.storedStateSnapshot():config instanceof dev.veltraph.client.config.JsonConfigService j?j.storedStateSnapshot():null;
            if(stored==null)return null;
            var actual=new Snapshot(stored);return snapshotCurrent(actual)?actual:null;
        }
        /** Private known backing state only; never calls the settings/loadout supplier. */
        public boolean snapshotCurrent(Object exact){return exact instanceof McPestTrapMaintenanceModule.GameplaySettings.Snapshot s&&s.issued==this&&s.policy==issuedPolicy&&client.isSameThread()&&s.settings.current();}
    }
    public FarmMaintenanceCoordinator.Policy maintenancePolicy(){var issuedSettings=new GameplaySettings();var issuedPolicy=new FarmMaintenanceCoordinator.Policy(Set.of(GameArea.GARDEN),screen->screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> &&allowsScreen(screen.getTitle().getString()),issuedSettings,new FarmMaintenanceCoordinator.LoadoutProof(){
        public Object captureBinding(){return client.player==null||client.level==null||client.getConnection()==null?null:new Binding(client.player,client.level,client.getConnection(),McNamedLoadoutPort.equipped(client.player));}
        public boolean restored(Object original,Object witness){return original instanceof Binding b&&witness instanceof BooleanSupplier proof&&client.player==b.player()&&client.level==b.world()&&client.getConnection()==b.connection()&&client.player!=null&&b.gear().equals(McNamedLoadoutPort.equipped(client.player))&&proof.getAsBoolean();}
    },config.get(PestTrapMaintenanceConfig.PEST_TRAP_MAINTENANCE_TIMEOUT_MS));issuedSettings.issuedPolicy=issuedPolicy;return issuedPolicy;}
    private void cancelOwned(){if(cancelled)return;cancelled=true;child.setEnabled(false);if(token==null)token=maintenance.tokenFor(this);maintenance.cancel(token,"Trap maintenance stopped; no farm resume");}
    @Override public void onDisable(){if(maintenance.isCompleting(this)){pending=false;child.setEnabled(false);return;}runtime.cancel();cancelOwned();pending=false;}
    @Override public void onServerMessage(String message){if(child.isEnabled())child.onServerMessage(message);maintenance.onServerMessage(message);}
    @Override public boolean allowsScreen(String title){return child.allowsScreen(title);}
    @Override public boolean consumeExpectedTeleport(Vec3 position,long now){return isEnabled()&&(child.isEnabled()&&child.consumeExpectedTeleport(position,now)||maintenance.consumeExpectedTeleport(position,now));}
    @Override public boolean requiresSkyBlock(){return true;}@Override public Optional<GameArea> requiredArea(){return Optional.of(GameArea.GARDEN);}
    public PestMaintenanceRuntime.State workflowState(){return runtime.state();}public Optional<Receipt> receipt(){return Optional.ofNullable(receipt);}
    @Override public String diagnostics(){return runtime.status()+" | "+child.diagnostics()+" | "+maintenance.status();}
}
