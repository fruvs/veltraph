package dev.veltraph.client.garden.pests.mc;

import dev.veltraph.client.config.*;
import dev.veltraph.client.core.*;
import dev.veltraph.client.game.*;
import dev.veltraph.client.game.mc.McGameContextService;
import dev.veltraph.client.garden.loadout.NamedLoadoutFlow;
import dev.veltraph.client.garden.loadout.mc.McNamedLoadoutPort;
import dev.veltraph.client.garden.pests.*;
import dev.veltraph.client.gui.*;
import dev.veltraph.client.inventory.NativeStackIdentity;
import dev.veltraph.client.rewarp.*;
import dev.veltraph.client.rewarp.mc.FarmMaintenanceCoordinator;
import dev.veltraph.client.service.FarmServices;
import dev.veltraph.client.service.mc.McHeldKeys;
import dev.veltraph.client.world.Vec3;
import dev.veltraph.client.navigation.mc.McNavigator;
import dev.veltraph.client.navigation.WalkController;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Source R41 parent, registered as one farm maintenance participant through final engine return. */
public final class McPestSprayonatorMaintenanceModule extends AbstractModule implements FarmMaintenanceCoordinator.Participant {
    public static final String ID="garden_sprayonator_cycle";
    private final Minecraft client;private final FarmServices services;private final ConfigService config;
    private final FarmMaintenanceCoordinator maintenance;private final AssignedPlotTravelPort travel;private final PestInventoryLease lease;
    private final McMenuAccess menus;private final McPestSupplyRefillModule supply;private final PestSprayonatorRuntime runtime;
    private final McNavigator escapeNavigation;
    private PestSprayonatorBindings bindings;private PestSprayonatorRuntime.Settings settings;private FarmMaintenanceLease.Token token;
    private Object player,world,connection,ownedMenu;private String frozen;private boolean pending,cancelled,selected,restoring;
    private int originalSlot,toolSource,toolDestination,restoreTick,openingTick;private boolean openingUse;private List<ItemStack> inventory=List.of();private ItemStack materialTemplate=ItemStack.EMPTY;
    private Map<String,NamedLoadoutFlow.Gear> originalGear=Map.of();
    private PestSpraySelectionCustody.Intent selectionClick;
    public McPestSprayonatorMaintenanceModule(Minecraft client,FarmServices services,ConfigService config,FarmMaintenanceCoordinator maintenance,AssignedPlotTravelPort travel){
        super(ID,"Selected-plot Sprayonator cycle",ModuleCategory.INTERMEDIARY);this.client=client;this.services=services;this.config=config;
        this.maintenance=Objects.requireNonNull(maintenance);this.travel=Objects.requireNonNull(travel);lease=new PestInventoryLease(client);menus=new McMenuAccess(client);
        supply=new McPestSupplyRefillModule(client,services,config);escapeNavigation=new McNavigator(client,services.input(),config);
        runtime=new PestSprayonatorRuntime(new PestSprayonatorRuntime.Actions(){
            public boolean valid(){return owned();}
            public boolean select(){selected=lease.begin(bindings.tool(),config.get(PestConfig.PEST_SPRAY_PARENT_HOTBAR_SLOT));return selected;}
            public boolean escape(){var e=bindings.escape();return owned()&&closed()&&e.sourceCandidate(position())&&trapFree(e.landing())&&escapeNavigation.standingClear(e.landing())
                    &&escapeNavigation.start(e.landing(),2500,32,Math.min(.35,e.arrivalRadius()));}
            public PestSprayonatorRuntime.Progress escaped(){
                if(!owned()||!closed()||!trapFree(bindings.escape().landing())||!escapeNavigation.standingClear(bindings.escape().landing()))return PestSprayonatorRuntime.Progress.FAILED;
                var s=escapeNavigation.tick();if(s==WalkController.State.BLOCKED)return PestSprayonatorRuntime.Progress.FAILED;
                if(s!=WalkController.State.ARRIVED)return PestSprayonatorRuntime.Progress.ACTIVE;
                boolean arrived=position().distanceTo(bindings.escape().landing())<=bindings.escape().arrivalRadius()&&client.player.onGround()&&escapeNavigation.standingClear(position())&&trapFree(position());
                escapeNavigation.stop();return arrived?PestSprayonatorRuntime.Progress.COMPLETE:PestSprayonatorRuntime.Progress.FAILED;
            }
            public boolean openMenu(){
                if(!lease.activeTool()||!closed())return false;
                // The opening gesture is an explicit observed binding; preference toggles are never changed.
                McHeldKeys.setHeld(client.options.keyShift,bindings.sneakToOpen());openingTick=client.player.tickCount;openingUse=true;return true;
            }
            public boolean click(MenuSnapshot menu,int slot){
                if(!owned()||client.player.containerMenu!=ownedMenu||!client.player.containerMenu.getCarried().isEmpty())return false;
                PestSpraySelectionCustody.Scope before;try{before=selectionScope();}catch(RuntimeException unknown){return false;}
                if(!menus.click(menu,slot))return false;
                // Confirm the exact local PICKUP result in the issued tick, before admitting waits.
                try{selectionClick=PestSpraySelectionCustody.capture(before,menu,slot,true,selectionScope()).orElse(null);}
                catch(RuntimeException unknown){selectionClick=null;}
                return true;
            }
            public boolean waitingForOwnSelectionPrediction(PestSprayonatorRuntime.Observation observed,MenuSnapshot issuedMenu,int issuedSlot){
                try{return selectionClick!=null&&selectionClick.pendingMatches(selectionScope(),issuedMenu,issuedSlot);}catch(RuntimeException unknown){return false;}
            }
            public void selectionAcknowledged(){selectionClick=null;}
            public boolean close(MenuSnapshot menu){return owned()&&client.player.containerMenu==ownedMenu&&menus.close(menu.id());}
            public boolean acquire(int deficit){
                if(!config.get(PestConfig.PEST_SUPPLY_REFILL_ENABLED))return false;
                var b=PestBindings.read(config.get(PestConfig.PEST_SUPPLY_BINDINGS),PestBindings.Supply.class);
                if(!b.item().equals(bindings.material())||b.quantity()!=deficit||supply.preStartCheck().isPresent())return false;
                supply.setEnabled(true);return supply.isEnabled();
            }
            public PestSprayonatorRuntime.Progress acquired(){
                if(supply.isEnabled())supply.onTick();
                return supply.isEnabled()?PestSprayonatorRuntime.Progress.ACTIVE:supply.workflowPhase()==GardenActionModule.Phase.COMPLETE
                        ?PestSprayonatorRuntime.Progress.COMPLETE:PestSprayonatorRuntime.Progress.FAILED;
            }
            public boolean travel(PestSprayonatorBindings.Plot plot){ownedMenu=null;menus.clear();return closed()&&travel.start(plot.target(),GameArea.GARDEN);}
            public PestSprayonatorRuntime.Progress travelled(PestSprayonatorBindings.Plot plot){
                var s=travel.tick(plot.target(),GameArea.GARDEN);
                if(s==AssignedPlotTravelRuntime.State.ARRIVED){travel.cancel();return PestSprayonatorRuntime.Progress.COMPLETE;}
                return s==AssignedPlotTravelRuntime.State.WAITING_ARRIVAL?PestSprayonatorRuntime.Progress.ACTIVE:PestSprayonatorRuntime.Progress.FAILED;
            }
            public boolean use(PestSprayonatorRuntime.Observation expected){return owned()&&expected.equals(observe())&&closed()&&lease.use();}
            public boolean restore(){if(!owned()||!closed())return false;lease.restore();restoring=true;restoreTick=client.player.tickCount;return true;}
            public PestSprayonatorRuntime.Progress restored(){
                if(client.player.tickCount<=restoreTick)return PestSprayonatorRuntime.Progress.ACTIVE;
                return inventoryRestored()?PestSprayonatorRuntime.Progress.COMPLETE:PestSprayonatorRuntime.Progress.FAILED;
            }
            public boolean beginReturn(){return maintenance.beginRestore(token)&&maintenance.beginReturn(token,(BooleanSupplier)McPestSprayonatorMaintenanceModule.this::inventoryRestored);}
            public PestSprayonatorRuntime.Progress returned(){return switch(maintenance.tickReturn(token)){case ACTIVE->PestSprayonatorRuntime.Progress.ACTIVE;case READY->PestSprayonatorRuntime.Progress.COMPLETE;case FAILED->PestSprayonatorRuntime.Progress.FAILED;};}
            public boolean resume(){return maintenance.resume(token,(BooleanSupplier)McPestSprayonatorMaintenanceModule.this::inventoryRestored);}
            public void cancel(){cancelOwned();}
        });
    }
    private long now(){return services.clock().nowMillis();}
    private String signature(){var values=new TreeMap<String,String>();try{for(var field:PestConfig.class.getFields())if(field.get(null) instanceof ConfigKey<?> key)values.put(key.name(),String.valueOf(config.get(key)));}
        catch(IllegalAccessException bad){throw new IllegalStateException(bad);}return values.toString();}
    private PestSprayonatorRuntime.Settings readSettings(){var bindings=PestSprayonatorBindings.read(config.get(PestConfig.PEST_SPRAY_PARENT_BINDINGS));
        return new PestSprayonatorRuntime.Settings(bindings,config.get(PestConfig.PEST_SPRAY_UNITS),config.get(PestConfig.PEST_SPRAY_UNIT_VALUE),config.get(PestConfig.PEST_SPRAY_MAX_VALUE),
                config.get(PestConfig.PEST_SPRAY_PARENT_TIMEOUT_MS),config.get(PestConfig.PEST_SPRAY_ACK_TIMEOUT_MS));}
    private boolean closed(){return client.player!=null&&client.gui.screen()==null&&client.player.containerMenu==client.player.inventoryMenu&&client.player.containerMenu.getCarried().isEmpty();}
    private boolean contest(){if(!config.get(PestConfig.PEST_SPRAY_CONTEST_ONLY))return true;var location=new McGameContextService(client);location.resolution();return GardenContestSignal.active(location.signals().sidebarRowsRaw());}
    private boolean owned(){return isEnabled()&&config.get(PestConfig.PEST_SPRAY_PARENT_ENABLED)&&config.get(PestConfig.PESTS_USE_SPRAYONATOR)
        &&client.player==player&&client.level==world&&client.getConnection()==connection&&client.player!=null&&client.player.isAlive()&&originalGear.equals(McNamedLoadoutPort.equipped(client.player))&&Objects.equals(frozen,signature())&&contest()&&maintenance.valid(token);}
    private record Capability(PestSprayonatorRuntime.Settings settings,Optional<String> refusal){
        static Capability refused(String reason){return new Capability(null,Optional.of(reason));}
    }
    /** Fresh read-only capability inspection; no lease, input, menu, tool or resource action. */
    private Capability freshCapability(){
        if(!config.get(PestConfig.PEST_SPRAY_PARENT_ENABLED)||!config.get(PestConfig.PESTS_USE_SPRAYONATOR))return Capability.refused("Enable the selected-plot Sprayonator parent explicitly");
        if(!closed()||client.level==null||client.getConnection()==null||client.gameMode==null||!client.player.isAlive()||!contest())return Capability.refused("Closed live Garden owner and configured contest guard required");
        try{var settings=readSettings();var bindings=settings.bindings();int destination=config.get(PestConfig.PEST_SPRAY_PARENT_HOTBAR_SLOT);
            if(destination<0||destination>8||PestItems.uniqueSlot(client,bindings.tool())<0)return Capability.refused("One exact Sprayonator and bounded hotbar destination required");
            var tool=client.player.getInventory().getItem(PestItems.uniqueSlot(client,bindings.tool()));
            if(PestItems.lore(tool).stream().filter(s->s.startsWith(bindings.selectionPrefix())).count()!=1)return Capability.refused("One exact source selection-prefix lore line required");
            if(!trapFree(position(),bindings.escape())&&(!bindings.escape().sourceCandidate(position())||!trapFree(bindings.escape().landing(),bindings.escape())||!escapeNavigation.standingClear(bindings.escape().landing())))
                return Capability.refused("Nearby source trap requires a loaded trap-free escape landing within ±10 and Y≥66");
            if(!materialUnambiguous(bindings.material()))return Capability.refused("Spray material components ambiguous");
            int count=PestItems.count(client,bindings.material(),false);
            if(count<settings.required()){
                if(!config.get(PestConfig.PEST_SUPPLY_REFILL_ENABLED))return Capability.refused("Exact aggregate spray supply or explicit guarded refill required");
                var supplyBinding=PestBindings.read(config.get(PestConfig.PEST_SUPPLY_BINDINGS),PestBindings.Supply.class);
                if(!supplyBinding.item().equals(bindings.material())||supplyBinding.quantity()!=settings.required()-count)return Capability.refused("Supply helper must acquire the exact selected-plot material deficit");
                var ready=supply.preStartCheck();if(ready.isPresent())return Capability.refused(ready.get());
            }
            return new Capability(settings,Optional.empty());
        }catch(RuntimeException invalid){return Capability.refused("Invalid exact Sprayonator parent bindings: "+invalid.getMessage());}
    }
    @Override public Optional<String> preReservationReadiness(){return freshCapability().refusal();}
    @Override public Optional<String> preStartCheck(){
        var capability=freshCapability();if(capability.refusal().isPresent())return capability.refusal();
        // Only the deferred full guard publishes the preview used by onEnable and runtime start.
        settings=capability.settings();bindings=settings.bindings();
        return maintenance.state()==FarmMaintenanceLease.State.PREPARED?Optional.empty():maintenance.startEligibility(this);
    }
    /** Captures only; Root commits the one original farm lease before the first native action. */
    @Override public void onEnable(){
        player=client.player;world=client.level;connection=client.getConnection();frozen=signature();pending=true;cancelled=selected=restoring=openingUse=false;ownedMenu=null;token=null;materialTemplate=ItemStack.EMPTY;
        originalSlot=client.player==null?-1:client.player.getInventory().getSelectedSlot();toolSource=bindings==null?-1:PestItems.uniqueSlot(client,bindings.tool());
        originalGear=client.player==null?Map.of():McNamedLoadoutPort.equipped(client.player);selectionClick=null;
        toolDestination=toolSource<9?toolSource:config.get(PestConfig.PEST_SPRAY_PARENT_HOTBAR_SLOT);inventory=new ArrayList<>();
        if(client.player!=null)for(int i=0;i<36;i++)inventory.add(client.player.getInventory().getItem(i).copy());
    }
    public void committed(FarmMaintenanceLease.Token committed){if(!isEnabled()||!pending||committed==null||maintenance.tokenFor(this)!=committed)throw new IllegalStateException("Sprayonator commit owner changed");token=committed;}
    @Override public void onTick(){
        if(openingUse&&owned()&&client.player.tickCount>openingTick&&(!bindings.sneakToOpen()||client.player.isShiftKeyDown())){
            openingUse=false;if(!closed()||!inventoryUnchanged(false)||!lease.use()){runtime.cancel();setEnabled(false);return;}McHeldKeys.setHeld(client.options.keyShift,false);
        }
        if(pending){pending=false;token=maintenance.tokenFor(this);runtime.start(settings,observe(),now());}
        else runtime.tick(settings,observe(),now());if(!runtime.active()&&isEnabled())setEnabled(false);
    }
    private String stableTool(ItemStack stack){
        if(stack.isEmpty())return "";var copy=stack.copy();var lore=copy.get(DataComponents.LORE);
        if(lore==null||PestItems.lore(copy).stream().filter(s->s.startsWith(bindings.selectionPrefix())).count()!=1)return "";
        copy.set(DataComponents.LORE,new ItemLore(lore.lines().stream().filter(s->!MenuSnapshot.clean(s.getString()).startsWith(bindings.selectionPrefix())).toList()));
        return PestItems.identity(copy)+"|count="+copy.getCount();
    }
    private boolean materialUnambiguous(){return materialUnambiguous(bindings.material());}
    private boolean materialUnambiguous(PestBindings.Item material){ItemStack seen=ItemStack.EMPTY;for(int i=0;i<36;i++){var stack=client.player.getInventory().getItem(i);if(!PestItems.matches(stack,material))continue;
        if(seen.isEmpty())seen=stack;else if(!ItemStack.isSameItemSameComponents(seen,stack))return false;}return true;}
    private boolean inventoryUnchanged(boolean restored){
        if(inventory.size()!=36||toolSource<0||toolDestination<0)return false;
        for(int i=0;i<36;i++){
            var live=client.player.getInventory().getItem(i);var initial=inventory.get(i);
            if(i==toolSource||i==toolDestination){
                int expected=selected&&!restored&&toolSource>=9?(i==toolSource?toolDestination:toolSource):i;var old=inventory.get(expected);
                if(PestItems.matches(old,bindings.tool())){if(!PestItems.matches(live,bindings.tool())||!stableTool(old).equals(stableTool(live)))return false;}
                else if(PestItems.matches(old,bindings.material())){if(!live.isEmpty()&&!PestItems.matches(live,bindings.material()))return false;}
                else if(!ItemStack.matches(old,live))return false;
            }else if(PestItems.matches(initial,bindings.material())){
                if(!live.isEmpty()&&!PestItems.matches(live,bindings.material()))return false;
            }else if(initial.isEmpty()&&PestItems.matches(live,bindings.material())){
                // Acquisition may add its exact material to a formerly empty inventory slot.
            }else if(!ItemStack.matches(initial,live))return false;
        }return true;
    }
    private PestSprayonatorRuntime.Observation observe(){
        if(client.player==null||client.level==null||client.getConnection()==null||bindings==null)return null;
        var p=client.player;int tool=PestItems.uniqueSlot(client,bindings.tool());if(tool<0)return null;var stack=p.getInventory().getItem(tool);
        if(!materialUnambiguous())return null;
        int count=PestItems.count(client,bindings.material(),false);
        for(int i=0;i<36;i++)if(PestItems.matches(p.getInventory().getItem(i),bindings.material())){
            var sample=p.getInventory().getItem(i);if(materialTemplate.isEmpty())materialTemplate=sample.copy();else if(!ItemStack.isSameItemSameComponents(materialTemplate,sample))return null;
        }
        var menu=menus.read();if(menu!=null&&runtime.allowsMenu(menu.title())){if(ownedMenu==null)ownedMenu=p.containerMenu;else if(ownedMenu!=p.containerMenu)return null;}
        var plot=runtime.plot();boolean inside=plot!=null&&GardenPlotBounds.contains(plot.id(),p.getX(),p.getZ())&&GardenPlotBounds.sourceSprayContains(plot.id(),p.getX(),p.getY(),p.getZ());
        // Before the SWAP correction tick, defer the observation rather than treating prediction as completion.
        boolean ready=selected&&!restoring&&lease.ready();boolean checked=inventoryUnchanged(restoring)||selected&&!restoring&&!ready&&runtime.state()==PestSprayonatorRuntime.State.OPEN_MENU;
        return new PestSprayonatorRuntime.Observation(client.player==player&&client.level==world&&client.getConnection()==connection&&p.isAlive()&&checked,
            closed(),p.containerMenu.getCarried().isEmpty(),ready,stableTool(stack),bindings.material().toString(),count,
            PestItems.lore(stack).stream().filter(s->s.equals(bindings.selectedLore())).count()==1,menu,inside,!trapFree(position()));
    }
    /** Native codec snapshots include count and every persistent component; unknown identity denies. */
    private PestSpraySelectionCustody.Scope selectionScope(){
        var p=client.player;if(p==null||client.level==null||client.getConnection()==null||p!=player||client.level!=world||client.getConnection()!=connection
                ||p.containerMenu!=ownedMenu||client.gui.screen()==null||!p.isAlive())throw new IllegalStateException("Selection custody owner changed");
        var registry=client.level.registryAccess();var rows=new ArrayList<NativeStackIdentity.Snapshot>();for(var entry:p.containerMenu.slots)rows.add(NativeStackIdentity.capture(entry.getItem(),registry));
        var inv=new ArrayList<NativeStackIdentity.Snapshot>();for(int i=0;i<36;i++)inv.add(NativeStackIdentity.capture(p.getInventory().getItem(i),registry));
        return new PestSpraySelectionCustody.Scope(p,client.level,client.getConnection(),p.containerMenu,p.containerMenu.containerId,p.containerMenu.getStateId(),
                p.getInventory().getSelectedSlot(),p.tickCount,rows,inv,McNamedLoadoutPort.equipped(p),NativeStackIdentity.capture(p.containerMenu.getCarried(),registry),
                p.containerMenu.getCarried().isEmpty(),NativeStackIdentity.capture(ItemStack.EMPTY,registry));
    }
    private Vec3 position(){return new Vec3(client.player.getX(),client.player.getY(),client.player.getZ());}
    private boolean trapFree(Vec3 point){return trapFree(point,bindings.escape());}
    private boolean trapFree(Vec3 point,PestSprayonatorBindings.Escape escape){for(var entity:client.level.entitiesForRendering())if(PestItems.entityMatches(entity,escape.trap())
            &&!escape.outsideTrap(point,new Vec3(entity.getX(),entity.getY(),entity.getZ())))return false;return true;}
    private boolean inventoryRestored(){return client.player==player&&client.level==world&&client.getConnection()==connection&&closed()&&inventoryUnchanged(true)
            &&client.player.getInventory().getSelectedSlot()==originalSlot&&materialUnambiguous();}
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
            if(expectedHelper!=McPestSprayonatorMaintenanceModule.this||expectedStore!=config||expectedPolicy==null||expectedPolicy!=issuedPolicy||!client.isSameThread())return null;
            var stored=config instanceof dev.veltraph.client.config.MapConfigService m?m.storedStateSnapshot():config instanceof dev.veltraph.client.config.JsonConfigService j?j.storedStateSnapshot():null;
            if(stored==null)return null;
            var actual=new Snapshot(stored);return snapshotCurrent(actual)?actual:null;
        }
        /** Private known backing state only; never calls the settings/loadout supplier. */
        public boolean snapshotCurrent(Object exact){return exact instanceof McPestSprayonatorMaintenanceModule.GameplaySettings.Snapshot s&&s.issued==this&&s.policy==issuedPolicy&&client.isSameThread()&&s.settings.current();}
    }
    public FarmMaintenanceCoordinator.Policy maintenancePolicy(){var issuedSettings=new GameplaySettings();var issuedPolicy=new FarmMaintenanceCoordinator.Policy(Set.of(GameArea.GARDEN),
        screen->screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> &&allowsScreen(screen.getTitle().getString()),issuedSettings,new FarmMaintenanceCoordinator.LoadoutProof(){
            public Object captureBinding(){return client.player==null||client.level==null||client.getConnection()==null?null:new Binding(client.player,client.level,client.getConnection(),McNamedLoadoutPort.equipped(client.player));}
            public boolean restored(Object binding,Object witness){return binding instanceof Binding b&&witness instanceof BooleanSupplier proof&&b.player()==client.player&&b.world()==client.level&&b.connection()==client.getConnection()
                    &&client.player!=null&&b.gear().equals(McNamedLoadoutPort.equipped(client.player))&&proof.getAsBoolean();}
        },config.get(PestConfig.PEST_SPRAY_PARENT_TIMEOUT_MS));issuedSettings.issuedPolicy=issuedPolicy;return issuedPolicy;}
    private void release(){services.rotation().cancelRotation();services.input().releaseAllMovement();services.breaking().setBreaking(false);McHeldKeys.setHeld(client.options.keyUse,false);McHeldKeys.setHeld(client.options.keyAttack,false);McHeldKeys.setHeld(client.options.keyShift,false);McHeldKeys.setHeld(client.options.keySprint,false);}
    private boolean canRestoreToolCustody(){
        if(client.player!=player||client.level!=world||client.getConnection()!=connection||client.player==null||bindings==null||toolSource<0||inventory.size()!=36)return false;
        int slot=PestItems.uniqueSlot(client,bindings.tool());return slot>=0&&stableTool(client.player.getInventory().getItem(slot)).equals(stableTool(inventory.get(toolSource)));
    }
    private void cleanup(){openingUse=false;selectionClick=null;supply.setEnabled(false);escapeNavigation.stop();travel.cancel();if(client.player==player&&client.level==world&&client.getConnection()==connection&&client.player!=null&&client.player.containerMenu==ownedMenu)client.player.closeContainer();ownedMenu=null;menus.clear();
        // An altered tool or new connection cannot authorize moving the old captured inventory.
        if(canRestoreToolCustody())lease.restore();release();}
    private void cancelOwned(){if(cancelled)return;cancelled=true;cleanup();if(token==null)token=maintenance.tokenFor(this);maintenance.cancel(token,"Sprayonator parent stopped; no farm resume");}
    @Override public void onDisable(){if(maintenance.isCompleting(this)){pending=false;cleanup();return;}runtime.cancel();cancelOwned();pending=false;}
    @Override public boolean requiresSkyBlock(){return true;}@Override public Optional<GameArea> requiredArea(){return Optional.of(GameArea.GARDEN);}
    @Override public boolean allowsScreen(String title){return runtime.allowsMenu(title)||supply.isEnabled()&&supply.allowsScreen(title);}
    @Override public void onServerMessage(String line){String clean=LocationText.stripColorCodes(line);if(supply.isEnabled())supply.onServerMessage(clean);runtime.message(clean);travel.onServerMessage(clean);maintenance.onServerMessage(clean);}
    @Override public boolean consumeExpectedTeleport(Vec3 position,long now){return isEnabled()&&(runtime.state()==PestSprayonatorRuntime.State.TRAVEL&&travel.consumeExpectedTeleport(position,now)||maintenance.consumeExpectedTeleport(position,now));}
    public PestSprayonatorRuntime.State workflowState(){return runtime.state();}public List<PestSprayonatorRuntime.Receipt> receipts(){return runtime.receipts();}
    @Override public String diagnostics(){return runtime.status()+" | "+travel.status()+" | "+maintenance.status();}
}
