package dev.veltraph.client.chocolate;

import java.math.BigDecimal;
import java.util.*;

/** Full native identity handles are opaque to the pure planner. Port validates them immediately before input. */
public record ChocolateFactoryObservation(String scope,Object session,Object menu,Object screen,int revision,long observedMillis,
        String title,boolean profileVerified,boolean cursorEmpty,Map<Integer,Row> rows,Optional<BigDecimal> balance,
        Optional<BigDecimal> production,Optional<BigDecimal> capacity,Optional<BigDecimal> prestigeProgress,Optional<BigDecimal> prestigeGoal,
        Map<String,Quote> quotes,Map<String,ResultTarget> targets,boolean contextBound) {
    /** Legacy exact-marker observations use that marker as their independent context evidence. */
    public ChocolateFactoryObservation(String scope,Object session,Object menu,Object screen,int revision,long observedMillis,String title,boolean verified,boolean cursor,Map<Integer,Row> rows,Optional<BigDecimal> balance,Optional<BigDecimal> production,Optional<BigDecimal> capacity,Optional<BigDecimal> progress,Optional<BigDecimal> goal,Map<String,Quote> quotes,Map<String,ResultTarget> targets){this(scope,session,menu,screen,revision,observedMillis,title,verified,cursor,rows,balance,production,capacity,progress,goal,quotes,targets,verified);}
    public ChocolateFactoryObservation(String scope,Object session,Object menu,Object screen,int revision,long observedMillis,String title,boolean verified,boolean cursor,Map<Integer,Row> rows,Optional<BigDecimal> balance,Optional<BigDecimal> production,Optional<BigDecimal> capacity,Optional<BigDecimal> progress,Optional<BigDecimal> goal,Map<String,Quote> quotes){this(scope,session,menu,screen,revision,observedMillis,title,verified,cursor,rows,balance,production,capacity,progress,goal,quotes,quoteTargets(quotes),verified);}
    public record ResultTarget(String id,ChocolateFactoryProfile.Kind kind,int slot,int level,Object fullIdentity) {}
    private static Map<String,ResultTarget> quoteTargets(Map<String,Quote> quotes){var result=new HashMap<String,ResultTarget>();for(var q:quotes.values())result.put(q.id(),new ResultTarget(q.id(),q.kind(),q.slot(),q.level(),q.fullIdentity()));return Map.copyOf(result);}
    public record Row(int slot,boolean playerInventory,String itemId,String name,int count,List<String> lore,Object fullIdentity){public Row{lore=List.copyOf(lore);}}
    public record Quote(String id,ChocolateFactoryProfile.Kind kind,int slot,int level,BigDecimal price,Object fullIdentity,
            Optional<BigDecimal> productionDelta,String assumption) {}
    public ChocolateFactoryObservation {rows=Map.copyOf(rows);quotes=Map.copyOf(quotes);targets=Map.copyOf(targets);}
    public boolean fresh(long now,long limit){return now>=observedMillis&&now-observedMillis<=limit;}
}
