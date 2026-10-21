package dev.veltraph.client.rotation;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
/** Genuine trajectory and adversarial callback ordering; not native registration or MC acceptance. */
class QueuedRotationWorkflowApplicationTest {
    @Test void retiredWorkflowKeepsPendingPoseWithoutObserver(){var f=new Fixture();f.version++;var old=f.t.controlSnapshot();assertFalse(f.run(false));assertTrue(old.current());assertEquals(0,f.observed);assertEquals(0,f.advanced);assertEquals(0,f.applied);}
    @Test void changedAndRevertedConfigDoesNotReuseOriginalVersion(){var f=new Fixture();f.version+=2;assertFalse(f.run(false));assertEquals(f.ticket,f.t.publicationSerial());assertEquals(0,f.t.epoch());assertEquals(0,f.actual.yaw());}
    @Test void workflowCallbackNewPlanCannotBeAdoptedByOldNativeGuard(){var f=new Fixture();f.workflow=()->{f.replace();return true;};assertFalse(f.run(false));assertEquals(0,f.observed);assertEquals(0,f.advanced);assertEquals(f.ticket+1,f.t.publicationSerial());assertEquals(120,f.t.requested(f.scope,7,f.actual).yaw());}
    @Test void nativeLeafCancellationCannotBeAdoptedAsNewEpoch(){var f=new Fixture();f.nativeLeaf=()->{f.t.cancel();return true;};assertFalse(f.run(false));assertEquals(0,f.advanced);assertEquals(1,f.t.epoch());assertEquals(f.ticket,f.t.publicationSerial());assertFalse(f.t.active());}
    @Test void falseWorkflowDoesNotCallNativeLeaf(){var f=new Fixture();int[] calls={0};f.workflow=()->false;f.nativeLeaf=()->{calls[0]++;return true;};assertFalse(f.run(false));assertEquals(0,calls[0]);}
    @Test void observerRetirementDoesNotAdvanceOriginalPendingPose(){var f=new Fixture();f.observer=()->f.version++;var old=f.t.controlSnapshot();assertFalse(f.run(false));assertTrue(old.current());assertEquals(1,f.observed);assertEquals(0,f.advanced);assertEquals(0,f.applied);}
    @Test void observerNewSameParentPlanSurvivesOldApplicationRefusal(){var f=new Fixture();f.observer=f::replace;assertFalse(f.run(false));assertEquals(1,f.observed);assertEquals(0,f.advanced);assertEquals(0,f.applied);assertEquals(f.ticket+1,f.t.publicationSerial());assertEquals(0,f.t.epoch());}
    @Test void finalWorkflowCallbackNewPlanDoesNotWriteOldPose(){var f=new Fixture();f.workflow=()->{if(f.advanced>0)f.replace();return true;};assertFalse(f.run(false));assertEquals(1,f.advanced);assertEquals(0,f.applied);assertEquals(0,f.actual.yaw());assertEquals(f.ticket+1,f.t.publicationSerial());assertEquals(120,f.t.requested(f.scope,7,f.actual).yaw());}
    @Test void genuineFrameKeepsOriginalArithmeticAndSingleObserver(){var f=new Fixture();assertTrue(f.run(false));assertEquals(45,f.actual.yaw());assertEquals(5,f.actual.pitch());assertEquals(1,f.observed);assertEquals(1,f.advanced);assertEquals(1,f.applied);assertEquals(f.ticket,f.t.publicationSerial());}
    @Test void genuineTickConsumesActualEndpointAndDoesNotReplay(){var f=new Fixture();assertTrue(f.run(true));assertEquals(90,f.actual.yaw());assertTrue(f.t.completed(f.scope,f.ticket));assertFalse(f.t.active());assertFalse(f.run(true));assertEquals(1,f.applied);assertEquals(1,f.observed);}
    @Test void expiredTickRefusesWithoutCancellingOriginalTicket(){var f=new Fixture();f.tick=8;assertFalse(f.run(false));assertEquals(0,f.observed);assertEquals(0,f.advanced);assertEquals(f.ticket,f.t.publicationSerial());assertEquals(0,f.t.epoch());assertTrue(f.t.active());}
    private static final class Fixture {
        final OwnedRotationTrajectory t=new OwnedRotationTrajectory();
        final OwnedRotationTrajectory.Scope scope=new OwnedRotationTrajectory.Scope(new Object(),new Object(),new Object(),new Object());
        OwnedRotationTrajectory.Pose actual=new OwnedRotationTrajectory.Pose(0,0);int tick=7,version=1,observed,advanced,applied;
        final long ticket=t.plan(scope,tick,actual,new OwnedRotationTrajectory.Pose(90,10)),epoch=t.epoch();
        java.util.function.BooleanSupplier workflow=()->version==1,nativeLeaf=()->true;Runnable observer=()->{};
        void replace(){t.plan(scope,7,actual,new OwnedRotationTrajectory.Pose(120,20));}
        boolean identity(){var c=t.controlSnapshot();return c!=null&&c.scope()==scope&&c.ticket()==ticket&&c.epoch()==epoch&&c.current();}
        boolean allowed(){return QueuedRotationWorkflowFence.allowed(workflow,nativeLeaf,this::identity);}
        boolean run(boolean head){
            var preview=head?t.previewBeforeTick(scope,tick,actual):t.previewFrame(scope,tick,.5f,actual);
            if(preview==null)return false;
            return CollectionCameraApplicationFence.run(new CollectionCameraApplicationFence.Effects(){
                public boolean beforeObserver(){return allowed()&&preview.current(actual);}
                public void observe(){observed++;observer.run();}
                public boolean beforeAdvance(){return allowed()&&preview.current(actual);}
                public Optional<OwnedRotationTrajectory.Application> advance(){advanced++;return head?t.beforeTick(scope,tick,actual):t.frame(scope,tick,.5f,actual);}
                public boolean beforeApply(OwnedRotationTrajectory.Application a){return a.ticket()==ticket&&a.pose().equals(preview.application().pose())&&allowed();}
                public boolean apply(OwnedRotationTrajectory.Application a){if(!identity())return false;applied++;actual=a.pose();return true;}
            });
        }
    }
}
