package dev.veltraph.client.navigation;

import dev.veltraph.client.world.Vec3;

/** Dynamic planning observations only, separate from initial directional/publication diagnostics.
 * Fixed scalar data binds to the exact original fence; no native/current/clock is invoked here. */
public final class GroundDynamicPlanningDiagnostics {
    private GroundDynamicPlanningDiagnostics() {}
    private static DynamicRouteQueryFence boundFence;private static boolean active;private static int depth;
    private static String status,firstSite,latestSite,firstCall,lastCall;private static GroundDynamicPlanningFailureSnapshot firstSnapshot,latestSnapshot;
    private static long visitedLow,visitedHigh,trueLow,trueHigh;private static int firstCandidate,lastCandidate,firstPoint,lastPoint,candidates,points,reads,bodies;
    private static boolean hasClock,hasFirstQuery;private static long budget,elapsed,firstQueryElapsed,lastQueryElapsed;private static boolean available;
    private static int clockVisited,clockTrue,queryOrdinal,queryVisited,queryTrue,firstQueryOrdinal,firstQueryVisited,firstQueryTrue,lastQueryOrdinal,lastQueryVisited,lastQueryTrue;
    private static int currentCandidate,currentPoint,tried,pointsVisited,firstQueryCandidate,firstQueryPoint,lastQueryCandidate,lastQueryPoint;
    private static String firstQueryCall,lastQueryCall;
    private static boolean hasOrigin,hasGoal,hasFrom,hasTo,hasModel;
    private static double originX,originY,originZ,goalX,goalY,goalZ,fromX,fromY,fromZ,toX,toY,toZ;
    private static long originalSeed,originalOrdinal;
    public static void begin(DynamicRouteQueryFence originalFence){
        if(depth>0){depth=Math.min(1024,depth+1);if(boundFence!=null)boundFence.endDynamicPlanningDiagnostic();boundFence=null;active=false;reset();status="NESTED_SCOPE_DROPPED";return;}
        depth=1;reset();status="ORIGINAL_SCOPE_COMPLETE";firstSnapshot=latestSnapshot=null;boundFence=originalFence;active=true;if(originalFence!=null)originalFence.beginDynamicPlanningDiagnostic();
    }
    private static void reset(){firstSite=latestSite=firstCall=lastCall=null;visitedLow=visitedHigh=trueLow=trueHigh=0;firstCandidate=lastCandidate=firstPoint=lastPoint=candidates=points=reads=bodies=-1;hasClock=hasFirstQuery=hasOrigin=hasGoal=hasFrom=hasTo=hasModel=false;clockVisited=clockTrue=queryOrdinal=queryVisited=queryTrue=0;currentCandidate=currentPoint=-1;tried=pointsVisited=0;firstQueryCall=lastQueryCall=null;}
    public static boolean copy(int slot,boolean original){if(active&&slot>=0&&slot<128){if(slot<64){visitedLow|=1L<<slot;if(original)trueLow|=1L<<slot;else trueLow&=~(1L<<slot);}else{visitedHigh|=1L<<(slot-64);if(original)trueHigh|=1L<<(slot-64);else trueHigh&=~(1L<<(slot-64));}}return original;}
    public static void returned(String originalSite){if(active){if(firstSite==null)firstSite=originalSite;latestSite=originalSite;}}
    public static void note(String originalCallSite,int candidateOrdinal,int pointOrdinal){if(active){if(firstCall==null){firstCall=originalCallSite;if(firstCandidate<0)firstCandidate=candidateOrdinal;if(firstPoint<0)firstPoint=pointOrdinal;}lastCall=originalCallSite;lastCandidate=candidateOrdinal;lastPoint=pointOrdinal;}}
    public static void candidate(int originalAttempt){if(active){currentCandidate=originalAttempt;currentPoint=-1;tried++;if(firstCandidate<0)firstCandidate=originalAttempt;lastCandidate=originalAttempt;lastPoint=-1;}}
    public static void point(){if(active){currentPoint++;pointsVisited++;if(firstPoint<0)firstPoint=currentPoint;lastPoint=currentPoint;}}
    public static void noteCurrent(String originalSite){note(originalSite,currentCandidate,currentPoint);}
    public static void model(Vec3 originalOrigin,Vec3 originalGoal,long seed,long ordinal){if(active){hasModel=true;originalSeed=seed;originalOrdinal=ordinal;hasOrigin=originalOrigin!=null;hasGoal=originalGoal!=null;if(hasOrigin){originX=originalOrigin.x();originY=originalOrigin.y();originZ=originalOrigin.z();}if(hasGoal){goalX=originalGoal.x();goalY=originalGoal.y();goalZ=originalGoal.z();}}}
    public static void geometryCall(String originalSite,Vec3 originalFrom,Vec3 originalTo){if(active){noteCurrent(originalSite);hasFrom=originalFrom!=null;hasTo=originalTo!=null;if(hasFrom){fromX=originalFrom.x();fromY=originalFrom.y();fromZ=originalFrom.z();}if(hasTo){toX=originalTo.x();toY=originalTo.y();toZ=originalTo.z();}}}
    public static void counters(int originalCandidates,int originalPoints,int originalReads,int originalBodies){if(active){candidates=originalCandidates;points=originalPoints;reads=originalReads;bodies=originalBodies;}}
    public static void fenceObservation(DynamicRouteQueryFence originalFence,long originalBudget,long originalElapsed,boolean originalAvailable,boolean evaluated,
            int observedClockVisited,int observedClockTrue,int ordinal,int observedQueryVisited,int observedQueryTrue,boolean originalQueryRefused){
        if(!active||originalFence!=boundFence)return;
        if(evaluated){hasClock=true;budget=originalBudget;elapsed=originalElapsed;available=originalAvailable;clockVisited=observedClockVisited;clockTrue=observedClockTrue;}
        queryOrdinal=ordinal;queryVisited=observedQueryVisited;queryTrue=observedQueryTrue;
        if(originalQueryRefused){if(!hasFirstQuery){hasFirstQuery=true;firstQueryOrdinal=ordinal;firstQueryVisited=observedQueryVisited;firstQueryTrue=observedQueryTrue;firstQueryElapsed=originalElapsed;firstQueryCandidate=currentCandidate;firstQueryPoint=currentPoint;firstQueryCall=lastCall;returned("originalPlanningFence.query.originalFalseReturn");}lastQueryOrdinal=ordinal;lastQueryVisited=observedQueryVisited;lastQueryTrue=observedQueryTrue;lastQueryElapsed=originalElapsed;lastQueryCandidate=currentCandidate;lastQueryPoint=currentPoint;lastQueryCall=lastCall;}
    }
    public static GroundDynamicPlanningFailureSnapshot completeOriginal(String outcome,String reason,boolean originalRejected){
        if(!active&&depth==0)return latestSnapshot;
        if(boundFence!=null)boundFence.copyOriginalDynamicPlanningDiagnostic();
        if(originalRejected){latestSnapshot=new GroundDynamicPlanningFailureSnapshot(status,firstSite,latestSite,outcome,reason,visitedLow,visitedHigh,trueLow,trueHigh,
            firstCall,lastCall,firstCandidate,lastCandidate,firstPoint,lastPoint,candidates,points,reads,bodies,
            hasClock?budget:null,hasClock?elapsed:null,hasClock?available:null,hasClock?clockVisited:null,hasClock?clockTrue:null,
            queryOrdinal==0?null:queryOrdinal,queryOrdinal==0?null:queryVisited,queryOrdinal==0?null:queryTrue,
            hasFirstQuery?firstQueryOrdinal:null,hasFirstQuery?firstQueryVisited:null,hasFirstQuery?firstQueryTrue:null,hasFirstQuery?firstQueryElapsed:null,
            hasFirstQuery?lastQueryOrdinal:null,hasFirstQuery?lastQueryVisited:null,hasFirstQuery?lastQueryTrue:null,hasFirstQuery?lastQueryElapsed:null,
            hasFirstQuery?firstQueryCandidate:null,hasFirstQuery?firstQueryPoint:null,firstQueryCall,
            hasFirstQuery?lastQueryCandidate:null,hasFirstQuery?lastQueryPoint:null,lastQueryCall,tried,pointsVisited,
            hasOrigin?originX:null,hasOrigin?originY:null,hasOrigin?originZ:null,hasGoal?goalX:null,hasGoal?goalY:null,hasGoal?goalZ:null,
            hasFrom?fromX:null,hasFrom?fromY:null,hasFrom?fromZ:null,hasTo?toX:null,hasTo?toY:null,hasTo?toZ:null,
            hasModel?originalSeed:null,hasModel?originalOrdinal:null);
            if(firstSnapshot==null)firstSnapshot=latestSnapshot;}
        if(boundFence!=null)boundFence.endDynamicPlanningDiagnostic();boundFence=null;active=false;if(depth>0)depth--;return latestSnapshot;
    }
    public static void abandon(){if(boundFence!=null)boundFence.endDynamicPlanningDiagnostic();boundFence=null;active=false;depth=0;}
    public static GroundDynamicPlanningFailureSnapshot cachedFirstFailure(){return firstSnapshot;}
    public static GroundDynamicPlanningFailureSnapshot cachedLatestFailure(){return latestSnapshot;}
}
