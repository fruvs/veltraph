package dev.veltraph.client.garden.pests.mc;

import dev.veltraph.client.config.*;
import dev.veltraph.client.core.*;
import dev.veltraph.client.game.*;
import dev.veltraph.client.garden.loadout.NamedLoadoutFlow;
import dev.veltraph.client.garden.loadout.mc.McNamedLoadoutPort;
import dev.veltraph.client.garden.pests.PestMaintenanceRuntime;
import dev.veltraph.client.rewarp.*;
import dev.veltraph.client.rewarp.mc.FarmMaintenanceCoordinator;
import dev.veltraph.client.service.FarmServices;
import dev.veltraph.client.world.Vec3;
import net.minecraft.client.Minecraft;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Current Philip transaction under the same exact farm lease, including protected return/resume. */
public final class McPestPhilipMaintenanceModule extends AbstractModule implements FarmMaintenanceCoordinator.Participant {
    public static final String ID="garden_pest_philip_cycle";
    private final Minecraft client;private final FarmServices services;private final ConfigService config;private final FarmMaintenanceCoordinator maintenance;
    private final McPestPhilipModule child;private final PestMaintenanceRuntime runtime;private FarmMaintenanceLease.Token token;
    private Object player,world,connection;private String frozen;private boolean pending,cancelled;
    public McPestPhilipMaintenanceModule(Minecraft client,FarmServices services,ConfigService config,FarmMaintenanceCoordinator maintenance){super(ID,"Leased Philip pest-bag exchange",ModuleCategory.INTERMEDIARY);this.client=client;this.services=services;this.config=config;this.maintenance=Objects.requireNonNull(maintenance);
        child=new McPestPhilipModule(client,services,config,this::valid);
        runtime=new PestMaintenanceRuntime(new PestMaintenanceRuntime.Actions(){
            public boolean acquire(){token=maintenance.tokenFor(McPestPhilipMaintenanceModule.this);return token!=null&&valid();}
            public boolean valid(){return McPestPhilipMaintenanceModule.this.valid();}
            public boolean apply(String name){return false;}public PestMaintenanceRuntime.Progress loadout(boolean restore){return PestMaintenanceRuntime.Progress.FAILED;}public boolean restore(){return false;}
            public boolean clean(){if(child.preStartCheck().isPresent())return false;child.setEnabled(true);return child.isEnabled();}
            public PestMaintenanceRuntime.Progress cleaned(){if(child.isEnabled())child.onTick();return child.isEnabled()?PestMaintenanceRuntime.Progress.ACTIVE:child.workflowState()==McPestPhilipModule.State.COMPLETE?PestMaintenanceRuntime.Progress.COMPLETE:PestMaintenanceRuntime.Progress.FAILED;}
            public boolean beginReturn(){return maintenance.beginRestore(token)&&maintenance.beginReturn(token,(BooleanSupplier)McPestPhilipMaintenanceModule.this::restored);}
            public PestMaintenanceRuntime.Progress returned(){return switch(maintenance.tickReturn(token)){case ACTIVE->PestMaintenanceRuntime.Progress.ACTIVE;case READY->PestMaintenanceRuntime.Progress.COMPLETE;case FAILED->PestMaintenanceRuntime.Progress.FAILED;};}
            public boolean resume(){return maintenance.resume(token,(BooleanSupplier)McPestPhilipMaintenanceModule.this::restored);}
            public void cancel(){cancelOwned();}
        });}
    private String signature(){return config.get(PestPhilipConfig.PEST_PHILIP_ENABLED)+"|"+config.get(PestPhilipConfig.PEST_PHILIP_BINDINGS)+"|"+config.get(PestPhilipConfig.PEST_PHILIP_MAINTENANCE_ENABLED)+"|"+config.get(PestPhilipConfig.PEST_PHILIP_MAINTENANCE_TIMEOUT_MS);}
    private boolean valid(){return isEnabled()&&config.get(PestPhilipConfig.PEST_PHILIP_MAINTENANCE_ENABLED)&&Objects.equals(signature(),frozen)&&client.player==player&&client.level==world&&client.getConnection()==connection&&client.player!=null&&client.player.isAlive()&&maintenance.valid(token);}
    @Override public Optional<String> preStartCheck(){if(!config.get(PestPhilipConfig.PEST_PHILIP_MAINTENANCE_ENABLED))return Optional.of("Enable leased Philip exchange explicitly");if(child.hasPendingCleanup())return Optional.of("Original Philip call cleanup remains active");
        if(config.get(PestPhilipConfig.PEST_PHILIP_MAINTENANCE_TIMEOUT_MS)<1000)return Optional.of("Bounded Philip maintenance deadline required");var ready=child.preStartCheck();if(ready.isPresent())return ready;
        return maintenance.state()==FarmMaintenanceLease.State.PREPARED?Optional.empty():maintenance.startEligibility(this);}
    /** Captures only; native child work starts after Root commits the exact token. */
    @Override public void onEnable(){player=client.player;world=client.level;connection=client.getConnection();frozen=signature();token=null;pending=true;cancelled=false;}
    public void committed(FarmMaintenanceLease.Token committed){if(!isEnabled()||!pending||committed==null||maintenance.tokenFor(this)!=committed)throw new IllegalStateException("Philip maintenance commit owner changed");token=committed;}
    @Override public void onTick(){if(pending){pending=false;runtime.start(new PestMaintenanceRuntime.Settings(false,"",config.get(PestPhilipConfig.PEST_PHILIP_MAINTENANCE_TIMEOUT_MS)),services.clock().nowMillis());}else runtime.tick(services.clock().nowMillis());if(!runtime.active()&&isEnabled())setEnabled(false);}
    private boolean restored(){return child.workflowState()==McPestPhilipModule.State.COMPLETE&&!child.hasPendingCleanup()&&child.exchanged()>0&&client.player==player&&client.level==world&&client.getConnection()==connection&&client.player!=null&&client.gui.screen()==null&&client.player.containerMenu==client.player.inventoryMenu&&client.player.containerMenu.getCarried().isEmpty();}
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
            if(expectedHelper!=McPestPhilipMaintenanceModule.this||expectedStore!=config||expectedPolicy==null||expectedPolicy!=issuedPolicy||!client.isSameThread())return null;
            var stored=config instanceof dev.veltraph.client.config.MapConfigService m?m.storedStateSnapshot():config instanceof dev.veltraph.client.config.JsonConfigService j?j.storedStateSnapshot():null;
            if(stored==null)return null;
            var actual=new Snapshot(stored);return snapshotCurrent(actual)?actual:null;
        }
        /** Private known backing state only; never calls the settings/loadout supplier. */
        public boolean snapshotCurrent(Object exact){return exact instanceof McPestPhilipMaintenanceModule.GameplaySettings.Snapshot s&&s.issued==this&&s.policy==issuedPolicy&&client.isSameThread()&&s.settings.current();}
    }
    public FarmMaintenanceCoordinator.Policy maintenancePolicy(){var issuedSettings=new GameplaySettings();var issuedPolicy=new FarmMaintenanceCoordinator.Policy(Set.of(GameArea.GARDEN),screen->screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> &&allowsScreen(screen.getTitle().getString()),issuedSettings,new FarmMaintenanceCoordinator.LoadoutProof(){
        public Object captureBinding(){return client.player==null||client.level==null||client.getConnection()==null?null:new Binding(client.player,client.level,client.getConnection(),McNamedLoadoutPort.equipped(client.player));}
        public boolean restored(Object original,Object witness){return original instanceof Binding b&&witness instanceof BooleanSupplier proof&&client.player==b.player()&&client.level==b.world()&&client.getConnection()==b.connection()&&client.player!=null&&b.gear().equals(McNamedLoadoutPort.equipped(client.player))&&proof.getAsBoolean();}
    },config.get(PestPhilipConfig.PEST_PHILIP_MAINTENANCE_TIMEOUT_MS));issuedSettings.issuedPolicy=issuedPolicy;return issuedPolicy;}
    private void cancelOwned(){if(cancelled)return;cancelled=true;child.setEnabled(false);if(token==null)token=maintenance.tokenFor(this);maintenance.cancel(token,"Philip exchange stopped; no farm resume");}
    @Override public void onDisable(){if(maintenance.isCompleting(this)){pending=false;child.setEnabled(false);return;}runtime.cancel();cancelOwned();pending=false;}
    @Override public boolean hasPendingCleanup(){return !isEnabled()&&child.hasPendingCleanup();}@Override public void maintenanceTick(){if(hasPendingCleanup())child.maintenanceTick();}
    @Override public void onServerMessage(String line){child.onServerMessage(line);maintenance.onServerMessage(line);}
    @Override public boolean consumeExpectedTeleport(Vec3 position,long now){return isEnabled()&&(child.consumeExpectedTeleport(position,now)||maintenance.consumeExpectedTeleport(position,now));}
    @Override public boolean allowsScreen(String title){return child.allowsScreen(title);}@Override public boolean requiresSkyBlock(){return true;}@Override public Optional<GameArea> requiredArea(){return Optional.of(GameArea.GARDEN);}
    public boolean acknowledgeManualRecovery(){return !isEnabled()&&child.acknowledgeManualRecovery();}
    public PestMaintenanceRuntime.State workflowState(){return runtime.state();}public int exchanged(){return child.exchanged();}public int exchangeClicks(){return child.exchangeClicks();}public int contactCommands(){return child.contactCommands();}
    @Override public String diagnostics(){return runtime.status()+" | "+child.diagnostics()+" | "+maintenance.status();}
}
