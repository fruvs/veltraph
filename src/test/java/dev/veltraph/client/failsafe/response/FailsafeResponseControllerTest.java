package dev.veltraph.client.failsafe.response;

import dev.veltraph.client.config.FailsafeConfig;
import dev.veltraph.client.config.MapConfigService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Controller gating: master toggle, per-trigger toggle, cooldown, dev bypass, reset. */
class FailsafeResponseControllerTest {

    private static final class CountingSink implements FailsafeActionSink {
        int releaseAllCount;
        @Override public float currentYaw() { return 0f; }
        @Override public float currentPitch() { return 0f; }
        @Override public void applyLook(float y, float p) { }
        @Override public void setMovement(boolean f, boolean b, boolean l, boolean r, boolean j, boolean s) { }
        @Override public void setAttack(boolean held) { }
        @Override public void setUse(boolean held) { }
        @Override public void sendCommand(String c) { }
        @Override public void releaseAll() { releaseAllCount++; }
    }

    private long now = 1000L;

    private FailsafeResponseController controller(MapConfigService cfg, CountingSink sink) {
        return new FailsafeResponseController(cfg, sink, () -> now);
    }

    @Test
    void noScheduleWhenMasterDisabled() {
        MapConfigService cfg = new MapConfigService(); // FAILSAFE_AUTOMATED_RESPONSE_ENABLED defaults false
        FailsafeResponseController c = controller(cfg, new CountingSink());
        assertFalse(c.onTrigger(FailsafeResponseType.BOSS_TELEPORTED), "master off → no schedule");
        assertFalse(c.active());
    }

    @Test
    void noScheduleWhenPerTriggerDisabled() {
        MapConfigService cfg = new MapConfigService();
        cfg.set(FailsafeConfig.FAILSAFE_AUTOMATED_RESPONSE_ENABLED, true);
        cfg.set(FailsafeConfig.FAILSAFE_RESPONSE_ON_MENTION, false);
        FailsafeResponseController c = controller(cfg, new CountingSink());
        assertFalse(c.onTrigger(FailsafeResponseType.MENTION), "per-trigger off → no schedule");
    }

    @Test
    void disablingMasterAbortsProductionResponseAndReleasesExactlyOnce() {
        MapConfigService cfg = new MapConfigService();
        cfg.set(FailsafeConfig.FAILSAFE_AUTOMATED_RESPONSE_ENABLED, true);
        CountingSink sink = new CountingSink();
        FailsafeResponseController c = controller(cfg, sink);
        assertTrue(c.onTrigger(FailsafeResponseType.PLAYER_NEARBY));
        c.tick();
        int before = sink.releaseAllCount;
        cfg.set(FailsafeConfig.FAILSAFE_AUTOMATED_RESPONSE_ENABLED, false);
        c.tick();
        assertFalse(c.active());
        org.junit.jupiter.api.Assertions.assertEquals(before + 1, sink.releaseAllCount);
        c.tick();
        org.junit.jupiter.api.Assertions.assertEquals(before + 1, sink.releaseAllCount);
    }

    @Test
    void disablingOnlyCurrentTriggerAbortsButUnrelatedTriggerDoesNot() {
        MapConfigService cfg = new MapConfigService();
        cfg.set(FailsafeConfig.FAILSAFE_AUTOMATED_RESPONSE_ENABLED, true);
        CountingSink sink = new CountingSink();
        FailsafeResponseController c = controller(cfg, sink);
        assertTrue(c.onTrigger(FailsafeResponseType.PLAYER_NEARBY));
        cfg.set(FailsafeConfig.FAILSAFE_RESPONSE_ON_MENTION, false);
        c.tick();
        assertTrue(c.active());
        cfg.set(FailsafeConfig.FAILSAFE_RESPONSE_ON_PLAYER_NEARBY, false);
        c.tick();
        assertFalse(c.active());
    }

    @Test
    void schedulesWhenEnabledAndCooldownGuardsRepeat() {
        MapConfigService cfg = new MapConfigService();
        cfg.set(FailsafeConfig.FAILSAFE_AUTOMATED_RESPONSE_ENABLED, true);
        cfg.set(FailsafeConfig.FAILSAFE_RESPONSE_COOLDOWN_MS, 5000L);
        FailsafeResponseController c = controller(cfg, new CountingSink());
        assertTrue(c.onTrigger(FailsafeResponseType.BOSS_TELEPORTED), "enabled → schedules");
        assertTrue(c.active());
        // within cooldown → no re-schedule
        now = 2000L;
        assertFalse(c.onTrigger(FailsafeResponseType.BOSS_TELEPORTED), "cooldown blocks repeat");
        // after cooldown → schedules again
        now = 7000L;
        assertTrue(c.onTrigger(FailsafeResponseType.BOSS_TELEPORTED));
    }

    @Test
    void devRunBypassesGateAndCooldown() {
        MapConfigService cfg = new MapConfigService(); // master OFF
        CountingSink sink = new CountingSink();
        FailsafeResponseController c = controller(cfg, sink);
        c.devRun(FailsafeResponseType.MENTION);
        assertTrue(c.active(), "dev run schedules even with the master toggle off");
        c.devRunRoutine(FailsafeRoutine.LOOK_SWEEP);
        assertTrue(c.active());
    }

    @Test
    void resetAbortsAndReleasesInput() {
        MapConfigService cfg = new MapConfigService();
        CountingSink sink = new CountingSink();
        FailsafeResponseController c = controller(cfg, sink);
        c.devRunRoutine(FailsafeRoutine.MOVEMENT_SHUFFLE);
        c.tick();
        assertTrue(c.active());
        c.reset();
        assertFalse(c.active());
        assertTrue(sink.releaseAllCount >= 1, "reset released input");
    }

    @Test
    void devRunAdvancesToCompletionViaTick() {
        MapConfigService cfg = new MapConfigService();
        FailsafeResponseController c = controller(cfg, new CountingSink());
        c.devRunRoutine(FailsafeRoutine.LOOK_SWEEP);
        int guard = 0;
        while (c.active() && guard++ < 1000) {
            c.tick();
        }
        assertFalse(c.active(), "the routine completes via tick");
    }
}
