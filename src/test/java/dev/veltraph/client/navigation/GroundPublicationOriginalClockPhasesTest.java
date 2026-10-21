package dev.veltraph.client.navigation;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class GroundPublicationOriginalClockPhasesTest {
    @AfterEach void disable(){GroundPublicationFailureCache.success();}
    @Test void queryCallsOriginalClockCurrentAndBodyInSameOrder(){
        GroundPublicationFailureCache.begin();var events=new ArrayList<String>();AtomicInteger tick=new AtomicInteger();
        var fence=new DynamicRouteQueryFence(()->{events.add("clock");return tick.getAndIncrement()*10L;},()->{events.add("current");return true;},50);
        assertTrue(fence.query(()->{events.add("body");return true;}));
        fence.copyOriginalInitialJournalReturn(true);fence.copyOriginalProofCreation();fence.copyOriginalClockPhasesToPublicationFailure();
        assertEquals(List.of("clock","clock","current","body","clock","current"),events);
        var c=GroundPublicationFailureCache.finish(null).observedOriginalClockPhases();assertEquals(0L,c.originalScopeBeganNanos());
        assertEquals(10L,c.firstOriginalQueryPreElapsedNanos());assertEquals(20L,c.lastOriginalQueryPostElapsedNanos());
        assertEquals(20L,c.lastOriginalAvailableAtProofCreationElapsedNanos());assertEquals(20L,c.lastOriginalAvailableAtInitialJournalReturnElapsedNanos());
    }
    @Test void failedBodySkipsOriginalPostAvailableWithoutInventingSample(){
        GroundPublicationFailureCache.begin();AtomicInteger clocks=new AtomicInteger();var fence=new DynamicRouteQueryFence(()->clocks.getAndIncrement()*10L,()->true,50);
        assertFalse(fence.query(()->false));fence.copyOriginalClockPhasesToPublicationFailure();var c=GroundPublicationFailureCache.finish(null).observedOriginalClockPhases();
        assertEquals(2,clocks.get());assertEquals(10L,c.lastOriginalQueryPreElapsedNanos());assertNull(c.lastOriginalQueryPostElapsedNanos());assertNull(c.lastOriginalAvailableAtProofCreationElapsedNanos());
    }
    @Test void originalNegativeElapsedAndExpiredBudgetAreNeverChanged(){
        GroundPublicationFailureCache.begin();AtomicInteger times=new AtomicInteger();long[] values={100,90};var fence=new DynamicRouteQueryFence(()->values[times.getAndIncrement()],()->{fail("original current skipped");return true;},50);
        assertFalse(fence.query(()->{fail("original body skipped");return true;}));fence.copyOriginalClockPhasesToPublicationFailure();
        assertEquals(-10L,GroundPublicationFailureCache.finish(null).observedOriginalClockPhases().firstOriginalQueryPreElapsedNanos());assertEquals(2,times.get());
    }
    @Test void firstFinalAvailableIsOriginalFirstReadAndDoesNotResetBudget(){
        GroundPublicationFailureCache.begin();AtomicInteger times=new AtomicInteger();long[] values={0,20,60,70};var fence=new DynamicRouteQueryFence(()->values[times.getAndIncrement()],()->true,50);
        assertTrue(fence.available());fence.copyOriginalProofCreation();fence.beginOriginalFinalPublicationObservation();
        assertFalse(fence.copyOriginalFirstFinalPublicationAvailable(fence.available()));assertFalse(fence.available());fence.copyOriginalAvailableToPublicationFailure();
        var c=GroundPublicationFailureCache.finish(null).observedOriginalClockPhases();assertEquals(4,times.get());assertEquals(20L,c.lastOriginalAvailableAtProofCreationElapsedNanos());assertEquals(60L,c.firstOriginalFinalPublicationAvailableElapsedNanos());assertEquals(0L,c.originalScopeBeganNanos());
    }
    @Test void originalDeadlineEarlyReturnKeepsNullRatherThanPreviousTimestamp(){
        GroundPublicationFailureCache.begin();GroundPublicationFailureCache.deadlineBegin();GroundPublicationFailureCache.deadlineNano(123);
        assertTrue(GroundPublicationFailureCache.deadlineSite(0,true));GroundPublicationFailureCache.deadlineBegin();
        assertFalse(GroundPublicationFailureCache.deadlineSite(1,false));var c=GroundPublicationFailureCache.finish(null).observedOriginalClockPhases();
        assertEquals(123L,c.originalAfterPublicationContextDeadlineNanoTime());assertNull(c.originalAfterPublicationFrameDeadlineNanoTime());
    }
    @Test void repeatedSameSiteEarlyReturnClearsPreviouslyObservedNano(){
        GroundPublicationFailureCache.begin();GroundPublicationFailureCache.deadlineBegin();GroundPublicationFailureCache.deadlineNano(123);GroundPublicationFailureCache.deadlineSite(2,true);
        GroundPublicationFailureCache.deadlineBegin();assertFalse(GroundPublicationFailureCache.deadlineSite(2,false));
        assertNull(GroundPublicationFailureCache.finish(null).observedOriginalClockPhases().originalBeforeFinalSelectedProofDeadlineNanoTime());
    }
    @Test void newAttemptAndSuccessfulPathDoNotAdoptOldClockEvidence(){
        GroundPublicationFailureCache.begin();GroundPublicationFailureCache.deadlineBegin();GroundPublicationFailureCache.deadlineNano(123);GroundPublicationFailureCache.deadlineSite(4,true);
        var first=GroundPublicationFailureCache.finish(null);GroundPublicationFailureCache.begin();var second=GroundPublicationFailureCache.finish(null);
        assertEquals(123L,first.observedOriginalClockPhases().originalBeforeObserverCommitDeadlineNanoTime());assertNull(second.observedOriginalClockPhases().originalBeforeObserverCommitDeadlineNanoTime());
        GroundPublicationFailureCache.begin();GroundPublicationFailureCache.success();assertNull(GroundPublicationFailureCache.finish(null));
    }
}
