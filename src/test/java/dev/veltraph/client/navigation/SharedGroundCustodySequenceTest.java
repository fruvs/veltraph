package dev.veltraph.client.navigation;

import dev.veltraph.client.rotation.OwnedRotationTrajectory;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Actual pure trajectory tickets composed with admission, dormant proof and cleanup accounting. */
final class SharedGroundCustodySequenceTest {
    static final class Scene {
        final Object root=new Object(),parent=new Object(),player=new Object(),world=new Object(),connection=new Object(),service=new Object(),token=new Object();
        Object config=new Object(),active; long version,revision=3,generation=4;
        final OwnedRotationTrajectory trajectory=new OwnedRotationTrajectory();
        final OwnedRotationTrajectory.Scope scope=new OwnedRotationTrajectory.Scope(parent,player,world,connection);
        final OwnedRotationTrajectory.Pose actual=new OwnedRotationTrajectory.Pose(0,0);
        LegacyTravelPublicationIdentity.Lease leg; int released,cancelled; boolean reenterAfterRelease;
        LegacyTravelPublicationIdentity.State state(){return new LegacyTravelPublicationIdentity.State(root,parent,parent,parent,player,world,connection,config,revision,7,service,generation,trajectory.epoch(),trajectory.publicationSerial(),active,true,true,true);}
        void admit(){var before=state();leg=LegacyTravelPublicationIdentity.Lease.admit(before,state(),token,version,version);assertNotNull(leg);active=token;version++;}
        long publish(float yaw){var before=state();long ticket=trajectory.plan(scope,10,actual,new OwnedRotationTrajectory.Pose(yaw,0));assertTrue(leg.cameraAttempted(before,ticket));assertTrue(leg.published(new LegacyTravelPublicationIdentity.Publication(before,state(),ticket)));return ticket;}
        LegacyTravelRetirement.RetirementOutcome retire(){return LegacyTravelRetirement.retire(new LegacyTravelRetirement.Effects(){
            public boolean keysPublished(){return leg.keysPublished();}
            public boolean cameraPublishedOrQueued(){return leg.cameraPublishedOrQueued();}
            public boolean activeLegMatches(){return active==token;}
            public boolean cleanupFence(){return leg.cleanupCurrent(state());}
            public boolean release(LegacyTravelRetirement.Key key){
                if(!leg.mayReleaseKeys(state()))return false;released++;
                if(reenterAfterRelease)trajectory.plan(scope,10,actual,new OwnedRotationTrajectory.Pose(90,0));
                return leg.cleanupCurrent(state());
            }
            public boolean cancelCamera(){if(!leg.mayCancelCamera(state()))return false;var before=state();trajectory.cancel();cancelled++;return leg.cancelled(before,state(),false);}
            public LegacyTravelRetirement.Removal retireLogical(){leg.closed();if(active!=token)return LegacyTravelRetirement.Removal.FOREIGN_PRESERVED;active=null;version++;return LegacyTravelRetirement.Removal.REMOVED_OWN;}
        });}
    }
    @Test void unpublishedCandidateOwnsAdmissionButHasNoNativePublication(){
        var scene=new Scene();scene.admit();var dormant=new DormantGroundPublication(()->scene.leg.current(scene.state()));
        assertTrue(dormant.current());assertEquals(0,scene.trajectory.publicationSerial());assertFalse(scene.leg.keysPublished());assertFalse(scene.leg.cameraPublishedOrQueued());
        dormant.cleanupOwned();assertEquals(0,scene.released);assertEquals(0,scene.cancelled);assertTrue(dormant.publishYaw(45)<=0);
        assertFalse(GroundPublicationFence.publish(dormant,()->true,true,45,()->fail("No own receipt"),()->fail("No key effect")));
        assertEquals(0,scene.trajectory.publicationSerial());assertTrue(scene.leg.current(scene.state()));
    }
    @Test void onlyActualReturnedTicketAllowsKeysAndSameEpochForeignPlanRevokes(){
        var scene=new Scene();scene.admit();long ticket=scene.publish(45);long epoch=scene.trajectory.epoch();
        assertEquals(ticket,scene.leg.ticket());assertTrue(scene.leg.inputAttempted(scene.state()));assertTrue(scene.leg.inputPublished(scene.state(),scene.state()));
        scene.trajectory.plan(scene.scope,10,scene.actual,new OwnedRotationTrajectory.Pose(90,0));
        assertEquals(epoch,scene.trajectory.epoch());assertEquals(ticket+1,scene.trajectory.publicationSerial());
        var dormant=new DormantGroundPublication(()->scene.leg.current(scene.state()));assertFalse(dormant.current());
        var retirement=scene.retire();assertFalse(retirement.cleanSuccess());assertEquals(0,scene.released);assertEquals(0,scene.cancelled);
        assertTrue(retirement.keysRequired());assertTrue(retirement.cameraRequired());assertTrue(retirement.hasUnconfirmedOwnedKeys());
    }
    @Test void nineActualReleasesAndOneOwnCancellationProduceClosedContinuation(){
        var scene=new Scene();scene.admit();scene.publish(45);assertTrue(scene.leg.inputAttempted(scene.state()));assertTrue(scene.leg.inputPublished(scene.state(),scene.state()));
        var retirement=scene.retire();assertTrue(retirement.cleanSuccess());assertTrue(retirement.allNineReleased());assertTrue(retirement.cameraCancelled());assertEquals(9,scene.released);assertEquals(1,scene.cancelled);
        long retiredVersion=scene.version;var closed=scene.state();assertTrue(scene.leg.ownsRetiredContinuation(closed,true,retiredVersion,scene.version));
        assertFalse(scene.leg.current(closed));assertFalse(scene.leg.mayReleaseKeys(closed));assertFalse(scene.leg.mayCancelCamera(closed));
        Object next=new Object();assertNotNull(LegacyTravelPublicationIdentity.Lease.admitAfter(closed,closed,next,closed,closed,retiredVersion,scene.version,scene.version));
    }
    @Test void noEffectIntermediateAdmissionCannotBeHiddenFromClosedHandoff(){
        var scene=new Scene();scene.admit();assertTrue(scene.retire().cleanSuccess());long retired=scene.version;var closed=scene.state();
        Object intermediate=new Object();var nested=LegacyTravelPublicationIdentity.Lease.admit(closed,closed,intermediate,scene.version,scene.version);assertNotNull(nested);scene.version++;nested.closed();scene.version++;
        assertEquals(0,scene.trajectory.publicationSerial());assertEquals(0,scene.trajectory.epoch());assertFalse(scene.leg.ownsRetiredContinuation(scene.state(),true,retired,scene.version));
        assertNull(LegacyTravelPublicationIdentity.Lease.admitAfter(scene.state(),scene.state(),new Object(),closed,closed,retired,scene.version,scene.version));
    }
    @Test void reentrantForeignPublicationDuringReleaseCannotReportCleanOrCancelIt(){
        var scene=new Scene();scene.admit();scene.publish(45);assertTrue(scene.leg.inputAttempted(scene.state()));assertTrue(scene.leg.inputPublished(scene.state(),scene.state()));scene.reenterAfterRelease=true;
        var retirement=scene.retire();assertEquals(1,scene.released);assertEquals(0,scene.cancelled);assertFalse(retirement.cleanSuccess());assertEquals(LegacyTravelRetirement.Result.FAILED,retirement.keys().get(0));assertEquals(LegacyTravelRetirement.Result.NOT_ATTEMPTED,retirement.camera());
        assertEquals(2,scene.trajectory.publicationSerial());assertTrue(scene.trajectory.active());
    }
    @Test void replacementConfigStoreRevokesDormantReadsAndPreservesForeignCustody(){
        var scene=new Scene();scene.admit();scene.publish(45);var dormant=new DormantGroundPublication(()->scene.leg.current(scene.state()));assertTrue(dormant.current());scene.config=new Object();assertFalse(dormant.current());
        var retirement=scene.retire();assertFalse(retirement.cleanSuccess());assertEquals(0,scene.released);assertEquals(0,scene.cancelled);assertTrue(scene.trajectory.active());
    }
    @Test void cachedCleanResultNeverGrantsNewMovementAfterPolicyOrRequestChange(){
        var scene=new Scene();scene.admit();scene.publish(45);var result=scene.retire();assertTrue(result.cleanSuccess());long retired=scene.version;
        assertTrue(scene.leg.ownsRetiredContinuation(scene.state(),true,retired,scene.version));
        scene.revision++;assertTrue(result.cleanSuccess());assertFalse(scene.leg.ownsRetiredContinuation(scene.state(),true,retired,scene.version));
        assertTrue(scene.leg.ownsRetiredCleanupParent(scene.state(),true,retired,scene.version));assertFalse(scene.leg.mayReleaseKeys(scene.state()));
        scene.generation++;assertFalse(scene.leg.ownsRetiredCleanupParent(scene.state(),true,retired,scene.version));assertFalse(scene.leg.ownsRetiredContinuation(scene.state(),true,retired,scene.version));
        assertEquals(0,scene.released);assertEquals(1,scene.cancelled);
    }
    @Test void consumedActualClosedHandoffCannotMintAnotherLegAfterNoEffectRetirement(){
        var scene=new Scene();scene.admit();assertTrue(scene.retire().cleanSuccess());long retired=scene.version;var closed=scene.state();Object next=new Object();
        var admitted=LegacyTravelPublicationIdentity.Lease.admitAfter(closed,closed,next,closed,closed,retired,scene.version,scene.version);assertNotNull(admitted);
        scene.active=next;scene.version++;assertFalse(scene.leg.ownsRetiredContinuation(scene.state(),true,retired,scene.version));
        admitted.closed();scene.active=null;scene.version++;assertEquals(0,scene.trajectory.publicationSerial());
        assertNull(LegacyTravelPublicationIdentity.Lease.admitAfter(scene.state(),scene.state(),new Object(),closed,closed,retired,scene.version,scene.version));
    }
}
