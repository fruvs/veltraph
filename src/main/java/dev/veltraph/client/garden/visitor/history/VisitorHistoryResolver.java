package dev.veltraph.client.garden.visitor.history;

import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** Native adapters receive UNKNOWN until a real profile/Garden confirmation producer is supplied. */
@FunctionalInterface
public interface VisitorHistoryResolver {
    VisitorHistoryStore.Observation observe(String actualVisitorName);
    VisitorHistoryResolver UNKNOWN = name -> new VisitorHistoryStore.Observation(
            VisitorHistoryStore.Newness.UNKNOWN,"Confirmed account/profile/Garden history unavailable");
    static VisitorHistoryResolver scoped(VisitorHistoryStore store,Supplier<VisitorHistoryContext> current,
            LongSupplier clock,long maximumCoverageAgeMs) {
        if(store==null||current==null||clock==null||maximumCoverageAgeMs<0)throw new IllegalArgumentException("Unknown history resolver");
        return name -> {
            try{
                var before=current.get();long now=clock.getAsLong();var after=current.get();
                if(before==null||!before.sameSession(after))return UNKNOWN.observe(name);
                return store.observe(after,name,now,maximumCoverageAgeMs);
            }
            catch(RuntimeException unavailable){return UNKNOWN.observe(name);}
        };
    }
}
