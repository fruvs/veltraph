package dev.veltraph.client.navigation;
import dev.veltraph.client.service.InputService;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class NeutralMovementPublicationTest {
 static final class F {
  final Object root=new Object(),parent=new Object(),player=new Object(),world=new Object(),connection=new Object(),store=new Object(),service=new Object(),leg=new Object();
  long generation=4,epoch=9,serial=0,admission=7;int observations,setters,publications,cameras;NeutralMovementPublication.Key poison;
  final LegacyTravelPublicationIdentity.Lease lease=LegacyTravelPublicationIdentity.Lease.capture(state());
  LegacyTravelPublicationIdentity.State state(){return new LegacyTravelPublicationIdentity.State(root,parent,parent,parent,player,world,connection,store,3,1,service,generation,epoch,serial,leg,true,true,true);}
  boolean publish(NeutralMovementPublication.Key key){var before=state();boolean applied=ScopedHeldKeyPublication.apply(false,new ScopedHeldKeyPublication.Effects(){
   public void observe(){observations++;if(key==poison)generation++;}public boolean current(){return admission==7&&lease.current(state());}
   public boolean down(){return false;}public void setDown(boolean held){setters++;}public boolean toggle(){return false;}public void clearRestore(){}
  });var after=state();boolean published=applied&&lease.inputPublished(before,after);if(published)publications++;return published;}
  InputService inputs(boolean oldCleanupOnly){return new InputService(){
   public void releaseAllMovement(){if(oldCleanupOnly){assertFalse(lease.mayReleaseKeys(state()));return;}if(!NeutralMovementPublication.publish(F.this::publish))throw new GroundPublicationRevokedException("Neutral scope revoked");}
   public void setForwardHeld(boolean held){fail("ARRIVED should use its actual releaseAllMovement output");}public void setBackHeld(boolean held){fail();}public void setLeftHeld(boolean held){fail();}public void setRightHeld(boolean held){fail();}public void setSneakHeld(boolean held){fail();}public void setJumpHeld(boolean held){fail();}
  };}
 }
 @Test void oldCleanupOnlyArrivedDispatchReproducesNoOwnedKeyPublication(){var f=new F();GroundWalkInputs.apply(f.inputs(true),new WalkController.Output(false,false,0,WalkController.State.ARRIVED));assertFalse(f.lease.keysPublished());assertEquals(0,f.observations);assertEquals(0,f.publications);}
 @Test void actualArrivedDispatchPublishesSevenNeutralReceiptsWithoutCameraOrNativePulse(){var f=new F();GroundWalkInputs.apply(f.inputs(false),new WalkController.Output(false,false,0,WalkController.State.ARRIVED));assertTrue(f.lease.keysPublished());assertEquals(7,f.observations);assertEquals(7,f.publications);assertEquals(0,f.setters);assertEquals(0,f.cameras);assertEquals(0,f.lease.ticket());assertFalse(f.lease.cameraPublishedOrQueued());}
 @Test void observerReplacementBeforeFirstNeutralPublicationStopsBeforeAnyMutationOrReceipt(){var f=new F();f.poison=NeutralMovementPublication.Key.FORWARD;assertThrows(GroundPublicationRevokedException.class,()->GroundWalkInputs.apply(f.inputs(false),new WalkController.Output(false,false,0,WalkController.State.ARRIVED)));assertEquals(1,f.observations);assertEquals(0,f.publications);assertEquals(0,f.setters);assertFalse(f.lease.keysPublished());}
 @Test void observerReplacementAfterFirstOwnNeutralReceiptCannotPublishRemainingKeys(){var f=new F();f.poison=NeutralMovementPublication.Key.BACK;assertFalse(NeutralMovementPublication.publish(f::publish));assertEquals(2,f.observations);assertEquals(1,f.publications);assertTrue(f.lease.keysPublished());assertEquals(0,f.setters);assertFalse(f.lease.cleanupCurrent(f.state()));}
 @Test void initialUnpublishedCleanupNeverManufacturesKeyReceipt(){var f=new F();assertFalse(f.lease.mayReleaseKeys(f.state()));assertFalse(f.lease.keysPublished());assertEquals(0,f.observations);}
 @Test void neutralReceiptThenActualAllNineRetirementHasNoCameraRequirement(){var f=new F();assertTrue(NeutralMovementPublication.publish(f::publish));int[] releases={0},camera={0};var outcome=LegacyTravelRetirement.retire(new LegacyTravelRetirement.Effects(){public boolean keysPublished(){return f.lease.keysPublished();}public boolean cameraPublishedOrQueued(){return f.lease.cameraPublishedOrQueued();}public boolean activeLegMatches(){return true;}public boolean cleanupFence(){return f.lease.cleanupCurrent(f.state());}public boolean release(LegacyTravelRetirement.Key key){releases[0]++;return f.lease.mayReleaseKeys(f.state());}public boolean cancelCamera(){camera[0]++;return false;}public LegacyTravelRetirement.Removal retireLogical(){f.lease.closed();return LegacyTravelRetirement.Removal.REMOVED_OWN;}});assertTrue(outcome.cleanSuccess());assertTrue(outcome.allNineReleased());assertFalse(outcome.cameraRequired());assertEquals(9,releases[0]);assertEquals(0,camera[0]);}
}
