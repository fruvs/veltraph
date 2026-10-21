package dev.veltraph.client.rewarp;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class FarmMaintenancePreparationTest {
    final Object farm=new Object(),helper=new Object(),checkpoint=new Object();
    final FarmMaintenanceLease lease=new FarmMaintenanceLease();
    final FarmMaintenanceLease.Token token=lease.prepare(farm,helper,100,1000);
    final List<String> calls=new ArrayList<>();boolean valid=true,acknowledged,allowActivation=true,invalidateDuringActivation,reenter;int activations,commits,rollbacks,captures;
    FarmMaintenancePreparation<Object> gate;
    FarmMaintenancePreparation<Object> make(){
        gate=new FarmMaintenancePreparation<>(lease,token,farm,helper,new FarmMaintenancePreparation.Port<>(){
            public boolean valid(){return valid;}
            public Optional<Object> captureWitness(){captures++;calls.add("capture");return acknowledged?Optional.of(checkpoint):Optional.empty();}
            public boolean activate(Object witness){assertSame(checkpoint,witness);activations++;calls.add("activate");if(invalidateDuringActivation)valid=false;return allowActivation;}
            public void committed(Object witness){assertSame(checkpoint,witness);assertEquals(FarmMaintenanceLease.State.HELPER_ACTIVE,lease.state());commits++;calls.add("committed");if(reenter)gate.tick(500);}
            public void rollback(){rollbacks++;calls.add("rollback");lease.rollback(token);}
        });return gate;
    }
    @Test void knownPendingAcksNeverActivateHelperOrObserver(){var g=make();g.tick(100);g.tick(200);g.tick(1000);assertEquals(3,captures);assertEquals(0,activations);assertEquals(0,commits);assertEquals(FarmMaintenanceLease.State.PREPARED,lease.state());}
    @Test void sameCapturedCheckpointPrecedesActivationCommitAndObserverExactlyOnce(){var g=make();g.tick(100);acknowledged=true;g.tick(200);g.tick(201);assertEquals(List.of("capture","capture","activate","committed"),calls);assertEquals(1,activations);assertEquals(1,commits);assertEquals(0,rollbacks);}
    @Test void waitingDoesNotRearmOriginalLeaseDeadline(){var g=make();g.tick(100);g.tick(1099);acknowledged=true;g.tick(1100);assertEquals(0,activations);assertEquals(1,rollbacks);assertEquals(FarmMaintenancePreparation.State.CANCELLED,g.state());}
    @Test void manualStopBeforeAckRevokesInsteadOfStartingLater(){var g=make();g.tick(100);valid=false;g.tick(101);valid=true;acknowledged=true;g.tick(102);assertEquals(0,activations);assertEquals(0,commits);assertEquals(1,rollbacks);}
    @Test void actualTerminalTimerExpiryBeforePreparedDispatchPreventsActivation(){var timer=new dev.veltraph.client.scheduling.SourceStopTimer();timer.arm(0,dev.veltraph.client.scheduling.ScheduleTimeUnit.SECONDS);var g=make();acknowledged=true;if(timer.tick())valid=false;g.tick(100);assertEquals(0,activations);assertEquals(0,commits);assertEquals(1,rollbacks);}
    @Test void failedGuardRollsBackWithoutFreshStartObserver(){var g=make();acknowledged=true;allowActivation=false;g.tick(100);assertEquals(1,activations);assertEquals(0,commits);assertEquals(1,rollbacks);assertFalse(lease.active());}
    @Test void CustodyOrCheckpointDriftDuringActivationCannotCommit(){var g=make();acknowledged=true;invalidateDuringActivation=true;g.tick(100);assertEquals(1,activations);assertEquals(0,commits);assertEquals(1,rollbacks);}
    @Test void synchronousObserverReentryCannotActivateOrCommitTwice(){var g=make();acknowledged=true;reenter=true;g.tick(100);assertEquals(1,activations);assertEquals(1,commits);}
    @Test void wrongLeaseTokenAndClockRegressionNeverActivate(){var g=make();acknowledged=true;g.tick(99);assertEquals(0,activations);assertEquals(1,rollbacks);}
}
