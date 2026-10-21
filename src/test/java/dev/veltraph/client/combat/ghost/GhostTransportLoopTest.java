package dev.veltraph.client.combat.ghost;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GhostTransportLoopTest {
    @Test void ignoredNativeFalseRetriesOnlyAfterStrictSourceCooldown(){var l=new GhostTransportLoop(1000,100,5000);var a=l.next(1000).orElseThrow();l.dispatched(a,false);assertTrue(l.next(1100).isEmpty());var b=l.next(1101).orElseThrow();assertEquals(2,b.ordinal());assertSame(a.activation(),b.activation());assertEquals(a.deadline(),b.deadline());}
    @Test void successfulDispatchWithoutArrivalAlsoRetries(){var l=new GhostTransportLoop(1000,100,5000);var a=l.next(1000).orElseThrow();l.dispatched(a,true);var b=l.next(1101).orElseThrow();assertFalse(l.complete(a,1102));l.dispatched(b,true);assertTrue(l.complete(b,1102));assertEquals(GhostTransportLoop.State.COMPLETE,l.state());}
    @Test void noInventedSevenAttemptCap(){var l=new GhostTransportLoop(1000,100,5000);for(int n=0;n<9;n++){var a=l.next(1000+n*101).orElseThrow();l.dispatched(a,false);}assertEquals(9,l.attempts());assertEquals(GhostTransportLoop.State.ACTIVE,l.state());}
    @Test void absoluteDeadlineNeverRenewsAfterRetries(){var l=new GhostTransportLoop(1000,100,500);for(int n=0;n<5;n++){var a=l.next(1000+n*101).orElseThrow();assertEquals(1500,a.deadline());l.dispatched(a,false);}assertTrue(l.next(1501).isEmpty());assertEquals(GhostTransportLoop.State.EXHAUSTED,l.state());assertTrue(l.current().isEmpty());}
    @Test void supersededDispatchAndReceiptCannotCompleteCurrentAttempt(){var l=new GhostTransportLoop(1000,100,5000);var a=l.next(1000).orElseThrow();var b=l.next(1101).orElseThrow();l.dispatched(a,true);assertFalse(l.dispatched());assertFalse(l.complete(a,1102));assertFalse(l.complete(b,1102));l.dispatched(b,true);assertTrue(l.complete(b,1102));}
    @Test void cancellationDoesNotRearmOrAcceptLateReceipt(){var l=new GhostTransportLoop(1000,100,5000);var a=l.next(1000).orElseThrow();l.dispatched(a,true);l.cancel();assertFalse(l.complete(a,1200));assertTrue(l.next(1300).isEmpty());assertEquals(GhostTransportLoop.State.CANCELLED,l.state());}
    @Test void backwardClockDoesNotIssueOrCompleteFutureReceipt(){var l=new GhostTransportLoop(1000,100,5000);var a=l.next(1000).orElseThrow();l.dispatched(a,true);assertTrue(l.next(999).isEmpty());assertFalse(l.complete(a,999));assertTrue(l.complete(a,1000));}
}
