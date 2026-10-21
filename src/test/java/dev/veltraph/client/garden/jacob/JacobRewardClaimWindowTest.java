package dev.veltraph.client.garden.jacob;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class JacobRewardClaimWindowTest {
    @Test void postPersistenceBoundaryRemainsOriginal(){var window=JacobRewardClaimWindow.start(10,1000);assertTrue(window.current(1009));assertFalse(window.current(1010));assertFalse(window.current(1011));assertEquals(1010,window.deadline());}
    @Test void clockRegressionCannotDispatch(){var window=JacobRewardClaimWindow.start(100,1000);assertFalse(window.current(99));assertTrue(window.current(100));}
    @Test void predictionOrRecreatedReadCannotExtendWindow(){var original=JacobRewardClaimWindow.start(10,60000);assertFalse(original.current(60010));assertFalse(original.current(90000));assertEquals(60010,original.deadline());}
    @Test void invalidOrOverflowedWindowRefuses(){assertThrows(IllegalArgumentException.class,()->JacobRewardClaimWindow.start(0,999));assertThrows(IllegalArgumentException.class,()->JacobRewardClaimWindow.start(0,60001));assertThrows(ArithmeticException.class,()->JacobRewardClaimWindow.start(Long.MAX_VALUE-500,1000));}
}
