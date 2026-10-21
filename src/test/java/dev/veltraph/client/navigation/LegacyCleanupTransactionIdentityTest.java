package dev.veltraph.client.navigation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class LegacyCleanupTransactionIdentityTest {
 final Object completion=new Object(),handle=new Object(),permit=new Object(),keys=new Object(),camera=new Object(),inventory=new Object(),menu=new Object();
 LegacyCleanupTransactionIdentity.Issuer issue(){return LegacyCleanupTransactionIdentity.Issuer.capture(completion,handle,permit,1,100);}
 LegacyCleanupTransactionIdentity.Issuer restored(){var i=issue();assertTrue(i.keys(keys,9,true));assertTrue(i.camera(camera,true,true));assertTrue(i.beginInventory(permit,handle,2,true));assertTrue(i.inventory(inventory,permit,handle));return i;}
 @Test void realIssueOrderNeedsAllNineBeforeCameraAndInventory(){var i=issue();assertFalse(i.camera(camera,true,true));assertFalse(i.beginInventory(permit,handle,2,true));assertFalse(i.keys(keys,8,true));assertTrue(i.keys(keys,9,true));assertTrue(i.camera(camera,true,true));assertTrue(i.beginInventory(permit,handle,2,true));}
 @Test void callerLoosePermitOrHandleCannotStampActualRestore(){var i=issue();assertTrue(i.keys(keys,9,true));assertTrue(i.camera(camera,true,true));assertFalse(i.beginInventory(new Object(),handle,2,true));assertFalse(i.beginInventory(permit,new Object(),2,true));assertTrue(i.beginInventory(permit,handle,2,true));assertFalse(i.inventory(inventory,new Object(),handle));}
 @Test void replayCannotIssueAnotherInventoryOperation(){var i=restored();assertFalse(i.beginInventory(permit,handle,2,true));assertFalse(i.inventory(new Object(),permit,handle));}
 @Test void unknownServerOrMenuRestorationNeverCompletes(){var i=restored();assertFalse(i.complete(inventory,menu,permit,completion,3,false,true));assertFalse(i.complete(inventory,null,permit,completion,3,true,true));}
 @Test void foreignInventoryOrNormalCauseCannotBorrowCompletedKeys(){var i=restored();assertFalse(i.complete(new Object(),menu,permit,completion,3,true,true));assertFalse(i.complete(inventory,menu,permit,new Object(),3,true,true));}
 @Test void policyWorldOrPrivateAdmissionLossAfterActualRestoreRefusesToken(){var i=restored();assertFalse(i.complete(inventory,menu,permit,completion,3,true,false));}
 @Test void expiredOriginalNormalDeadlineCannotMintOrConsume(){var i=restored();assertFalse(i.complete(inventory,menu,permit,completion,100,true,true));assertTrue(i.complete(inventory,menu,permit,completion,3,true,true));assertFalse(i.consume(100,true,true));}
 @Test void manualCancelledAbortedSourceCannotMintNormalToken(){var i=restored();i.abort();assertFalse(i.complete(inventory,menu,permit,completion,3,true,true));assertFalse(i.consume(3,true,true));}
 @Test void onlyActualCompletedCoreEnableAndFreshScopeCanConsumeOnce(){var i=restored();assertTrue(i.complete(inventory,menu,permit,completion,3,true,true));assertFalse(i.consume(4,false,true));assertFalse(i.consume(4,true,false));assertTrue(i.consume(4,true,true));assertFalse(i.consume(4,true,true));}
 @Test void partialNativeKeysOrCancelledScopeCannotAdvanceStage(){var i=issue();assertFalse(i.keys(keys,9,false));assertTrue(i.keys(keys,9,true));assertFalse(i.camera(camera,false,true));assertFalse(i.camera(camera,true,false));}
}
