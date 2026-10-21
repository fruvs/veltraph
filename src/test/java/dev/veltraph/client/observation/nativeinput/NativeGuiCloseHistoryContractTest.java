package dev.veltraph.client.observation.nativeinput;
import dev.veltraph.client.rotation.NormalIdleInputVersion;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
/** Existing native carrier semantics used by close: real outer attempts, with no value-based waiver. */
final class NativeGuiCloseHistoryContractTest {
 private static void query(NormalIdleInputVersion v){v.begin(null,true);v.end(false);}
 private static void toggleWithBase(NormalIdleInputVersion v){v.begin(null,true);v.begin(null,true);v.end(true);v.end(true);}
 @Test void untouchedPlainMappingRequiresZeroAttempts(){var v=new NormalIdleInputVersion();var before=v.snapshot();assertTrue(v.advancedOnly(before,before,List.of()));assertFalse(v.advancedOnly(before,before,Collections.singletonList(null)));}
 @Test void falseRestoreQueryIsOneNormalOuterAttempt(){var v=new NormalIdleInputVersion();var before=v.snapshot();query(v);var after=v.snapshot();assertNotNull(after);assertFalse(v.current(before));assertTrue(v.advancedOnly(before,after,Collections.singletonList(null)));assertFalse(v.advancedOnly(before,after,List.of()));}
 @Test void trueQueryAndActualNestedBaseRestoreAreTwoOuterAttempts(){var v=new NormalIdleInputVersion();var before=v.snapshot();query(v);toggleWithBase(v);var after=v.snapshot();assertNotNull(after);assertTrue(v.advancedOnly(before,after,Collections.nCopies(2,null)));assertFalse(v.advancedOnly(before,after,Collections.singletonList(null)));}
 @Test void missingOptionalSetterCannotMeetTwoAttemptContinuation(){var v=new NormalIdleInputVersion();var before=v.snapshot();query(v);assertFalse(v.advancedOnly(before,v.snapshot(),Collections.nCopies(2,null)));}
 @Test void sameValueForeignAttemptBreaksExactRestoreCount(){var v=new NormalIdleInputVersion();var before=v.snapshot();query(v);query(v);toggleWithBase(v);assertFalse(v.advancedOnly(before,v.snapshot(),Collections.nCopies(2,null)));}
 @Test void foreignABAAttemptsCannotBeAbsorbedIntoClose(){var v=new NormalIdleInputVersion();var before=v.snapshot();query(v);query(v);query(v);toggleWithBase(v);assertFalse(v.advancedOnly(before,v.snapshot(),Collections.nCopies(2,null)));}
 @Test void throwingRestoreCallbackWithoutReturnCannotMintAfter(){var v=new NormalIdleInputVersion();var before=v.snapshot();v.begin(null,true);assertNull(v.snapshot());assertFalse(v.current(before));assertFalse(v.advancedOnly(before,v.snapshot(),Collections.singletonList(null)));}
 @Test void secondNestedForeignAttemptPoisonsToggleShape(){var v=new NormalIdleInputVersion();var before=v.snapshot();query(v);v.begin(null,true);v.begin(null,true);v.end(true);v.begin(null,true);v.end(true);v.end(true);assertNull(v.snapshot());assertFalse(v.advancedOnly(before,v.snapshot(),Collections.nCopies(2,null)));}
 @Test void ownedEventCannotMasqueradeAsNullSdkRestore(){var v=new NormalIdleInputVersion();var before=v.snapshot();query(v);v.begin(new Object(),true);v.begin(null,true);v.end(true);v.end(true);assertNotNull(v.snapshot());assertFalse(v.advancedOnly(before,v.snapshot(),Collections.nCopies(2,null)));}
}
