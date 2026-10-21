package dev.veltraph.client.combat.ghost;

import dev.veltraph.client.observation.unexpected.UnexpectedBehaviorMonitor;
import static dev.veltraph.client.observation.unexpected.UnexpectedBehaviorMonitor.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GhostTeleportObservationTest {
    final Context context=new Context(new Activation("ghost_grind",1,true),1,2,3);
    final Value target=Value.position(.5,149,-68.5),before=Value.position(10,70,10);
    UnexpectedBehaviorMonitor monitor(){return new UnexpectedBehaviorMonitor(f->{});}
    @Test void longTransportDeadlineMustRegisterWithActualBoundedMonitor(){
        var loop=new GhostTransportLoop(1000,1000,60000);var attempt=loop.next(1001).orElseThrow();var monitor=monitor();
        assertDoesNotThrow(()->monitor.intent(context,Kind.POSITION,target,1,1001,GhostTeleportObservation.ttlMillis(attempt,1001)));
        assertEquals(1,monitor.pendingIntents());assertEquals(61000,attempt.deadline());assertSame(attempt,loop.current().orElseThrow());
    }
    @Test void observationExpiresAfterFiveSecondsWhileTransportRemainsLive(){
        var loop=new GhostTransportLoop(1000,1000,60000);var attempt=loop.next(1001).orElseThrow();loop.dispatched(attempt,true);var monitor=monitor();
        monitor.intent(context,Kind.POSITION,target,1,1001,GhostTeleportObservation.ttlMillis(attempt,1001));
        assertEquals(Classification.UNEXPECTED,monitor.observe(new Observation(context,Kind.POSITION,before,target,6002)).classification());
        assertEquals(0,monitor.pendingIntents());assertEquals(GhostTransportLoop.State.ACTIVE,loop.state());assertEquals(61000,attempt.deadline());assertTrue(loop.complete(attempt,6002));
    }
    @Test void shorterRemainingWindowAndOneUseAttributionRemainExact(){
        var attempt=new GhostTransportLoop.Attempt(new Object(),1,1000,3000);var monitor=monitor();assertEquals(2000,GhostTeleportObservation.ttlMillis(attempt,1000));
        monitor.intent(context,Kind.POSITION,target,1,1000,GhostTeleportObservation.ttlMillis(attempt,1000));
        assertEquals(Classification.EXPLAINED,monitor.observe(new Observation(context,Kind.POSITION,before,target,3000)).classification());
        assertEquals(Classification.UNEXPECTED,monitor.observe(new Observation(context,Kind.POSITION,before,target,3001)).classification());
    }
    @Test void actualMonitorBoundIsUnchangedAndNoWaitRefreshIsIntroduced(){
        var monitor=monitor();assertThrows(IllegalArgumentException.class,()->monitor.intent(context,Kind.POSITION,target,1,1000,5001));
        var attempt=new GhostTransportLoop.Attempt(new Object(),1,1000,30000);monitor.intent(context,Kind.POSITION,target,1,1000,GhostTeleportObservation.ttlMillis(attempt,1000));
        monitor.context(context,5999);assertEquals(1,monitor.pendingIntents());monitor.context(context,6001);assertEquals(0,monitor.pendingIntents());
    }
    @Test void cappedObservationDoesNotChangeRetryActivationDeadlineOrStaleAttemptRejection(){
        var loop=new GhostTransportLoop(1000,1000,60000);var a=loop.next(1001).orElseThrow();loop.dispatched(a,true);GhostTeleportObservation.ttlMillis(a,1001);
        assertTrue(loop.next(2001).isEmpty());var b=loop.next(2002).orElseThrow();assertSame(a.activation(),b.activation());assertEquals(a.deadline(),b.deadline());assertFalse(loop.complete(a,2003));
        assertEquals(5000,GhostTeleportObservation.ttlMillis(b,2002));loop.dispatched(b,true);assertTrue(loop.complete(b,2003));
    }
}
