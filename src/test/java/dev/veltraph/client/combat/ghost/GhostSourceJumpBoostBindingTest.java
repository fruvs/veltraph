package dev.veltraph.client.combat.ghost;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static dev.veltraph.client.combat.ghost.GhostSourceJumpBoostGate.*;
final class GhostSourceJumpBoostBindingTest {
    @Test void noAccessAndRecentEntryPermitOrdinarySearch(){assertEquals(Result.NOT_REQUIRED,evaluate(Observed.YES,Observed.NO,true,800000,0));
        assertEquals(Result.NOT_REQUIRED,evaluate(Observed.YES,Observed.YES,true,800000,500000));assertEquals(Result.MODIFY_REQUIRED,evaluate(Observed.YES,Observed.YES,true,800001,500000));}
    @Test void accessUnknownNeverGrantsMenuOrSearch(){assertEquals(Result.UNKNOWN,evaluate(Observed.YES,Observed.UNKNOWN,true,800001,0));assertEquals(Result.NOT_REQUIRED,evaluate(Observed.NO,Observed.UNKNOWN,true,800001,0));}
    @Test void actualEntryResetsStaticStyleClockWithoutConfigurationOrWorldReset(){var clock=new GhostSourceJumpBoostClock();assertEquals(0,clock.lastEntry());clock.actualHelperEntry(500000);
        assertEquals(Result.NOT_REQUIRED,evaluate(Observed.YES,Observed.YES,true,800000,clock.lastEntry()));assertEquals(Result.MODIFY_REQUIRED,evaluate(Observed.YES,Observed.YES,true,800001,clock.lastEntry()));clock.actualHelperEntry(800001);assertEquals(800001,clock.lastEntry());}
    private static GhostSourceJumpBoostContinuation.Scope scope(Object parent,Object child,Object player,Object world,Object connection,long generation){return new GhostSourceJumpBoostContinuation.Scope(parent,child,generation,2,player,world,connection,9);}
    @Test void onlySameChildAndGenuineCloseConsumeOnce(){var parent=new Object();var child=new Object();var p=new Object();var w=new Object();var c=new Object();var scope=scope(parent,child,p,w,c,1);var continuation=new GhostSourceJumpBoostContinuation(scope);
        assertFalse(continuation.consumeActualClose(scope,false));assertEquals(GhostSourceJumpBoostContinuation.State.ACTIVE,continuation.state());assertTrue(continuation.consumeActualClose(scope,true));assertFalse(continuation.consumeActualClose(scope,true));}
    @Test void childGenerationAndWorldChangesCannotBorrowCompletion(){var parent=new Object();var child=new Object();var p=new Object();var w=new Object();var c=new Object();var scope=scope(parent,child,p,w,c,1);
        for(var changed:java.util.List.of(scope(parent,new Object(),p,w,c,1),scope(parent,child,p,w,c,2),scope(parent,child,p,new Object(),c,1))){var continuation=new GhostSourceJumpBoostContinuation(scope);assertFalse(continuation.consumeActualClose(changed,true));assertEquals(GhostSourceJumpBoostContinuation.State.REVOKED,continuation.state());}}
    @Test void cancelledHelperDoesNotResumeAfterLaterRealClose(){var object=new Object();var scope=scope(object,object,object,object,object,1);var continuation=new GhostSourceJumpBoostContinuation(scope);continuation.revoke();assertFalse(continuation.consumeActualClose(scope,true));}
}
