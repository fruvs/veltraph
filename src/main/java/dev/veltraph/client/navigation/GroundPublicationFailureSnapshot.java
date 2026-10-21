package dev.veltraph.client.navigation;

/** Immutable observations of evaluated operands. Unprefixed sites/reasons describe ORIGINAL
 * predicates; DIRECTIONAL_QUALIFIED:: declares privately selected directional predicates for
 * slots 25, 40 and 55. Unvisited/null values remain skipped by the same short circuit.
 * This carries no frame, scope, world or native authority. */
public record GroundPublicationFailureSnapshot(String firstReturnSite,String outerReturnSite,String originalReason,
        long visitedLow,long visitedHigh,long trueLow,long trueHigh,
        CapturedProfileTrajectory.Input attemptedInput,CapturedProfileTrajectory.JumpIntent attemptedJump,
        CapturedProfileTrajectory.SprintIntent attemptedSprint,CapturedProfileTrajectory.Phase attemptedNativePhase,
        CapturedProfileTrajectory.Input input,CapturedProfileTrajectory.JumpIntent jump,
        CapturedProfileTrajectory.SprintIntent sprint,CapturedProfileTrajectory.Phase nativePhase,
        Float poseYaw,Float queuedEndpointYaw,Boolean queuedPending,Long budgetNanos,Long elapsedNanos,Boolean budgetAvailable,
        GroundPublicationObservedOriginalClocks observedOriginalClockPhases) {}
