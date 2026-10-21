package dev.veltraph.client.navigation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class LegacyParentCleanupReentryIdentityTest {
 final Object root=new Object(),parent=new Object(),player=new Object(),world=new Object(),connection=new Object(),config=new Object(),service=new Object();
 LegacyTravelPublicationIdentity.State state(boolean enabled,long generation,long epoch,long serial,Object world,Object config,Object active){return new LegacyTravelPublicationIdentity.State(root,parent,enabled?parent:null,enabled?parent:null,player,world,connection,config,3,enabled?2:1,service,generation,epoch,serial,active,true,enabled,enabled);}
 LegacyTravelPublicationIdentity.State old(){return state(false,5,8,4,world,config,null);}
 LegacyTravelPublicationIdentity.State next(){return state(true,5,8,4,world,config,null);}
 @Test void actualEnabledRoleTransitionMayKeepExactPriorControlReservation(){assertTrue(LegacyParentCleanupReentryIdentity.sameControlScope(old(),next(),next(),parent));}
 @Test void idleEpochAdvanceWithoutNativeCauseCertificateRemainsUnknown(){assertFalse(LegacyParentCleanupReentryIdentity.sameControlScope(old(),next(),state(true,5,9,4,world,config,null),parent));}
 @Test void foreignSameEpochPublicationCannotBorrowOldCleanupToken(){assertFalse(LegacyParentCleanupReentryIdentity.sameControlScope(old(),next(),state(true,5,8,5,world,config,null),parent));}
 @Test void foreignSameParentRequestCannotBorrowOldCleanupToken(){assertFalse(LegacyParentCleanupReentryIdentity.sameControlScope(old(),next(),state(true,6,8,4,world,config,null),parent));}
 @Test void replacementWorldOrStoreCannotBorrowOldCleanupToken(){assertFalse(LegacyParentCleanupReentryIdentity.sameControlScope(old(),next(),state(true,5,8,4,new Object(),config,null),parent));assertFalse(LegacyParentCleanupReentryIdentity.sameControlScope(old(),next(),state(true,5,8,4,world,new Object(),null),parent));}
 @Test void actualOtherActiveLegOrWrongParentRefuses(){assertFalse(LegacyParentCleanupReentryIdentity.sameControlScope(old(),next(),state(true,5,8,4,world,config,new Object()),parent));assertFalse(LegacyParentCleanupReentryIdentity.sameControlScope(old(),next(),next(),new Object()));}
 @Test void beforeCallbackForeignScopeCannotBeRecapturedByAfterSnapshot(){assertFalse(LegacyParentCleanupReentryIdentity.sameControlScope(old(),state(true,5,8,5,world,config,null),next(),parent));}
 @Test void issuerConsumesOnceAndManualAbortNeverAllowsRestart(){var normal=new Object();var handle=new Object();var permit=new Object();var inv=new Object();var issuer=LegacyCleanupTransactionIdentity.Issuer.capture(normal,handle,permit,1,100);assertTrue(issuer.keys(new Object(),9,true));assertTrue(issuer.camera(new Object(),true,true));assertTrue(issuer.beginInventory(permit,handle,2,true));assertTrue(issuer.inventory(inv,permit,handle));assertTrue(issuer.complete(inv,new Object(),permit,normal,3,true,true));assertTrue(issuer.consume(4,true,true));assertFalse(issuer.consume(4,true,true));issuer.abort();assertFalse(issuer.consume(4,true,true));}
}
