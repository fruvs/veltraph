package dev.veltraph.client.hydra.source;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static dev.veltraph.client.hydra.source.SourceHydraLocalDispatch.*;
final class SourceHydraLocalDispatchTest {
    @Test void inactiveDoesNotAdvanceWhileAllActiveInvocationsAdvance(){assertEquals(7,tick(State.INACTIVE,7,20,Policy.IGNORE,false,false,false).counter());assertEquals(new Decision(State.LISTENING,8,Action.SCAN_ORDERED_CHECKS),tick(State.LISTENING,7,20,Policy.IGNORE,false,false,false));assertEquals(new Decision(State.RESPONDING,8,Action.NONE),tick(State.RESPONDING,7,20,Policy.IGNORE,false,false,false));}
    @Test void thresholdUsesIncrementedCounterAndRareOverridePreservesDecisionOrdering(){assertEquals(Action.NONE,tick(State.ABOUT_TO_RESPOND,18,20,Policy.STOP_ALL,true,true,false).action());assertEquals(new Decision(State.ABOUT_TO_RESPOND,0,Action.BEGIN_LOCAL_REACTION),tick(State.ABOUT_TO_RESPOND,19,20,Policy.STOP_ALL,true,true,false));assertEquals(new Decision(State.RESPONDING_TEMPORARILY_DISABLED,0,Action.STOP_ALL),tick(State.ABOUT_TO_RESPOND,19,20,Policy.STOP_ALL,true,false,false));assertEquals(Action.CLEAR_LOCAL_STATE,tick(State.ABOUT_TO_RESPOND,19,20,Policy.IGNORE,false,true,false).action());}
    @Test void temporaryDisableReenablesAtInvocation600AndAfterResponseRequiresHandler(){assertEquals(Action.NONE,tick(State.RESPONDING_TEMPORARILY_DISABLED,598,0,Policy.IGNORE,false,false,false).action());assertEquals(new Decision(State.LISTENING,0,Action.CLEAR_LOCAL_STATE),tick(State.RESPONDING_TEMPORARILY_DISABLED,599,0,Policy.IGNORE,false,false,false));assertEquals(new Decision(State.LISTENING,1,Action.SCAN_ORDERED_CHECKS),tick(State.LISTENING,0,0,Policy.IGNORE,false,false,false));assertEquals(Action.NONE,tick(State.AFTER_RESPONDING,8,0,Policy.REACT,false,false,false).action());assertEquals(Action.ADVANCE_AFTER_RESPONSE,tick(State.AFTER_RESPONDING,8,0,Policy.REACT,false,false,true).action());}
    @Test void busyReportDoesNotReplaceSelectedCheckOrClockAndFreeReportCapturesExactIdentity(){Object old=new Object(),fresh=new Object(),kind=new Object();var busy=acquire(State.FIX_PITCH,9,20,old,kind,100,fresh,kind,200,30);assertSame(old,busy.check());assertEquals(100,busy.occurredAt());assertEquals(9,busy.counter());var ready=acquire(State.LISTENING,9,20,old,kind,100,fresh,kind,200,30);assertSame(fresh,ready.check());assertEquals(State.ABOUT_TO_RESPOND,ready.state());assertEquals(0,ready.counter());assertEquals(30,ready.threshold());assertEquals(200,ready.occurredAt());}
    @Test void cleanupResetsPrefixBeforeExternalFailureWithoutClearingSelectedIdentityOrThreshold(){
        Object check=new Object(),kind=new Object();var before=new CleanupSnapshot(State.RESPONDING_TEMPORARILY_DISABLED,600,91,check,kind,25);
        var observed=new java.util.concurrent.atomic.AtomicReference<CleanupSnapshot>();
        var expected=new CleanupSnapshot(State.INACTIVE,0,0,check,kind,25);
        RuntimeException boom=new RuntimeException("external helper failure");
        assertSame(boom,assertThrows(RuntimeException.class,()->cleanupThen(before,State.LISTENING,prefix->{observed.set(prefix);throw boom;})));
        assertEquals(expected,observed.get());assertSame(check,observed.get().check());assertSame(kind,observed.get().kind());
        assertEquals(new CleanupSnapshot(State.LISTENING,0,0,check,kind,25),cleanupThen(before,State.LISTENING,observed::set));
    }
    @Test void delayConversionTruncatesAndExceptionFallbackIs25(){assertEquals(25,sampledThreshold(1.299,false));assertEquals(26,sampledThreshold(1.3,false));assertEquals(25,sampledThreshold(Double.NaN,true));}
}
