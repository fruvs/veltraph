package dev.veltraph.client.navigation;

/** Immutable ORIGINAL dynamic-planning scalar evidence, never route or physical permission.
 * Query bits0..4 are preAvailable,currentBefore,queryBody,postAvailable,currentAfter;
 * clock bits0..1 are elapsed>=0,elapsed<=budget. Null query fields are originally skipped. */
public record GroundDynamicPlanningFailureSnapshot(String diagnosticScopeStatus,String firstOriginalRefusalSite,String latestOriginalRefusalSite,String originalOutcome,String originalReason,
        long visitedLow,long visitedHigh,long trueLow,long trueHigh,
        String firstOriginalCall,String latestOriginalCall,int firstCandidate,int latestCandidate,int firstPoint,int latestPoint,int candidates,int points,int reads,int bodies,
        Long budgetNanos,Long originalElapsedNanos,Boolean originalAvailableResult,Integer originalClockVisited,Integer originalClockTrue,
        Integer latestQueryOrdinal,Integer latestQueryVisited,Integer latestQueryTrue,
        Integer firstRefusedQueryOrdinal,Integer firstRefusedQueryVisited,Integer firstRefusedQueryTrue,Long firstRefusedQueryElapsedNanos,
        Integer latestRefusedQueryOrdinal,Integer latestRefusedQueryVisited,Integer latestRefusedQueryTrue,Long latestRefusedQueryElapsedNanos,
        Integer firstRefusedQueryCandidate,Integer firstRefusedQueryPoint,String firstRefusedQueryCall,
        Integer latestRefusedQueryCandidate,Integer latestRefusedQueryPoint,String latestRefusedQueryCall,int diagnosticCandidatesTried,int diagnosticPointsVisited,
        Double originalOriginX,Double originalOriginY,Double originalOriginZ,Double originalGoalX,Double originalGoalY,Double originalGoalZ,
        Double latestGeometryFromX,Double latestGeometryFromY,Double latestGeometryFromZ,Double latestGeometryToX,Double latestGeometryToY,Double latestGeometryToZ,
        Long originalSeed,Long originalOrdinal) {}
