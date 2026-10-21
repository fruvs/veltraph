package dev.veltraph.client.rotation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class RotationReceiptIdentityTest {
    final Object service=new Object(),owner=new Object(),player=new Object(),world=new Object(),connection=new Object();
    RotationReceiptIdentity.Stamp stamp(long generation,long epoch){return new RotationReceiptIdentity.Stamp(service,generation,owner,player,world,connection,epoch);}
    boolean fresh(RotationReceiptIdentity.Stamp expected,RotationReceiptIdentity.Stamp actual){return RotationReceiptIdentity.current(expected,actual,player,world,connection,owner,false);}
    @Test void justCompletedSameRequestRemainsCurrentForItsClearedBeforeCallbackLifecycle(){assertTrue(fresh(stamp(5,7),stamp(5,7)));}
    @Test void sameOwnerReplacementInvalidatesQueuedOldCompletion(){assertFalse(fresh(stamp(5,7),stamp(6,7)));}
    @Test void sameOwnerReplacementBetweenAimAndUseCannotAuthorizeCast(){var aimed=stamp(5,7);assertTrue(fresh(aimed,stamp(5,7)));assertFalse(fresh(aimed,stamp(6,7)));}
    @Test void worldPlayerConnectionOrCameraEpochChangeRevokes(){var expected=stamp(5,7);assertFalse(fresh(expected,stamp(5,8)));for(int field=0;field<3;field++)assertFalse(RotationReceiptIdentity.current(expected,stamp(5,7),field==0?new Object():player,field==1?new Object():world,field==2?new Object():connection,owner,false));}
    @Test void absentOwnerCanOnlyAuthorizeExplicitCleanupNotGameplay(){assertFalse(RotationReceiptIdentity.current(stamp(5,7),stamp(5,7),player,world,connection,null,false));assertTrue(RotationReceiptIdentity.current(stamp(5,7),stamp(5,7),player,world,connection,null,true));assertFalse(RotationReceiptIdentity.current(stamp(5,7),stamp(5,7),player,world,connection,new Object(),true));}
    @Test void cancellationOrForeignServiceCannotReuseReceipt(){assertFalse(fresh(stamp(5,7),stamp(6,7)));assertFalse(fresh(stamp(5,7),new RotationReceiptIdentity.Stamp(new Object(),5,owner,player,world,connection,7)));}
}
