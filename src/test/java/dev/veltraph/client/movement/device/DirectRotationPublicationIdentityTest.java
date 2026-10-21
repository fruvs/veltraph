package dev.veltraph.client.movement.device;
import dev.veltraph.client.rotation.DirectRotationPublicationIdentity;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class DirectRotationPublicationIdentityTest {
 private final Object root=new Object(),parent=new Object(),p=new Object(),w=new Object(),c=new Object(),store=new Object(),service=new Object();
 private DirectRotationPublicationIdentity.Scope scope(long gen,long epoch,long serial){return new DirectRotationPublicationIdentity.Scope(root,parent,parent,parent,p,w,c,store,service,gen,epoch,serial,3);}
 private DirectRotationPublicationIdentity.Proof track(){return new DirectRotationPublicationIdentity.Proof(scope(5,8,10),scope(7,9,11),11,DirectRotationPublicationIdentity.Mode.TRACK);}
 @Test void exactActualTrackPublication(){assertTrue(DirectRotationPublicationIdentity.publication(track()));}
 @Test void exactActualAimPublication(){assertTrue(DirectRotationPublicationIdentity.publication(new DirectRotationPublicationIdentity.Proof(scope(5,8,10),scope(6,8,11),11,DirectRotationPublicationIdentity.Mode.AIM)));}
 @Test void sameTickRetainsExactEarlierOwnTicket(){var same=new DirectRotationPublicationIdentity.Proof(track().after(),scope(8,9,11),11,DirectRotationPublicationIdentity.Mode.SAME_TICK_TRACK);assertTrue(DirectRotationPublicationIdentity.retained(same,track()));}
 @Test void sameTickWithoutPreviousOwnPublicationDenied(){var same=new DirectRotationPublicationIdentity.Proof(scope(7,9,11),scope(8,9,11),11,DirectRotationPublicationIdentity.Mode.SAME_TICK_TRACK);assertFalse(DirectRotationPublicationIdentity.retained(same,null));}
 @Test void newerRawEndpointSameEpochCannotBeRetained(){var same=new DirectRotationPublicationIdentity.Proof(scope(7,9,12),scope(8,9,12),12,DirectRotationPublicationIdentity.Mode.SAME_TICK_TRACK);assertFalse(DirectRotationPublicationIdentity.retained(same,track()));}
 @Test void newerRawEndpointCannotBeCurrent(){assertFalse(DirectRotationPublicationIdentity.current(track(),scope(7,9,12)));}
 @Test void reentrantNewServiceGenerationDenied(){assertFalse(DirectRotationPublicationIdentity.preplan(scope(5,8,10),scope(8,8,10),DirectRotationPublicationIdentity.Mode.TRACK));}
 @Test void reentrantRawPublicationBeforePlannerDenied(){assertFalse(DirectRotationPublicationIdentity.preplan(scope(5,8,10),scope(7,8,11),DirectRotationPublicationIdentity.Mode.TRACK));}
 @Test void unrelatedServiceCannotMintProof(){var foreign=new DirectRotationPublicationIdentity.Scope(root,parent,parent,parent,p,w,c,store,new Object(),7,9,11,3);assertFalse(DirectRotationPublicationIdentity.publication(new DirectRotationPublicationIdentity.Proof(scope(5,8,10),foreign,11,DirectRotationPublicationIdentity.Mode.TRACK)));}
 @Test void foreignPwcCannotMintProof(){var foreign=new DirectRotationPublicationIdentity.Scope(root,parent,parent,parent,p,new Object(),c,store,service,7,9,11,3);assertFalse(DirectRotationPublicationIdentity.publication(new DirectRotationPublicationIdentity.Proof(scope(5,8,10),foreign,11,DirectRotationPublicationIdentity.Mode.TRACK)));}
 @Test void rootInputCameraAuthorityMustBeParent(){var foreign=new DirectRotationPublicationIdentity.Scope(root,parent,new Object(),parent,p,w,c,store,service,7,9,11,3);assertFalse(DirectRotationPublicationIdentity.publication(new DirectRotationPublicationIdentity.Proof(scope(5,8,10),foreign,11,DirectRotationPublicationIdentity.Mode.TRACK)));}
 @Test void configAbaRevisionPreventsPublication(){var changed=new DirectRotationPublicationIdentity.Scope(root,parent,parent,parent,p,w,c,store,service,7,8,10,5);assertFalse(DirectRotationPublicationIdentity.preplan(scope(5,8,10),changed,DirectRotationPublicationIdentity.Mode.TRACK));}
 @Test void returnedTicketMustRemainActualLatestSerial(){assertFalse(DirectRotationPublicationIdentity.publication(new DirectRotationPublicationIdentity.Proof(scope(5,8,10),scope(7,9,12),11,DirectRotationPublicationIdentity.Mode.TRACK)));}
 @Test void absenceOfAuthorityCannotGrantPublication(){var absent=new DirectRotationPublicationIdentity.Scope(root,parent,parent,null,p,w,c,store,service,7,9,11,3);assertFalse(DirectRotationPublicationIdentity.publication(new DirectRotationPublicationIdentity.Proof(scope(5,8,10),absent,11,DirectRotationPublicationIdentity.Mode.TRACK)));}
}
