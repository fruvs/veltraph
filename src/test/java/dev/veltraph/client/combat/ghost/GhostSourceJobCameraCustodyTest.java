package dev.veltraph.client.combat.ghost;

import dev.veltraph.client.rotation.OwnedRotationTrajectory;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GhostSourceJobCameraCustodyTest {
    private final Object owner=new Object(),player=new Object(),world=new Object(),connection=new Object();
    private final OwnedRotationTrajectory.Scope scope=new OwnedRotationTrajectory.Scope(owner,player,world,connection);
    private static OwnedRotationTrajectory.Pose pose(float yaw,float pitch){return new OwnedRotationTrajectory.Pose(yaw,pitch);}
    @Test void validOwnedTrajectoryFramesAndConsumedEndpointKeepIssuedCameraCustody(){
        var trajectory=new OwnedRotationTrajectory();var issued=new GhostSourceJobCameraCustody(owner,trajectory.epoch());
        trajectory.plan(scope,1,pose(0,0),pose(6,0));
        var half=trajectory.frame(scope,1,.5f,pose(0,0)).orElseThrow().pose();
        assertTrue(issued.current(owner,trajectory.epoch()));
        var endpoint=trajectory.beforeTick(scope,1,half).orElseThrow().pose();
        trajectory.validate(scope,2,endpoint);assertTrue(issued.current(owner,trajectory.epoch()));
    }
    @Test void actualTrajectoryCancelRejectsAsyncJobEvenThoughSameParentStillOwnsCamera(){
        var trajectory=new OwnedRotationTrajectory();trajectory.plan(scope,1,pose(0,0),pose(6,0));
        var issued=new GhostSourceJobCameraCustody(owner,trajectory.epoch());trajectory.cancel();
        // Q's prior owner-only long-lived guard would still accept owner here.
        assertSame(owner,scope.owner());assertFalse(issued.current(owner,trajectory.epoch()));
    }
    @Test void actualManualPoseCorrectionRevokesSameOwnerEpochInsteadOfRebasingOldJob(){
        var trajectory=new OwnedRotationTrajectory();trajectory.plan(scope,1,pose(0,0),pose(6,0));
        var issued=new GhostSourceJobCameraCustody(owner,trajectory.epoch());
        assertTrue(trajectory.frame(scope,1,.5f,pose(12,7)).isEmpty());
        assertFalse(issued.current(owner,trajectory.epoch()));
    }
    @Test void foreignAuthorityAndLaterReturnToOriginalOwnerDoNotReviveIssuedEpoch(){
        var trajectory=new OwnedRotationTrajectory();trajectory.plan(scope,1,pose(0,0),pose(6,0));
        var issued=new GhostSourceJobCameraCustody(owner,trajectory.epoch());var foreignOwner=new Object();
        var foreign=new OwnedRotationTrajectory.Scope(foreignOwner,player,world,connection);
        trajectory.plan(foreign,1,pose(0,0),pose(9,0));assertFalse(issued.current(foreignOwner,trajectory.epoch()));
        trajectory.plan(scope,2,pose(0,0),pose(6,0));assertFalse(issued.current(owner,trajectory.epoch()));
    }
    @Test void replacementEpochWithoutCancellationStillRequiresASeparatelyIssuedJob(){
        var issued=new GhostSourceJobCameraCustody(owner,4);assertFalse(issued.current(owner,5));
        var newlyIssued=new GhostSourceJobCameraCustody(owner,5);assertTrue(newlyIssued.current(owner,5));assertFalse(issued.current(owner,5));
        assertFalse(newlyIssued.current(null,5));
    }
    @Test void unavailableOwnerOrInvalidEpochCannotMintCustody(){
        assertThrows(NullPointerException.class,()->new GhostSourceJobCameraCustody(null,0));
        assertThrows(IllegalArgumentException.class,()->new GhostSourceJobCameraCustody(owner,-1));
    }
}
