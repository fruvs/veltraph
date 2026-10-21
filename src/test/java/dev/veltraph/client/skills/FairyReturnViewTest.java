package dev.veltraph.client.skills;
import dev.veltraph.client.service.RotationService;
import dev.veltraph.client.world.Vec3;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class FairyReturnViewTest {
 static class Rotation implements RotationService {
  float yaw=60,pitch=30;int queues,cancels,direct;Runnable callback;
  public float yaw(){return yaw;}public float pitch(){return pitch;}
  public void aim(float y,float p){direct++;yaw=y;pitch=p;}
  public void lookAt(Vec3 p){}
  public void rotateTo(float y,float p,Runnable done){queues++;callback=done;}
  public void cancelRotation(){cancels++;}
  void applied(float y,float p){yaw=y;pitch=p;callback.run();}
 }
 @Test void queueDoesNotSnapAndPhysicalReturnCannotResumeBeforeActualCallback(){var r=new Rotation();var view=new FairyReturnView(r,()->true,()->true,-90,0);assertTrue(view.start());assertEquals(60,r.yaw);assertEquals(30,r.pitch);assertEquals(0,r.direct);assertEquals(FairyReturnView.State.RESTORING,view.tick());r.applied(-90,0);assertEquals(FairyReturnView.State.COMPLETE,view.tick());assertEquals(1,r.queues);}
 @Test void prematureCallbackWithoutActualEndpointFails(){var r=new Rotation();var v=new FairyReturnView(r,()->true,()->true,-90,0);v.start();r.callback.run();assertEquals(FairyReturnView.State.FAILED,v.tick());assertEquals(1,r.cancels);}
 @Test void manualStopInvalidatesDelayedCallback(){var r=new Rotation();var v=new FairyReturnView(r,()->true,()->true,-90,0);v.start();var late=r.callback;v.cancel();late.run();assertEquals(FairyReturnView.State.CANCELLED,v.tick());assertEquals(1,r.cancels);assertEquals(60,r.yaw);assertEquals(1,r.queues);}
 @Test void foreignEpochIsNotCancelled(){var current=new AtomicBoolean(true);var owns=new AtomicBoolean(true);var r=new Rotation();var v=new FairyReturnView(r,current::get,owns::get,-90,0);v.start();current.set(false);owns.set(false);assertEquals(FairyReturnView.State.FAILED,v.tick());r.callback.run();assertEquals(0,r.cancels);assertEquals(FairyReturnView.State.FAILED,v.tick());}
 @Test void changedInventoryWorldMenuConfigCustodyStopsOriginalRequest(){for(String change:new String[]{"inventory","world","connection","menu","cursor","slot","settings","authority","manual"}){var current=new AtomicBoolean(true);var r=new Rotation();var v=new FairyReturnView(r,current::get,()->true,-90,0);v.start();current.set(false);assertEquals(FairyReturnView.State.FAILED,v.tick(),change);r.callback.run();assertEquals(1,r.cancels,change);assertEquals(60,r.yaw);}}
 @Test void neverRestartsCancelledOrCompletedState(){var r=new Rotation();var v=new FairyReturnView(r,()->true,()->true,-90,0);v.start();v.cancel();assertFalse(v.start());assertEquals(1,r.queues);var complete=new FairyReturnView(r,()->true,()->true,-90,0);complete.start();r.applied(-90,0);assertFalse(complete.start());assertEquals(2,r.queues);}
 @Test void smallQuantizedRemainderUsesBoundedTrackingThenObserved(){var r=new Rotation(){public void aim(float y,float p){direct++;}};var v=new FairyReturnView(r,()->true,()->true,-90,0);v.start();r.applied(-89.95f,.02f);assertEquals(FairyReturnView.State.RESTORING,v.tick());assertEquals(1,r.direct);r.yaw=-90;r.pitch=0;assertEquals(FairyReturnView.State.COMPLETE,v.tick());}
 @Test void wrapEquivalentAppliedViewQualifies(){var r=new Rotation();var v=new FairyReturnView(r,()->true,()->true,-180,0);v.start();r.applied(180,0);assertEquals(FairyReturnView.State.COMPLETE,v.tick());}
}
