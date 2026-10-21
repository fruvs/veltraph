package dev.veltraph.client.navigation;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

final class GroundPublicationFailureCacheTest {
    @AfterEach void disable(){GroundPublicationFailureCache.success();}
    @Test void originalShortCircuitLeavesSkippedOperandUnvisited(){
        GroundPublicationFailureCache.begin();AtomicInteger reads=new AtomicInteger();
        boolean original=GroundPublicationFailureCache.copy(29,true)||GroundPublicationFailureCache.copy(30,reads.incrementAndGet()>0);
        assertTrue(original);assertEquals(0,reads.get());GroundPublicationFailureCache.returned("first");
        var s=GroundPublicationFailureCache.finish(null);assertEquals(1L<<29,s.visitedLow());assertEquals(s.visitedLow(),s.trueLow());assertNull(s.input());
    }
    @Test void highSlotsDoNotAliasLowSlotsAndLoopLastValueIsExact(){
        GroundPublicationFailureCache.begin();GroundPublicationFailureCache.copy(0,true);GroundPublicationFailureCache.copy(64,true);
        GroundPublicationFailureCache.copy(71,true);GroundPublicationFailureCache.copy(71,false);
        var s=GroundPublicationFailureCache.finish(null);assertEquals(1,s.visitedLow());assertEquals(129,s.visitedHigh());assertEquals(1,s.trueHigh());
    }
    @Test void innerFailurePrecedesOuterAndScalarValuesAreSuppliedOnce(){
        GroundPublicationFailureCache.begin();assertEquals(-23.6F,GroundPublicationFailureCache.poseYaw(-23.6F));
        assertEquals(CapturedProfileTrajectory.Input.FORWARD,GroundPublicationFailureCache.input(CapturedProfileTrajectory.Input.FORWARD));
        GroundPublicationFailureCache.returned("selectedProof.originalFalseReturn1");GroundPublicationFailureCache.returned("afterPublished.originalFalseReturn12");
        GroundPublicationFailureCache.fence(50_000_000,112_915_250,false,true);
        var s=GroundPublicationFailureCache.finish("original reason");assertEquals("selectedProof.originalFalseReturn1",s.firstReturnSite());
        assertEquals("afterPublished.originalFalseReturn12",s.outerReturnSite());assertEquals(112_915_250L,s.elapsedNanos());assertFalse(s.budgetAvailable());
        assertNull(s.queuedEndpointYaw());assertNull(s.queuedPending());
    }
    @Test void newAttemptDoesNotAdoptEarlierBitsOrBudgetAndSuccessEmitsNothing(){
        GroundPublicationFailureCache.begin();GroundPublicationFailureCache.copy(0,true);GroundPublicationFailureCache.fence(50,100,false,true);
        var first=GroundPublicationFailureCache.finish("old");GroundPublicationFailureCache.begin();GroundPublicationFailureCache.copy(1,false);
        var second=GroundPublicationFailureCache.finish(null);assertEquals(1,first.visitedLow());assertEquals(2,second.visitedLow());assertEquals(0,second.trueLow());assertNull(second.elapsedNanos());
        GroundPublicationFailureCache.begin();GroundPublicationFailureCache.success();assertNull(GroundPublicationFailureCache.finish("not a failure"));
    }
    @Test void earlyActualYawRefusalKeepsAttemptMetadataWhileOriginalControlReadsRemainSkipped(){
        GroundPublicationFailureCache.begin();
        GroundPublicationFailureCache.attempt(CapturedProfileTrajectory.Input.FORWARD,CapturedProfileTrajectory.JumpIntent.RELEASE,
                CapturedProfileTrajectory.SprintIntent.RELEASE,CapturedProfileTrajectory.Phase.SUPPORTED_GROUND);
        GroundPublicationFailureCache.poseYaw(-23.6F);GroundPublicationFailureCache.copy(40,true);
        GroundPublicationFailureCache.returned("afterPublished.originalFalseReturn5");
        var first=GroundPublicationFailureCache.finish(null);
        assertEquals(CapturedProfileTrajectory.Input.FORWARD,first.attemptedInput());
        assertEquals(CapturedProfileTrajectory.Phase.SUPPORTED_GROUND,first.attemptedNativePhase());
        assertNull(first.input());assertNull(first.nativePhase());assertNull(first.queuedPending());
        assertEquals(0L,first.visitedLow()&(1L<<55));
        GroundPublicationFailureCache.begin();
        GroundPublicationFailureCache.attempt(CapturedProfileTrajectory.Input.NEUTRAL,CapturedProfileTrajectory.JumpIntent.RELEASE,
                CapturedProfileTrajectory.SprintIntent.RELEASE,CapturedProfileTrajectory.Phase.SUPPORTED_GROUND);
        GroundPublicationFailureCache.returned("afterPublished.originalFalseReturn12");
        var latest=GroundPublicationFailureCache.finish("later terminal refusal");
        assertEquals(CapturedProfileTrajectory.Input.NEUTRAL,latest.attemptedInput());
        assertEquals(CapturedProfileTrajectory.Input.FORWARD,first.attemptedInput());assertNotSame(first,latest);
    }
    @Test void queuedEndpointRefusalReportsAttemptPhaseWithoutPretendingExpectedFrameWasEvaluated(){
        GroundPublicationFailureCache.begin();
        GroundPublicationFailureCache.attempt(CapturedProfileTrajectory.Input.FORWARD,CapturedProfileTrajectory.JumpIntent.RELEASE,
                CapturedProfileTrajectory.SprintIntent.RELEASE,CapturedProfileTrajectory.Phase.SUPPORTED_GROUND);
        GroundPublicationFailureCache.queuedPending(true);GroundPublicationFailureCache.queuedYaw(-23F);
        GroundPublicationFailureCache.input(CapturedProfileTrajectory.Input.FORWARD);GroundPublicationFailureCache.copy(55,true);
        GroundPublicationFailureCache.returned("afterPublished.originalFalseReturn10");var first=GroundPublicationFailureCache.finish(null);
        assertEquals(CapturedProfileTrajectory.Input.FORWARD,first.input());assertNull(first.jump());assertNull(first.nativePhase());
        assertEquals(CapturedProfileTrajectory.Phase.SUPPORTED_GROUND,first.attemptedNativePhase());
        assertTrue(first.queuedPending());assertEquals(0L,first.visitedLow()&(1L<<58));
    }
    @Test void cleanupCatchDoesNotReplaceAlreadyFrozenOriginalRefusal(){
        GroundPublicationFailureCache.begin();GroundPublicationFailureCache.returned("original guard");
        var original=GroundPublicationFailureCache.finish("before cleanup");
        GroundPublicationFailureCache.returned("cleanup catch");assertSame(original,GroundPublicationFailureCache.finish("cleanup unavailable",original));
    }
    @Test void fencePreservesOriginalClockQueryAndScopeOrderAndLateShortCircuit(){
        GroundPublicationFailureCache.begin();var events=new ArrayList<String>();AtomicInteger times=new AtomicInteger();
        var fence=new DynamicRouteQueryFence(()->{events.add("clock");return times.getAndIncrement()==2?51:0;},()->{events.add("current");return true;},50);
        assertFalse(fence.query(()->{events.add("query");return true;}));
        assertEquals(java.util.List.of("clock","clock","current","query","clock"),events);
        fence.copyOriginalAvailableToPublicationFailure();assertEquals(5,events.size());
        var s=GroundPublicationFailureCache.finish(null);assertEquals(51L,s.elapsedNanos());assertFalse(s.budgetAvailable());
    }
}
