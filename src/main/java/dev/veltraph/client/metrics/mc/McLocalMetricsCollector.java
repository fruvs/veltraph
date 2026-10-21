package dev.veltraph.client.metrics.mc;

import dev.veltraph.client.config.*;
import dev.veltraph.client.metrics.*;
import dev.veltraph.client.combat.slayer.SlayerPickupSignal;
import dev.veltraph.client.inventory.NativeStackIdentity;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

/** Read-only bridge. Root owns installation and received-message routing. Never requests statistics. */
public final class McLocalMetricsCollector {
    private final Minecraft client;private final ConfigService config;private final LocalSessionMetrics metrics;
    private LocalSessionMetrics.Context context;private long pickupSerial,inventorySerial,statsSerial,tabSerial,lastStatsReceiptMillis;
    private LocalSessionMetrics.Series signalSeries,tabSeries;private final String frozen;
    private List<String> previousTab=List.of();
    private final OwnerMetricEpochs ownerEpochs=new OwnerMetricEpochs();
    private final McSourceSlayerXpObserver sourceSlayerXp;
    public McLocalMetricsCollector(Minecraft client,ConfigService config,long now){
        this.client=Objects.requireNonNull(client);this.config=Objects.requireNonNull(config);
        sourceSlayerXp=new McSourceSlayerXpObserver(client);
        var contracts=MetricContracts.parse(config.get(LocalMetricsConfig.LOCAL_METRICS_ITEMS),config.get(LocalMetricsConfig.LOCAL_METRICS_SIGNALS));
        frozen=signature();metrics=new LocalSessionMetrics(contracts,now,config.get(LocalMetricsConfig.LOCAL_METRICS_BPS_WINDOW_MS),config.get(LocalMetricsConfig.LOCAL_METRICS_PICKUP_TIMEOUT_MS),config.get(LocalMetricsConfig.LOCAL_METRICS_NOTICE_DEDUP_MS));
    }
    public static String fingerprint(ItemStack item,net.minecraft.core.HolderLookup.Provider registry){return NativeStackIdentity.capture(item.copyWithCount(1),registry).toString();}
    public LocalSessionMetrics model(){return metrics;}
    private String signature(){return config.get(LocalMetricsConfig.LOCAL_METRICS_ITEMS)+"|"+config.get(LocalMetricsConfig.LOCAL_METRICS_SIGNALS)+"|"+config.get(LocalMetricsConfig.LOCAL_METRICS_BPS_WINDOW_MS)+"|"+config.get(LocalMetricsConfig.LOCAL_METRICS_PICKUP_TIMEOUT_MS)+"|"+config.get(LocalMetricsConfig.LOCAL_METRICS_NOTICE_DEDUP_MS);}
    private boolean available(long now){
        if(!client.isSameThread())return false;
        if(!config.get(LocalMetricsConfig.LOCAL_METRICS_ENABLED)||!signature().equals(frozen)||client.player==null||client.level==null||client.getConnection()==null){
            ClientAwardStatsSignal.deactivate(this);sourceSlayerXp.close();if(context!=null)metrics.unbind(now);context=null;lastStatsReceiptMillis=0;ownerEpochs.clear();return false;
        }
        var next=new LocalSessionMetrics.Context(client.player,client.level,client.getConnection());
        if(context==null||context.player()!=next.player()||context.world()!=next.world()||context.connection()!=next.connection()){
            signalSeries=new LocalSessionMetrics.Series("received-cumulative-signals",UUID.randomUUID());tabSeries=new LocalSessionMetrics.Series("source-tab-skill-estimates",UUID.randomUUID());
            context=next;ownerEpochs.clear();metrics.bind(next,inventory(),now);pickupSerial=SlayerPickupSignal.serial(client.level,client.getConnection());inventorySerial=tabSerial=0;previousTab=List.of();lastStatsReceiptMillis=0;
            ClientAwardStatsSignal.activate(this,client.player,client.level,client.getConnection());
            statsSerial=ClientAwardStatsSignal.serial(client.player,client.level,client.getConnection());
        }
        return true;
    }
    private Map<String,Long> inventory(){var out=new HashMap<String,Long>();for(var s:client.player.getInventory().getNonEquipmentItems())if(!s.isEmpty()){try{out.merge(fingerprint(s,client.level.registryAccess()),(long)s.getCount(),Math::addExact);}catch(RuntimeException unsupported){/* unsupported identity cannot be valued or credited */}}return out;}
    /** Passive native pickup receipt + exact same-content inventory polling, never disappearance. */
    public void poll(long now){
        if(!available(now))return;
        sourceSlayerXp.poll(now);
        for(var receipt:ClientAwardStatsSignal.read(client.player,client.level,client.getConnection(),statsSerial)){
            for(var total:receipt.totals().entrySet())metrics.cumulative(context,new LocalSessionMetrics.Series("native-player-stats",receipt.generation()),total.getKey(),receipt.serial(),total.getValue(),now);
            for(var block:receipt.blockTotals().entrySet())metrics.cumulative(context,new LocalSessionMetrics.Series("native-block:"+block.getKey(),receipt.generation()),LocalSessionMetrics.Counter.NATIVE_BLOCKS_MINED,receipt.serial(),block.getValue(),now);
            statsSerial=receipt.serial();lastStatsReceiptMillis=receipt.receivedAt();
        }
        var rows=client.getConnection().getOnlinePlayers().stream().sorted(Comparator.comparingInt(net.minecraft.client.multiplayer.PlayerInfo::getTabListOrder).reversed().thenComparing(p->p.getProfile().name()))
            .map(p->p.getTabListDisplayName()).filter(Objects::nonNull).map(p->p.getString()).toList();
        if(!rows.equals(previousTab)){previousTab=List.copyOf(rows);long revision=++tabSerial;for(var estimate:SourceSkillXpProjection.tabRows(rows))metrics.estimatedSkillXpTotal(context,tabSeries,estimate.skill(),revision,estimate.projectedTotal(),now);}
        if(client.gui.screen()!=null||client.player.containerMenu!=client.player.inventoryMenu||!client.player.containerMenu.getCarried().isEmpty())return;
        for(var receipt:SlayerPickupSignal.read(client.level,client.getConnection(),client.player.getUUID(),pickupSerial)){
            metrics.pickupReceipt(context,receipt.serial(),receipt.content().toString(),receipt.amount(),now);pickupSerial=Math.max(pickupSerial,receipt.serial());
        }
        metrics.inventory(context,++inventorySerial,inventory(),now);
    }
    /** Zero means no received native stats in the current scope. Native stats are not continuous. */
    public long lastStatsReceiptMillis(){return lastStatsReceiptMillis;}
    public long statsReceiptSerial(){return statsSerial;}
    /** Release the local observation subscription when root replaces a session collector. */
    public void close(){ClientAwardStatsSignal.deactivate(this);sourceSlayerXp.close();context=null;lastStatsReceiptMillis=0;}
    /** Current displayed source poll only; never exact earned XP, never merged into Snapshot.xpGained. */
    public Optional<SourceSlayerXpObserver.Snapshot> sourceObservedSlayerXp(){
        return config.get(LocalMetricsConfig.LOCAL_METRICS_ENABLED)&&signature().equals(frozen)?sourceSlayerXp.snapshot():Optional.empty();
    }
    public SourceSlayerXpObserver.Availability sourceObservedSlayerXpAvailability(){
        return config.get(LocalMetricsConfig.LOCAL_METRICS_ENABLED)&&signature().equals(frozen)?sourceSlayerXp.availability():SourceSlayerXpObserver.Availability.UNAVAILABLE;
    }
    public void receivedMessage(MetricContracts.Channel channel,long receiptSerial,String text,long now){if(available(now))metrics.message(context,channel,receiptSerial,text,now,signalSeries);}
    /** Opaque current observation scope for the passive owner bridge. This getter never binds or polls. */
    public Object ownerObservationIdentity(){
        if(!client.isSameThread()||!config.get(LocalMetricsConfig.LOCAL_METRICS_ENABLED)||!signature().equals(frozen)
                ||context==null||client.player!=context.player()||client.level!=context.world()||client.getConnection()!=context.connection())return null;
        return context;
    }
    /** Root feeds already-acknowledged module counters with their stable generation; no chat inference. */
    public void confirmedOwnerCounter(LocalSessionMetrics.Series source,LocalSessionMetrics.Counter counter,long serial,long total,long now){if(available(now))metrics.cumulative(context,source,counter,serial,total,now);}
    public void ownerFreshStart(OwnerMetricEpochs.OwnerCounter kind,long actualBaseline,long now){
        ownerFreshStart("singleton",kind,actualBaseline,now);
    }
    public void ownerTotal(OwnerMetricEpochs.OwnerCounter kind,long actualTotal,long now){
        ownerTotal("singleton",kind,actualTotal,now);
    }
    public void ownerFreshStart(String ownerId,OwnerMetricEpochs.OwnerCounter kind,long actualBaseline,long now){
        if(!available(now))return;var sample=ownerEpochs.freshStart(ownerId,kind,actualBaseline);metrics.cumulative(context,sample.series(),sample.counter(),sample.serial(),sample.cumulativeTotal(),now);
    }
    public void ownerTotal(String ownerId,OwnerMetricEpochs.OwnerCounter kind,long actualTotal,long now){
        if(!available(now))return;ownerEpochs.sample(ownerId,kind,actualTotal).ifPresent(sample->metrics.cumulative(context,sample.series(),sample.counter(),sample.serial(),sample.cumulativeTotal(),now));
    }
    /** Feed an already acknowledged outcome with an owner-unique ID; null value remains unknown. */
    public boolean confirmedFinancialOutcome(String outcomeId,boolean expense,java.math.BigDecimal amount,long now){
        return available(now)&&metrics.financial(context,outcomeId,expense,amount,now);
    }
    public boolean confirmedFinancialOutcome(dev.veltraph.client.metrics.FinancialActionReceipt receipt,long now){
        return available(now)&&metrics.financial(context,receipt,now);
    }
    public java.util.List<dev.veltraph.client.metrics.FinancialActionReceipt> financialEvidence(){return metrics.financialEvidence();}
    public LocalSessionMetrics.Snapshot snapshot(long now){return metrics.snapshot(now);}
}
