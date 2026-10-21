package dev.veltraph.client.rotation;
import dev.veltraph.client.navigation.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class NormalIdleCertifiedEpochEvidenceTest {
 final Object root=new Object(),parent=new Object(),player=new Object(),world=new Object(),connection=new Object(),store=new Object(),service=new Object();
 LegacyTravelPublicationIdentity.State s(boolean enabled,long epoch,long generation,long serial){return new LegacyTravelPublicationIdentity.State(root,parent,enabled?parent:null,enabled?parent:null,player,world,connection,store,3,enabled?2:1,service,generation,epoch,serial,null,true,enabled,enabled);}
 @Test void unchangedPathRemainsExactAndCannotNumericallyCatchUp(){assertTrue(LegacyParentCleanupReentryIdentity.sameControlScope(s(false,8,5,4),s(true,8,5,4),s(true,8,5,4),parent));assertFalse(LegacyParentCleanupReentryIdentity.sameControlScope(s(false,8,5,4),s(true,9,5,4),s(true,9,5,4),parent));}
 @Test void evidenceCanMatchOneCertificateEndpointWithoutMutatingOriginalScope(){var original=s(false,8,5,4);assertTrue(LegacyParentCleanupReentryIdentity.sameControlScopeAtCertifiedEpoch(original,s(true,9,5,4),s(true,9,5,4),parent,9));assertEquals(8,original.epoch());}
 @Test void foreignRequestPublicationOrDifferentEndpointCannotBorrowEvidence(){var original=s(false,8,5,4);assertFalse(LegacyParentCleanupReentryIdentity.sameControlScopeAtCertifiedEpoch(original,s(true,9,6,4),s(true,9,6,4),parent,9));assertFalse(LegacyParentCleanupReentryIdentity.sameControlScopeAtCertifiedEpoch(original,s(true,9,5,5),s(true,9,5,5),parent,9));assertFalse(LegacyParentCleanupReentryIdentity.sameControlScopeAtCertifiedEpoch(original,s(true,10,5,4),s(true,10,5,4),parent,9));}
}
