package dev.veltraph.client.navigation;

/** Original evaluated scalar diagnostics only; never geometry, custody or input authority.
 * Null clock/query values mean no original evaluation. Query bits0..4: preAvailable,
 * currentBefore, bodyResult, postAvailable, currentAfter. Clock bits0..1: elapsed>=0, elapsed<=budget. */
public record GroundInitialDirectionalFailureSnapshot(String diagnosticScopeStatus,String firstOriginalRefusalSite,String latestOriginalRefusalSite,String originalOutcome,
        long visitedLow,long visitedHigh,long trueLow,long trueHigh,
        int courseSize,int rectanglesVisited,int planCells,int factCells,int bodies,int readRemaining,Integer cellX,Integer cellY,Integer cellZ,int bodiesVisited,
        Long budgetNanos,Long originalElapsedNanos,Boolean originalAvailableResult,Integer originalClockVisited,Integer originalClockTrue,
        Integer latestQueryOrdinal,Integer latestQueryVisited,Integer latestQueryTrue,
        Integer firstRefusedQueryOrdinal,Integer firstRefusedQueryVisited,Integer firstRefusedQueryTrue,Long firstRefusedQueryElapsedNanos) {}
