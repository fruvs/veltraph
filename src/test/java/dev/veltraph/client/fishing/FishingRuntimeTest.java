package dev.veltraph.client.fishing;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pure fishing FSM tests (injected clock + Random). Covers cast / too-soon / reel / recast / timeout. */
class FishingRuntimeTest {

    private static FishingTuning tuning(boolean humanize) {
        return new FishingTuning(700, 200, humanize, 4000, FishingDetectionMode.BOBBER_DIP, -0.15);
    }

    private static FishingInputs in(boolean biting, boolean bobber, long now) {
        return new FishingInputs(true, true, true, bobber, biting, now);
    }

    private FishingRuntime newRuntime(boolean humanize) {
        return new FishingRuntime(new Random(0), tuning(humanize));
    }

    @Test
    void disabledStaysIdle() {
        FishingRuntime r = newRuntime(false);
        FishingAction a = r.tick(new FishingInputs(false, true, true, false, false, 0));
        assertEquals(FishingAction.NONE, a);
        assertEquals(FishingState.IDLE, r.state());
    }

    @Test
    void castsOnFirstEnabledTickThenWaits() {
        FishingRuntime r = newRuntime(false);
        assertEquals(FishingAction.CAST, r.tick(in(false, false, 0)));
        assertEquals(FishingState.WAITING_BITE, r.state());
        assertEquals(FishingAction.NONE, r.tick(in(false, true, 100)));
    }

    @Test
    void rejectsBiteTooSoonThenReelsAfterMinCatch() {
        FishingRuntime r = newRuntime(false);
        r.tick(in(false, false, 0)); // cast @ t=0
        // bite at t=300 (< 700 minCatch) -> too soon, no reel
        assertEquals(FishingAction.NONE, r.tick(in(true, true, 300)));
        assertTrue(r.lastBiteTooSoon());
        assertEquals(FishingState.WAITING_BITE, r.state());
        // bite at t=800 (>= 700) -> reel
        assertEquals(FishingAction.REEL, r.tick(in(true, true, 800)));
        assertEquals(FishingState.POST_CATCH_DELAY, r.state());
        assertEquals(1, r.catchCount());
    }

    @Test
    void holdsPostCatchDelayThenRecasts() {
        FishingRuntime r = newRuntime(false);
        r.tick(in(false, false, 0));        // cast
        r.tick(in(true, true, 800));        // reel @ 800
        // delay 200ms: at 900 still waiting
        assertEquals(FishingAction.NONE, r.tick(in(false, false, 900)));
        assertEquals(FishingState.POST_CATCH_DELAY, r.state());
        // at 1000 (>= 800+200) -> transitions to NEED_CAST (returns NONE this tick)
        assertEquals(FishingAction.NONE, r.tick(in(false, false, 1000)));
        assertEquals(FishingState.NEED_CAST, r.state());
        // next tick casts again
        assertEquals(FishingAction.CAST, r.tick(in(false, false, 1050)));
    }

    @Test
    void noRodWaitsWithoutCasting() {
        FishingRuntime r = newRuntime(false);
        FishingInputs noRod = new FishingInputs(true, true, false, false, false, 0);
        assertEquals(FishingAction.NONE, r.tick(noRod));
        assertEquals(FishingState.NEED_CAST, r.state()); // armed, just waiting for a rod
        // rod appears -> casts
        assertEquals(FishingAction.CAST, r.tick(in(false, false, 50)));
    }

    @Test
    void recastsWhenBobberNeverAppears() {
        FishingRuntime r = newRuntime(false);
        r.tick(in(false, false, 0)); // cast @ 0
        // no bobber by t=5000 (> 4000 timeout) -> NEED_CAST
        assertEquals(FishingAction.NONE, r.tick(in(false, false, 5000)));
        assertEquals(FishingState.NEED_CAST, r.state());
        assertEquals(FishingAction.CAST, r.tick(in(false, false, 5050)));
    }

    @Test
    void humanizeKeepsRecastWithinExpectedWindow() {
        FishingRuntime r = newRuntime(true);
        r.tick(in(false, false, 0));
        r.tick(in(true, true, 800)); // reel @ 800; delay = 200 + nextInt(3)*50 (<= 300)
        // before the minimum delay it must still be waiting
        assertEquals(FishingState.POST_CATCH_DELAY, r.state());
        r.tick(in(false, false, 800 + 199));
        assertEquals(FishingState.POST_CATCH_DELAY, r.state());
        // after the maximum possible (200 + 2 ticks) it must have transitioned
        r.tick(in(false, false, 800 + 301));
        assertEquals(FishingState.NEED_CAST, r.state());
    }

    @Test
    void delayedReelRemovalNeverReusesOrReelsTheSameBobber() {
        FishingRuntime r = newRuntime(false);
        r.tick(in(false, false, 0));
        assertEquals(FishingAction.REEL, r.tick(in(true, true, 800)));
        for (long now = 850; now <= 3000; now += 50) {
            assertEquals(FishingAction.NONE, r.tick(in(true, true, now)));
            assertEquals(FishingState.POST_CATCH_DELAY, r.state());
        }
        assertEquals(1, r.catchCount());
        assertEquals(FishingAction.NONE, r.tick(in(false, false, 3050)));
        assertEquals(FishingAction.CAST, r.tick(in(false, false, 3100)));
    }

    @Test
    void missingReelAcknowledgmentFailsWithoutRepeatedClicks() {
        FishingRuntime r = newRuntime(false);
        r.tick(in(false, false, 0));
        r.tick(in(true, true, 800));
        assertEquals(FishingAction.NONE, r.tick(in(true, true, 4801)));
        assertEquals(FishingState.FAILED, r.state());
        assertEquals(FishingAction.NONE, r.tick(in(false, false, 4900)));
        assertEquals(FishingState.FAILED, r.state());
    }

    @Test
    void subflowReturnAdoptsNewHookAndPreservesStatistics() {
        var r=newRuntime(false);r.tick(in(false,false,0));r.tick(in(true,true,800));
        assertEquals(FishingState.POST_CATCH_DELAY,r.state());
        r.resumeAfterSubflow();
        assertEquals(FishingAction.NONE,r.tick(in(true,true,10000)));
        assertEquals(FishingState.WAITING_BITE,r.state());
        assertEquals(1,r.catchCount());
        assertEquals(FishingAction.NONE,r.tick(in(true,true,10100)));
        assertTrue(r.lastBiteTooSoon());
        assertEquals(FishingAction.REEL,r.tick(in(true,true,10700)));
        assertEquals(2,r.catchCount());
    }

    @Test
    void disableResetsState() {
        FishingRuntime r = newRuntime(false);
        r.tick(in(false, false, 0));
        r.tick(new FishingInputs(false, true, true, false, false, 100));
        assertEquals(FishingState.IDLE, r.state());
        assertEquals(0, r.catchCount());
    }
}
