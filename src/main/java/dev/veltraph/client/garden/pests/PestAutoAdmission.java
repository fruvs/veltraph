package dev.veltraph.client.garden.pests;

/** Read-only source parent admission. This never stops a child batch or manufactures an outcome. */
public final class PestAutoAdmission {
    private PestAutoAdmission() {}

    /** A fresh native observation is bound to the actual farm and selected plot. */
    public record Scope(Object player,Object world,Object connection,String profile,int plot) {}

    /**
     * The two source differences have independent producers. Exclusions count BOTH sets' entries;
     * an entity and its plot deliberately contribute two entries, rather than one deduplicated pest.
     * scheduledTotal is the parent's accumulated requested total, never a loaded-entity census.
     */
    public record Counts(Integer nativeTotal,int excludedEntityEntries,int excludedPlotEntries,
                         int scheduledTotal,int acknowledgedCompletions) {
        public long available() {
            return (long)(nativeTotal==null?0:nativeTotal)-excludedEntityEntries-excludedPlotEntries;
        }
        public long pending() { return (long)scheduledTotal-acknowledgedCompletions; }
        private boolean valid() {
            return nativeTotal!=null&&nativeTotal>=0&&excludedEntityEntries>=0&&excludedPlotEntries>=0
                &&scheduledTotal>=0&&acknowledgedCompletions>=0;
        }
    }
    /** sourceBlocked is nullable: an unbound source flag cannot authorize automatic activation. */
    public record Snapshot(Scope scope,long tick,Counts counts,Boolean sourceBlocked) {}
    public enum Decision { ADMIT, UNKNOWN, CONTEXT_CHANGED, STALE, SOURCE_BLOCKED, AVAILABLE_THRESHOLD, PENDING_THRESHOLD }

    /**
     * Current-tick equality is a local adapter freshness contract, not a recovered source TTL.
     * The source's strict comparisons are preserved. Long differences prevent int overflow from
     * turning an invalid subtraction into positive admission; negative signed counts cannot qualify.
     */
    public static Decision decision(Snapshot snapshot,Scope current,long tick,boolean leaveOne) {
        if(snapshot==null||snapshot.counts()==null||snapshot.sourceBlocked()==null
                ||!valid(snapshot.scope())||!valid(current)||!snapshot.counts().valid())return Decision.UNKNOWN;
        if(!same(snapshot.scope(),current))return Decision.CONTEXT_CHANGED;
        if(tick<0||snapshot.tick()!=tick)return Decision.STALE;
        if(snapshot.sourceBlocked())return Decision.SOURCE_BLOCKED;
        int threshold=leaveOne?1:0;
        if(snapshot.counts().available()<=threshold)return Decision.AVAILABLE_THRESHOLD;
        if(snapshot.counts().pending()<=threshold)return Decision.PENDING_THRESHOLD;
        return Decision.ADMIT;
    }
    public static boolean ready(Snapshot snapshot,Scope current,long tick,boolean leaveOne) {
        return decision(snapshot,current,tick,leaveOne)==Decision.ADMIT;
    }
    private static boolean valid(Scope scope) {
        return scope!=null&&scope.player()!=null&&scope.world()!=null&&scope.connection()!=null
            &&scope.profile()!=null&&!scope.profile().isBlank()&&scope.plot()>=1&&scope.plot()<=24;
    }
    private static boolean same(Scope first,Scope second) {
        return first.player()==second.player()&&first.world()==second.world()
            &&first.connection()==second.connection()&&first.profile().equals(second.profile())&&first.plot()==second.plot();
    }
}
