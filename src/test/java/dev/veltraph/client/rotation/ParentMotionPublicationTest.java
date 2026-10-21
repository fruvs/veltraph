package dev.veltraph.client.rotation;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ParentMotionPublicationTest {
    private final Object service=new Object(),owner=new Object(),player=new Object(),world=new Object(),connection=new Object();
    private RotationReceiptIdentity.Stamp stamp(long generation,long epoch){return new RotationReceiptIdentity.Stamp(service,generation,owner,player,world,connection,epoch);}
    @Test void oneOrdinaryLookPublicationOwnsOnlyItsNewRequest(){assertTrue(ParentMotionPublication.own(stamp(7,3),stamp(8,4),1));}
    @Test void trackingUsesTwoKnownNativePublications(){assertTrue(ParentMotionPublication.own(stamp(7,3),stamp(9,4),2));}
    @Test void replacedCompletedCallbackDoesNotAdoptItsNewRequest(){assertFalse(ParentMotionPublication.own(stamp(7,3),stamp(9,4),1));}
    @Test void sameTickTrackingWithoutAimCannotAdoptPriorCamera(){assertFalse(ParentMotionPublication.own(stamp(7,3),stamp(8,3),2));}
    @Test void noPublicationAndOverflowDoNotOwnCleanup(){assertFalse(ParentMotionPublication.own(stamp(7,3),stamp(7,3),1));assertFalse(ParentMotionPublication.own(stamp(Long.MAX_VALUE,3),stamp(Long.MIN_VALUE,3),1));}
    @Test void contextAndAuthorityReplacementAreDenied(){var before=stamp(7,3);var replacements=java.util.List.of(
        new RotationReceiptIdentity.Stamp(new Object(),8,owner,player,world,connection,4),
        new RotationReceiptIdentity.Stamp(service,8,new Object(),player,world,connection,4),
        new RotationReceiptIdentity.Stamp(service,8,owner,new Object(),world,connection,4),
        new RotationReceiptIdentity.Stamp(service,8,owner,player,new Object(),connection,4),
        new RotationReceiptIdentity.Stamp(service,8,owner,player,world,new Object(),4));
        for(var after:replacements)assertFalse(ParentMotionPublication.own(before,after,1));}
    @Test void missingProofAndInvalidCountsAreDenied(){assertFalse(ParentMotionPublication.own(null,stamp(8,4),1));assertFalse(ParentMotionPublication.own(stamp(7,3),null,1));assertFalse(ParentMotionPublication.own(stamp(7,3),stamp(8,4),0));assertFalse(ParentMotionPublication.own(stamp(7,3),stamp(10,4),3));}
    @Test void nullAuthorityCannotCreateCleanup(){assertFalse(ParentMotionPublication.own(new RotationReceiptIdentity.Stamp(service,7,null,player,world,connection,3),new RotationReceiptIdentity.Stamp(service,8,null,player,world,connection,4),1));}
}
