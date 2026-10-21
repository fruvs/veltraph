package dev.veltraph.client.navigation;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class GroundDynamicStageCostTest {
    @Test void thirteenOriginalCheckpointsHaveOneExtraClockReadEachInCallOrder(){
        AtomicInteger clock=new AtomicInteger();var events=new ArrayList<String>();var t=new GroundDynamicStageCost(()->{events.add("diagnosticClock");return clock.incrementAndGet()*10L;});
        for(int i=0;i<13;i++){events.add("originalStage"+i);t.mark(i);}var s=t.freeze();assertEquals(13,clock.get());assertEquals(13,s.diagnosticClockCalls());assertEquals((1L<<13)-1,s.visitedMask());assertEquals(10L,s.beforeOriginalPlanNanos());assertEquals(130L,s.afterOriginalOuterEligibilityGuardNanos());
        for(int i=0;i<13;i++)assertEquals(List.of("originalStage"+i,"diagnosticClock"),events.subList(i*2,i*2+2));
    }
    @Test void earlyOriginalRefusalKeepsLaterSkippedSitesNull(){
        AtomicInteger clock=new AtomicInteger();var t=new GroundDynamicStageCost(()->clock.incrementAndGet());t.mark(0);t.mark(1);t.mark(2);var s=t.freeze();assertEquals(3,clock.get());assertEquals(7,s.visitedMask());assertNull(s.beforeOriginalAdapterSnapshotNanos());assertNull(s.afterOriginalPlaneEligibilityNanos());assertNull(s.afterOriginalOuterEligibilityGuardNanos());
    }
    @Test void originalStageExceptionCreatesNoInventedAfterTimestamp(){
        AtomicInteger clock=new AtomicInteger();var t=new GroundDynamicStageCost(()->clock.incrementAndGet());t.mark(5);try{throw new IllegalStateException("original capture");}catch(IllegalStateException original){var s=t.freeze();assertEquals(1,clock.get());assertEquals(1L<<5,s.visitedMask());assertNull(s.afterOriginalDirectionalCaptureNanos());}
    }
    @Test void diagnosticClockExceptionNeverChangesOriginalControlPath(){
        AtomicInteger clock=new AtomicInteger();var t=new GroundDynamicStageCost(()->{if(clock.incrementAndGet()==2)throw new IllegalStateException("diagnostic only");return 7;});t.mark(0);assertDoesNotThrow(()->t.mark(1));t.mark(2);var s=t.freeze();assertEquals(3,clock.get());assertEquals((1L<<0)|(1L<<2),s.visitedMask());assertEquals(1L<<1,s.failedDiagnosticClockMask());assertNull(s.afterOriginalPlanNanos());assertEquals(7L,s.afterOriginalFinalPlanContextNanos());
    }
    @Test void invalidSitesAndFrozenHelperPerformNoFurtherClockReads(){
        AtomicInteger clock=new AtomicInteger();var t=new GroundDynamicStageCost(()->clock.incrementAndGet());t.mark(-1);t.mark(13);t.mark(Integer.MAX_VALUE);assertEquals(0,clock.get());t.mark(0);var s=t.freeze();t.mark(1);assertEquals(1,clock.get());assertSame(s,t.freeze());assertNull(s.afterOriginalPlanNanos());
    }
    @Test void independentActualStartInstancesCannotReusePriorTimestamps(){
        var a=new GroundDynamicStageCost(()->123);var b=new GroundDynamicStageCost(()->456);a.mark(0);b.mark(9);var first=a.freeze();var second=b.freeze();assertEquals(123L,first.beforeOriginalPlanNanos());assertNull(first.beforeOriginalPlaneEligibilityNanos());assertNull(second.beforeOriginalPlanNanos());assertEquals(456L,second.beforeOriginalPlaneEligibilityNanos());
    }
    @Test void rawNegativeAndEqualDiagnosticTimesAreNotAuthorityOrClockNormalization(){
        var t=new GroundDynamicStageCost(()->-7);t.mark(0);t.mark(1);var s=t.freeze();assertEquals(-7L,s.beforeOriginalPlanNanos());assertEquals(-7L,s.afterOriginalPlanNanos());assertEquals(3,s.visitedMask());
    }
    @Test void immutableSiteIdsAreExactlyApprovedThirteenCheckpoints(){
        var sites=GroundDynamicStageCostSnapshot.Site.values();assertEquals(13,sites.length);for(int i=0;i<sites.length;i++)assertEquals(i,sites[i].id());
    }
}
