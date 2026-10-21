package dev.veltraph.client.rotation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class NormalIdleAttemptOriginTest {
 @Test void unscopedSameValueAttemptRemainsRejectedWithRealCaller(){var v=new NormalIdleInputVersion();var old=v.snapshot();v.watchAttemptOrigin(old);actualNativeHead(v);assertFalse(v.current(old));assertTrue(v.cachedCurrentDiagnostic().contains("unscoped=true"));assertTrue(v.cachedCurrentDiagnostic().contains("actualNativeHead"));}
 private void actualNativeHead(NormalIdleInputVersion v){v.begin(null,true);v.observeAttemptOrigin(false,false);v.end(false);}
 @Test void noWatchDoesNotCollectCallOrigin(){var v=new NormalIdleInputVersion();actualNativeHead(v);assertTrue(v.cachedCurrentDiagnostic().endsWith("firstWatchedAttempt=NONE"));}
 @Test void foreignSnapshotCannotInstallWatch(){var v=new NormalIdleInputVersion();v.watchAttemptOrigin(new NormalIdleInputVersion().snapshot());actualNativeHead(v);assertTrue(v.cachedCurrentDiagnostic().endsWith("firstWatchedAttempt=NONE"));}
 @Test void firstAttemptPreservedAcrossLaterCalls(){var v=new NormalIdleInputVersion();v.watchAttemptOrigin(v.snapshot());actualNativeHead(v);String first=v.cachedCurrentDiagnostic();v.begin(new Object(),true);v.observeAttemptOrigin(true,false);v.end(false);assertEquals(first,v.cachedCurrentDiagnostic());}
 @Test void scopedAttemptReportsActualIdentityWithoutMakingOldCurrent(){var v=new NormalIdleInputVersion();var old=v.snapshot();v.watchAttemptOrigin(old);var own=new Object();v.begin(own,true);v.observeAttemptOrigin(false,true);v.end(false);assertFalse(v.current(old));assertTrue(v.cachedCurrentDiagnostic().contains("eventIdentity="+System.identityHashCode(own)));assertTrue(v.cachedCurrentDiagnostic().contains("unscoped=false"));}
 @Test void offThreadAttemptIsUnknownAndCollectsNoStack(){var v=new NormalIdleInputVersion();var old=v.snapshot();v.watchAttemptOrigin(old);v.begin(null,false);v.observeAttemptOrigin(false,false);assertFalse(v.current(old));assertTrue(v.cachedCurrentDiagnostic().endsWith("firstWatchedAttempt=NONE"));}
}
