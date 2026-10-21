package dev.veltraph.client.navigation;

/** Fixed scalar observation scope. Binding attributes data to the original fence only; no query,
 * supplier, clock, player, world or geometry is read here. It never supplies control permission. */
public final class GroundInitialDirectionalDiagnostics {
    private GroundInitialDirectionalDiagnostics() {}
    private static DynamicRouteQueryFence boundFence;private static boolean active;
    private static GroundInitialDirectionalFailureSnapshot cached;private static String droppedReason;private static int scopeDepth;
    private static String first,latest;private static long visitedLow,visitedHigh,trueLow,trueHigh;
    private static int courseSize,rectangles,plan,facts,bodies,remaining,x,y,z,bodiesVisited;
    private static long budget,elapsed,firstQueryElapsed;private static boolean hasClock,hasCell,available,firstQuerySeen;
    private static int clockVisited,clockTrue,queryOrdinal,queryVisited,queryTrue,firstQueryOrdinal,firstQueryVisited,firstQueryTrue;
    public static void begin(DynamicRouteQueryFence originalFence){
        if(scopeDepth>0){scopeDepth=Math.min(1024,scopeDepth+1);if(boundFence!=null)boundFence.endInitialDirectionalDiagnostic();boundFence=null;active=false;resetScalars();cached=null;droppedReason="NESTED_SCOPE_DROPPED";return;}
        scopeDepth=1;
        resetScalars();droppedReason=null;boundFence=originalFence;active=true;cached=null;
        if(originalFence!=null)originalFence.beginInitialDirectionalDiagnostic();
    }
    private static void resetScalars(){first=latest=null;visitedLow=visitedHigh=trueLow=trueHigh=0;courseSize=plan=facts=bodies=remaining=x=y=z=-1;rectangles=bodiesVisited=0;hasClock=hasCell=firstQuerySeen=false;clockVisited=clockTrue=queryOrdinal=queryVisited=queryTrue=0;}
    /** Exception cleanup and nested-scope drop only; no snapshot, callback, query or clock. */
    public static void abandon(){if(boundFence!=null)boundFence.endInitialDirectionalDiagnostic();boundFence=null;active=false;scopeDepth=0;}
    public static String cachedScopeDropReason(){return droppedReason;}
    public static boolean copy(int slot,boolean original){if(active&&slot>=0&&slot<128){if(slot<64){visitedLow|=1L<<slot;if(original)trueLow|=1L<<slot;else trueLow&=~(1L<<slot);}else{visitedHigh|=1L<<(slot-64);if(original)trueHigh|=1L<<(slot-64);else trueHigh&=~(1L<<(slot-64));}}return original;}
    public static void returned(String originalSite){if(active){if(first==null)first=originalSite;latest=originalSite;}}
    public static void counts(int originalPlan,int originalFacts,int originalBodies,int originalRemaining){if(active){plan=originalPlan;facts=originalFacts;bodies=originalBodies;remaining=originalRemaining;}}
    public static int courseSize(int original){if(active)courseSize=original;return original;}
    public static void cell(int originalX,int originalY,int originalZ){if(active){hasCell=true;x=originalX;y=originalY;z=originalZ;}}
    public static void rectangle(){if(active)rectangles++;}public static void body(){if(active)bodiesVisited++;}
    public static void fenceObservation(DynamicRouteQueryFence originalFence,long originalBudget,long originalElapsed,boolean originalAvailable,boolean evaluated,
            int observedClockVisited,int observedClockTrue,int ordinal,int observedQueryVisited,int observedQueryTrue,boolean originalQueryRefused){
        if(!active||originalFence!=boundFence)return;
        if(evaluated){hasClock=true;budget=originalBudget;elapsed=originalElapsed;available=originalAvailable;clockVisited=observedClockVisited;clockTrue=observedClockTrue;}
        queryOrdinal=ordinal;queryVisited=observedQueryVisited;queryTrue=observedQueryTrue;
        if(originalQueryRefused&&!firstQuerySeen){firstQuerySeen=true;firstQueryOrdinal=ordinal;firstQueryVisited=observedQueryVisited;firstQueryTrue=observedQueryTrue;firstQueryElapsed=originalElapsed;returned("originalFence.query.originalFalseReturn");}
    }
    public static GroundInitialDirectionalFailureSnapshot completeOriginal(String outcome,boolean originalFailed){
        if(!active&&droppedReason==null)return cached;
        if(boundFence!=null)boundFence.copyOriginalInitialDirectionalDiagnostic();
        if(originalFailed)cached=new GroundInitialDirectionalFailureSnapshot(droppedReason==null?"ORIGINAL_SCOPE_COMPLETE":droppedReason,first,latest,outcome,visitedLow,visitedHigh,trueLow,trueHigh,
                courseSize,rectangles,plan,facts,bodies,remaining,hasCell?x:null,hasCell?y:null,hasCell?z:null,bodiesVisited,
                hasClock?budget:null,hasClock?elapsed:null,hasClock?available:null,hasClock?clockVisited:null,hasClock?clockTrue:null,
                queryOrdinal==0?null:queryOrdinal,queryOrdinal==0?null:queryVisited,queryOrdinal==0?null:queryTrue,
                firstQuerySeen?firstQueryOrdinal:null,firstQuerySeen?firstQueryVisited:null,firstQuerySeen?firstQueryTrue:null,firstQuerySeen?firstQueryElapsed:null);
        if(boundFence!=null)boundFence.endInitialDirectionalDiagnostic();boundFence=null;active=false;if(scopeDepth>0)scopeDepth--;return cached;
    }
    public static GroundInitialDirectionalFailureSnapshot cachedFailure(){return cached;}
}
