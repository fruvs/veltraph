package dev.veltraph.client.combat.ghost;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class GhostSourceWalkPhaseTimingsTest {
    @Test void cachedPhaseReadoutReportsDistinctReadAndScopeCostsWithoutReevaluatingWork(){
        var t=new GhostSourceWalkPhaseTimings();t.add(GhostSourceWalkPhaseTimings.Phase.SCOPE,100);t.add(GhostSourceWalkPhaseTimings.Phase.SCOPE,300);t.add(GhostSourceWalkPhaseTimings.Phase.VALIDATION_READ,50);
        var captured=t.cached();var scope=captured.samples().get(GhostSourceWalkPhaseTimings.Phase.SCOPE);assertEquals(2,scope.calls());assertEquals(400,scope.totalNanos());assertEquals(300,scope.maximumNanos());
        assertEquals(1,captured.samples().get(GhostSourceWalkPhaseTimings.Phase.VALIDATION_READ).calls());assertEquals(0,captured.samples().get(GhostSourceWalkPhaseTimings.Phase.NATIVE_READ).calls());
        assertEquals(captured,t.cached());
    }
    @Test void earlierEvidenceIsImmutableAndDoesNotClaimHardRealtimeCompliance(){
        var t=new GhostSourceWalkPhaseTimings();var before=t.cached();t.add(GhostSourceWalkPhaseTimings.Phase.NATIVE_READ,20_000_000);
        assertEquals(0,before.samples().get(GhostSourceWalkPhaseTimings.Phase.NATIVE_READ).calls());assertEquals(20_000_000,t.cached().samples().get(GhostSourceWalkPhaseTimings.Phase.NATIVE_READ).maximumNanos());
        assertThrows(UnsupportedOperationException.class,()->before.samples().clear());
    }
}
