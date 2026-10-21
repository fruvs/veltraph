package dev.veltraph.client.navigation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class LegacyPublicationSnapshotIdentityTest {
 final Object leg=new Object(),identity=new Object(),root=new Object(),p=new Object(),w=new Object(),c=new Object(),store=new Object();
 LegacyPublicationSnapshotIdentity.State state(Object active,long admission,long generation,long epoch,long serial){return new LegacyPublicationSnapshotIdentity.State(active,identity,root,p,w,c,store,admission,generation,epoch,serial);}
 @Test void exactActualReservationSurvivesReadOnlyDeviceCallbacks(){var captured=state(leg,7,4,9,20);Runnable reads=()->assertEquals(7,captured.admission());reads.run();assertTrue(LegacyPublicationSnapshotIdentity.same(captured,state(leg,7,4,9,20)));}
 @Test void callerSameFieldsCannotSubstituteAnotherLegHandle(){assertFalse(LegacyPublicationSnapshotIdentity.same(state(leg,7,4,9,20),state(new Object(),7,4,9,20)));}
 @Test void noEffectAdmissionAbaAfterLastProviderCallbackCannotPassLeaf(){long[] admission={7};var captured=state(leg,7,4,9,20);Runnable callback=()->admission[0]+=2;callback.run();assertFalse(LegacyPublicationSnapshotIdentity.same(captured,state(leg,admission[0],4,9,20)));}
 @Test void retiredReservationBeforeFinalNativeLeafCannotPass(){assertFalse(LegacyPublicationSnapshotIdentity.same(state(leg,7,4,9,20),state(null,7,4,9,20)));}
 @Test void sameParentNewRequestFromCodecCallbackCannotPass(){long[] generation={4};var captured=state(leg,7,4,9,20);Runnable codec=()->generation[0]++;codec.run();assertFalse(LegacyPublicationSnapshotIdentity.same(captured,state(leg,7,generation[0],9,20)));}
 @Test void sameEpochForeignPublicationFromCameraSupplierCannotPass(){long[] serial={20};var captured=state(leg,7,4,9,20);Runnable camera=()->serial[0]++;camera.run();assertFalse(LegacyPublicationSnapshotIdentity.same(captured,state(leg,7,4,9,serial[0])));}
 @Test void ownCancellationRequiresNewSnapshotAndCannotRenewOldSnapshot(){assertFalse(LegacyPublicationSnapshotIdentity.same(state(leg,7,4,9,20),state(leg,7,5,10,20)));}
 @Test void replacedRootPlayerWorldConnectionStoreEachRefuses(){var captured=state(leg,7,4,9,20);Object[] fields={root,p,w,c,store};for(int i=0;i<fields.length;i++){Object[] next=fields.clone();next[i]=new Object();var live=new LegacyPublicationSnapshotIdentity.State(leg,identity,next[0],next[1],next[2],next[3],next[4],7,4,9,20);assertFalse(LegacyPublicationSnapshotIdentity.same(captured,live));}}
 @Test void replacedPrivateIdentityCannotMatchEvenWithSameNativeFields(){var captured=state(leg,7,4,9,20);assertFalse(LegacyPublicationSnapshotIdentity.same(captured,new LegacyPublicationSnapshotIdentity.State(leg,new Object(),root,p,w,c,store,7,4,9,20)));}
 @Test void emptyOrUnknownSnapshotCannotGrantLeaf(){assertFalse(LegacyPublicationSnapshotIdentity.same(null,state(leg,7,4,9,20)));assertFalse(LegacyPublicationSnapshotIdentity.same(state(leg,-1,4,9,20),state(leg,-1,4,9,20)));}
}
