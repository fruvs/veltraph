package dev.veltraph.client.rotation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
final class UnscopedHeldOriginObservationTest {
 @Test void oneGenuineNormalUnscopedOuterReturnMayBeObserved(){var h=new NormalIdleInputVersion();var before=h.snapshot();h.begin(null,true);h.end(false);var after=h.snapshot();assertTrue(h.unscopedAdvancedOnce(before,after));assertFalse(h.advancedOnly(before,after,java.util.List.of()));}
 @Test void noOpCannotRefreshPersistentOrigin(){var h=new NormalIdleInputVersion();var before=h.snapshot();assertFalse(h.unscopedAdvancedOnce(before,h.snapshot()));}
 @Test void actualScopedEventIsNotAnUnscopedMethodObservation(){var h=new NormalIdleInputVersion();var before=h.snapshot();Object actual=new Object();h.begin(actual,true);h.end(false);var after=h.snapshot();assertFalse(h.unscopedAdvancedOnce(before,after));assertTrue(h.advancedOnly(before,after,java.util.List.of(actual)));}
 @Test void twoRawAttemptsCannotMasqueradeAsOneActualMethodReturn(){var h=new NormalIdleInputVersion();var before=h.snapshot();h.begin(null,true);h.end(false);h.begin(null,true);h.end(false);assertFalse(h.unscopedAdvancedOnce(before,h.snapshot()));}
 @Test void inFlightAndWrongThreadAreUnavailable(){var h=new NormalIdleInputVersion();var before=h.snapshot();h.begin(null,true);assertNull(h.snapshot());assertFalse(h.unscopedAdvancedOnce(before,before));h.end(false);h.begin(null,false);assertNull(h.snapshot());assertFalse(h.unscopedAdvancedOnce(before,before));}
 @Test void staleAfterCannotBeAdoptedFollowingMoreForeignHistory(){var h=new NormalIdleInputVersion();var before=h.snapshot();h.begin(null,true);h.end(false);var after=h.snapshot();h.begin(null,true);h.end(false);assertFalse(h.unscopedAdvancedOnce(before,after));assertFalse(h.current(after));}
 @Test void anotherCarrierCannotSupplyAnObservation(){var h=new NormalIdleInputVersion();var before=h.snapshot();var other=new NormalIdleInputVersion();other.begin(null,true);other.end(false);assertFalse(h.unscopedAdvancedOnce(before,other.snapshot()));}
 @Test void evenLegalToggleNestedShapeCannotClaimFlatUnscopedProvenance(){var h=new NormalIdleInputVersion();var before=h.snapshot();h.begin(null,true);h.begin(null,true);h.end(true);h.end(true);var after=h.snapshot();assertNotNull(after);assertFalse(h.unscopedAdvancedOnce(before,after));}
}
