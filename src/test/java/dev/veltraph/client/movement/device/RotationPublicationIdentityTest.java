package dev.veltraph.client.movement.device;
import dev.veltraph.client.rotation.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class RotationPublicationIdentityTest {
 private final Object s=new Object(),o=new Object(),p=new Object(),w=new Object(),c=new Object();
 private RotationReceiptIdentity.Stamp stamp(long gen,long epoch){return new RotationReceiptIdentity.Stamp(s,gen,o,p,w,c,epoch);}
 private boolean refresh(RotationReceiptIdentity.Stamp expected,RotationReceiptIdentity.Stamp actual,RotationPublicationIdentity.Transition t){return RotationPublicationIdentity.refresh(expected,actual,t,p,w,c,o);}
 @Test void actualScopeNullFirstPlanRetainsEpoch(){var t=new OwnedRotationTrajectory();var scope=new OwnedRotationTrajectory.Scope(o,p,w,c);long before=t.epoch();long ticket=t.plan(scope,1,new OwnedRotationTrajectory.Pose(0,0),new OwnedRotationTrajectory.Pose(10,0));assertEquals(before,t.epoch());assertTrue(refresh(stamp(2,before),stamp(2,t.epoch()),new RotationPublicationIdentity.Transition(stamp(2,before),stamp(2,t.epoch()),ticket)));}
 @Test void actualOwnPlanAcrossLiveTickAdvancesQualifiedEpoch(){var t=new OwnedRotationTrajectory();var scope=new OwnedRotationTrajectory.Scope(o,p,w,c);var pose=new OwnedRotationTrajectory.Pose(0,0);t.plan(scope,1,pose,new OwnedRotationTrajectory.Pose(10,0));long before=t.epoch();long ticket=t.plan(scope,2,pose,new OwnedRotationTrajectory.Pose(20,0));assertEquals(before+1,t.epoch());assertTrue(refresh(stamp(2,before),stamp(2,t.epoch()),new RotationPublicationIdentity.Transition(stamp(2,before),stamp(2,t.epoch()),ticket)));}
 @Test void currentEpochGetterWithoutOwnPublicationIsNotPermission(){assertFalse(refresh(stamp(2,8),stamp(2,9),null));}
 @Test void foreignNewRequestSameParentEpochDenied(){assertFalse(refresh(stamp(2,8),stamp(3,9),new RotationPublicationIdentity.Transition(stamp(2,8),stamp(2,9),1)));}
 @Test void reentrantRequestDuringPublicationDenied(){assertFalse(refresh(stamp(2,8),stamp(3,9),new RotationPublicationIdentity.Transition(stamp(2,8),stamp(3,9),1)));}
 @Test void replacedPlayerDenied(){var after=new RotationReceiptIdentity.Stamp(s,2,o,new Object(),w,c,9);assertFalse(refresh(stamp(2,8),after,new RotationPublicationIdentity.Transition(stamp(2,8),after,1)));}
 @Test void replacedWorldDenied(){var after=new RotationReceiptIdentity.Stamp(s,2,o,p,new Object(),c,9);assertFalse(refresh(stamp(2,8),after,new RotationPublicationIdentity.Transition(stamp(2,8),after,1)));}
 @Test void foreignAuthorityDenied(){assertFalse(RotationPublicationIdentity.refresh(stamp(2,8),stamp(2,9),new RotationPublicationIdentity.Transition(stamp(2,8),stamp(2,9),1),p,w,c,new Object()));}
 @Test void foreignServiceDenied(){var after=new RotationReceiptIdentity.Stamp(new Object(),2,o,p,w,c,9);assertFalse(refresh(stamp(2,8),after,new RotationPublicationIdentity.Transition(stamp(2,8),after,1)));}
 @Test void wrongOldReceiptCannotSkipOwnTransition(){assertFalse(refresh(stamp(2,7),stamp(2,9),new RotationPublicationIdentity.Transition(stamp(2,8),stamp(2,9),1)));}
 @Test void zeroOrNegativeNativeTicketDenied(){assertFalse(RotationPublicationIdentity.valid(new RotationPublicationIdentity.Transition(stamp(2,8),stamp(2,9),0)));assertFalse(RotationPublicationIdentity.valid(new RotationPublicationIdentity.Transition(stamp(2,8),stamp(2,9),-1)));}
 @Test void epochJumpCannotAdoptUnknownPublications(){assertFalse(refresh(stamp(2,8),stamp(2,10),new RotationPublicationIdentity.Transition(stamp(2,8),stamp(2,10),1)));}
 @Test void rollbackCannotRefresh(){assertFalse(refresh(stamp(2,8),stamp(2,7),new RotationPublicationIdentity.Transition(stamp(2,8),stamp(2,7),1)));}
 @Test void replacedConnectionDenied(){assertFalse(RotationPublicationIdentity.refresh(stamp(2,8),stamp(2,9),new RotationPublicationIdentity.Transition(stamp(2,8),stamp(2,9),1),p,w,new Object(),o));}
}
