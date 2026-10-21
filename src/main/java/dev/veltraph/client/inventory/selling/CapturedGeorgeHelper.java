package dev.veltraph.client.inventory.selling;

import dev.veltraph.client.config.*;
import dev.veltraph.client.core.*;
import dev.veltraph.client.game.*;
import dev.veltraph.client.gui.MenuSnapshot;
import dev.veltraph.client.metrics.*;
import dev.veltraph.client.mixin.PlayerListHudAccessor;
import dev.veltraph.client.navigation.mc.McNavigator;
import dev.veltraph.client.rewarp.FarmMaintenanceLease;
import dev.veltraph.client.rewarp.mc.*;
import dev.veltraph.client.service.FarmServices;
import dev.veltraph.client.world.Vec3;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Deferred captured farm participant for the reviewed source Hub → real NPC batch → independent Garden landing → physical return. */
public final class CapturedGeorgeHelper extends AbstractModule implements FarmMaintenanceCoordinator.GeorgeParticipant,GeorgeFinancialOwner {
    public static final String ID="captured_george";
    /** Root retains existing timer/financial activation on the exact typed native handoff. Hooks cannot grant world admission. */
    public interface WarpHooks {
        boolean beforeCommand(FarmMaintenanceCoordinator.GeorgeCapture capture,FarmMaintenanceLease.Token token,GeorgeWarpPermit permit);
        boolean admitted(FarmMaintenanceCoordinator.GeorgeCapture capture,FarmMaintenanceLease.Token token,GeorgeWarpPermit permit,
                FinancialOutcomeBindings.Scope previous,FinancialOutcomeBindings.Scope current);
    }
    private final Minecraft client;private final FarmServices services;private final ConfigService config;
    private final FarmMaintenanceCoordinator maintenance;private final GameContextService context;private final SellingMenuAccess sellingAccess;
    private final CapturedGeorgeContinuation continuation=new CapturedGeorgeContinuation();
    private WarpHooks hooks;private FarmMaintenanceLease.Token preparedToken,token;
    private CapturedGeorgeContract contract;private CapturedGeorgeContract.FrozenSale frozenSale;private String frozenSettings="",activation="";
    private FarmMaintenanceCoordinator.GeorgeCapture original;private GeorgeSourceHubParent parent;
    private GeorgeBatchRuntime batch;private McGeorgeBatchNpcPort npcPort;
    private SellingAcknowledgment.Denomination runDenomination=SellingAcknowledgment.Denomination.UNKNOWN;
    private boolean pending,cancelled,resumed;private long lastAttempt=-1,lastObservation=-1;private String reason="Idle";
    private record Binding(UUID uuid,Object connection,Map<EquipmentSlot,ItemStack> gear) {}
    public record Snapshot(GeorgeSourceHubParent.State parent,GeorgeBatchRuntime.State batch,SellingRuntime.State sale,
            int acknowledgedPets,boolean pendingWarp,boolean pendingCleanup,boolean resumed,String reason) {}
    public CapturedGeorgeHelper(Minecraft client,FarmServices services,ConfigService config,FarmMaintenanceCoordinator maintenance,GameContextService context) {
        super(ID,"Captured George",ModuleCategory.INTERMEDIARY);
        this.client=Objects.requireNonNull(client);this.services=Objects.requireNonNull(services);this.config=Objects.requireNonNull(config);
        this.maintenance=Objects.requireNonNull(maintenance);this.context=Objects.requireNonNull(context);sellingAccess=new SellingMenuAccess(client);
    }
    public void warpHooks(WarpHooks hooks) {if(isEnabled()||preparedToken!=null)throw new IllegalStateException("Root hooks must be installed before preparation");this.hooks=Objects.requireNonNull(hooks);}
    private long tick(){return services.clock().currentTick();}
    private long now(){return services.clock().nowMillis();}
    public boolean selected(){return !config.get(GeorgeBatchConfig.GEORGE_SOURCE_HUB_PROFILE).isBlank();}
    private boolean closed(){return client.player!=null&&client.gui.screen()==null&&client.player.containerMenu==client.player.inventoryMenu&&client.player.containerMenu.getCarried().isEmpty();}
    public String maintenanceSettings(){return CapturedGeorgeContract.settings(config);}
    private boolean configurationValid(){return contract!=null&&Objects.equals(frozenSettings,maintenanceSettings());}
    private boolean cookie(){
        if(!(client.gui.hud.getTabList() instanceof PlayerListHudAccessor accessor))return false;
        var tab=accessor.veltraph$getPlayerInfos().stream().map(info->info.getTabListDisplayName()).filter(Objects::nonNull)
                .map(line->MenuSnapshot.clean(line.getString())).filter(line->!line.isBlank()).toList();
        return CookieEvidence.active(accessor.getFooter()==null?"":accessor.getFooter().getString(),tab,
                config.get(AutoSellMaintenanceConfig.AUTOSELL_COOKIE_ACTIVE_LINE),config.get(AutoSellMaintenanceConfig.AUTOSELL_COOKIE_GRAMMAR_JSON));
    }
    /** Read-only native/config observation; no slot changes, keys, camera ownership or packets. */
    public Optional<String> activationReadiness(){
        if(!client.isSameThread())throw new IllegalStateException("Captured George observation requires client thread");
        if(hooks==null)return Optional.of("Root George timer/financial hooks are not installed");
        if(hasPendingCleanup())return Optional.of("Owned George escrow restitution remains pending");
        long cooldown=config.get(AutoSellMaintenanceConfig.AUTOSELL_COOLDOWN_MS);
        if(cooldown<1||now()<lastObservation||lastAttempt>=0&&now()-lastAttempt<cooldown)return Optional.of("Bounded George cooldown or monotonic clock required");
        if(!closed()||client.level==null||client.getConnection()==null||!client.player.isAlive())return Optional.of("Closed healthy native George origin required");
        int maximum=config.get(AutoSellMaintenanceConfig.AUTOSELL_MAX_EMPTY_SLOTS),empty=0;
        if(maximum<0||maximum>2)return Optional.of("Source empty-slot pressure threshold must be 0..2");
        for(var stack:client.player.getInventory().getNonEquipmentItems())if(stack.isEmpty())empty++;
        if(!CookieEvidence.pressureReady(empty,maximum,false)||!cookie())return Optional.of("Current native pressure and unambiguous active-cookie evidence required");
        try {CapturedGeorgeContract.read(config).freeze(sellingAccess.read(config.get(SellingConfig.SELLING_CURRENCY_PREFIX)));}
        catch(RuntimeException invalid){return Optional.of("Explicit source George contract: "+invalid.getMessage());}
        return maintenance.state()==FarmMaintenanceLease.State.PREPARED?Optional.empty():maintenance.startEligibility(this);
    }
    @Override public Optional<String> preStartCheck(){return activationReadiness();}
    @Override public boolean prepareActivation(FarmMaintenanceLease.Token candidate){
        if(candidate==null||hooks==null||hasPendingCleanup())return false;
        try {var parsed=CapturedGeorgeContract.read(config);var approvals=parsed.freeze(sellingAccess.read(parsed.currencyPrefix()));
            contract=parsed;frozenSale=approvals;frozenSettings=maintenanceSettings();preparedToken=candidate;return true;}
        catch(RuntimeException invalid){reason="George preparation refused: "+invalid.getMessage();return false;}
    }
    @Override public void cancelPrepared(FarmMaintenanceLease.Token candidate){if(preparedToken==candidate)preparedToken=null;}
    /** No native action occurs in onEnable. The coordinator invokes committed only after its witness and activation guard commit. */
    @Override public void onEnable(){pending=true;cancelled=false;resumed=false;token=null;original=null;parent=null;batch=null;npcPort=null;continuation.clear();activation=UUID.randomUUID().toString();runDenomination=contract==null?SellingAcknowledgment.Denomination.UNKNOWN:contract.denomination();reason="Waiting for exact prepared farm commit";}
    public void committed(FarmMaintenanceLease.Token candidate){
        if(!isEnabled()||!pending||candidate==null||candidate!=preparedToken||maintenance.tokenFor(this)!=candidate||!configurationValid())
            throw new IllegalStateException("Captured George prepared commit changed owner");
        token=candidate;original=maintenance.capturedGeorge(this,token).orElseThrow();lastAttempt=now();reason="Exact farm witness committed; native ingress deferred to owned tick";
    }
    private boolean valid(){
        if(!isEnabled()||token==null||!configurationValid()||original==null)return false;
        var permit=pendingGeorgeWarp().orElse(null);
        return permit!=null?maintenance.validGeorgeTransit(this,token,permit):maintenance.valid(token);
    }
    private GeorgeWarpPermit.Observation observation(){
        var p=client.player;var level=client.level;var connection=client.getConnection();var signal=NativeTeleportSignal.read(client);
        var area=context.current().area();boolean loaded=p!=null&&level!=null&&level.hasChunkAt(p.blockPosition());
        Vec3 body=p==null?null:new Vec3(p.getX(),p.getY(),p.getZ());
        boolean support=p!=null&&loaded&&p.onGround()&&Math.hypot(p.getDeltaMovement().x,p.getDeltaMovement().z)<=.02
                &&new McNavigator(client,services.input(),config).standingClear(body);
        var issued=pendingGeorgeWarp().orElse(null);
        boolean loading=issued!=null&&maintenance.validGeorgeTransit(this,token,issued)&&(p==null||level==null||area==GameArea.UNKNOWN||!loaded);
        return new GeorgeWarpPermit.Observation(p==null?(original==null?null:original.playerId()):p.getUUID(),p,level,connection,area,
                level==null?null:level.dimension().identifier().toString(),body,p!=null&&p.isAlive(),loaded,support,signal.serial(),signal.position(),tick(),loading);
    }
    private GeorgeSourceHubParent createParent(){return new GeorgeSourceHubParent(new GeorgeSourceHubParent.Port(){
        public GeorgeWarpPermit.Observation read(){return observation();}
        public boolean valid(){return CapturedGeorgeHelper.this.valid();}
        public boolean closed(){return CapturedGeorgeHelper.this.closed();}
        public boolean send(String command){
            var permit=pendingGeorgeWarp().orElse(null);
            if(permit==null||!configurationValid()||!closed()||!maintenance.bindGeorgeWarp(CapturedGeorgeHelper.this,token,permit))return false;
            var capture=maintenance.capturedGeorge(CapturedGeorgeHelper.this,token).orElse(null);
            if(capture==null||!hooks.beforeCommand(capture,token,permit)||!maintenance.pendingGeorgeWarp(CapturedGeorgeHelper.this,token,permit))return false;
            client.getConnection().sendCommand(command);return true;
        }
        public boolean commitWarp(GeorgeWarpPermit permit,GeorgeWarpPermit.Observation arrival){
            var before=maintenance.capturedGeorge(CapturedGeorgeHelper.this,token).orElse(null);
            if(before==null||!configurationValid()||!maintenance.admitGeorgeWarp(CapturedGeorgeHelper.this,token,permit,arrival))return false;
            var after=maintenance.capturedGeorge(CapturedGeorgeHelper.this,token).orElse(null);
            if(after==null)return false;
            var previous=new FinancialOutcomeBindings.Scope(before.player(),before.world(),before.connection());
            var current=new FinancialOutcomeBindings.Scope(after.player(),after.world(),after.connection());
            return continuation.record(token,permit,previous,current)&&hooks.admitted(after,token,permit,previous,current);
        }
        public boolean startNpcBatch(){
            if(!configurationValid()||!maintenance.valid(token)||!frozenSale.unchanged(sellingAccess.read(contract.currencyPrefix())))return false;
            npcPort=new McGeorgeBatchNpcPort(client,services,config,contract.hub(),contract.dialogue(),contract.selling(),contract.currencyPrefix(),()->isEnabled()&&configurationValid()&&maintenance.valid(token),()->CapturedGeorgeHelper.this);
            batch=new GeorgeBatchRuntime(npcPort);npcPort.bind(batch);
            return batch.start(frozenSale.plan(),frozenSale.rules(),contract.selling(),tick());
        }
        public FarmAutoSellRuntime.Progress npcBatch(){
            if(batch==null)return FarmAutoSellRuntime.Progress.FAILED;
            batch.tick(tick());return batch.active()?FarmAutoSellRuntime.Progress.ACTIVE:batch.state()==GeorgeBatchRuntime.State.COMPLETE?FarmAutoSellRuntime.Progress.COMPLETE:FarmAutoSellRuntime.Progress.FAILED;
        }
        public void cancelBatch(){if(batch!=null)batch.cancel(tick());}
        public boolean beginPhysicalReturn(){return maintenance.beginRestore(token)&&maintenance.beginReturn(token,(BooleanSupplier)CapturedGeorgeHelper.this::batchComplete);}
        public FarmAutoSellRuntime.Progress physicalReturn(){return switch(maintenance.tickReturn(token)){case ACTIVE->FarmAutoSellRuntime.Progress.ACTIVE;case READY->FarmAutoSellRuntime.Progress.COMPLETE;case FAILED->FarmAutoSellRuntime.Progress.FAILED;};}
    });}
    private boolean batchComplete(){return batch!=null&&batch.state()==GeorgeBatchRuntime.State.COMPLETE&&!batch.cleaning()&&!batch.escrowHeld()&&closed();}
    @Override public void onTick(){
        lastObservation=now();
        if(pending){if(token==null)return;pending=false;parent=createParent();
            try {if(!frozenSale.unchanged(sellingAccess.read(contract.currencyPrefix()))||!parent.start(contract.hub(),contract.garden(),tick(),Math.toIntExact((contract.maintenanceTimeoutMs()+49)/50)))
                fail("Captured George origin changed or initial source ingress refused");}
            catch(RuntimeException invalid){fail("Source George start refused: "+invalid.getMessage());}
            return;
        }
        if(parent==null)return;
        parent.tick(tick());reason=parent.reason();
        if(parent.state()==GeorgeSourceHubParent.State.COMPLETE){resumed=maintenance.resume(token,(BooleanSupplier)this::batchComplete);if(!resumed)fail("Independent same-engine return acknowledgment refused");}
        else if(!parent.active())fail(parent.reason());
    }
    private void fail(String why){reason=why;if(isEnabled())setEnabled(false);}
    @Override public void onDisable(){
        pending=false;continuation.clear();
        if(maintenance.isCompleting(this)){reason="Exact source Hub sales and saved physical pose restored; same farm engine continued";return;}
        if(cancelled)return;cancelled=true;if(parent!=null)parent.cancel();else if(batch!=null)batch.cancel(tick());
        if(token!=null)maintenance.cancel(token,"Captured George cancelled; no retry or farm restart");
    }
    @Override public void maintenanceTick(){if(batch!=null&&batch.cleaning())batch.maintenance(tick());}
    @Override public boolean hasPendingCleanup(){return batch!=null&&batch.cleaning();}
    @Override public Optional<GeorgeWarpPermit> pendingGeorgeWarp(){return parent==null?Optional.empty():parent.pendingWarpPermit();}
    /** Root calls after its higher-priority revocations, before General teleport observation. This only caches received native evidence. */
    @Override public void advanceIssuedGeorgeWarp(){if(isEnabled()&&parent!=null&&pendingGeorgeWarp().isPresent()&&configurationValid())parent.observeNativeWarp();}
    @Override public boolean consumeExpectedTeleport(Vec3 position,long nowMillis){return isEnabled()&&valid()&&(npcPort!=null&&npcPort.consumeExpectedTeleport(position,nowMillis)||parent!=null&&parent.consumeExpectedTeleport(position,tick()));}
    @Override public void onServerMessage(String line){if(!isEnabled())return;if(parent!=null)parent.message(line);if(npcPort!=null)npcPort.message(line);maintenance.onServerMessage(line);}
    @Override public boolean allowsScreen(String title){return isEnabled()&&batch!=null&&batch.active()&&batch.allowsScreen(title);}
    @Override public boolean requiresSkyBlock(){return true;}
    @Override public Optional<GameArea> requiredArea(){return Optional.of(GameArea.GARDEN);}
    public List<SellingAcknowledgment> acknowledgedGeorgeSales(){return npcPort==null?List.of():npcPort.acknowledgments(activation,runDenomination);}
    public List<GeorgeSaleIdentity> acknowledgedGeorgeSourceIdentities(){return npcPort==null?List.of():npcPort.acknowledgedSourceIdentities(activation);}
    @Override public List<FinancialActionReceipt> acknowledgedGeorgeFinancialActions(){return acknowledgedGeorgeSales().stream().map(p->new FinancialActionReceipt(p.outcomeId(),this,FinancialActionReceipt.Kind.SALE,
            p.denomination()==SellingAcknowledgment.Denomination.COINS?FinancialActionReceipt.Denomination.COINS:FinancialActionReceipt.Denomination.UNKNOWN,
            p.proceeds(),new FinancialOutcomeBindings.Scope(p.player(),p.world(),p.connection()),List.of(p.evidence()))).toList();}
    @Override public boolean admittedGeorgeContinuation(FarmMaintenanceLease.Token candidate,GeorgeWarpPermit permit,FinancialOutcomeBindings.Scope previous,FinancialOutcomeBindings.Scope current){
        var captured=maintenance.capturedGeorge(this,candidate).orElse(null);
        return isEnabled()&&candidate==token&&configurationValid()&&captured!=null&&current!=null&&captured.player()==current.player()
                &&captured.world()==current.world()&&captured.connection()==current.connection()&&continuation.consume(candidate,permit,previous,current);
    }
    private static Map<EquipmentSlot,ItemStack> gear(net.minecraft.client.player.LocalPlayer p){var out=new EnumMap<EquipmentSlot,ItemStack>(EquipmentSlot.class);
        for(var slot:List.of(EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET,EquipmentSlot.OFFHAND))out.put(slot,p.getItemBySlot(slot).copy());return Map.copyOf(out);}
    /** Actual policy-creation provenance; generic supplied settings closures are never accepted by OWNED_USE. */
    public final class GameplaySettings implements java.util.function.Supplier<String> {
        private dev.veltraph.client.rewarp.mc.FarmMaintenanceCoordinator.Policy issuedPolicy;
        private GameplaySettings(){}
        @Override public String get(){return maintenanceSettings();}
        private final class Snapshot {
            final GameplaySettings issued=GameplaySettings.this;final Object policy=issuedPolicy;
            final dev.veltraph.client.config.MapConfigService.StoredStateSnapshot settings;
            private Snapshot(dev.veltraph.client.config.MapConfigService.StoredStateSnapshot settings){this.settings=settings;}
        }
        public Object snapshot(Object expectedHelper,dev.veltraph.client.config.ConfigService expectedStore,Object expectedPolicy){
            if(expectedHelper!=CapturedGeorgeHelper.this||expectedStore!=config||expectedPolicy==null||expectedPolicy!=issuedPolicy||!client.isSameThread())return null;
            var stored=config instanceof dev.veltraph.client.config.MapConfigService m?m.storedStateSnapshot():config instanceof dev.veltraph.client.config.JsonConfigService j?j.storedStateSnapshot():null;
            if(stored==null)return null;
            var actual=new Snapshot(stored);return snapshotCurrent(actual)?actual:null;
        }
        /** Private known backing state only; never calls the settings/loadout supplier. */
        public boolean snapshotCurrent(Object exact){return exact instanceof CapturedGeorgeHelper.GameplaySettings.Snapshot s&&s.issued==this&&s.policy==issuedPolicy&&client.isSameThread()&&s.settings.current();}
    }
    public FarmMaintenanceCoordinator.Policy maintenancePolicy(){var issuedSettings=new GameplaySettings();var issuedPolicy=new FarmMaintenanceCoordinator.Policy(Set.of(GameArea.GARDEN,GameArea.HUB),
            screen->screen instanceof AbstractContainerScreen<?> &&allowsScreen(screen.getTitle().getString()),issuedSettings,new FarmMaintenanceCoordinator.LoadoutProof(){
                public Object captureBinding(){return client.player==null||client.getConnection()==null?null:new Binding(client.player.getUUID(),client.getConnection(),gear(client.player));}
                public boolean restored(Object binding,Object witness){return binding instanceof Binding b&&witness instanceof BooleanSupplier proof&&client.player!=null
                        &&b.uuid().equals(client.player.getUUID())&&b.connection()==client.getConnection()&&b.gear().entrySet().stream().allMatch(e->ItemStack.matches(e.getValue(),client.player.getItemBySlot(e.getKey())))&&proof.getAsBoolean();}
            },config.get(AutoSellMaintenanceConfig.AUTOSELL_MAINTENANCE_TIMEOUT_MS));issuedSettings.issuedPolicy=issuedPolicy;return issuedPolicy;}
    public Snapshot snapshot(){return new Snapshot(parent==null?GeorgeSourceHubParent.State.IDLE:parent.state(),batch==null?GeorgeBatchRuntime.State.IDLE:batch.state(),
            batch==null?SellingRuntime.State.IDLE:batch.saleState(),batch==null?0:batch.completedCount(),pendingGeorgeWarp().isPresent(),hasPendingCleanup(),resumed,reason);}
    @Override public String diagnostics(){return snapshot().toString();}
}
