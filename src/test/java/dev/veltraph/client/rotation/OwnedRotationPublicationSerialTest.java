package dev.veltraph.client.rotation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class OwnedRotationPublicationSerialTest {
    final OwnedRotationTrajectory.Scope scope=new OwnedRotationTrajectory.Scope(new Object(),new Object(),new Object(),new Object());
    final OwnedRotationTrajectory.Pose actual=new OwnedRotationTrajectory.Pose(0,0);
    @Test void actualReturnedTicketMatchesPublicationSerial(){var t=new OwnedRotationTrajectory();assertEquals(0,t.publicationSerial());long issued=t.plan(scope,10,actual,new OwnedRotationTrajectory.Pose(45,0));assertTrue(issued>0);assertEquals(issued,t.publicationSerial());}
    @Test void rawSameParentSameEpochNewPlanInvalidatesOldTicket(){var t=new OwnedRotationTrajectory();long old=t.plan(scope,10,actual,new OwnedRotationTrajectory.Pose(45,0));long epoch=t.epoch();long next=t.plan(scope,10,actual,new OwnedRotationTrajectory.Pose(90,0));assertEquals(epoch,t.epoch());assertEquals(old+1,next);assertNotEquals(old,t.publicationSerial());}
    @Test void consumedEndpointRetainsExactActualPublication(){var t=new OwnedRotationTrajectory();long issued=t.plan(scope,10,actual,new OwnedRotationTrajectory.Pose(45,0));var consumed=t.beforeTick(scope,10,actual).orElseThrow();assertEquals(issued,consumed.ticket());assertFalse(t.active());assertEquals(issued,t.publicationSerial());assertTrue(t.completed(scope,issued));}
    @Test void cancellationDoesNotInventPublicationButRevokesEpoch(){var t=new OwnedRotationTrajectory();long issued=t.plan(scope,10,actual,new OwnedRotationTrajectory.Pose(45,0));long epoch=t.epoch();t.cancel();assertEquals(issued,t.publicationSerial());assertTrue(t.epoch()>epoch);assertFalse(t.completed(scope,issued));}
    @Test void foreignScopePublicationCannotEqualOldTicket(){var t=new OwnedRotationTrajectory();long issued=t.plan(scope,10,actual,new OwnedRotationTrajectory.Pose(45,0));var foreign=new OwnedRotationTrajectory.Scope(new Object(),scope.player(),scope.world(),scope.connection());long next=t.plan(foreign,10,actual,new OwnedRotationTrajectory.Pose(15,0));assertTrue(next>issued);assertEquals(next,t.publicationSerial());assertNotEquals(issued,t.publicationSerial());}
}
