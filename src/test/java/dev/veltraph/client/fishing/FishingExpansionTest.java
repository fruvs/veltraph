package dev.veltraph.client.fishing;
import org.junit.jupiter.api.Test;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;
class FishingExpansionTest {
    @Test void trackedBiteDoesNotTreatFlightOrHookedMobAsFish() {
        assertFalse(BiteDetector.isBite(new BobberFacts(true,-.8,true,false),FishingDetectionMode.TRACKED_BITE,-.15));
        assertTrue(BiteDetector.isBite(new BobberFacts(true,0,false,true),FishingDetectionMode.TRACKED_BITE,-.15));
        assertFalse(BiteDetector.isBite(new BobberFacts(true,0,false),FishingDetectionMode.BOBBER_DIP,Double.POSITIVE_INFINITY));
    }
    @Test void adoptsExistingBobberWithoutAccidentallyReeling() {
        var runtime=new FishingRuntime(new Random(0),new FishingTuning(700,200,false,4000,FishingDetectionMode.TRACKED_BITE,-.15));
        assertEquals(FishingAction.NONE,runtime.tick(new FishingInputs(true,true,true,true,false,1000)));
        assertEquals(FishingAction.REEL,runtime.tick(new FishingInputs(true,true,true,true,true,1800)));
    }
    @Test void surfaceRequiresOptInLowAirAndClearWaterColumn() {
        var surface=new FishingSurfaceController();
        assertFalse(surface.tick(false,true,1,true,3,1000,2000));
        assertFalse(surface.tick(true,true,1,false,3,1000,2000));
        assertFalse(surface.tick(true,true,9,true,3,1000,2000));
        assertTrue(surface.tick(true,true,1,true,3,1000,2000));
        assertFalse(surface.tick(true,false,10,true,3,1000,2200));
        assertFalse(surface.tick(true,true,1,true,3,1000,2300));
    }
    @Test void surfacingTimesOutAndDisableReleasesIt() {
        var surface=new FishingSurfaceController();
        assertTrue(surface.tick(true,true,1,true,3,1000,2000));
        assertFalse(surface.tick(true,true,1,true,3,1000,12001));
        assertFalse(surface.tick(true,true,1,true,3,1000,12002));
        assertTrue(surface.tick(true,true,1,true,3,1000,14000));
        assertFalse(surface.tick(false,true,1,true,3,1000,14001));
    }
}
