package dev.veltraph.client.combat.heal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pure Hyperion auto-heal FSM tests (injected clock). */
class HyperionAutoHealTest {

    @Test
    void readOnlyPredictionMatchesCooldownAndEmergencyWithoutAdvancing() {
        var h=new HyperionAutoHeal();assertTrue(h.wouldStart(in(40,true,0),T));assertEquals(HealState.IDLE,h.state());
        h.tick(in(40,true,0),T);assertFalse(h.wouldStart(in(20,true,1),T));
        h.tick(in(40,true,50),T);h.tick(in(40,true,100),T);h.tick(in(40,true,1300),T);h.tick(in(40,true,1350),T);
        assertFalse(h.wouldStart(in(40,true,1500),T));assertTrue(h.wouldStart(in(20,true,1500),T));
        assertTrue(h.wouldStart(in(40,true,4350),T));assertEquals(HealState.IDLE,h.state());assertEquals(1,h.healCount());
    }

    private static final HealTuning T = new HealTuning(50, 25, 1200, 3000, 15000);

    /** enabled, available, hasHyperion all true; vary hp/clear/now. */
    private static HealInputs in(double hp, boolean clear, long now) {
        return new HealInputs(true, true, true, hp, clear, now);
    }

    @Test
    void highHealthDoesNotHeal() {
        HyperionAutoHeal h = new HyperionAutoHeal();
        assertEquals(HealAction.NONE, h.tick(in(80, true, 0), T));
        assertEquals(HealState.IDLE, h.state());
    }

    @Test
    void lowHealthRunsLookDownCheckCastWaitRestore() {
        HyperionAutoHeal h = new HyperionAutoHeal();
        assertEquals(HealAction.LOOK_DOWN, h.tick(in(40, true, 0), T));
        assertEquals(HealState.LOOK_DOWN, h.state());
        assertEquals(HealAction.NONE, h.tick(in(40, true, 50), T));      // -> CHECK_CLEAR
        assertEquals(HealState.CHECK_CLEAR, h.state());
        assertEquals(HealAction.CAST, h.tick(in(40, true, 100), T));     // clear -> cast
        assertEquals(HealState.WAIT, h.state());
        assertEquals(1, h.healCount());
        assertEquals(HealAction.NONE, h.tick(in(40, true, 600), T));     // 500 < 1200
        assertEquals(HealAction.RESTORE, h.tick(in(40, true, 1300), T)); // 1200 elapsed
        assertEquals(HealState.RESTORE, h.state());
        assertEquals(HealAction.NONE, h.tick(in(40, true, 1350), T));    // finalize -> IDLE + cooldown
        assertEquals(HealState.IDLE, h.state());
    }

    @Test
    void blockedAbortsWithoutCasting() {
        HyperionAutoHeal h = new HyperionAutoHeal();
        h.tick(in(40, false, 0), T);                                     // LOOK_DOWN
        h.tick(in(40, false, 50), T);                                    // CHECK_CLEAR
        assertEquals(HealAction.RESTORE, h.tick(in(40, false, 100), T)); // blocked -> restore, no cast
        assertTrue(h.lastAttemptBlocked());
        assertEquals(0, h.healCount());
    }

    @Test
    void noHyperionDoesNotHeal() {
        HyperionAutoHeal h = new HyperionAutoHeal();
        HealInputs noWeapon = new HealInputs(true, true, false, 40, true, 0);
        assertEquals(HealAction.NONE, h.tick(noWeapon, T));
        assertEquals(HealState.IDLE, h.state());
    }

    @Test
    void cooldownBlocksReHealButEmergencyBypasses() {
        HyperionAutoHeal h = new HyperionAutoHeal();
        // full cycle -> arms cooldown at ~1350+3000
        h.tick(in(40, true, 0), T);
        h.tick(in(40, true, 50), T);
        h.tick(in(40, true, 100), T);
        h.tick(in(40, true, 1300), T);
        h.tick(in(40, true, 1350), T); // IDLE, cooldownUntil=4350
        assertEquals(HealState.IDLE, h.state());
        // within cooldown, normal low health -> no heal
        assertEquals(HealAction.NONE, h.tick(in(40, true, 1500), T));
        // within cooldown, emergency health -> bypasses cooldown
        assertEquals(HealAction.LOOK_DOWN, h.tick(in(20, true, 1550), T));
    }

    @Test
    void disabledMidHealRestoresCamera() {
        HyperionAutoHeal h = new HyperionAutoHeal();
        assertEquals(HealAction.LOOK_DOWN, h.tick(in(40, true, 0), T));
        HealInputs disabled = new HealInputs(false, true, true, 40, true, 50);
        assertEquals(HealAction.RESTORE, h.tick(disabled, T));
        assertEquals(HealState.IDLE, h.state());
    }

    @Test
    void fifteenSecondWindowForcesRestore() {
        HyperionAutoHeal h = new HyperionAutoHeal();
        h.tick(in(40, true, 0), T);     // LOOK_DOWN (start=0)
        h.tick(in(40, true, 50), T);    // CHECK_CLEAR
        h.tick(in(40, true, 100), T);   // CAST -> WAIT
        // jump beyond the 15 s window from start
        assertEquals(HealAction.RESTORE, h.tick(in(40, true, 16000), T));
        assertEquals(HealState.RESTORE, h.state());
    }

    @Test
    void emergencyThresholdIsStricterThanActivate() {
        // hp between emergency(25) and activate(50) still triggers on first heal (off cooldown).
        HyperionAutoHeal h = new HyperionAutoHeal();
        assertEquals(HealAction.LOOK_DOWN, h.tick(in(45, true, 0), T));
        assertFalse(h.lastAttemptBlocked());
    }
}
