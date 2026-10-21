package dev.veltraph.client.combat.ghost;

import org.junit.jupiter.api.Test;
import java.util.concurrent.CompletableFuture;
import static org.junit.jupiter.api.Assertions.*;

class GhostSourceRetryMovementTest {
    @Test void scheduledSearchKeepsExactOldCursorWhileNewResultIsPending(){
        var movement=new GhostSourceRetryMovement<Object,Object>();var producer=new Object();var cursor=new Object();
        var retry=new GhostSourceSearchRetryCounter();var first=retry.recordIssuedFailure();
        var lifetime=new GhostSourceFallbackLifetime();var anchor=new GhostSourceWalkFrontier.Cell(153,76,38);lifetime.installed(anchor);
        assertEquals(20,first.delayTicks());assertEquals(GhostSourceSearchRetryCounter.Next.SCHEDULE_SAME_HANDLER_ENTRY,first.next());
        lifetime.sourceSearchEntry();movement.retain(producer,cursor);var pending=new CompletableFuture<Object>();
        assertFalse(lifetime.active());assertEquals(anchor,lifetime.lastAnchor());assertFalse(pending.isDone());
        assertSame(cursor,movement.retained().cursor());assertTrue(movement.registered(producer));assertFalse(movement.registered(new Object()));
    }
    @Test void retainedIdentityDoesNotGrantParentOrCameraAuthority(){
        var movement=new GhostSourceRetryMovement<Object,Object>();var producer=new Object();movement.retain(producer,new Object());
        var owner=new Object();var player=new Object();var world=new Object();var connection=new Object();
        var original=new GhostSourceOwnedParentArea.Stamp(owner,2,7,player,world,connection);
        var area=new GhostSourceOwnedParentArea(owner);area.observeOwnedConstructorAbsence(original);var receipt=area.capture(original);
        assertTrue(movement.registered(producer));assertFalse(receipt.current(new GhostSourceOwnedParentArea.Stamp(owner,3,7,player,world,connection)));
        assertFalse(receipt.current(new GhostSourceOwnedParentArea.Stamp(owner,2,8,player,world,connection)));
        assertFalse(receipt.current(new GhostSourceOwnedParentArea.Stamp(owner,2,7,player,new Object(),connection)));
        var camera=new GhostSourceJobCameraCustody(owner,19);assertFalse(camera.current(owner,20));assertFalse(camera.current(new Object(),19));
    }
    @Test void actualNewResultCanRetireOldProducerExactlyOnceWithoutReplacingItsCursor(){
        var movement=new GhostSourceRetryMovement<Object,Object>();var producer=new Object();var cursor=new Object();movement.retain(producer,cursor);
        var newResult=new CompletableFuture<Object>();var next=new Object();newResult.complete(next);var old=movement.clear();
        assertSame(producer,old.producer());assertSame(cursor,old.cursor());assertSame(next,newResult.join());assertNull(movement.clear());assertFalse(movement.registered(producer));
    }
    @Test void interruptionCannotReviveRegistrationWhenOriginalContextReturns(){
        var movement=new GhostSourceRetryMovement<Object,Object>();var producer=new Object();movement.retain(producer,new Object());
        movement.clear();assertFalse(movement.active());assertFalse(movement.registered(producer));assertNull(movement.retained());
    }
    @Test void possessionCannotSilentlyReplaceWithAnotherJob(){
        var movement=new GhostSourceRetryMovement<Object,Object>();var producer=new Object();var cursor=new Object();movement.retain(producer,cursor);
        assertThrows(IllegalStateException.class,()->movement.retain(new Object(),new Object()));
        assertSame(producer,movement.retained().producer());assertSame(cursor,movement.retained().cursor());
    }
    @Test void nullInputsNeverCreatePartialRegistration(){
        var movement=new GhostSourceRetryMovement<Object,Object>();assertThrows(NullPointerException.class,()->movement.retain(null,new Object()));assertFalse(movement.active());
        assertThrows(NullPointerException.class,()->movement.retain(new Object(),null));assertFalse(movement.active());
    }
    @Test void separateNewSearchSteeringCannotResetTheExistingCursorTickState(){
        var existing=new GhostSourceWalkSteering();var nextSearch=new GhostSourceWalkSteering();
        var point=new GhostSourceSampledRay.Point(10,76,0);var position=new GhostSourceSampledRay.Point(0,76,0);
        var first=existing.advance(new GhostSourceWalkSteering.Frame(20,position,0,0,0,0,true,point,false,0));
        assertEquals(GhostSourceWalkSteering.Outcome.MOVING,first.outcome());
        nextSearch.reset();assertEquals(GhostSourceWalkSteering.Outcome.REJECTED,existing.advance(new GhostSourceWalkSteering.Frame(20,position,0,0,0,0,true,point,false,0)).outcome());
        assertEquals(GhostSourceWalkSteering.Outcome.MOVING,existing.advance(new GhostSourceWalkSteering.Frame(21,position,first.yaw(),0,0,0,true,point,false,0)).outcome());
    }
    @Test void sixActualFailuresStillChooseRecoveryWithoutADeadlineOrRetryChange(){
        var counter=new GhostSourceSearchRetryCounter();var movement=new GhostSourceRetryMovement<Object,Object>();var producer=new Object();var cursor=new Object();
        for(int i=1;i<=5;i++){var failure=counter.recordIssuedFailure();assertEquals(i,failure.attempts());assertEquals(20,failure.delayTicks());assertEquals(GhostSourceSearchRetryCounter.Next.SCHEDULE_SAME_HANDLER_ENTRY,failure.next());
            movement.retain(producer,cursor);assertTrue(movement.registered(producer));movement.clear();}
        var sixth=counter.recordIssuedFailure();assertEquals(6,sixth.attempts());assertEquals(0,sixth.delayTicks());assertEquals(GhostSourceSearchRetryCounter.Next.SWITCH_PARENT_RECOVERY_CHILD,sixth.next());
    }
}
