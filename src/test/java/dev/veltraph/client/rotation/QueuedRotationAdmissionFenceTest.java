package dev.veltraph.client.rotation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
/** Genuine original trajectory/pose snapshots around adversarial provider callbacks; no native MC authority claim. */
class QueuedRotationAdmissionFenceTest {
    @Test void genuineEmptyObservationDoesNotPublishOrCancel(){var f=new Fixture();assertSame(f.scope,f.observe());assertEquals(1,f.providerCalls);assertTrue(f.t.emptyAt(0,0));}
    @Test void originalActivePlanObservationHasNoTrajectoryEffects(){var f=new Fixture();f.plan();var before=f.t.controlSnapshot();assertSame(f.scope,f.observe());assertTrue(before.current());assertEquals(1,f.t.publicationSerial());assertEquals(0,f.t.epoch());}
    @Test void completedReceiptObservationIsReadOnly(){var f=new Fixture();f.plan();var a=f.t.beforeTick(f.scope,4,f.pose).orElseThrow();f.pose=a.pose();assertSame(f.scope,f.observe());assertTrue(f.t.completed(f.scope,1));assertFalse(f.t.active());}
    @Test void providerNewPlanIsRefusedAndRetainedWithoutOldValidationCancel(){var f=new Fixture();f.plan();f.callback=f::plan;assertNull(f.observe());assertEquals(1,f.providerCalls);assertEquals(2,f.t.publicationSerial());assertEquals(0,f.t.epoch());assertTrue(f.t.active());}
    @Test void providerCancellationIsNotAdoptedAsFreshEmpty(){var f=new Fixture();f.plan();f.callback=f.t::cancel;assertNull(f.observe());assertTrue(f.t.emptyAt(1,1));}
    @Test void providerPoseCorrectionIsPreservedWithoutRestoration(){var f=new Fixture();f.plan();f.callback=()->f.pose=new OwnedRotationTrajectory.Pose(40,5);assertNull(f.observe());assertEquals(40,f.pose.yaw());assertEquals(1,f.t.publicationSerial());assertEquals(0,f.t.epoch());}
    @Test void providerNativeTickChangeRefusesObservation(){var f=new Fixture();f.callback=()->f.tick++;assertNull(f.observe());assertTrue(f.t.emptyAt(0,0));assertEquals(5,f.tick);}
    private static final class Fixture {
        final OwnedRotationTrajectory t=new OwnedRotationTrajectory();final OwnedRotationTrajectory.Scope scope=new OwnedRotationTrajectory.Scope(new Object(),new Object(),new Object(),new Object());
        OwnedRotationTrajectory.Pose pose=new OwnedRotationTrajectory.Pose(0,0);int tick=4,providerCalls;Runnable callback=()->{};
        void plan(){t.plan(scope,tick,pose,new OwnedRotationTrajectory.Pose(90,5));}
        OwnedRotationTrajectory.Scope observe(){
            var control=t.controlSnapshot();long epoch=t.epoch(),serial=t.publicationSerial();int originalTick=tick;var originalPose=pose;
            return QueuedRotationAdmissionFence.observe(()->{providerCalls++;callback.run();return scope;},actual->actual==scope,
                ()->tick==originalTick&&pose.equals(originalPose)&&t.epoch()==epoch&&t.publicationSerial()==serial
                    &&(control==null?t.emptyAt(epoch,serial):control.current()&&control.poseCurrent(pose)));
        }
    }
}
