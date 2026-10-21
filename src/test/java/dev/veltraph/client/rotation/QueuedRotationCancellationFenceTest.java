package dev.veltraph.client.rotation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
/** Real trajectory cancellation, including replacement during original post-cancel observation. */
class QueuedRotationCancellationFenceTest {
    @Test void originalCancellationPreservesSerialAndHasOneRealEpochTransition(){var f=new Fixture();assertTrue(f.run());assertEquals(1,f.cancelled);assertEquals(f.ticket,f.t.publicationSerial());assertEquals(f.epoch+1,f.t.epoch());assertTrue(f.t.emptyAt(f.epoch+1,f.ticket));}
    @Test void refusedCleanupDoesNotMutateTrajectory(){var f=new Fixture();f.workflow=()->false;var control=f.t.controlSnapshot();assertFalse(f.run());assertTrue(control.current());assertEquals(0,f.cancelled);}
    @Test void cleanupLeafNewPlanIsRetainedWithoutCancellation(){var f=new Fixture();f.leaf=()->{f.replace();return true;};assertFalse(f.run());assertEquals(0,f.cancelled);assertEquals(f.ticket+1,f.t.publicationSerial());assertEquals(120,f.t.requested(f.scope,4,f.actual).yaw());}
    @Test void newPlanDuringPostCancelDiagnosticRefusesReceiptAndPreservesReplacement(){var f=new Fixture();f.diagnostic=f::replace;assertFalse(f.run());assertEquals(1,f.cancelled);assertEquals(f.ticket+1,f.t.publicationSerial());assertEquals(120,f.t.requested(f.scope,4,f.actual).yaw());assertTrue(f.t.active());}
    @Test void laterCancellationDuringDiagnosticCannotBeCreditedAsOriginalTransition(){var f=new Fixture();f.diagnostic=f.t::cancel;assertFalse(f.run());assertEquals(1,f.cancelled);assertEquals(f.epoch+2,f.t.epoch());assertEquals(f.ticket,f.t.publicationSerial());}
    @Test void callbackExceptionNeverMintsReturnedCancellationReceipt(){var f=new Fixture();f.diagnostic=()->{throw new IllegalStateException("actual observer failed");};assertThrows(IllegalStateException.class,f::run);assertEquals(1,f.cancelled);assertTrue(f.t.emptyAt(f.epoch+1,f.ticket));}
    private static final class Fixture {
        final OwnedRotationTrajectory t=new OwnedRotationTrajectory();final OwnedRotationTrajectory.Scope scope=new OwnedRotationTrajectory.Scope(new Object(),new Object(),new Object(),new Object());
        final OwnedRotationTrajectory.Pose actual=new OwnedRotationTrajectory.Pose(0,0);final long ticket=t.plan(scope,4,actual,new OwnedRotationTrajectory.Pose(90,5)),epoch=t.epoch();
        int cancelled;java.util.function.BooleanSupplier workflow=()->true,leaf=()->true;Runnable diagnostic=()->{};
        void replace(){t.plan(scope,4,actual,new OwnedRotationTrajectory.Pose(120,10));}
        boolean own(){var c=t.controlSnapshot();return c!=null&&c.scope()==scope&&c.ticket()==ticket&&c.epoch()==epoch&&c.current();}
        boolean run(){return QueuedRotationCancellationFence.run(new QueuedRotationCancellationFence.Effects(){
            public boolean beforeCancel(){return QueuedRotationWorkflowFence.allowed(workflow,leaf,Fixture.this::own);}
            public void cancel(){cancelled++;t.cancel();diagnostic.run();}
            public boolean afterCancel(){return t.emptyAt(epoch+1,ticket);}
        });}
    }
}
