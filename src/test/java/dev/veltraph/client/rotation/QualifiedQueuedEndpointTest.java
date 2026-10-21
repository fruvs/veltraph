package dev.veltraph.client.rotation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class QualifiedQueuedEndpointTest {
    private static OwnedRotationTrajectory.Scope scope(){return new OwnedRotationTrajectory.Scope(new Object(),new Object(),new Object(),new Object());}
    @Test void readOnlyActualEndDoesNotAdvanceSerialEpochOrBeforeTick(){
        var t=new OwnedRotationTrajectory();var scope=scope();var actual=new OwnedRotationTrajectory.Pose(0,0);
        long ticket=t.plan(scope,10,actual,new OwnedRotationTrajectory.Pose(20,0));long epoch=t.epoch();
        var queued=t.readQueued(scope,10,actual,ticket,epoch);assertNotNull(queued);assertTrue(queued.pending());
        assertEquals(20,queued.endpoint().yaw());assertTrue(queued.current(actual));
        assertEquals(ticket,t.publicationSerial());assertEquals(epoch,t.epoch());assertTrue(t.active());
        var normal=t.beforeTick(scope,10,actual).orElseThrow();assertEquals(20,normal.pose().yaw());assertEquals(ticket,normal.ticket());
    }
    @Test void priorOrForeignScopeTicketTickOrPoseCannotQualifyQueuedEnd(){
        var t=new OwnedRotationTrajectory();var scope=scope();var actual=new OwnedRotationTrajectory.Pose(0,0);
        long prior=t.plan(scope,10,actual,new OwnedRotationTrajectory.Pose(10,0));
        long latest=t.plan(scope,10,actual,new OwnedRotationTrajectory.Pose(20,0));
        assertNull(t.readQueued(scope,10,actual,prior,t.epoch()));assertNull(t.readQueued(scope(),10,actual,latest,t.epoch()));
        assertNull(t.readQueued(scope,11,actual,latest,t.epoch()));
        assertNull(t.readQueued(scope,10,new OwnedRotationTrajectory.Pose(.01F,0),latest,t.epoch()));
    }
    @Test void genuinePartialFrameStillReadsStoredEndAndNormalBeforeTickConsumesIt(){
        var t=new OwnedRotationTrajectory();var scope=scope();var actual=new OwnedRotationTrajectory.Pose(0,0);
        long ticket=t.plan(scope,10,actual,new OwnedRotationTrajectory.Pose(20,0));
        var half=t.frame(scope,10,.5F,actual).orElseThrow().pose();assertEquals(10,half.yaw());
        var queued=t.readQueued(scope,10,half,ticket,t.epoch());assertNotNull(queued);assertEquals(20,queued.endpoint().yaw());
        assertEquals(20,t.beforeTick(scope,10,half).orElseThrow().pose().yaw());
    }
    @Test void consumedEndIsKnownWithoutReplayingPoseAndCancelInvalidatesRead(){
        var t=new OwnedRotationTrajectory();var scope=scope();var actual=new OwnedRotationTrajectory.Pose(0,0);
        long ticket=t.plan(scope,10,actual,new OwnedRotationTrajectory.Pose(20,0));var applied=t.beforeTick(scope,10,actual).orElseThrow().pose();
        var consumed=t.readQueued(scope,11,applied,ticket,t.epoch());assertNotNull(consumed);assertFalse(consumed.pending());
        assertTrue(t.beforeTick(scope,11,applied).isEmpty());t.cancel();assertFalse(consumed.current(applied));
    }
}
