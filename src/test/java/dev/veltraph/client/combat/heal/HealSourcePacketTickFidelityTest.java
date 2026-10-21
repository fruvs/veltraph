package dev.veltraph.client.combat.heal;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class HealSourcePacketTickFidelityTest {
    @Test void oneCounterDecrementsWithoutDispatchingThatInvocation() {
        var delay = new HealSourcePhaseDelay();
        delay.install(1);
        assertFalse(delay.enterNextInvocation()); assertEquals(0, delay.remaining());
        assertTrue(delay.enterNextInvocation());
    }
    @Test void zeroDoesNotIntroduceASecondSkipOrAClockDuration() {
        var delay = new HealSourcePhaseDelay(); delay.install(0);
        assertTrue(delay.enterNextInvocation());
        delay.install(2); assertFalse(delay.enterNextInvocation());
        assertFalse(delay.enterNextInvocation()); assertTrue(delay.enterNextInvocation());
        assertThrows(IllegalArgumentException.class, () -> delay.install(-1));
    }
    @Test void independentSwitchBackAndEndSamplesHaveFourDistinctFrameTimelines() {
        for (int beforeSwitch = 0; beforeSwitch <= 1; beforeSwitch++) {
            for (int beforeEnd = 0; beforeEnd <= 1; beforeEnd++) {
                var delay = new HealSourcePhaseDelay();
                int phase = 0, switchAt = -1, endAt = -1;
                // t0 emitted use. t1 dispatches the post-use phase and installs its counter.
                for (int tick = 1; tick < 8; tick++) {
                    if (!delay.enterNextInvocation()) continue;
                    if (phase == 0) { phase = 1; delay.install(beforeSwitch); }
                    else if (phase == 1) { switchAt = tick; phase = 2; delay.install(beforeEnd); }
                    else { endAt = tick; break; }
                }
                assertEquals(2 + beforeSwitch, switchAt);
                assertEquals(3 + beforeSwitch + beforeEnd, endAt);
            }
        }
    }
    @Test void bothSourcePacketKindsMatchWithoutAnAngleOrPositionDelta() {
        for (var kind : HealSourcePosePacketContract.Kind.values()) {
            assertEquals(kind != HealSourcePosePacketContract.Kind.OTHER,
                    HealSourcePosePacketContract.eligible(kind, true, true, true, true, true, true, true));
        }
    }
    @Test void foreignOrInactivePacketCannotResolveTheOwnedBuffer() {
        var kind = HealSourcePosePacketContract.Kind.PLAYER_ROTATION;
        assertFalse(HealSourcePosePacketContract.eligible(kind, false, true, true, true, true, true, true));
        assertFalse(HealSourcePosePacketContract.eligible(kind, true, false, true, true, true, true, true));
        assertFalse(HealSourcePosePacketContract.eligible(kind, true, true, true, false, true, true, true));
        assertFalse(HealSourcePosePacketContract.eligible(kind, true, true, true, true, true, false, true));
        assertFalse(HealSourcePosePacketContract.eligible(kind, true, true, true, true, true, true, false));
    }
}
