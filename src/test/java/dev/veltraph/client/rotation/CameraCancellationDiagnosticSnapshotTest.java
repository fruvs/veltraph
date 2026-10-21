package dev.veltraph.client.rotation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
/** Pure actual trajectory operands; no native permission or correlation is inferred. */
final class CameraCancellationDiagnosticSnapshotTest {
 final OwnedRotationTrajectory.Scope scope=new OwnedRotationTrajectory.Scope(new Object(),new Object(),new Object(),new Object());
 final OwnedRotationTrajectory.Pose pose=new OwnedRotationTrajectory.Pose(0,0);
 @Test void repeatedDiagnosticReadsCannotPlanOrCancel(){var t=new OwnedRotationTrajectory();long ticket=t.plan(scope,10,pose,new OwnedRotationTrajectory.Pose(40,0));long epoch=t.epoch();var before=t.debugSnapshot();for(int n=0;n<100;n++)assertEquals(before,t.debugSnapshot());assertEquals(epoch,t.epoch());assertEquals(ticket,t.publicationSerial());assertTrue(t.active());}
 @Test void actualCancelChangesEpochOnceWithoutInventingPublication(){var t=new OwnedRotationTrajectory();long ticket=t.plan(scope,10,pose,new OwnedRotationTrajectory.Pose(40,0));var before=t.debugSnapshot();t.cancel();var after=t.debugSnapshot();assertEquals(before.epoch()+1,after.epoch());assertEquals(ticket,after.serial());assertNull(after.scope());assertNull(after.completedScope());assertSame(scope,before.scope());}
 @Test void consumedEndpointRetainsRealOwnerUntilRevocation(){var t=new OwnedRotationTrajectory();long ticket=t.plan(scope,10,pose,new OwnedRotationTrajectory.Pose(40,0));t.beforeTick(scope,10,pose).orElseThrow();var before=t.debugSnapshot();assertNull(before.scope());assertSame(scope,before.completedScope());assertEquals(ticket,before.completedTicket());t.cancel();assertNull(t.debugSnapshot().completedScope());assertEquals(ticket,t.debugSnapshot().serial());}
 @Test void snapshotsCannotHideForeignNewPublication(){var t=new OwnedRotationTrajectory();long old=t.plan(scope,10,pose,new OwnedRotationTrajectory.Pose(40,0));var before=t.debugSnapshot();var foreign=new OwnedRotationTrajectory.Scope(new Object(),scope.player(),scope.world(),scope.connection());long next=t.plan(foreign,10,pose,new OwnedRotationTrajectory.Pose(80,0));var after=t.debugSnapshot();assertEquals(old,before.serial());assertEquals(next,after.serial());assertSame(foreign,after.scope());assertNotEquals(before,after);}
}
