package dev.veltraph.client.core;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class CombatWeaponCleanupLifecycleTest {
 static final class Actor extends AbstractModule {
  Runnable stopped=()->{};
  Actor(String id){super(id,id,ModuleCategory.COMBAT);}
  @Override public void onDisable(){stopped.run();}
 }
 @Test void actualChildDisableRetainsOnlyItsCapturedActiveParent(){
  var parent=new Actor("parent");var child=new Actor("child");parent.setEnabled(true);child.setEnabled(true);
  var witness=CombatFinishingLifecycle.capture(parent,child);assertNotNull(witness);
  boolean[] observed={false};child.stopped=()->observed[0]=witness.childStoppingWithCurrentParent();child.setEnabled(false);
  assertTrue(observed[0]);assertFalse(witness.parentAndChildStopping());
 }
 @Test void publicOnDisableWithoutStoredTransitionCannotGrantCleanup(){
  var parent=new Actor("parent");var child=new Actor("child");parent.setEnabled(true);child.setEnabled(true);var witness=CombatFinishingLifecycle.capture(parent,child);
  boolean[] observed={true};child.stopped=()->observed[0]=witness.childStoppingWithCurrentParent()||witness.parentAndChildStopping();child.onDisable();assertFalse(observed[0]);
 }
 @Test void realRegisteredParentAndUnregisteredChildStopUseOriginalCoreInvocation(){
  var root=new ModuleManager();var parent=new Actor("parent");var child=new Actor("child");root.register(parent);parent.setEnabled(true);child.setEnabled(true);
  var enabled=root.lifecycleSnapshot();var witness=CombatFinishingLifecycle.capture(parent,child);boolean[] observed={false};
  child.stopped=()->observed[0]=root.pendingOwnDisableFromMatches(enabled,parent)&&witness.parentAndChildStopping();parent.stopped=()->child.setEnabled(false);root.stopAll();assertTrue(observed[0]);
 }
 @Test void parentDisablePublicHookWithoutChildStoredStopCannotBorrowWitness(){
  var parent=new Actor("parent");var child=new Actor("child");parent.setEnabled(true);child.setEnabled(true);var witness=CombatFinishingLifecycle.capture(parent,child);
  boolean[] observed={true};parent.stopped=()->observed[0]=witness.parentAndChildStopping();parent.setEnabled(false);assertFalse(observed[0]);
 }
 @Test void parentAbaAndChildAbaBothRejectOldRun(){
  var parent=new Actor("parent");var child=new Actor("child");parent.setEnabled(true);child.setEnabled(true);var old=CombatFinishingLifecycle.capture(parent,child);
  parent.setEnabled(false);parent.setEnabled(true);child.setEnabled(false);assertFalse(old.childStoppingWithCurrentParent());assertFalse(old.parentAndChildStopping());
  child.setEnabled(true);var next=CombatFinishingLifecycle.capture(parent,child);child.setEnabled(false);child.setEnabled(true);child.setEnabled(false);assertFalse(next.childStoppingWithCurrentParent());
 }
 @Test void standaloneActualRegisteredStopDiffersFromDirectHookAndRosterReplacement(){
  var root=new ModuleManager();var actor=new Actor("actor");root.register(actor);actor.setEnabled(true);var before=root.lifecycleSnapshot();boolean[] observed={true};
  actor.stopped=()->observed[0]=root.pendingOwnDisableFromMatches(before,actor);actor.onDisable();assertFalse(observed[0]);root.stopAll();assertTrue(observed[0]);
  actor.setEnabled(true);var next=root.lifecycleSnapshot();root.register(new Actor("new"));actor.stopped=()->observed[0]=root.pendingOwnDisableFromMatches(next,actor);root.stopAll();assertFalse(observed[0]);
 }
}
