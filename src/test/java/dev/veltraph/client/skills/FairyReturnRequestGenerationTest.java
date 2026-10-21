package dev.veltraph.client.skills;

import dev.veltraph.client.service.RotationService;
import dev.veltraph.client.world.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FairyReturnRequestGenerationTest {
    static class Rotation implements RotationService {
        long generation;float yaw=60,pitch=30;int cancels,tracks;Runnable callback;
        public float yaw(){return yaw;}public float pitch(){return pitch;}
        public void aim(float y,float p){generation++;yaw=y;pitch=p;}
        public void lookAt(Vec3 target){}
        public void rotateTo(float y,float p,Runnable done){generation++;callback=done;}
        public void track(float y,float p){generation++;tracks++;aim(y,p);}
        public void cancelRotation(){generation++;cancels++;}
        void applied(float y,float p){yaw=y;pitch=p;callback.run();}
    }
    private FairyReturnView view(Rotation r){return new FairyReturnView(r,()->true,()->true,-90,0,.2f,()->r.generation);}
    @Test void newerQueuedRequestWithSameOwnerAndEpochIsNotCancelledOrCredited(){var r=new Rotation();var v=view(r);assertTrue(v.start());var old=r.callback;r.rotateTo(40,20,()->{});assertEquals(FairyReturnView.State.FAILED,v.tick());old.run();assertEquals(0,r.cancels);assertEquals(0,r.tracks);assertEquals(FairyReturnView.State.FAILED,v.state());}
    @Test void synchronousCompletedCallbackReplacementCannotBeAdopted(){var r=new Rotation(){public void rotateTo(float y,float p,Runnable done){generation++;yaw=y;pitch=p;done.run();generation++;}};var v=view(r);assertFalse(v.start());assertEquals(FairyReturnView.State.FAILED,v.state());assertEquals(0,r.cancels);}
    @Test void ownCurrentRequestCanBeCancelledAndLateCallbackCannotResume(){var r=new Rotation();var v=view(r);assertTrue(v.start());var callback=r.callback;v.cancel();callback.run();assertEquals(FairyReturnView.State.CANCELLED,v.state());assertEquals(1,r.cancels);assertEquals(0,r.tracks);}
    @Test void trackingPublicationKeepsItsExactRequestUntilActuallyApplied(){var r=new Rotation();var v=view(r);assertTrue(v.start());r.applied(-89.95f,.02f);assertEquals(FairyReturnView.State.COMPLETE,v.tick());assertEquals(1,r.tracks);assertEquals(0,r.cancels);}
    @Test void replacementDuringRemainderTrackingCannotBeAdopted(){var r=new Rotation(){public void track(float y,float p){super.track(y,p);generation++;}};var v=view(r);assertTrue(v.start());r.applied(-89.95f,.02f);assertEquals(FairyReturnView.State.FAILED,v.tick());assertEquals(0,r.cancels);assertEquals(1,r.tracks);}
    @Test void sameTickTrackingWithoutNativeAimRefusesTheStaleEndpoint(){var r=new Rotation(){public void track(float y,float p){generation++;tracks++;}};var v=view(r);assertTrue(v.start());r.applied(-89.95f,.02f);assertEquals(FairyReturnView.State.FAILED,v.tick());assertEquals(0,r.cancels);}
    @Test void overflowCannotPublishARequest(){var r=new Rotation();r.generation=Long.MAX_VALUE;var v=view(r);assertFalse(v.start());assertNull(r.callback);assertEquals(0,r.cancels);}
}
