package dev.veltraph.client.core;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class CombatFinishingOwnDisableTest {
 static final class Parent extends AbstractModule {
  final ModuleManager registry;ModuleManager.OwnDisableReceipt receipt;ModuleManager.LifecycleSnapshot stopped;
  boolean throwHook,throwPost;boolean insideAuthorized;
  Parent(ModuleManager registry){super("parent","parent",ModuleCategory.COMBAT);this.registry=registry;}
  public void onDisable(){if(throwHook)throw new IllegalStateException("original disable failed");}
  protected void postDisableNormalReturned(ModuleManager.OwnDisableReceipt actual){receipt=actual;stopped=actual.stoppedSnapshot();insideAuthorized=registry.ownDisableFromMatches(actual,registry.lifecycleSnapshot(),stopped,this);if(throwPost)throw new IllegalStateException("post observer failed");}
 }
 @Test void actualNormalReturnAndExactOldSnapshotAreRequired(){var root=new ModuleManager();var parent=new Parent(root);root.register(parent);parent.setEnabled(true);var before=root.lifecycleSnapshot();parent.setEnabled(false);assertFalse(parent.insideAuthorized);assertTrue(root.ownDisableFromMatches(parent.receipt,before,parent.stopped,parent));assertFalse(root.ownDisableFromMatches(parent.receipt,parent.stopped,parent.stopped,parent));}
 @Test void publicOnDisableAndCopiedStoppedStateCannotMintReceipt(){var root=new ModuleManager();var parent=new Parent(root);root.register(parent);parent.setEnabled(true);var before=root.lifecycleSnapshot();parent.onDisable();assertNull(parent.receipt);assertFalse(root.ownDisableFromMatches(null,before,root.lifecycleSnapshot(),parent));}
 @Test void abnormalOriginalAndPostReturnCannotCertifyCleanup(){for(boolean post:new boolean[]{false,true}){var root=new ModuleManager();var parent=new Parent(root);root.register(parent);parent.setEnabled(true);var before=root.lifecycleSnapshot();parent.throwHook=!post;parent.throwPost=post;assertThrows(IllegalStateException.class,()->parent.setEnabled(false));assertFalse(root.ownDisableFromMatches(parent.receipt,before,parent.stopped,parent));}}
 @Test void reenableAbaInvalidatesOldCurrentClosure(){var root=new ModuleManager();var parent=new Parent(root);root.register(parent);parent.setEnabled(true);var before=root.lifecycleSnapshot();parent.setEnabled(false);var receipt=parent.receipt;var stopped=parent.stopped;parent.setEnabled(true);parent.setEnabled(false);assertFalse(root.ownDisableFromMatches(receipt,before,stopped,parent));}
 static final class Child extends AbstractModule {Child(){super("child","child",ModuleCategory.COMBAT);}public void onDisable(){}}
 @Test void publicChildHookDoesNotAttestPrivateChildStop(){var root=new ModuleManager();var parent=new Parent(root);var child=new Child();root.register(parent);parent.setEnabled(true);child.setEnabled(true);var pair=CombatFinishingLifecycle.capture(parent,child);child.onDisable();assertFalse(pair.childStoppedNormally());child.setEnabled(false);assertTrue(pair.childStoppedNormally());child.setEnabled(true);child.setEnabled(false);assertFalse(pair.childStoppedNormally());}
 @Test void anotherRegisteredModuleTransitionInvalidatesExactOldScope(){var root=new ModuleManager();var parent=new Parent(root);var other=new Child();root.register(parent);root.register(other);parent.setEnabled(true);var before=root.lifecycleSnapshot();other.setEnabled(true);parent.setEnabled(false);assertFalse(root.ownDisableFromMatches(parent.receipt,before,parent.stopped,parent));}
}
