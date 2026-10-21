package dev.veltraph.client.navigation;

/** Immutable DIAGNOSTIC timestamps only. These are EXTRA diagnostic clock reads; null sites
 * were unvisited or their diagnostic clock threw. No original fence/deadline is read or changed. */
public record GroundDynamicStageCostSnapshot(long visitedMask,long failedDiagnosticClockMask,int diagnosticClockCalls,
        Long beforeOriginalPlanNanos,
        Long afterOriginalPlanNanos,
        Long afterOriginalFinalPlanContextNanos,
        Long beforeOriginalAdapterSnapshotNanos,
        Long afterOriginalAdapterSnapshotNanos,
        Long beforeOriginalDirectionalCaptureNanos,
        Long afterOriginalDirectionalCaptureNanos,
        Long beforeOriginalCaptureGuardsNanos,
        Long afterOriginalCaptureGuardsNanos,
        Long beforeOriginalPlaneEligibilityNanos,
        Long afterOriginalPlaneEligibilityNanos,
        Long beforeOriginalOuterEligibilityGuardNanos,
        Long afterOriginalOuterEligibilityGuardNanos) {
    public enum Site {
        BEFORE_ORIGINAL_PLAN(0),
        AFTER_ORIGINAL_PLAN(1),
        AFTER_ORIGINAL_FINAL_PLAN_CONTEXT(2),
        BEFORE_ORIGINAL_ADAPTER_SNAPSHOT(3),
        AFTER_ORIGINAL_ADAPTER_SNAPSHOT(4),
        BEFORE_ORIGINAL_DIRECTIONAL_CAPTURE(5),
        AFTER_ORIGINAL_DIRECTIONAL_CAPTURE(6),
        BEFORE_ORIGINAL_CAPTURE_GUARDS(7),
        AFTER_ORIGINAL_CAPTURE_GUARDS(8),
        BEFORE_ORIGINAL_PLANE_ELIGIBILITY(9),
        AFTER_ORIGINAL_PLANE_ELIGIBILITY(10),
        BEFORE_ORIGINAL_OUTER_ELIGIBILITY_GUARD(11),
        AFTER_ORIGINAL_OUTER_ELIGIBILITY_GUARD(12);
        private final int id;Site(int id){this.id=id;}public int id(){return id;}
    }
}
