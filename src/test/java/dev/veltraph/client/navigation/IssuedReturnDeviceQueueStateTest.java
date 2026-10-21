package dev.veltraph.client.navigation;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Production model protocol tests only. Object fixtures never establish native source/SDK authority. */
final class IssuedReturnDeviceQueueStateTest {
    private final Object owner=new Object(),queued=new Object();
    private IssuedReturnDeviceQueueState state(){return new IssuedReturnDeviceQueueState(owner,4,7,10,100,500);}
    private IssuedReturnDeviceQueueState attached(){var s=state();assertTrue(s.attach(owner,queued,5,7,10,101));return s;}
    @Test void genuinePendingRefreshAndLaterPositiveTicketDeliverOnce(){
        var s=attached();assertTrue(s.refresh(owner,queued,queued,5,7,10,-1,120));
        assertTrue(s.observedCompletion(owner,130));var actual=new Object();
        assertTrue(s.refresh(owner,queued,actual,5,8,11,11,150));
        assertTrue(s.deliver(owner,actual,11,151));assertFalse(s.deliver(owner,actual,11,152));
        assertEquals(2,s.refreshCount());assertTrue(s.completionObserved());assertTrue(s.completionDelivered());
    }
    @Test void attachRequiresExactOwnerQueueGenerationAndUnchangedOriginalCounters(){
        var s=state();assertFalse(s.attach(new Object(),queued,5,7,10,101));assertFalse(s.attach(owner,queued,4,7,10,101));
        assertFalse(s.attach(owner,queued,5,8,10,101));assertFalse(s.attach(owner,queued,5,7,11,101));
        assertFalse(s.attach(owner,null,5,7,10,101));assertTrue(s.attach(owner,queued,5,7,10,101));
        assertFalse(s.attach(owner,new Object(),5,7,10,102));
    }
    @Test void foreignCopiedOrReplayedReceiptCannotRefreshOrDeliver(){
        var s=attached();var actual=new Object();assertTrue(s.observedCompletion(owner,120));
        assertFalse(s.refresh(owner,new Object(),actual,5,8,11,11,121));assertFalse(s.refresh(new Object(),queued,actual,5,8,11,11,121));
        assertTrue(s.refresh(owner,queued,actual,5,8,11,11,121));
        assertFalse(s.refresh(owner,queued,new Object(),5,8,11,11,122));assertFalse(s.deliver(owner,new Object(),11,123));
        assertFalse(s.deliver(new Object(),actual,11,123));assertTrue(s.deliver(owner,actual,11,123));
    }
    @Test void noCompletionBeforeQueueOrWithoutGenuinePositiveTicket(){
        var s=state();assertFalse(s.observedCompletion(owner,101));assertFalse(s.deliver(owner,queued,11,101));
        assertTrue(s.attach(owner,queued,5,7,10,101));assertTrue(s.observedCompletion(owner,102));
        assertFalse(s.deliver(owner,queued,10,103));assertFalse(s.observedCompletion(owner,104));
    }
    @Test void generationEpochAndSerialRollbackOrSkippedTicketRefuse(){
        var s=attached();assertFalse(s.refresh(owner,queued,new Object(),6,8,11,11,120));
        assertFalse(s.refresh(owner,queued,new Object(),5,6,11,11,120));assertFalse(s.refresh(owner,queued,new Object(),5,9,11,11,120));
        assertFalse(s.refresh(owner,queued,new Object(),5,8,12,12,120));assertFalse(s.refresh(owner,queued,new Object(),5,8,11,12,120));
        assertFalse(s.refresh(owner,queued,new Object(),5,7,9,9,120));assertEquals(0,s.refreshCount());
    }
    @Test void originalDeadlineIsExclusiveAcrossAllPhases(){
        var s=state();assertFalse(s.attach(owner,queued,5,7,10,99));assertFalse(s.attach(owner,queued,5,7,10,500));
        assertTrue(s.attach(owner,queued,5,7,10,101));assertFalse(s.observedCompletion(owner,500));
        assertTrue(s.observedCompletion(owner,200));var actual=new Object();assertTrue(s.refresh(owner,queued,actual,5,8,11,11,499));
        assertFalse(s.deliver(owner,actual,11,500));assertFalse(s.refresh(owner,actual,actual,5,8,11,11,501));
    }
    @Test void revokedQueueCannotResumeEvenWithMatchingRecordedFields(){
        var s=attached();s.revoke();assertFalse(s.observedCompletion(owner,120));
        assertFalse(s.refresh(owner,queued,queued,5,7,10,-1,120));assertFalse(s.deliver(owner,queued,10,120));
    }
    @Test void successiveRealPositiveTransitionsRequireTheActualCurrentReceipt(){
        var s=attached();var a=new Object();var b=new Object();
        assertTrue(s.refresh(owner,queued,a,5,8,11,11,120));assertTrue(s.refresh(owner,a,b,5,8,12,12,121));
        assertFalse(s.refresh(owner,a,new Object(),5,8,13,13,122));assertTrue(s.observedCompletion(owner,123));
        assertTrue(s.deliver(owner,b,12,124));assertEquals(12,s.ticket());assertSame(b,s.receiptIdentity());
    }
    @Test void recordedClockCannotRollBackWithinOriginalTimeWindow(){
        var s=attached();assertTrue(s.refresh(owner,queued,queued,5,7,10,-1,180));
        assertFalse(s.observedCompletion(owner,179));assertTrue(s.observedCompletion(owner,181));
        var actual=new Object();assertFalse(s.refresh(owner,queued,actual,5,8,11,11,180));
        assertTrue(s.refresh(owner,queued,actual,5,8,11,11,182));assertFalse(s.deliver(owner,actual,11,181));
        assertTrue(s.deliver(owner,actual,11,183));
    }

}
