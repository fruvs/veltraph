package dev.veltraph.client.rotation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class GuiStoredControlIdentityTest {
 private OwnedRotationTrajectory.Scope scope(){return new OwnedRotationTrajectory.Scope(new Object(),new Object(),new Object(),new Object());}
 private OwnedRotationTrajectory.Pose pose(){return new OwnedRotationTrajectory.Pose(14,3);}
 @Test void emptyControlCannotMintSnapshot(){assertNull(new OwnedRotationTrajectory().controlSnapshot());}
 @Test void plannedControlCapturesActualPositiveTicket(){var t=new OwnedRotationTrajectory();var s=scope();long ticket=t.plan(s,2,pose(),new OwnedRotationTrajectory.Pose(15,4));var c=t.controlSnapshot();assertSame(s,c.scope());assertEquals(ticket,c.ticket());assertTrue(c.current());assertTrue(c.poseCurrent(pose()));}
 @Test void completedControlRemainsAuthentic(){var t=new OwnedRotationTrajectory();var s=scope();var end=new OwnedRotationTrajectory.Pose(15,4);long ticket=t.plan(s,2,pose(),end);t.beforeTick(s,2,pose());var c=t.controlSnapshot();assertEquals(ticket,c.ticket());assertTrue(c.current());assertTrue(c.poseCurrent(end));}
 @Test void actualCancellationInvalidatesStoredControlWithoutPublication(){var t=new OwnedRotationTrajectory();long ticket=t.plan(scope(),2,pose(),pose());var c=t.controlSnapshot();long before=c.epoch();t.cancel();assertFalse(c.current());assertNull(t.controlSnapshot());assertTrue(t.emptyAt(before+1,ticket));assertEquals(ticket,t.publicationSerial());}
 @Test void repeatedActualEmptyCancellationHasExactEndpointOnly(){var t=new OwnedRotationTrajectory();long ticket=t.plan(scope(),2,pose(),pose());long before=t.epoch();t.cancel();assertTrue(t.emptyAt(before+1,ticket));t.cancel();assertFalse(t.emptyAt(before+1,ticket));assertTrue(t.emptyAt(before+2,ticket));}
 @Test void sameEpochNewPublicationRevokesOldControl(){var t=new OwnedRotationTrajectory();var s=scope();t.plan(s,2,pose(),pose());var c=t.controlSnapshot();long epoch=t.epoch();t.plan(s,2,pose(),pose());assertEquals(epoch,t.epoch());assertFalse(c.current());}
 @Test void foreignControlCannotKeepOriginalIdentity(){var t=new OwnedRotationTrajectory();var original=scope();t.plan(original,2,pose(),pose());var c=t.controlSnapshot();t.plan(scope(),2,pose(),pose());assertFalse(c.current());assertNotSame(original,t.controlSnapshot().scope());}
 @Test void correctedPoseDoesNotPassStoredControl(){var t=new OwnedRotationTrajectory();t.plan(scope(),2,pose(),pose());var c=t.controlSnapshot();assertFalse(c.poseCurrent(new OwnedRotationTrajectory.Pose(15,3)));assertFalse(c.poseCurrent(new OwnedRotationTrajectory.Pose(14,4)));}
}
