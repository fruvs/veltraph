package dev.veltraph.client.movement.device;
import dev.veltraph.client.rotation.DirectRotationPublicationIdentity;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class DirectRotationCancellationIdentityTest {
 private final Object root=new Object(),parent=new Object(),p=new Object(),w=new Object(),c=new Object(),store=new Object(),service=new Object();
 private DirectRotationPublicationIdentity.Scope s(long gen,long epoch,long serial,Object input,Object camera,long rev){return new DirectRotationPublicationIdentity.Scope(root,parent,input,camera,p,w,c,store,service,gen,epoch,serial,rev);}
 private DirectRotationPublicationIdentity.Proof issued(){return new DirectRotationPublicationIdentity.Proof(s(5,8,10,parent,parent,3),s(7,9,11,parent,parent,3),11,DirectRotationPublicationIdentity.Mode.TRACK);}
 @Test void actualOwnedCancelHasOneGenerationAndEpochWithStableTicket(){assertTrue(DirectRotationPublicationIdentity.cancellation(issued(),s(7,9,11,parent,parent,3),s(8,10,11,parent,parent,3),false));}
 @Test void newerSameParentRequestCannotBeCancelled(){assertFalse(DirectRotationPublicationIdentity.ownedCleanup(issued(),s(8,9,11,parent,parent,3),false));}
 @Test void rawSameEpochNewEndpointCannotBeCancelled(){assertFalse(DirectRotationPublicationIdentity.ownedCleanup(issued(),s(7,9,12,parent,parent,3),false));}
 @Test void guessedNoEffectCancelCannotProduceReceipt(){assertFalse(DirectRotationPublicationIdentity.cancellation(issued(),s(7,9,11,parent,parent,3),s(7,9,11,parent,parent,3),false));}
 @Test void onlyOneEpochChangeIsAccepted(){assertFalse(DirectRotationPublicationIdentity.cancellation(issued(),s(7,9,11,parent,parent,3),s(8,11,11,parent,parent,3),false));}
 @Test void aNewPublicationDuringCancelCannotBeAdopted(){assertFalse(DirectRotationPublicationIdentity.cancellation(issued(),s(7,9,11,parent,parent,3),s(8,10,12,parent,parent,3),false));}
 @Test void typedAbsentDisabledCleanupIsPossibleWithoutNewGameplay(){assertTrue(DirectRotationPublicationIdentity.cancellation(issued(),s(7,9,11,null,null,4),s(8,10,11,null,null,4),true));assertFalse(DirectRotationPublicationIdentity.ownedCleanup(issued(),s(7,9,11,null,null,4),false));}
 @Test void mixedOrForeignOwnershipCannotBeDisabledCleanup(){assertFalse(DirectRotationPublicationIdentity.ownedCleanup(issued(),s(7,9,11,null,parent,3),true));assertFalse(DirectRotationPublicationIdentity.ownedCleanup(issued(),s(7,9,11,new Object(),new Object(),3),true));}
 @Test void sameStoreRevisionRevocationMayRetireOwnActions(){assertTrue(DirectRotationPublicationIdentity.ownedCleanup(issued(),s(7,9,11,parent,parent,5),false));}
 @Test void foreignPwcCannotBeCancellation(){var after=new DirectRotationPublicationIdentity.Scope(root,parent,parent,parent,p,new Object(),c,store,service,8,10,11,3);assertFalse(DirectRotationPublicationIdentity.cancellation(issued(),s(7,9,11,parent,parent,3),after,false));}
 @Test void cancellationReceiptCurrentRejectsLaterCameraAndSourceRequests(){var after=s(8,10,11,parent,parent,3);assertTrue(DirectRotationPublicationIdentity.cancellationCurrent(after,after,false,true));assertFalse(DirectRotationPublicationIdentity.cancellationCurrent(after,s(9,10,11,parent,parent,3),false,true));assertFalse(DirectRotationPublicationIdentity.cancellationCurrent(after,s(8,10,12,parent,parent,3),false,true));}
 @Test void cancellationCleanupMayIgnoreRevokedValuesButStrictCurrentDoesNot(){var after=s(8,10,11,parent,parent,3);var actual=s(8,10,11,parent,parent,5);assertFalse(DirectRotationPublicationIdentity.cancellationCurrent(after,actual,false,true));assertTrue(DirectRotationPublicationIdentity.cancellationCurrent(after,actual,false,false));}
}
