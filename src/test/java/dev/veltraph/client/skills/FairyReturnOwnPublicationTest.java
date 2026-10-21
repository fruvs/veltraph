package dev.veltraph.client.skills;

import dev.veltraph.client.service.RotationService;
import dev.veltraph.client.world.Vec3;
import dev.veltraph.client.rotation.RotationReceiptIdentity;
import dev.veltraph.client.rotation.RotationPublicationIdentity;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FairyReturnOwnPublicationTest {
    static class QueuedRotation implements RotationService {
        final Object service=new Object(),owner=new Object();Object player=new Object(),world=new Object(),connection=new Object(),authority=owner;
        long generation,epoch;float yaw=60,pitch=30;int cancels;Runnable callback;
        RotationReceiptIdentity.Stamp owned;RotationPublicationIdentity.Transition transition;
        RotationReceiptIdentity.Stamp stamp(){return new RotationReceiptIdentity.Stamp(service,generation,authority,player,world,connection,epoch);}
        boolean current(){
            var actual=stamp();if(RotationReceiptIdentity.current(owned,actual,player,world,connection,authority,false))return true;
            if(RotationPublicationIdentity.refresh(owned,actual,transition,player,world,connection,authority)){owned=actual;return true;}return false;
        }
        public float yaw(){return yaw;}public float pitch(){return pitch;}
        public void aim(float y,float p){generation++;yaw=y;pitch=p;owned=stamp();}
        public void lookAt(Vec3 target){}
        public void rotateTo(float y,float p,Runnable completed){generation++;owned=stamp();callback=completed;}
        public void track(float y,float p){generation++;aim(y,p);}
        public void cancelRotation(){generation++;cancels++;}
        void ownEndpoint(float y,float p){var before=stamp();epoch++;transition=new RotationPublicationIdentity.Transition(before,stamp(),1);yaw=y;pitch=p;}
    }
    private FairyReturnView view(QueuedRotation r){return new FairyReturnView(r,()->r.owned==null||r.current(),r::current,-90,0,.2f,()->r.generation);}
    @Test void provedOwnQueuedEpochCanCompleteItsOriginalCallback(){var r=new QueuedRotation();var v=view(r);assertTrue(v.start());long issued=r.generation;r.ownEndpoint(-90,0);r.callback.run();assertEquals(issued,r.generation);assertEquals(FairyReturnView.State.COMPLETE,v.tick());assertEquals(0,r.cancels);}
    @Test void unknownSameOwnerEpochCannotBeAdoptedOrCancelled(){var r=new QueuedRotation();var v=view(r);assertTrue(v.start());r.epoch++;r.yaw=-90;r.pitch=0;r.callback.run();assertEquals(FairyReturnView.State.FAILED,v.tick());assertEquals(0,r.cancels);}
    @Test void sameParentNewRequestCannotBorrowAnEarlierOwnPlan(){var r=new QueuedRotation();var v=view(r);assertTrue(v.start());r.ownEndpoint(-90,0);r.generation++;r.callback.run();assertEquals(FairyReturnView.State.FAILED,v.tick());assertEquals(0,r.cancels);}
    @Test void foreignContextCannotBorrowAnEarlierOwnPlan(){var r=new QueuedRotation();var v=view(r);assertTrue(v.start());r.ownEndpoint(-90,0);r.connection=new Object();r.callback.run();assertEquals(FairyReturnView.State.FAILED,v.tick());assertEquals(0,r.cancels);}
    @Test void provedRemainderStillWaitsForItsOwnTrackingGeneration(){var r=new QueuedRotation();var v=view(r);assertTrue(v.start());r.ownEndpoint(-89.95f,.02f);r.callback.run();assertEquals(FairyReturnView.State.COMPLETE,v.tick());assertEquals(3,r.generation);assertEquals(0,r.cancels);}
}
