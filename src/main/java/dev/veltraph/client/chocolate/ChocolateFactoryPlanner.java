package dev.veltraph.client.chocolate;

import java.math.*;
import java.util.*;

/** Transparent constant-rate local estimates. Values are never extrapolated into an action quote. */
public final class ChocolateFactoryPlanner {
    private static final MathContext MC=MathContext.DECIMAL128;
    public record Estimate(Optional<BigDecimal> seconds,String explanation) {}
    public record UpgradePlan(String id,BigDecimal price,boolean affordable,Estimate payback,Estimate timeToAfford,String assumption) {}
    public record Plan(String scope,boolean stale,List<UpgradePlan> upgrades,Estimate untilCapacity,Estimate untilPrestige,List<String> notes) {}
    public record GoalComparison(String upgrade,Estimate holding,Estimate buying,String assumption) {}
    public record Collection(int uniqueCollected,int knownTotal,Optional<BigDecimal> ownedCapacity,Optional<BigDecimal> nextRabbitAtMillis,String source) {}
    public record Upkeep(int chocobits,BigDecimal coinsPerSkyBlockYear,BigDecimal additionalMultiplier,String assumption) {}
    public Plan plan(ChocolateFactoryObservation o,long now,long freshnessMillis,BigDecimal reserve){
        if(reserve==null||reserve.signum()<0)throw new IllegalArgumentException("Reserve must be nonnegative");
        boolean stale=!o.fresh(now,freshnessMillis);var plans=new ArrayList<UpgradePlan>();var notes=new ArrayList<String>();
        notes.add("Estimates hold the observed production rate constant; temporary bonuses, click production and later upgrades are excluded.");
        if(!o.profileVerified())notes.add("Profile-specific menu marker is unverified; purchases are unavailable.");
        if(stale)notes.add("Observation is stale; refresh the visible menu before selecting an upgrade.");
        for(var q:o.quotes().values()){
            var delta=q.productionDelta().filter(v->v.signum()>0);
            var payback=new Estimate(delta.map(d->q.price().divide(d,MC)),delta.isPresent()?"Quoted chocolate cost / supplied incremental chocolate per second":"Incremental production is unknown; no payback ranking");
            boolean affordable=o.balance().map(b->b.subtract(reserve).compareTo(q.price())>=0).orElse(false);
            var afford=o.capacity().isPresent()&&q.price().add(reserve).compareTo(o.capacity().get())>0?new Estimate(Optional.empty(),"Quoted cost plus reserve exceeds observed storage"):time(o.balance().map(b->b.subtract(reserve)),Optional.of(q.price()),o.production());
            plans.add(new UpgradePlan(q.id(),q.price(),affordable,payback,afford,q.assumption()==null?"":q.assumption()));
        }
        plans.sort(Comparator.comparing((UpgradePlan p)->p.payback().seconds().orElse(new BigDecimal("1E100"))).thenComparing(UpgradePlan::id));
        return new Plan(o.scope(),stale,List.copyOf(plans),time(o.balance(),o.capacity(),o.production()),time(o.prestigeProgress(),o.prestigeGoal(),o.production()),List.copyOf(notes));
    }
    public static Estimate time(Optional<BigDecimal> progress,Optional<BigDecimal> goal,Optional<BigDecimal> rate){
        if(progress.isEmpty()||goal.isEmpty())return new Estimate(Optional.empty(),"Observed progress or goal is unknown");
        var remaining=goal.get().subtract(progress.get());if(remaining.signum()<=0)return new Estimate(Optional.of(BigDecimal.ZERO),"Observed goal already reached");
        if(rate.isEmpty()||rate.get().signum()<=0)return new Estimate(Optional.empty(),"Positive observed production rate required");
        return new Estimate(Optional.of(remaining.divide(rate.get(),MC)),"Remaining observed chocolate / constant observed chocolate per second");
    }
    public GoalComparison walletGoal(ChocolateFactoryObservation o,String upgrade,BigDecimal goal,BigDecimal reserve){
        var unknown=new Estimate(Optional.empty(),"Exact reachable wallet goal, capacity, current/final rate and cost required");
        var q=o.quotes().get(upgrade);if(goal==null||goal.signum()<0||reserve==null||reserve.signum()<0||o.balance().isEmpty()||o.capacity().isEmpty()||o.production().isEmpty()||o.production().get().signum()<=0||goal.compareTo(o.capacity().get())>0||q==null||q.productionDelta().filter(d->d.signum()>0).isEmpty())return new GoalComparison(upgrade,unknown,unknown,"Unknown composition is not inferred");
        var holding=time(o.balance(),Optional.of(goal),o.production());
        if(q.price().add(reserve).compareTo(o.capacity().get())>0)return new GoalComparison(upgrade,holding,unknown,"Upgrade cannot be funded within observed storage and reserve");
        var wait=time(Optional.of(o.balance().get().subtract(reserve)),Optional.of(q.price()),o.production()).seconds().orElseThrow();
        var after=o.balance().get().add(o.production().get().multiply(wait)).subtract(q.price());
        var buy=wait.add(time(Optional.of(after),Optional.of(goal),Optional.of(o.production().get().add(q.productionDelta().get()))).seconds().orElseThrow());
        return new GoalComparison(upgrade,holding,new Estimate(Optional.of(buy),"Fund quoted cost, deduct spending, then use supplied final rate"),q.assumption());
    }
    public Plan plan(ChocolateFactoryProfile p,ChocolateFactoryObservation o,long now,BigDecimal reserve){
        var base=plan(o,now,p.freshnessMillis(),reserve);var prestige=base.untilPrestige();var notes=new ArrayList<>(base.notes());
        if(!p.progressAtCapacityVerified()&&prestige.seconds().isPresent()&&base.untilCapacity().seconds().isPresent()&&prestige.seconds().get().compareTo(base.untilCapacity().seconds().get())>0){prestige=new Estimate(Optional.empty(),"Prestige estimate would extend past full storage; progress-at-capacity semantics are unverified");}
        var capacity=base.untilCapacity();var upgrades=base.upgrades();
        if(p.usesCapturedLayout()){
            var tower=CapturedChocolateFactoryParser.tower(o.rows());if(tower.isPresent()&&tower.get().status().equals("ACTIVE")){
                notes.add("Time Tower is active; constant-rate estimates beyond its observed expiry are unsupported. Charges do not imply future activations.");var horizon=tower.get().activeRemainingSeconds();capacity=bounded(capacity,horizon);prestige=bounded(prestige,horizon);var limited=new ArrayList<UpgradePlan>();for(var u:upgrades)limited.add(new UpgradePlan(u.id(),u.price(),u.affordable(),bounded(u.payback(),horizon),bounded(u.timeToAfford(),horizon),u.assumption()));upgrades=List.copyOf(limited);
            }
        }
        return new Plan(base.scope(),base.stale(),upgrades,capacity,prestige,List.copyOf(notes));
    }
    private static Estimate bounded(Estimate e,OptionalLong horizon){if(e.seconds().isEmpty())return e;if(horizon.isEmpty()||e.seconds().get().compareTo(BigDecimal.valueOf(horizon.getAsLong()))>0)return new Estimate(Optional.empty(),"Estimate extends past the observed Time Tower expiry; future production is not extrapolated");return e;}
    public GoalComparison walletGoal(ChocolateFactoryProfile p,ChocolateFactoryObservation o,String upgrade,BigDecimal goal,BigDecimal reserve){var comparison=walletGoal(o,upgrade,goal,reserve);if(!p.usesCapturedLayout())return comparison;var tower=CapturedChocolateFactoryParser.tower(o.rows());if(tower.isEmpty()||!tower.get().status().equals("ACTIVE"))return comparison;return new GoalComparison(upgrade,bounded(comparison.holding(),tower.get().activeRemainingSeconds()),bounded(comparison.buying(),tower.get().activeRemainingSeconds()),comparison.assumption()+"; no later Tower activation assumed");}
    public static Optional<Collection> collection(ChocolateFactoryProfile p,ChocolateFactoryObservation o){
        if(p.usesCapturedLayout()){var s=CapturedChocolateFactoryParser.parse(o.title(),o.rows());if(s.rabbitsOwned().isEmpty()||s.collectionTotal().isEmpty())return Optional.empty();return Optional.of(new Collection(s.rabbitsOwned().getAsInt(),s.collectionTotal().getAsInt(),s.rabbitCapacity().isPresent()?Optional.of(BigDecimal.valueOf(s.rabbitCapacity().getAsInt())):Optional.empty(),Optional.empty(),"Captured current main-menu counts; uncaptured rabbit pages and future timing remain unknown"));}
        if(p.rabbitsOwned()==null||p.collectionTotal()==null)return Optional.empty();try{var owned=p.rabbitsOwned().read(o.rows());var total=p.collectionTotal().read(o.rows());if(owned.isEmpty()||total.isEmpty())return Optional.empty();int n=owned.get().intValueExact(),t=total.get().intValueExact();if(n<0||t<n)return Optional.empty();return Optional.of(new Collection(n,t,p.rabbitCapacity()==null?Optional.empty():p.rabbitCapacity().read(o.rows()),Optional.empty(),"Exact configured visible collection fields; future rabbit timing and duplicate outcomes unknown"));}catch(ArithmeticException invalid){return Optional.empty();}
    }
    /** 0.27.1 published rule; never authorizes release/consumption or purchases. */
    public static Upkeep chocobitUpkeep(int count){if(count<0)throw new IllegalArgumentException("Negative Chocobits");return new Upkeep(count,BigDecimal.valueOf(count).multiply(BigDecimal.valueOf(1_000_000)),BigDecimal.valueOf(count).multiply(new BigDecimal("0.2")),"Hypixel 0.27.1 (2026-09-01): 1M coins per SkyBlock year per Chocobit and +0.2x each; year duration and multiplier composition are not inferred.");}
}
