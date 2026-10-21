package dev.veltraph.client.navigation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
/** Actual pure equality kernel used by the private native GUI capsule; no native authority/receipt is created. */
class LegacyGuiCleanupSnapshotIdentityTest {
 final Object leg=new Object(),identity=new Object(),root=new Object(),p=new Object(),w=new Object(),c=new Object(),store=new Object();
 LegacyPublicationSnapshotIdentity.State state(long admission,long g,long e,long s){return new LegacyPublicationSnapshotIdentity.State(leg,identity,root,p,w,c,store,admission,g,e,s);}
 @Test void currentExactGuiCleanupReservationIsReadOnly(){var captured=state(3,6,10,21);assertTrue(LegacyPublicationSnapshotIdentity.same(captured,state(3,6,10,21)));assertTrue(LegacyPublicationSnapshotIdentity.same(captured,captured));}
 @Test void callbackNoEffectAdmissionAbaRevokesSamePwcControl(){var captured=state(3,6,10,21);assertFalse(LegacyPublicationSnapshotIdentity.same(captured,state(5,6,10,21)));assertTrue(LegacyPublicationSnapshotIdentity.same(captured,state(3,6,10,21)));}
 @Test void authenticNewGuiDescendantRequiresNewFactoryNeverLeafRefresh(){var captured=state(3,6,10,21);assertFalse(LegacyPublicationSnapshotIdentity.same(captured,state(3,6,11,21)));assertTrue(LegacyPublicationSnapshotIdentity.same(state(3,6,11,21),state(3,6,11,21)));}
 @Test void callbackRequestOrPublicationReplacementRevokesOldCapsule(){var captured=state(3,6,10,21);assertFalse(LegacyPublicationSnapshotIdentity.same(captured,state(3,7,10,21)));assertFalse(LegacyPublicationSnapshotIdentity.same(captured,state(3,6,10,22)));}
 @Test void differentLegIdentityRootPwcStoreCannotBorrowCapturedLeaf(){var captured=state(3,6,10,21);for(int field=0;field<7;field++){Object other=new Object();var live=new LegacyPublicationSnapshotIdentity.State(field==0?other:leg,field==1?other:identity,field==2?other:root,field==3?other:p,field==4?other:w,field==5?other:c,field==6?other:store,3,6,10,21);assertFalse(LegacyPublicationSnapshotIdentity.same(captured,live));}assertFalse(LegacyPublicationSnapshotIdentity.same(captured,null));}
}
