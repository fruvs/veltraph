package dev.veltraph.client.inventory.selling;

import dev.veltraph.client.config.*;
import dev.veltraph.client.core.*;
import dev.veltraph.client.game.GameArea;
import dev.veltraph.client.gui.MenuSnapshot;
import dev.veltraph.client.mixin.PlayerListHudAccessor;
import dev.veltraph.client.rewarp.*;
import dev.veltraph.client.rewarp.mc.FarmMaintenanceCoordinator;
import dev.veltraph.client.service.FarmServices;
import dev.veltraph.client.world.Vec3;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Garden exact-stack participant, including explicit current-source George command ingress. Hub warps remain separate. */
public final class McFarmAutoSellModule extends AbstractModule implements FarmMaintenanceCoordinator.Participant {
    public static final String ID="farm_auto_sell";
    private final Minecraft client;private final FarmServices services;private final ConfigService config;
    private final FarmMaintenanceCoordinator maintenance;private final GuardedSellingModule child;private final McGeorgeContactSeller georgeChild;private final BazaarCustodyModule bazaarChild;private final FarmAutoSellRuntime runtime;
    private AutoSellMode childMode=AutoSellMode.NPC;
    private FarmMaintenanceLease.Token token;private Object player,world,connection;private String frozen;
    private boolean pending,cancelled;private long lastAttempt=-1,lastObservation=-1;
    private record Binding(Object player,Object world,Object connection,Map<EquipmentSlot,ItemStack> gear){}
    public McFarmAutoSellModule(Minecraft client,FarmServices services,ConfigService config,FarmMaintenanceCoordinator maintenance){
        super(ID,"Farm Auto-sell",ModuleCategory.INTERMEDIARY);this.client=client;this.services=services;this.config=config;this.maintenance=Objects.requireNonNull(maintenance);
        child=new GuardedSellingModule(client,services,config);georgeChild=new McGeorgeContactSeller(client,services,config);bazaarChild=new BazaarCustodyModule(client,config);
        runtime=new FarmAutoSellRuntime(new FarmAutoSellRuntime.Actions(){
            public boolean acquire(){token=maintenance.tokenFor(McFarmAutoSellModule.this);return token!=null;}
            public boolean valid(){return scopeValid()&&maintenance.valid(token);}
            public boolean startChild(){if(!valid())return false;if(childMode==AutoSellMode.BAZAAR){if(bazaarChild.preStartCheck().isPresent())return false;bazaarChild.setEnabled(true);return bazaarChild.isEnabled();}if(childMode==AutoSellMode.GEORGE_PETS)return georgeChild.start(this::valid);if(child.preStartCheck().isPresent())return false;child.setEnabled(true);return child.isEnabled();}
            public FarmAutoSellRuntime.Progress child(){if(childMode==AutoSellMode.BAZAAR){if(bazaarChild.isEnabled())bazaarChild.onTick();return bazaarChild.isEnabled()?FarmAutoSellRuntime.Progress.ACTIVE:bazaarChild.acknowledgedSale().isPresent()?FarmAutoSellRuntime.Progress.COMPLETE:FarmAutoSellRuntime.Progress.FAILED;}if(childMode==AutoSellMode.GEORGE_PETS)return georgeChild.tick();if(child.isEnabled())child.onTick();return child.isEnabled()?FarmAutoSellRuntime.Progress.ACTIVE:
                child.sellingState()==SellingRuntime.State.COMPLETE?FarmAutoSellRuntime.Progress.COMPLETE:FarmAutoSellRuntime.Progress.FAILED;}
            public FarmAutoSellRuntime.Progress cleanup(){if(childMode==AutoSellMode.BAZAAR)return !bazaarChild.isEnabled()&&bazaarChild.acknowledgedSale().isPresent()&&closed()?FarmAutoSellRuntime.Progress.COMPLETE:FarmAutoSellRuntime.Progress.FAILED;if(childMode==AutoSellMode.GEORGE_PETS){var p=georgeChild.cleanup();return p==FarmAutoSellRuntime.Progress.COMPLETE&&!closed()?FarmAutoSellRuntime.Progress.FAILED:p;}child.maintenanceTick();if(child.isEnabled()||child.hasPendingCleanup())return FarmAutoSellRuntime.Progress.ACTIVE;
                return child.sellingState()==SellingRuntime.State.COMPLETE&&closed()?FarmAutoSellRuntime.Progress.COMPLETE:FarmAutoSellRuntime.Progress.FAILED;}
            public boolean beginReturn(){return maintenance.beginRestore(token)&&maintenance.beginReturn(token,(BooleanSupplier)McFarmAutoSellModule.this::childComplete);}
            public FarmAutoSellRuntime.Progress returned(){return switch(maintenance.tickReturn(token)){case ACTIVE->FarmAutoSellRuntime.Progress.ACTIVE;case READY->FarmAutoSellRuntime.Progress.COMPLETE;case FAILED->FarmAutoSellRuntime.Progress.FAILED;};}
            public boolean resume(){return maintenance.resume(token,(BooleanSupplier)McFarmAutoSellModule.this::childComplete);}
            public void cancel(){cancelOwned();}
        });
    }
    private long now(){return services.clock().nowMillis();}
    private boolean closed(){return client.player!=null&&client.gui.screen()==null&&client.player.containerMenu==client.player.inventoryMenu&&client.player.containerMenu.getCarried().isEmpty();}
    private boolean childComplete(){if(childMode==AutoSellMode.BAZAAR)return !bazaarChild.isEnabled()&&bazaarChild.acknowledgedSale().isPresent()&&closed();if(childMode==AutoSellMode.GEORGE_PETS)return georgeChild.complete()&&closed();return !child.isEnabled()&&!child.hasPendingCleanup()&&child.sellingState()==SellingRuntime.State.COMPLETE&&closed();}
    private String configuration(){var values=new TreeMap<String,String>();try{for(var holder:List.of(SellingConfig.class,AutoSellMaintenanceConfig.class))for(var field:holder.getFields())
        if(field.get(null) instanceof ConfigKey<?> key)values.put(key.name(),String.valueOf(config.get(key)));}catch(IllegalAccessException bad){throw new IllegalStateException(bad);}
        values.put("GEORGE_BATCH_PROFILE",config.get(GeorgeBatchConfig.GEORGE_BATCH_PROFILE));
        values.put("AUTO_SELL_ENABLED",String.valueOf(config.get(MiscConfig.AUTO_SELL_ENABLED)));values.put("AUTO_SELL_MODE",String.valueOf(config.get(MiscConfig.AUTO_SELL_MODE)));values.put("AUTO_SELL_SACKS",String.valueOf(config.get(MiscConfig.AUTO_SELL_SACKS)));return values.toString();}
    private boolean scopeValid(){return isEnabled()&&client.player==player&&client.level==world&&client.getConnection()==connection&&client.player!=null&&client.player.isAlive()&&Objects.equals(frozen,configuration());}
    /** Fresh native footer and native ordered tab rows; unrecovered labels require explicit bindings. */
    private boolean cookie(){
        if(!(client.gui.hud.getTabList() instanceof PlayerListHudAccessor accessor))return false;
        String footer=accessor.getFooter()==null?"":accessor.getFooter().getString();
        var tab=accessor.veltraph$getPlayerInfos().stream().map(info->info.getTabListDisplayName()).filter(Objects::nonNull)
                .map(component->MenuSnapshot.clean(component.getString())).filter(line->!line.isBlank()).toList();
        return CookieEvidence.active(footer,tab,config.get(AutoSellMaintenanceConfig.AUTOSELL_COOKIE_ACTIVE_LINE),config.get(AutoSellMaintenanceConfig.AUTOSELL_COOKIE_GRAMMAR_JSON));
    }
    /** Root invokes this after emergency and terminal handlers. It sends no packets or changes keys. */
    public Optional<String> activationReadiness(){
        if(!client.isSameThread())throw new IllegalStateException("Auto-sell observation requires client thread");
        if(!config.get(AutoSellMaintenanceConfig.AUTOSELL_MAINTENANCE_ENABLED))return Optional.of("Enable leased auto-sell explicitly");
        if(hasPendingCleanup())return Optional.of("Owned selling cleanup remains pending");
        long now=now(),cooldown=config.get(AutoSellMaintenanceConfig.AUTOSELL_COOLDOWN_MS),timeout=config.get(AutoSellMaintenanceConfig.AUTOSELL_MAINTENANCE_TIMEOUT_MS);
        if(cooldown<1||timeout<1000)return Optional.of("Bounded positive cooldown/deadline required");
        if(now<lastObservation||lastAttempt>=0&&now-lastAttempt<cooldown)return Optional.of("Auto-sell cooldown or clock reversal");
        if(!closed()||client.level==null||client.getConnection()==null||!client.player.isAlive())return Optional.of("Living native player with closed inventory required");
        int max=config.get(AutoSellMaintenanceConfig.AUTOSELL_MAX_EMPTY_SLOTS);if(max<0||max>2)return Optional.of("Source empty-slot threshold must be 0..2");
        int empty=0;for(var stack:client.player.getInventory().getNonEquipmentItems())if(stack.isEmpty())empty++;
        if(!CookieEvidence.pressureReady(empty,max,false))return Optional.of("Native non-equipment inventory pressure threshold not met");
        if(!cookie())return Optional.of("Exact current native active-cookie line missing or ambiguous");
        if(config.get(MiscConfig.AUTO_SELL_MODE)==AutoSellMode.BAZAAR){
            try{var plan=BazaarCustodyPlan.parse(config.get(SellingConfig.SELLING_BAZAAR_PROFILE_JSON));
                // Closed gameplay has actual main-stack evidence; a sacks-only configured count is not a native observation.
                if(plan.inventory().isEmpty()||plan.inventory().stream().map(i->i.unitValue().multiply(java.math.BigDecimal.valueOf(i.quantity()))).reduce(java.math.BigDecimal.ZERO,java.math.BigDecimal::add).signum()<=0)
                    return Optional.of("Automatic Bazaar lease requires current exact positive-value main inventory custody; sacks-only readiness is unobserved");
            }catch(RuntimeException invalid){return Optional.of("Invalid explicit Bazaar custody contract: "+invalid.getMessage());}
        }else try{var rules=SellingRule.parse(config.get(SellingConfig.SELLING_RULES_JSON));var mode=config.get(MiscConfig.AUTO_SELL_MODE);
            var available=rules.stream().filter(r->r.channel()==mode&&r.matches(SellingMenuAccess.describeInventoryStack(client.player.getInventory().getItem(r.inventoryIndex()),r.inventoryIndex()))).toList();
            if(mode==AutoSellMode.GEORGE_PETS&&!config.get(GeorgeBatchConfig.GEORGE_BATCH_PROFILE).isBlank()){if(available.isEmpty()||available.stream().anyMatch(r->r.total().signum()<=0))return Optional.of("Approved positive-value George queue required");}
            else if(available.size()!=1||available.getFirst().total().signum()<=0)return Optional.of("One exact authorized positive-value stack required");}
        catch(RuntimeException invalid){return Optional.of("Invalid exact stack contract: "+invalid.getMessage());}
        var ready=switch(config.get(MiscConfig.AUTO_SELL_MODE)){case GEORGE_PETS->georgeChild.ready();case BAZAAR->bazaarChild.preStartCheck();default->child.preStartCheck();};if(ready.isPresent())return ready;
        return maintenance.state()==FarmMaintenanceLease.State.PREPARED?Optional.empty():maintenance.startEligibility(this);
    }
    @Override public Optional<String> preStartCheck(){return activationReadiness();}
    /** Deferred: no native action, input release, screen close, slot or movement change before commit. */
    @Override public void onEnable(){player=client.player;world=client.level;connection=client.getConnection();frozen=configuration();childMode=config.get(MiscConfig.AUTO_SELL_MODE);pending=true;cancelled=false;token=null;}
    public void committed(FarmMaintenanceLease.Token committed){if(!isEnabled()||!pending||committed==null||maintenance.tokenFor(this)!=committed)throw new IllegalStateException("Auto-sell commit changed owner");token=committed;lastAttempt=now();}
    @Override public void onTick(){if(pending){pending=false;lastAttempt=now();runtime.start(now(),config.get(AutoSellMaintenanceConfig.AUTOSELL_MAINTENANCE_TIMEOUT_MS));}else runtime.tick(now());lastObservation=now();if(!runtime.active()&&isEnabled())setEnabled(false);}
    private void cancelOwned(){if(cancelled)return;cancelled=true;child.setEnabled(false);georgeChild.cancel();bazaarChild.setEnabled(false);if(token==null)token=maintenance.tokenFor(this);maintenance.cancel(token,"Auto-sell cancelled; no farm resume");}
    @Override public void onDisable(){if(maintenance.isCompleting(this)){pending=false;child.setEnabled(false);georgeChild.cancel();bazaarChild.setEnabled(false);return;}runtime.cancel();cancelOwned();pending=false;}
    @Override public void maintenanceTick(){child.maintenanceTick();georgeChild.maintenanceTick();}
    @Override public boolean hasPendingCleanup(){return child.hasPendingCleanup()||georgeChild.hasPendingCleanup();}
    @Override public boolean requiresSkyBlock(){return true;}
    @Override public Optional<GameArea> requiredArea(){return Optional.of(GameArea.GARDEN);}
    @Override public boolean allowsScreen(String title){if(childMode==AutoSellMode.BAZAAR)return bazaarChild.allowsScreen(title);return childMode==AutoSellMode.GEORGE_PETS?georgeChild.allowsScreen(title):child.allowsScreen(title);}
    @Override public void onServerMessage(String line){if(childMode==AutoSellMode.BAZAAR&&isEnabled())bazaarChild.onServerMessage(line);else if(childMode==AutoSellMode.GEORGE_PETS&&isEnabled())georgeChild.message(line);else if(child.isEnabled())child.onServerMessage(line);maintenance.onServerMessage(line);}
    @Override public boolean consumeExpectedTeleport(Vec3 position,long time){return isEnabled()&&maintenance.consumeExpectedTeleport(position,time);}
    public FarmAutoSellRuntime.State workflowState(){return runtime.state();}
    public Optional<SellingAcknowledgment> acknowledgedSale(){return runtime.state()==FarmAutoSellRuntime.State.COMPLETE&&childMode!=AutoSellMode.BAZAAR?(childMode==AutoSellMode.GEORGE_PETS?georgeChild.acknowledgment():child.acknowledgedSale()):Optional.empty();}
    /** Individually acknowledged George outcomes survive partial queue failure; callers deduplicate outcomeId. */
    public List<SellingAcknowledgment> acknowledgedGeorgeSales(){return childMode==AutoSellMode.GEORGE_PETS?georgeChild.acknowledgments():List.of();}
    public List<GeorgeSaleIdentity> acknowledgedGeorgeSourceIdentities(){return childMode==AutoSellMode.GEORGE_PETS?georgeChild.acknowledgedSourceIdentities():List.of();}
    public Optional<BazaarCustodyModule.Acknowledgment> acknowledgedBazaarSale(){return runtime.state()==FarmAutoSellRuntime.State.COMPLETE&&childMode==AutoSellMode.BAZAAR?bazaarChild.acknowledgedSale():Optional.empty();}
    public dev.veltraph.client.inventory.selling.BazaarCustodyRuntime.State bazaarSellingState(){return bazaarChild.sellingState();}
    public SellingRuntime.State sellingState(){return childMode==AutoSellMode.GEORGE_PETS?georgeChild.sellingState():child.sellingState();}
    public String maintenanceSettings(){return configuration();}
    private static Map<EquipmentSlot,ItemStack> gear(net.minecraft.client.player.LocalPlayer player){var result=new EnumMap<EquipmentSlot,ItemStack>(EquipmentSlot.class);for(var slot:List.of(EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET,EquipmentSlot.OFFHAND))result.put(slot,player.getItemBySlot(slot).copy());return Map.copyOf(result);}
    /** Actual policy-creation provenance; generic supplied settings closures are never accepted by OWNED_USE. */
    public final class GameplaySettings implements java.util.function.Supplier<String> {
        private dev.veltraph.client.rewarp.mc.FarmMaintenanceCoordinator.Policy issuedPolicy;
        private GameplaySettings(){}
        @Override public String get(){return configuration();}
        private final class Snapshot {
            final GameplaySettings issued=GameplaySettings.this;final Object policy=issuedPolicy;
            final dev.veltraph.client.config.MapConfigService.StoredStateSnapshot settings;
            private Snapshot(dev.veltraph.client.config.MapConfigService.StoredStateSnapshot settings){this.settings=settings;}
        }
        public Object snapshot(Object expectedHelper,dev.veltraph.client.config.ConfigService expectedStore,Object expectedPolicy){
            if(expectedHelper!=McFarmAutoSellModule.this||expectedStore!=config||expectedPolicy==null||expectedPolicy!=issuedPolicy||!client.isSameThread())return null;
            var stored=config instanceof dev.veltraph.client.config.MapConfigService m?m.storedStateSnapshot():config instanceof dev.veltraph.client.config.JsonConfigService j?j.storedStateSnapshot():null;
            if(stored==null)return null;
            var actual=new Snapshot(stored);return snapshotCurrent(actual)?actual:null;
        }
        /** Private known backing state only; never calls the settings/loadout supplier. */
        public boolean snapshotCurrent(Object exact){return exact instanceof McFarmAutoSellModule.GameplaySettings.Snapshot s&&s.issued==this&&s.policy==issuedPolicy&&client.isSameThread()&&s.settings.current();}
    }
    public FarmMaintenanceCoordinator.Policy maintenancePolicy(){var issuedSettings=new GameplaySettings();var issuedPolicy=new FarmMaintenanceCoordinator.Policy(Set.of(GameArea.GARDEN),screen->screen instanceof AbstractContainerScreen<?> &&allowsScreen(screen.getTitle().getString()),issuedSettings,new FarmMaintenanceCoordinator.LoadoutProof(){
        public Object captureBinding(){return client.player==null||client.level==null||client.getConnection()==null?null:new Binding(client.player,client.level,client.getConnection(),gear(client.player));}
        public boolean restored(Object binding,Object witness){return binding instanceof Binding b&&witness instanceof BooleanSupplier proof&&client.player==b.player()&&client.level==b.world()&&client.getConnection()==b.connection()
            &&b.gear().entrySet().stream().allMatch(e->ItemStack.matches(e.getValue(),client.player.getItemBySlot(e.getKey())))&&proof.getAsBoolean();}
    },config.get(AutoSellMaintenanceConfig.AUTOSELL_MAINTENANCE_TIMEOUT_MS));issuedSettings.issuedPolicy=issuedPolicy;return issuedPolicy;}
    @Override public String diagnostics(){return runtime.status()+" | "+(childMode==AutoSellMode.BAZAAR?bazaarChild.diagnostics():childMode==AutoSellMode.GEORGE_PETS?georgeChild.diagnostics():child.diagnostics())+" | "+maintenance.status();}
}
