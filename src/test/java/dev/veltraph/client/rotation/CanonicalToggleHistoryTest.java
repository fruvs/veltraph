package dev.veltraph.client.rotation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class CanonicalToggleHistoryTest {
 private static void paired(NormalIdleInputVersion v,Object event){v.begin(null,event,true);v.begin(null,event,true);v.end(true);v.end(true);}
 @Test void pairedNormalHistoryNeedsExactNativeEventIdentity(){var v=new NormalIdleInputVersion();var before=v.snapshot();var event=new Object();paired(v,event);var after=v.snapshot();assertTrue(v.canonicalUnscopedAdvancedOnce(before,after,event));assertFalse(v.canonicalUnscopedAdvancedOnce(before,after,new Object()));assertFalse(v.unscopedAdvancedOnce(before,after));}
 @Test void rawPairedHistoryCannotBorrowCanonicalIdentity(){var v=new NormalIdleInputVersion();var before=v.snapshot();paired(v,null);assertFalse(v.canonicalUnscopedAdvancedOnce(before,v.snapshot(),new Object()));}
 @Test void missingBaseReturnOrOuterReturnIsNotACompletedPair(){var v=new NormalIdleInputVersion();var before=v.snapshot();var event=new Object();v.begin(null,event,true);v.begin(null,event,true);v.end(true);assertNull(v.snapshot());assertFalse(v.canonicalUnscopedAdvancedOnce(before,v.snapshot(),event));}
 @Test void additionalNestedSetterRetiresCanonicalHistory(){var v=new NormalIdleInputVersion();var before=v.snapshot();var event=new Object();v.begin(null,event,true);v.begin(null,event,true);v.end(true);v.begin(null,event,true);v.end(true);v.end(true);assertNull(v.snapshot());assertFalse(v.canonicalUnscopedAdvancedOnce(before,v.snapshot(),event));}
 @Test void rawSameValueAttemptInvalidatesPriorPair(){var v=new NormalIdleInputVersion();var before=v.snapshot();var event=new Object();paired(v,event);var after=v.snapshot();v.begin(null,true);v.end(true);assertFalse(v.current(after));assertFalse(v.canonicalUnscopedAdvancedOnce(before,v.snapshot(),event));}
 @Test void rawAbaCannotReuseOriginalPair(){var v=new NormalIdleInputVersion();var before=v.snapshot();var event=new Object();paired(v,event);paired(v,null);paired(v,null);assertFalse(v.canonicalUnscopedAdvancedOnce(before,v.snapshot(),event));}
 @Test void scopedEventCannotBeReclassifiedAsUnscopedCanonical(){var v=new NormalIdleInputVersion();var before=v.snapshot();var event=new Object();v.begin(new Object(),event,true);v.begin(null,event,true);v.end(true);v.end(true);assertFalse(v.canonicalUnscopedAdvancedOnce(before,v.snapshot(),event));}
 @Test void ignoredOuterAndRestorationCannotBeFoldedIntoOnePair(){var v=new NormalIdleInputVersion();var before=v.snapshot();var event=new Object();v.begin(null,true);v.end(true);paired(v,event);v.begin(null,true);v.end(true);assertFalse(v.canonicalUnscopedAdvancedOnce(before,v.snapshot(),event));}
 @Test void onePlainOuterKeepsItsOriginalUnscopedContract(){var v=new NormalIdleInputVersion();var before=v.snapshot();v.begin(null,true);v.end(false);assertTrue(v.unscopedAdvancedOnce(before,v.snapshot()));assertFalse(v.canonicalUnscopedAdvancedOnce(before,v.snapshot(),new Object()));}
}
