package dev.veltraph.client.garden.pests.mc;

import dev.veltraph.client.VeltraphBootstrap;
import dev.veltraph.client.config.*;
import dev.veltraph.client.core.*;
import dev.veltraph.client.game.GameArea;
import dev.veltraph.client.garden.loadout.NamedLoadoutFlow;
import dev.veltraph.client.garden.loadout.mc.McNamedLoadoutPort;
import dev.veltraph.client.garden.pests.PestBindings;
import dev.veltraph.client.rewarp.FarmMaintenanceLease;
import dev.veltraph.client.rewarp.mc.FarmMaintenanceCoordinator;
import dev.veltraph.client.service.FarmServices;
import net.minecraft.client.Minecraft;
import java.util.*;
import java.util.function.*;

/** Closed early equipment operation. Enable and normal completion publish no input or camera effects. */
public final class McPestEarlyLoadoutModule extends AbstractModule implements FarmMaintenanceCoordinator.Participant {
    public static final String ID="garden_pest_early_retained_loadout";
    public record Contracts(McNamedLoadoutPort.AncillaryContract main,McNamedLoadoutPort.AncillaryContract alt){
        public Contracts {Objects.requireNonNull(main);Objects.requireNonNull(alt);if(!main.values().equals(alt.values())||!main.exactRow().equals(alt.exactRow()))throw new IllegalArgumentException("All six ancillary settings must stay identical");}
    }
    private final Minecraft client;private final ConfigService store;private final FarmServices services;
    private final FarmMaintenanceCoordinator coordinator;private final ModuleManager registry;
    private final McNamedLoadoutPort port;private final NamedLoadoutFlow flow;
    private final VeltraphBootstrap.RootNamedLoadoutSettingsSource settingsSource;
    private VeltraphBootstrap.RootRetainedLoadoutDispatch dispatch;
    private NamedLoadoutFlow.Settings frozen;private Contracts contracts;private McNamedLoadoutPort.AncillaryContract selectedContract;private Map<String,dev.veltraph.client.inventory.NativeStackIdentity.Snapshot> armedMain;private McNamedLoadoutPort.InitialGearHistory initialGear;private String target;
    private FarmMaintenanceLease.Token token;private ModuleManager.OwnEnableReceipt enable;
    private FarmMaintenanceCoordinator.RetainedLoadoutOperation operation;private McNamedLoadoutPort.RetainedWindow window;
    private McNamedLoadoutPort.AppliedReceipt lastApplied;private boolean pending,failed;private String diagnostic="Configure and capture farming/pest loadouts before early swapping can run";
    public McPestEarlyLoadoutModule(Minecraft client,FarmServices services,ConfigService store,FarmMaintenanceCoordinator coordinator,ModuleManager registry,
            McNamedLoadoutPort port,NamedLoadoutFlow flow,VeltraphBootstrap.RootNamedLoadoutSettingsSource source){
        super(ID,"Early pest retained loadout",ModuleCategory.INTERMEDIARY);this.client=client;this.services=services;this.store=store;this.coordinator=coordinator;this.registry=registry;this.port=port;this.flow=flow;settingsSource=source;
    }
    /** Only the privately constructed actual Root invocation can arm this registered helper. */
    public boolean arm(VeltraphBootstrap.RootRetainedLoadoutDispatch actual){
        if(isEnabled()||actual==null||!actual.initialCurrent(this,coordinator)||!settingsSource.affiliates(store)
                ||!store.get(PestLoadoutConfig.PEST_LOADOUT_SWAP))return false;
        McNamedLoadoutPort.InitialGearHistory initial=null;
        try{
            // Missing MAIN must refuse before the real farm pause, even when this source decision wants ALT.
            var settings=settingsSource.get();
            String main=store.get(PestLoadoutConfig.PEST_FARMING_LOADOUT),alt=store.get(PestLoadoutConfig.PEST_PEST_LOADOUT);
            var pair=dev.veltraph.client.garden.pests.PestEarlyConfiguredProfiles.read(settings,main,alt).orElse(null);
            if(pair==null){diagnostic="Both configured farming and pest profiles are required before early swapping";return false;}
            var observed=PestBindings.read(store.get(PestLoadoutConfig.PEST_RETAINED_LOADOUT_CONTRACTS),Contracts.class);
            var desired=actual.desired();
            if(desired==dev.veltraph.client.garden.pests.PestEarlyLoadoutPolicy.Desired.UNKNOWN
                    ||!actual.initialCurrent(this,coordinator)||!settingsSource.affiliates(store)
                    ||!store.get(PestLoadoutConfig.PEST_LOADOUT_SWAP))return false;
            String name=desired==dev.veltraph.client.garden.pests.PestEarlyLoadoutPolicy.Desired.ALT?pair.alt().name():pair.main().name();
            initial=port.captureInitialGearHistory();if(initial==null)return false;
            if(!coordinator.retainedInitialGearAllowed(actual.originalFarm(),observed.main())||!actual.initialCurrent(this,coordinator))return false;
            armedMain=coordinator.originalRetainedMainAtArm(actual.originalFarm(),observed.main(),initial);
            if(armedMain==null||!actual.initialCurrent(this,coordinator)||!initial.current())return false;initialGear=initial;
            dispatch=actual;frozen=settings;contracts=observed;target=name;selectedContract=desired==dev.veltraph.client.garden.pests.PestEarlyLoadoutPolicy.Desired.ALT?observed.alt():observed.main();token=null;enable=null;operation=null;window=null;lastApplied=null;failed=false;pending=false;return true;
        }catch(RuntimeException unknown){diagnostic="Exact retained contract unavailable: "+unknown.getClass().getSimpleName();return false;}
        finally{if(initial!=null&&initialGear!=initial)initial.revoke();}
    }
    @Override public Optional<String> preReservationReadiness(){return dispatch!=null&&dispatch.initialCurrent(this,coordinator)&&frozen!=null&&contracts!=null&&settingsSource.affiliates(store)?Optional.empty():Optional.of("Actual fresh Root early-loadout dispatch and complete observed contracts required");}
    @Override public boolean prepareActivation(FarmMaintenanceLease.Token issued){if(issued==null||dispatch==null||!dispatch.reservedCurrent(this,coordinator,issued))return false;token=issued;return true;}
    @Override public void cancelPrepared(FarmMaintenanceLease.Token issued){if(issued==token){retireSourceMetadata();pending=false;failed=true;}}
    @Override public void onEnable(){enable=registry.currentOwnEnableReceipt(this).orElse(null);pending=true;}
    public void committed(FarmMaintenanceLease.Token issued){if(token!=issued||dispatch==null||!dispatch.commitActual(this,coordinator,issued,enable)){fail("Normal original helper enable/commit unavailable");return;}diagnostic="Original farm checkpoint reserved; waiting for Root input handoff";}
    @Override public void onTick(){
        if(failed||!isEnabled())return;
        if(pending){if(initialGear==null||!initialGear.current()){fail("Original pre-window gear history changed");return;}if(!dispatch.inputHandoffReady())return;if(!coordinator.retainedPauseSettled(this,token,dispatch))return;pending=false;
            operation=coordinator.beginRetainedLoadout(this,dispatch,port,flow,target);
            if(operation==null){fail("Original retained operation unavailable");return;}
            var contract=selectedContract;
            window=port.beginRetained(flow,operation,frozen,target,contract);
            if(window==null){fail("Full native inventory/gear/ancillary window unavailable");return;}
            flow.start(target,frozen,services.clock().currentTick());
        }
        if(window==null||window.refused()||!operation.currentFor(port,flow,target)){fail("Original native retained window changed");return;}
        flow.tick(services.clock().currentTick());diagnostic="Retained "+target+" "+flow.state();
        if(flow.state()==NamedLoadoutFlow.State.FAILED||flow.state()==NamedLoadoutFlow.State.CANCELLED){fail("Named loadout failed; no automatic retry");return;}
        if(flow.state()==NamedLoadoutFlow.State.APPLIED){lastApplied=port.appliedReceipt(window);
            if(lastApplied==null||!coordinator.finishRetainedLoadout(operation,lastApplied)){fail("Native applied receipt/original farm handoff refused");return;}
            window.retire();diagnostic="Native retained "+target+"; same original farm resumed";
        }
    }
    /** Idempotent exact held metadata retirement; this publishes no input, packets, gear, or restoration. */
    private void retireSourceMetadata(){if(window!=null)window.retire();else if(initialGear!=null)initialGear.revoke();if(lastApplied!=null)lastApplied.revokeRetainedGear();}
    private void fail(String why){retireSourceMetadata();if(failed)return;failed=true;diagnostic=why;if(token!=null)coordinator.cancel(token,why);}
    @Override public void onDisable(){pending=false;if(coordinator.isCompleting(this))return;fail("Early retained operation stopped; applied equipment requires reconciliation");}
    @Override public boolean requiresSkyBlock(){return true;}@Override public Optional<GameArea> requiredArea(){return Optional.of(GameArea.GARDEN);}
    @Override public boolean allowsScreen(String title){return frozen!=null&&frozen.menuTitle().equals(title);}
    /** Exact private helper-policy provenance; copied strings or a generic Supplier cannot qualify. */
    public final class GameplaySettings implements Supplier<String> {
        private FarmMaintenanceCoordinator.Policy issuedPolicy;
        private GameplaySettings(){}
        @Override public String get(){return store.get(PestLoadoutConfig.PEST_RETAINED_LOADOUT_CONTRACTS);}
        private final class Snapshot {
            final GameplaySettings issued=GameplaySettings.this;
            final Object policy=issuedPolicy;
            final dev.veltraph.client.config.MapConfigService.StoredStateSnapshot stored;
            final VeltraphBootstrap.RootNamedLoadoutSettingsSource source=settingsSource;
            final NamedLoadoutFlow.Settings profiles=frozen;
            final Contracts observed=contracts;
            final McNamedLoadoutPort.AncillaryContract selected=selectedContract;
            final String name=target;
            private Snapshot(dev.veltraph.client.config.MapConfigService.StoredStateSnapshot stored){this.stored=stored;}
        }
        public Object snapshot(Object expectedHelper,ConfigService expectedStore,Object expectedPolicy){
            if(expectedHelper!=McPestEarlyLoadoutModule.this||expectedStore!=store||expectedPolicy==null
                    ||expectedPolicy!=issuedPolicy||!client.isSameThread()||!settingsSource.affiliates(store)
                    ||frozen==null||contracts==null||selectedContract==null||target==null)return null;
            var stored=store instanceof MapConfigService m?m.storedStateSnapshot():store instanceof JsonConfigService j?j.storedStateSnapshot():null;
            if(stored==null)return null;
            var exact=new Snapshot(stored);return snapshotCurrent(exact)?exact:null;
        }
        /** Retained immutable source data only; no Supplier.get, profile parsing, or native effect. */
        public boolean snapshotCurrent(Object exact){return exact instanceof McPestEarlyLoadoutModule.GameplaySettings.Snapshot s
            &&s.issued==this&&s.policy==issuedPolicy&&client.isSameThread()&&s.source==settingsSource
            &&s.source.affiliates(store)&&s.stored.current()&&s.profiles==frozen&&s.observed==contracts
            &&s.selected==selectedContract&&s.name==target&&s.profiles!=null&&s.observed!=null&&s.selected!=null&&s.name!=null;}
    }
    public FarmMaintenanceCoordinator.Policy maintenancePolicy(){var issuedSettings=new GameplaySettings();var issuedPolicy=new FarmMaintenanceCoordinator.Policy(Set.of(GameArea.GARDEN),screen->screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> &&allowsScreen(screen.getTitle().getString()),issuedSettings,new FarmMaintenanceCoordinator.LoadoutProof(){
        public Object captureBinding(){return client.player==null?null:McNamedLoadoutPort.equipped(client.player);}
        public boolean restored(Object old,Object witness){return false;}
    },15000);issuedSettings.issuedPolicy=issuedPolicy;return issuedPolicy;}
    public VeltraphBootstrap.RootRetainedLoadoutDispatch armedDispatch(){return dispatch;}
    public NamedLoadoutFlow.Settings frozenBindings(){return frozen;}
    public Map<String,dev.veltraph.client.inventory.NativeStackIdentity.Snapshot> originalMainAtArm(){return armedMain;}
    public McNamedLoadoutPort.InitialGearHistory initialGearHistory(){return initialGear;}
    public McNamedLoadoutPort.AncillaryContract frozenContract(){return selectedContract;}
    public boolean retainedBindingsCurrent(McNamedLoadoutPort exactPort,NamedLoadoutFlow exactFlow,String name,NamedLoadoutFlow.Settings settings,McNamedLoadoutPort.AncillaryContract contract){return exactPort==port&&exactFlow==flow&&Objects.equals(name,target)&&settings==frozen&&contract==selectedContract&&settingsSource.affiliates(store);}
    public McNamedLoadoutPort.RetainedWindow lastWindow(){return window;}
    public McNamedLoadoutPort.AppliedReceipt lastAppliedReceipt(){return lastApplied;}
    public FarmMaintenanceCoordinator.RetainedLoadoutOperation lastOperation(){return operation;}
    @Override public String diagnostics(){return diagnostic;}
}
