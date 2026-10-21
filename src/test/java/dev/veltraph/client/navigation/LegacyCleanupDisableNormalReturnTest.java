package dev.veltraph.client.navigation;
import dev.veltraph.client.core.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class LegacyCleanupDisableNormalReturnTest {
 static class Parent extends AbstractModule {
  final ModuleManager root;final Object normal=new Object(),handle=new Object(),permit=new Object(),inventory=new Object(),menu=new Object();
  final LegacyCleanupTransactionIdentity.Issuer issuer;
  ModuleManager.OwnDisableReceipt receipt;ModuleManager.LifecycleSnapshot stopped;boolean insideResult;Runnable post=()->{};
  Parent(ModuleManager root){super("normal","normal",ModuleCategory.MISC);this.root=root;issuer=LegacyCleanupTransactionIdentity.Issuer.capture(normal,handle,permit,1,100);
   assertTrue(issuer.keys(new Object(),9,true));assertTrue(issuer.camera(new Object(),true,true));assertTrue(issuer.beginInventory(permit,handle,2,true));assertTrue(issuer.inventory(inventory,permit,handle));}
  protected void postDisableNormalReturned(ModuleManager.OwnDisableReceipt actual){receipt=actual;stopped=actual.stoppedSnapshot();insideResult=attempt();post.run();}
  boolean attempt(){boolean confirmed=root.ownDisableMatches(receipt,stopped,this)&&root.lifecycleCurrent(stopped);
   return issuer.complete(inventory,menu,permit,normal,3,true,confirmed);}
 }
 @Test void actualPendingHookCannotTransitionOrCompleteThenWholeNormalReturnCan(){var root=new ModuleManager();var p=new Parent(root);root.register(p);p.setEnabled(true);p.setEnabled(false);
  assertFalse(p.insideResult);assertEquals(LegacyCleanupTransactionIdentity.Stage.RESTORED,p.issuer.stage());assertTrue(p.attempt());assertEquals(LegacyCleanupTransactionIdentity.Stage.COMPLETE,p.issuer.stage());}
 @Test void originalDisableExceptionNeverProducesStopReceipt(){var root=new ModuleManager();var p=new Parent(root){public void onDisable(){throw new IllegalStateException("failed original disable");}};root.register(p);p.setEnabled(true);assertThrows(IllegalStateException.class,()->p.setEnabled(false));assertNull(p.receipt);assertFalse(p.attempt());assertEquals(LegacyCleanupTransactionIdentity.Stage.RESTORED,p.issuer.stage());}
 @Test void postHookExceptionCannotMintAfterReturn(){var root=new ModuleManager();var p=new Parent(root);root.register(p);p.setEnabled(true);p.post=()->{throw new IllegalStateException("failed post hook");};assertThrows(IllegalStateException.class,()->p.setEnabled(false));assertFalse(p.insideResult);assertFalse(p.attempt());assertEquals(LegacyCleanupTransactionIdentity.Stage.RESTORED,p.issuer.stage());}
 @Test void enableInsidePendingHookRevokesActualReceiptWithoutGrant(){var root=new ModuleManager();var p=new Parent(root);root.register(p);p.setEnabled(true);p.post=()->p.setEnabled(true);p.setEnabled(false);assertFalse(p.insideResult);assertFalse(p.attempt());assertEquals(LegacyCleanupTransactionIdentity.Stage.RESTORED,p.issuer.stage());}
 @Test void laterActualEnableDisableAbaCannotReuseOldStoppedScope(){var root=new ModuleManager();var p=new Parent(root);root.register(p);p.setEnabled(true);p.setEnabled(false);var oldReceipt=p.receipt;var oldStopped=p.stopped;p.setEnabled(true);
  assertTrue(root.ownDisableMatches(oldReceipt,oldStopped,p));assertFalse(root.lifecycleCurrent(oldStopped));assertFalse(p.attempt());}
 @Test void unrelatedSupplierBindingAfterReturnCannotReuseExactStop(){var root=new ModuleManager();var p=new Parent(root);root.register(p);p.setEnabled(true);p.setEnabled(false);root.leasedInputOwner(()->null);assertFalse(p.attempt());assertEquals(LegacyCleanupTransactionIdentity.Stage.RESTORED,p.issuer.stage());}
}
