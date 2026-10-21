package dev.veltraph.client.movement.device;
import dev.veltraph.client.rotation.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class RotationOwnPublicationTicketTest {
 private final Object service=new Object(),owner=new Object(),player=new Object(),world=new Object(),connection=new Object();
 private RotationReceiptIdentity.Stamp stamp(long generation,long epoch){return new RotationReceiptIdentity.Stamp(service,generation,owner,player,world,connection,epoch);}
 private RotationPublicationIdentity.Transition proof(){return new RotationPublicationIdentity.Transition(stamp(2,8),stamp(2,9),17);}
 private long ticket(RotationReceiptIdentity.Stamp old,RotationReceiptIdentity.Stamp actual,RotationPublicationIdentity.Transition proof,long serial){return RotationPublicationIdentity.ownedTicket(old,actual,proof,player,world,connection,owner,serial);}
 @Test void originalOwnedBeforeReceiptReturnsActualTicket(){assertEquals(17,ticket(stamp(2,8),stamp(2,9),proof(),17));}
 @Test void AlreadyRefreshedOwnAfterReceiptReturnsSameTicket(){assertEquals(17,ticket(stamp(2,9),stamp(2,9),proof(),17));}
 @Test void rawSerialWithoutTransitionCannotAuthorize(){assertEquals(-1,ticket(stamp(2,9),stamp(2,9),null,17));}
 @Test void newerSameParentSameEpochPublicationDeniesOldTicket(){assertEquals(-1,ticket(stamp(2,9),stamp(2,9),proof(),18));}
 @Test void newerRequestSameParentAndEpochDeniesOldTicket(){assertEquals(-1,ticket(stamp(2,9),stamp(3,9),proof(),17));}
 @Test void unrecordedEpochCannotBecomeOwnedTicket(){assertEquals(-1,ticket(stamp(2,9),stamp(2,10),proof(),17));}
 @Test void foreignAuthorityOrWorldDenied(){assertEquals(-1,RotationPublicationIdentity.ownedTicket(stamp(2,8),stamp(2,9),proof(),player,world,connection,new Object(),17));assertEquals(-1,RotationPublicationIdentity.ownedTicket(stamp(2,8),stamp(2,9),proof(),player,new Object(),connection,owner,17));}
 @Test void actualReentrantRawSameEpochPublisherRefusesImmediateCandidateAdoption(){
  var t=new OwnedRotationTrajectory();var scope=new OwnedRotationTrajectory.Scope(owner,player,world,connection);var actual=new OwnedRotationTrajectory.Pose(0,0);
  long epoch=t.epoch(),candidate=t.plan(scope,1,actual,new OwnedRotationTrajectory.Pose(15,0));
  var before=stamp(2,epoch);var after=stamp(2,t.epoch());var transition=new RotationPublicationIdentity.Transition(before,after,candidate);
  long newer=t.plan(scope,1,actual,new OwnedRotationTrajectory.Pose(35,0));
  assertEquals(epoch,t.epoch());assertTrue(newer>candidate);
  assertTrue(RotationPublicationIdentity.refresh(before,after,transition,player,world,connection,owner));
  assertEquals(-1,RotationPublicationIdentity.ownedTicket(before,after,transition,player,world,connection,owner,t.publicationSerial()));
 }
 @Test void nonpositiveSerialCannotAuthorize(){assertEquals(-1,ticket(stamp(2,8),stamp(2,9),proof(),0));}
}
