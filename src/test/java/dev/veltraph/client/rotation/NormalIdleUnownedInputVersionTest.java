package dev.veltraph.client.rotation;
import java.lang.reflect.Modifier;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
/** Kernel sequences only: model event identities here grant no native input or publication permission. */
class NormalIdleUnownedInputVersionTest {
 private static void returned(NormalIdleInputVersion state,Object modelEvent){state.begin(modelEvent,true);state.end(false);}
 @Test void modelOwnedAttemptLeavesNegativeOperandButOldExactHistoryStillChanges(){
  var state=new NormalIdleInputVersion();var original=state.unownedSnapshot();var old=state.snapshot();var event=new Object();
  state.begin(event,true);assertNull(state.unownedSnapshot());assertFalse(state.unownedCurrent(original));state.end(false);
  assertTrue(state.unownedCurrent(original));assertFalse(state.current(old));assertTrue(state.advancedOnly(old,state.snapshot(),List.of(event)));
 }
 @Test void sameValueUnownedAttemptCountsBeforeAnyNormalReturn(){
  var state=new NormalIdleInputVersion();var original=state.unownedSnapshot();state.begin(null,true);
  assertNull(state.unownedSnapshot());assertFalse(state.unownedCurrent(original));state.end(false);assertFalse(state.unownedCurrent(original));
 }
 @Test void sameValueAndAbaUnownedAttemptsCannotBeErasedByLaterOwnedEvents(){
  var state=new NormalIdleInputVersion();var original=state.unownedSnapshot();returned(state,null);returned(state,null);returned(state,new Object());
  assertFalse(state.unownedCurrent(original));returned(state,new Object());assertFalse(state.unownedCurrent(original));
 }
 @Test void legalOwnedToggleBaseChildInheritsOuterClaimAndPreservesNegativeOperand(){
  var state=new NormalIdleInputVersion();var original=state.unownedSnapshot();var old=state.snapshot();var owned=new Object();
  state.begin(owned,true);state.begin(null,true);assertNull(state.unownedSnapshot());assertFalse(state.unownedCurrent(original));
  state.end(true);assertFalse(state.unownedCurrent(original));state.end(true);
  assertTrue(state.unownedCurrent(original));assertTrue(state.advancedOnly(old,state.snapshot(),List.of(owned)));
 }
 @Test void unownedToggleOuterAndBaseChildCountsTheOuterOnce(){
  var state=new NormalIdleInputVersion();var original=state.unownedSnapshot();var old=state.snapshot();
  state.begin(null,new Object(),true);state.begin(null,true);state.end(true);state.end(true);
  assertNotNull(state.unownedSnapshot());assertFalse(state.unownedCurrent(original));
  assertFalse(state.unscopedAdvancedOnce(old,state.snapshot()));
 }
 @Test void illegalPlainChildAndDuplicateToggleChildrenPermanentlyRefuse(){
  var plain=new NormalIdleInputVersion();var original=plain.unownedSnapshot();plain.begin(new Object(),true);plain.begin(null,true);plain.end(false);plain.end(false);
  assertNull(plain.unownedSnapshot());assertFalse(plain.unownedCurrent(original));returned(plain,new Object());assertFalse(plain.unownedCurrent(original));
  var toggle=new NormalIdleInputVersion();var second=toggle.unownedSnapshot();toggle.begin(new Object(),true);toggle.begin(null,true);toggle.end(true);toggle.begin(null,true);toggle.end(true);toggle.end(true);
  assertNull(toggle.unownedSnapshot());assertFalse(toggle.unownedCurrent(second));
 }
 @Test void missingReturnAndNativeFailureCannotProduceOrRefreshAnOperand(){
  var state=new NormalIdleInputVersion();var original=state.unownedSnapshot();state.begin(null,true);
  assertNull(state.unownedSnapshot());assertFalse(state.unownedCurrent(original));state.invalidate();state.end(false);
  assertNull(state.unownedSnapshot());returned(state,new Object());assertFalse(state.unownedCurrent(original));
 }
 @Test void offThreadAndUnmatchedReturnRemainUnknown(){
  var state=new NormalIdleInputVersion();var original=state.unownedSnapshot();state.begin(null,false);assertNull(state.unownedSnapshot());assertFalse(state.unownedCurrent(original));
  var other=new NormalIdleInputVersion();var before=other.unownedSnapshot();other.end(false);assertNull(other.unownedSnapshot());assertFalse(other.unownedCurrent(before));
 }
 @Test void foreignAndNullSnapshotsRefuseEvenWhenTheirCountersMatch(){
  var state=new NormalIdleInputVersion();var other=new NormalIdleInputVersion();assertFalse(state.unownedCurrent(null));assertFalse(state.unownedCurrent(other.unownedSnapshot()));
 }
 @Test void overflowCannotWrapOrRearmAndDoesNotChangeOldOwnedHistory() throws Exception {
  var state=new NormalIdleInputVersion();var field=NormalIdleInputVersion.class.getDeclaredField("unownedRevision");field.setAccessible(true);field.setLong(state,Long.MAX_VALUE);
  var original=state.unownedSnapshot();returned(state,null);assertNull(state.unownedSnapshot());assertFalse(state.unownedCurrent(original));
  assertEquals(Long.MAX_VALUE,field.getLong(state));assertNotNull(state.snapshot());returned(state,new Object());assertFalse(state.unownedCurrent(original));
 }
 @Test void snapshotConstructorAndStateHaveNoPublicMutationOrPermissionSurface(){
  assertTrue(Modifier.isFinal(NormalIdleInputVersion.UnownedSnapshot.class.getModifiers()));
  for(var constructor:NormalIdleInputVersion.UnownedSnapshot.class.getDeclaredConstructors())assertTrue(Modifier.isPrivate(constructor.getModifiers()));
  for(var field:NormalIdleInputVersion.UnownedSnapshot.class.getDeclaredFields()){assertTrue(Modifier.isPrivate(field.getModifiers()));assertTrue(Modifier.isFinal(field.getModifiers()));}
 }
}
