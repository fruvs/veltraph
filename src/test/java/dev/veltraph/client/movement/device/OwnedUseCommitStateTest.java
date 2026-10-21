package dev.veltraph.client.movement.device;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class OwnedUseCommitStateTest {
 private OwnedUseCommitState local(){var state=new OwnedUseCommitState();assertTrue(state.claim());assertTrue(state.localResult(true));return state;}
 @Test void initialMetadataCannotReserve(){var s=new OwnedUseCommitState();assertFalse(s.reserve(()->true));}
 @Test void oneActualClaimOnly(){var s=new OwnedUseCommitState();assertTrue(s.claim());assertFalse(s.claim());}
 @Test void priorLocalRefusalCannotSend(){var s=new OwnedUseCommitState();assertTrue(s.claim());assertFalse(s.localResult(false));assertFalse(s.reserve(()->true));}
 @Test void lastLeafRefusalCannotRetry(){var s=local();assertFalse(s.reserve(()->false));assertFalse(s.reserve(()->true));}
 @Test void validOriginalAttemptReservesOnce(){var s=local();assertTrue(s.reserve(()->true));assertFalse(s.reserve(()->true));}
 @Test void nestedReservationPreventsOuterDuplicate(){var s=local();assertFalse(s.reserve(()->{assertTrue(s.reserve(()->true));return true;}));assertFalse(s.reserve(()->true));}
 @Test void closeDuringLeafRefuses(){var s=local();assertFalse(s.reserve(()->{s.close();return true;}));assertFalse(s.reserve(()->true));}
 @Test void originalExceptionIsPreservedAndAttemptRefuses(){var s=local();var error=new IllegalStateException("native leaf failure");assertSame(error,assertThrows(IllegalStateException.class,()->s.reserve(()->{throw error;})));assertFalse(s.reserve(()->true));}
}
