package dev.veltraph.client.metrics;

import java.util.*;

/** Explicit producer generations for public counters that their gameplay owner resets on fresh start. */
public final class OwnerMetricEpochs {
    public enum OwnerCounter {
        AUTO_FORAGING_TREES(LocalSessionMetrics.Counter.CONFIRMED_TREES),
        GALATEA_TREES(LocalSessionMetrics.Counter.CONFIRMED_TREES),
        SLAYER_QUESTS(LocalSessionMetrics.Counter.CONFIRMED_SLAYER_QUESTS),
        GHOST_KILLS(LocalSessionMetrics.Counter.CONFIRMED_GHOST_KILLS),
        ZEALOT_KILLS(LocalSessionMetrics.Counter.CONFIRMED_ZEALOT_KILLS),
        ACTIVE_HUNTING_FISH(LocalSessionMetrics.Counter.CONFIRMED_FISH_HUNTED);
        private final LocalSessionMetrics.Counter metric;
        OwnerCounter(LocalSessionMetrics.Counter metric){this.metric=metric;}
        public LocalSessionMetrics.Counter metric(){return metric;}
    }
    public record Observation(LocalSessionMetrics.Series series,LocalSessionMetrics.Counter counter,long serial,long cumulativeTotal) {}
    private static final class Epoch {final LocalSessionMetrics.Series series;long serial;Epoch(OwnerCounter kind,String ownerId){series=new LocalSessionMetrics.Series("confirmed-owner:"+kind.name()+":"+ownerId,UUID.randomUUID());}}
    private record Key(String ownerId,OwnerCounter kind) {}
    private final Map<Key,Epoch> epochs=new HashMap<>();
    /** Call after the owner's actual fresh-start reset, supplying its observed baseline. */
    public Observation freshStart(OwnerCounter kind,long baseline){
        return freshStart("singleton",kind,baseline);
    }
    public Observation freshStart(String ownerId,OwnerCounter kind,long baseline){
        var key=key(ownerId,kind);if(baseline<0)throw new IllegalArgumentException("Negative owner baseline");
        var epoch=new Epoch(kind,ownerId);epochs.put(key,epoch);return observation(kind,epoch,baseline);
    }
    /** An unregistered owner stays unavailable; sampling cannot invent a fresh-start generation. */
    public Optional<Observation> sample(OwnerCounter kind,long total){
        return sample("singleton",kind,total);
    }
    public Optional<Observation> sample(String ownerId,OwnerCounter kind,long total){
        if(kind==null||total<0)return Optional.empty();var epoch=epochs.get(key(ownerId,kind));return epoch==null?Optional.empty():Optional.of(observation(kind,epoch,total));
    }
    private static Key key(String ownerId,OwnerCounter kind){Objects.requireNonNull(kind);if(ownerId==null||!ownerId.matches("[a-zA-Z0-9_-]{1,64}"))throw new IllegalArgumentException("Explicit bounded owner ID required");return new Key(ownerId,kind);}
    private static Observation observation(OwnerCounter kind,Epoch epoch,long total){return new Observation(epoch.series,kind.metric(),++epoch.serial,total);}
    public void clear(){epochs.clear();}
}
