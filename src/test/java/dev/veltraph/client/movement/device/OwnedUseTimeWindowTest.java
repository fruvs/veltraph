package dev.veltraph.client.movement.device;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class OwnedUseTimeWindowTest {
 @Test void delayedCallbackCannotBorrowRemainingDeviceTime(){assertFalse(OwnedUseTimeWindow.permits(120000,119999,124999,120000));}
 @Test void ordinaryOriginalWindowRemainsValid(){assertTrue(OwnedUseTimeWindow.permits(119999,119998,124998,120000));}
 @Test void exactDeviceBoundaryRefusesEvenWhenJourneyRemains(){assertFalse(OwnedUseTimeWindow.permits(5000,100,5000,120000));}
 @Test void staleOrUnavailableSourceDeadlineCannotGrant(){assertFalse(OwnedUseTimeWindow.permits(100,100,5100,-1));assertFalse(OwnedUseTimeWindow.permits(100,100,5100,100));}
 @Test void backwardClockCannotUseTheOriginalIssuedAttempt(){assertFalse(OwnedUseTimeWindow.permits(99,100,5100,120000));}
}
