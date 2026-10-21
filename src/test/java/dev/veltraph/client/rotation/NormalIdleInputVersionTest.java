package dev.veltraph.client.rotation;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class NormalIdleInputVersionTest {
 @Test void actualBoundScopeEventCanAdvanceOnceAfterNormalNativeReturn(){var state=new NormalIdleInputVersion();var before=state.snapshot();var privateIssued=new Object();state.begin(privateIssued,true);assertNull(state.snapshot());state.end(false);var after=state.snapshot();assertNotNull(after);assertTrue(state.advancedOnly(before,after,List.of(privateIssued)));}
 @Test void foreignOrHardwareNoEffectEventCannotBorrowScopedIdentity(){var state=new NormalIdleInputVersion();var before=state.snapshot();var privateIssued=new Object();state.begin(null,true);state.end(false);assertFalse(state.advancedOnly(before,state.snapshot(),List.of(privateIssued)));assertFalse(state.current(before));}
 @Test void keyTrueFalseAbaAdvancesTwoAttemptsAndRejectsOldSnapshot(){var state=new NormalIdleInputVersion();var before=state.snapshot();state.begin(null,true);state.end(false);state.begin(null,true);state.end(false);assertFalse(state.current(before));assertFalse(state.advancedOnly(before,state.snapshot(),List.of(new Object())));}
 @Test void missingReturnAndOffThreadAttemptNeverQualify(){var state=new NormalIdleInputVersion();state.begin(new Object(),true);assertNull(state.snapshot());var other=new NormalIdleInputVersion();other.begin(new Object(),false);assertNull(other.snapshot());}
 @Test void actualVanillaToggleBaseChildIsOneOuterAttempt(){var state=new NormalIdleInputVersion();var before=state.snapshot();var owned=new Object();state.begin(owned,true);state.begin(null,true);state.end(true);state.end(true);assertTrue(state.advancedOnly(before,state.snapshot(),List.of(owned)));}
 @Test void unexpectedAdditionalNestedSetterMakesHistoryUnknown(){var state=new NormalIdleInputVersion();state.begin(new Object(),true);state.begin(null,true);state.end(true);state.begin(null,true);state.end(true);state.end(true);assertNull(state.snapshot());}
 @Test void noopScopedReadCanObserveOnlyExactUnchangedVersion(){var state=new NormalIdleInputVersion();var before=state.snapshot();assertTrue(state.advancedOnly(before,state.snapshot(),List.of()));state.begin(null,true);state.end(false);assertFalse(state.advancedOnly(before,state.snapshot(),List.of()));}
 @Test void snapshotsAreBoundToTheActualPrivateStateInstance(){var a=new NormalIdleInputVersion();var b=new NormalIdleInputVersion();assertFalse(b.current(a.snapshot()));assertFalse(b.advancedOnly(a.snapshot(),b.snapshot(),List.of()));}
 @Test void wholeWindowDetectsForeignFutureKeyBeforeItsOwnNoopObservation(){var keys=new NormalIdleInputVersion[9];var original=new NormalIdleInputVersion.Snapshot[9];for(int i=0;i<9;i++){keys[i]=new NormalIdleInputVersion();original[i]=keys[i].snapshot();}
  // Earlier key's observer writes a later key true/false, then that later key's local scoped read sees unchanged value.
  keys[8].begin(null,true);keys[8].end(false);keys[8].begin(null,true);keys[8].end(false);
  var laterLocalRead=keys[8].snapshot();assertTrue(keys[8].advancedOnly(laterLocalRead,keys[8].snapshot(),List.of()));
  assertFalse(keys[8].advancedOnly(original[8],keys[8].snapshot(),List.of()));
 }
}
