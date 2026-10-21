package dev.veltraph.client.rotation;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
/** Real private trajectory state and ordering, not a forged native source or MC fixture. */
class CollectionCameraApplicationFenceTest {
 @Test void itemTakeDuringObserverDoesNotAdvanceOrApply(){var f=new Fixture();var before=f.t.controlSnapshot();f.observer=()->f.source=false;assertFalse(f.run());assertEquals(1,f.observed);assertEquals(0,f.advanced);assertEquals(0,f.applied);assertTrue(before.current());assertTrue(f.t.readQueued(f.scope,7,f.actual,f.ticket,f.t.epoch()).pending());}
 @Test void sourceRevocationBeforeObserverDoesNotRecordOrAdvance(){var f=new Fixture();var before=f.t.controlSnapshot();f.source=false;assertFalse(f.run());assertEquals(0,f.observed);assertEquals(0,f.advanced);assertTrue(before.current());}
 @Test void rawPoseMutationAfterLastCallbackDoesNotAdvance(){var f=new Fixture();long epoch=f.t.epoch();f.observer=()->f.actual=new OwnedRotationTrajectory.Pose(15,0);assertFalse(f.run());assertEquals(0,f.advanced);assertEquals(0,f.applied);assertEquals(epoch,f.t.epoch());assertEquals(f.ticket,f.t.publicationSerial());}
 @Test void newerSameParentPlanIsNotAppliedOrCancelledByOldPreview(){var f=new Fixture();long epoch=f.t.epoch();f.observer=()->f.t.plan(f.scope,7,f.actual,new OwnedRotationTrajectory.Pose(120,5));assertFalse(f.run());assertEquals(0,f.advanced);assertEquals(0,f.applied);assertEquals(epoch,f.t.epoch());assertEquals(f.ticket+1,f.t.publicationSerial());assertEquals(120,f.t.requested(f.scope,7,f.actual).yaw());}
 @Test void validPrivatePreviewMatchesOriginalFrameArithmetic(){var f=new Fixture();assertTrue(f.run());assertEquals(1,f.observed);assertEquals(1,f.advanced);assertEquals(1,f.applied);assertEquals(45,f.actual.yaw());}
 @Test void tickPreviewHasNoStateSideEffectsAndMatchesOriginalEndpoint(){var f=new Fixture();var control=f.t.controlSnapshot();var p=f.t.previewBeforeTick(f.scope,7,f.actual);assertNotNull(p);assertTrue(control.current());assertTrue(p.current(f.actual));var a=f.t.beforeTick(f.scope,7,f.actual).orElseThrow();assertEquals(p.application(),a);assertFalse(p.current(f.actual));}
 @Test void postAdvanceRefusalNeverPerformsActualPoseWrite(){var f=new Fixture();f.afterAdvance=()->f.source=false;assertFalse(f.run());assertEquals(1,f.advanced);assertEquals(0,f.applied);assertEquals(0,f.actual.yaw());}
 private static final class Fixture {
  final OwnedRotationTrajectory t=new OwnedRotationTrajectory();final OwnedRotationTrajectory.Scope scope=new OwnedRotationTrajectory.Scope(new Object(),new Object(),new Object(),new Object());
  OwnedRotationTrajectory.Pose actual=new OwnedRotationTrajectory.Pose(0,0);final long ticket=t.plan(scope,7,actual,new OwnedRotationTrajectory.Pose(90,10));final OwnedRotationTrajectory.ApplicationPreview preview=t.previewFrame(scope,7,.5f,actual);
  boolean source=true;int observed,advanced,applied;Runnable observer=()->{},afterAdvance=()->{};
  boolean run(){return CollectionCameraApplicationFence.run(new CollectionCameraApplicationFence.Effects(){
   public boolean beforeObserver(){return source&&preview.current(actual);}public void observe(){observed++;observer.run();}public boolean beforeAdvance(){return source&&preview.current(actual);}
   public Optional<OwnedRotationTrajectory.Application> advance(){advanced++;var a=t.frame(scope,7,.5f,actual);afterAdvance.run();return a;}
   public boolean beforeApply(OwnedRotationTrajectory.Application a){return source&&a.ticket()==ticket&&a.pose().equals(preview.application().pose());}
   public boolean apply(OwnedRotationTrajectory.Application a){applied++;actual=a.pose();return true;}
  });}
 }
}
