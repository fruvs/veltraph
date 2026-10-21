package dev.veltraph.client.metrics;

import java.math.BigDecimal;
import java.util.*;

/** Passive session accounting. Only native receipts plus exact aggregate delivery credit pickups. */
public final class LocalSessionMetrics {
    public enum Counter { NATIVE_BLOCKS_MINED, NATIVE_MOB_KILLS, NATIVE_FISH_CAUGHT, CONFIRMED_BOSS_KILLS,
        CONFIRMED_TREES, CONFIRMED_FISH_HUNTED, CONFIRMED_SLAYER_QUESTS, CONFIRMED_GHOST_KILLS,
        CONFIRMED_ZEALOT_KILLS, NATIVE_ITEMS_PICKED_UP, SORROWS_COLLECTED,
        SUMMONING_EYES_COLLECTED, RARE_ITEMS_COLLECTED, LUSHLILACS_COLLECTED }
    public record Context(Object player,Object world,Object connection) {
        public Context {Objects.requireNonNull(player);Objects.requireNonNull(world);Objects.requireNonNull(connection);}
        boolean same(Context o){return o!=null&&player==o.player&&world==o.world&&connection==o.connection;}
    }
    /** A producer generation must change when its underlying cumulative counter resets. */
    public record Series(String producer,UUID generation) {public Series {if(producer==null||producer.isBlank()||producer.length()>128)throw new IllegalArgumentException();Objects.requireNonNull(generation);}}
    public record Snapshot(UUID session,Map<Counter,Long> counts,Map<String,BigDecimal> xpGained,
        BigDecimal rngPercent,BigDecimal luckRatioPercent,long sorrowNotices,Map<String,Long> collectedByFingerprint,
        BigDecimal knownCollectedValue,BigDecimal completeCollectedValue,long unpricedUnits,
        BigDecimal knownProceeds,BigDecimal knownCosts,BigDecimal completeRealizedNet,long unknownCashEvents,
        Double nativeBlocksPerSecond,long rejectedPickupGroups) {
        public Snapshot {counts=Map.copyOf(counts);xpGained=Map.copyOf(xpGained);collectedByFingerprint=Map.copyOf(collectedByFingerprint);}
        /** Absent means unobserved, rather than pretending the whole roster is measured. */
        public OptionalLong count(Counter key){var n=counts.get(key);return n==null?OptionalLong.empty():OptionalLong.of(n);}
    }
    private record Stream(Series series,String name) {}
    private record Sample(long serial,BigDecimal total) {}
    private record Rate(long time,long units) {}
    private static final class Pickup {final long baseline,started;long units;Pickup(long baseline,long now){this.baseline=baseline;started=now;}}
    private final UUID session=UUID.randomUUID();private final long started,bpsWindow,pickupTimeout,noticeWindow;
    private final MetricContracts contracts;private Context context;private long lastNow,lastPickupSerial,lastInventorySerial,lastMessageSerial;
    private final Map<Stream,Sample> samples=new HashMap<>();private final EnumMap<Counter,Long> counts=new EnumMap<>(Counter.class);
    private final Map<String,UUID> generations=new HashMap<>();private final Set<Series> retired=new HashSet<>();
    private final Map<String,BigDecimal> xp=new TreeMap<>();private final Map<String,Long> inventory=new HashMap<>(),collected=new HashMap<>();
    private final Map<String,Pickup> pending=new HashMap<>();private final ArrayDeque<Rate> rates=new ArrayDeque<>();
    private final Set<String> financialReceipts=new HashSet<>();private final List<FinancialActionReceipt> financialEvidence=new ArrayList<>();private final Map<String,Long> notices=new HashMap<>();
    private BigDecimal rng,luckRatio,proceeds=BigDecimal.ZERO,costs=BigDecimal.ZERO;private long sorrowNotices,rejected,unknownCash;
    public LocalSessionMetrics(MetricContracts contracts,long now,long bpsWindow,long pickupTimeout,long noticeWindow) {
        this.contracts=Objects.requireNonNull(contracts);if(now<0||bpsWindow<1000||bpsWindow>60000||pickupTimeout<100||pickupTimeout>30000||noticeWindow<0||noticeWindow>30000)throw new IllegalArgumentException("Explicit bounded observation intervals required");
        started=lastNow=now;this.bpsWindow=bpsWindow;this.pickupTimeout=pickupTimeout;this.noticeWindow=noticeWindow;
    }
    public void bind(Context next,Map<String,Long> actualInventory,long now) {
        if(!time(now))throw new IllegalArgumentException("Clock regressed");if(context!=null&&context.same(next))return;
        validateInventory(actualInventory);context=Objects.requireNonNull(next);pending.clear();inventory.clear();inventory.putAll(actualInventory);
        lastPickupSerial=lastInventorySerial=lastMessageSerial=0;notices.clear();counts.putIfAbsent(Counter.NATIVE_ITEMS_PICKED_UP,0L);
    }
    public void unbind(long now){if(!time(now))throw new IllegalArgumentException("Clock regressed");context=null;pending.clear();inventory.clear();notices.clear();}
    private boolean time(long now){if(now<lastNow)return false;lastNow=now;return true;}
    private boolean valid(Context ctx,long now){return context!=null&&context.same(ctx)&&time(now);}
    private static void validateInventory(Map<String,Long> items){if(items.size()>4096||items.entrySet().stream().anyMatch(e->e.getKey()==null||e.getKey().isBlank()||e.getValue()==null||e.getValue()<0))throw new IllegalArgumentException("Invalid full-identity inventory");}
    public boolean cumulative(Context ctx,Series series,Counter metric,long serial,long total,long now) {
        if(!valid(ctx,now)||series==null||metric==null||serial<1||total<0||!Set.of(Counter.NATIVE_BLOCKS_MINED,Counter.NATIVE_MOB_KILLS,Counter.NATIVE_FISH_CAUGHT,Counter.CONFIRMED_BOSS_KILLS,Counter.CONFIRMED_TREES,Counter.CONFIRMED_FISH_HUNTED,Counter.CONFIRMED_SLAYER_QUESTS,Counter.CONFIRMED_GHOST_KILLS,Counter.CONFIRMED_ZEALOT_KILLS).contains(metric))return false;
        return sample(series,metric.name(),serial,BigDecimal.valueOf(total),(delta)->{
            long units=delta.longValueExact();counts.put(metric,Math.addExact(counts.getOrDefault(metric,0L),units));if(metric==Counter.NATIVE_BLOCKS_MINED&&units>0)rates.addLast(new Rate(now,units));
        },()->counts.putIfAbsent(metric,0L));
    }
    private boolean sample(Series series,String name,long serial,BigDecimal total,java.util.function.Consumer<BigDecimal> credit,Runnable baseline) {
        if(retired.contains(series))return false;
        var knownGeneration=generations.get(series.producer());
        if(knownGeneration!=null&&!knownGeneration.equals(series.generation())){
            if(retired.size()>=4096)return false;
            retired.add(new Series(series.producer(),knownGeneration));
        }
        generations.put(series.producer(),series.generation());
        var key=new Stream(series,name);var prior=samples.get(key);if(prior==null&&samples.size()>=4096)return false;
        if(prior!=null&&(serial<=prior.serial()||total.compareTo(prior.total())<0))return false;
        try{if(prior==null)baseline.run();else credit.accept(total.subtract(prior.total()));samples.put(key,new Sample(serial,total));return true;}catch(ArithmeticException unsupported){return false;}
    }
    public boolean xpTotal(Context ctx,Series series,String kind,long serial,BigDecimal total,long now) {
        if(!valid(ctx,now)||series==null||kind==null||serial<1||total==null||total.signum()<0||total.precision()>24||!(kind.startsWith("SKILL_XP:")||Set.of("SLAYER_XP_TOTAL","HOTF_XP_TOTAL","WHISPERS_TOTAL").contains(kind)))return false;
        return sample(series,kind,serial,total,d->xp.merge(kind,d,BigDecimal::add),()->xp.putIfAbsent(kind,BigDecimal.ZERO));
    }
    /** Source level+percentage projections have rounding uncertainty; they never merge into exact XP. */
    public boolean estimatedSkillXpTotal(Context ctx,Series series,String skill,long serial,long total,long now){
        if(!valid(ctx,now)||series==null||serial<1||total<0||!SourceSkillXpProjection.isSkill(skill))return false;
        return sample(series,"SOURCE_ESTIMATED_SKILL_XP:"+skill,serial,BigDecimal.valueOf(total),d->xp.merge("SOURCE_ESTIMATED_SKILL_XP:"+skill,d,BigDecimal::add),()->xp.putIfAbsent("SOURCE_ESTIMATED_SKILL_XP:"+skill,BigDecimal.ZERO));
    }
    /** Received wire serial, not an item disappearance or an outgoing use/click request. */
    public boolean pickupReceipt(Context ctx,long serial,String fullFingerprint,int amount,long now) {
        if(!valid(ctx,now)||serial<=lastPickupSerial||amount<1||amount>4096||fullFingerprint==null||fullFingerprint.isBlank())return false;
        lastPickupSerial=serial;if(pending.size()>=128)return false;
        var p=pending.computeIfAbsent(fullFingerprint,k->new Pickup(inventory.getOrDefault(k,0L),now));
        try{p.units=Math.addExact(p.units,amount);return true;}catch(ArithmeticException bad){pending.remove(fullFingerprint);rejected++;return false;}
    }
    /** Exact aggregate equality prevents multiple same-item drops crediting one inventory delta. */
    public void inventory(Context ctx,long revision,Map<String,Long> actual,long now) {
        if(!valid(ctx,now)||revision<=lastInventorySerial)return;validateInventory(actual);lastInventorySerial=revision;
        for(var entry:new ArrayList<>(pending.entrySet())){
            String key=entry.getKey();var p=entry.getValue();long current=actual.getOrDefault(key,0L);long expected;
            try{expected=Math.addExact(p.baseline,p.units);}catch(ArithmeticException bad){pending.remove(key);rejected++;continue;}
            if(current==expected){creditPickup(key,p.units);pending.remove(key);}
            else if(current<p.baseline||current>expected||now-p.started>pickupTimeout){pending.remove(key);rejected++;}
        }
        for(var e:actual.entrySet())if(!pending.containsKey(e.getKey()))inventory.put(e.getKey(),e.getValue());
        for(var key:new ArrayList<>(inventory.keySet()))if(!actual.containsKey(key)&&!pending.containsKey(key))inventory.remove(key);
    }
    private void creditPickup(String fingerprint,long units) {
        counts.merge(Counter.NATIVE_ITEMS_PICKED_UP,units,Math::addExact);collected.merge(fingerprint,units,Math::addExact);
        var item=contracts.items().get(fingerprint);if(item==null)return;
        for(var role:item.roles())counts.merge(switch(role){case SORROW->Counter.SORROWS_COLLECTED;case SUMMONING_EYE->Counter.SUMMONING_EYES_COLLECTED;case RARE_ITEM->Counter.RARE_ITEMS_COLLECTED;case LUSHLILAC->Counter.LUSHLILACS_COLLECTED;},units,Math::addExact);
    }
    public void message(Context ctx,MetricContracts.Channel channel,long serial,String text,long now,Series signalSeries) {
        if(!valid(ctx,now)||channel==MetricContracts.Channel.PLAYER_CHAT||serial<=lastMessageSerial||text==null||text.length()>2048)return;lastMessageSerial=serial;
        String line=text.replaceAll("§[0-9A-FK-ORa-fk-or]","");
        var matched=contracts.signals().stream().filter(s->s.channel()==channel&&s.parse(line).isPresent()).toList();
        if(matched.size()==1){var s=matched.getFirst();var n=s.parse(line).orElseThrow();if(s.kind()==MetricContracts.SignalKind.RNG_PERCENT)rng=n;else if(s.kind()==MetricContracts.SignalKind.LUCK_RATIO_PERCENT)luckRatio=n;else xpTotal(ctx,signalSeries,s.kind()==MetricContracts.SignalKind.SKILL_XP_TOTAL?"SKILL_XP:"+s.skill():s.key(),serial,n,now);}
        if(channel==MetricContracts.Channel.SYSTEM&&line.matches("RARE DROP! Sorrow(?: \\([0-9]+(?:\\.[0-9]+)?% Magic Find\\))?")){
            notices.entrySet().removeIf(e->now-e.getValue()>noticeWindow);var prior=notices.get(line);if(prior==null||now-prior>noticeWindow){notices.put(line,now);sorrowNotices++;}
        }
    }
    /** Feed only an already confirmed sale/acquisition outcome. Never infer cash from chat/purse. */
    public boolean financial(Context ctx,String outcomeId,boolean expense,BigDecimal amount,long now) {
        if(!valid(ctx,now)||outcomeId==null||outcomeId.isBlank()||financialReceipts.size()>=4096||financialReceipts.contains(outcomeId)||amount!=null&&(amount.signum()<0||amount.precision()>24))return false;
        financialReceipts.add(outcomeId);if(amount==null)unknownCash++;else if(expense)costs=costs.add(amount);else proceeds=proceeds.add(amount);return true;
    }
    public boolean financial(Context ctx,FinancialActionReceipt receipt,long now){
        if(ctx==null||receipt==null||!receipt.scope().same(new FinancialOutcomeBindings.Scope(ctx.player(),ctx.world(),ctx.connection())))return false;
        if(!financial(ctx,receipt.id(),receipt.expense(),receipt.coinAmountOrNull(),now))return false;
        financialEvidence.add(receipt);return true;
    }
    /** Bounded read-only local attribution; never exported or priced by this collector. */
    public List<FinancialActionReceipt> financialEvidence(){return List.copyOf(financialEvidence);}
    public Snapshot snapshot(long now) {
        if(!time(now))throw new IllegalArgumentException("Clock regressed");while(!rates.isEmpty()&&now-rates.getFirst().time()>bpsWindow)rates.removeFirst();
        BigDecimal known=BigDecimal.ZERO;long unpriced=0;
        for(var e:collected.entrySet()){var item=contracts.items().get(e.getKey());if(item==null||item.unitValue()==null)unpriced=Math.addExact(unpriced,e.getValue());else known=known.add(item.unitValue().multiply(BigDecimal.valueOf(e.getValue())));}
        Double bps=null;if(counts.containsKey(Counter.NATIVE_BLOCKS_MINED)&&now>started){long units=0;for(var r:rates)units=Math.addExact(units,r.units());bps=units*1000.0/Math.min(now-started,bpsWindow);}
        return new Snapshot(session,counts,xp,rng,luckRatio,sorrowNotices,collected,known,counts.containsKey(Counter.NATIVE_ITEMS_PICKED_UP)&&unpriced==0?known:null,unpriced,proceeds,costs,financialReceipts.isEmpty()||unknownCash>0?null:proceeds.subtract(costs),unknownCash,bps,rejected);
    }
}
