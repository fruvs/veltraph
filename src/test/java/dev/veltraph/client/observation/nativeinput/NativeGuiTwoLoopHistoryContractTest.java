package dev.veltraph.client.observation.nativeinput;
import dev.veltraph.client.rotation.NormalIdleInputVersion;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
/** Actual native history law for two queries and the optional normally returned nested-base toggle. */
final class NativeGuiTwoLoopHistoryContractTest {
 private static void query(NormalIdleInputVersion v){v.begin(null,true);v.end(false);}
 @Test void twoRealQueriesAreTwoOuterAttempts(){var v=new NormalIdleInputVersion();var old=v.snapshot();query(v);query(v);assertTrue(v.advancedOnly(old,v.snapshot(),Collections.nCopies(2,null)));}
 @Test void actualRestorePlusSecondQueryAreThreeOuterAttempts(){var v=new NormalIdleInputVersion();var old=v.snapshot();query(v);v.begin(null,true);v.begin(null,true);v.end(true);v.end(true);query(v);assertTrue(v.advancedOnly(old,v.snapshot(),Collections.nCopies(3,null)));}
 @Test void missingInnerLoopCannotMeetTwoLoopHistory(){var v=new NormalIdleInputVersion();var old=v.snapshot();query(v);assertFalse(v.advancedOnly(old,v.snapshot(),Collections.nCopies(2,null)));}
 @Test void arbitraryThirdLoopCannotBeIgnored(){var v=new NormalIdleInputVersion();var old=v.snapshot();query(v);query(v);query(v);assertFalse(v.advancedOnly(old,v.snapshot(),Collections.nCopies(2,null)));}
 @Test void sameValueForeignAttemptCannotBeAbsorbed(){var v=new NormalIdleInputVersion();var old=v.snapshot();query(v);v.begin(new Object(),true);v.end(false);query(v);assertFalse(v.advancedOnly(old,v.snapshot(),Collections.nCopies(2,null)));}
 @Test void secondQueryExceptionCannotMintCompletedHistory(){var v=new NormalIdleInputVersion();var old=v.snapshot();query(v);v.begin(null,true);assertNull(v.snapshot());assertFalse(v.advancedOnly(old,v.snapshot(),Collections.nCopies(2,null)));}
}
