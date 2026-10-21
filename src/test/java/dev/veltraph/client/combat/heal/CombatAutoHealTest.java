package dev.veltraph.client.combat.heal;

import dev.veltraph.client.config.GhostConfig;
import dev.veltraph.client.config.MapConfigService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** Orchestrator glue tests (the FSM itself is covered by HyperionAutoHealTest). */
class CombatAutoHealTest {

    @Test
    void predictsAcquisitionWithoutCapturingAndYieldsUntilDedicatedOwnerReleases() {
        var cfg=new MapConfigService();cfg.set(GhostConfig.GHOST_AUTO_HEAL_ENABLED,true);
        var s=new FakeHeal();s.hp=30;var h=heal(cfg,s);
        assertTrue(h.shouldAcquireControl(true));assertFalse(h.shouldAcquireControl(false));
        assertEquals(HealState.IDLE,h.state());assertEquals(0,s.lookDown);
        h.tick(true,true);assertEquals(HealState.IDLE,h.state());assertEquals(0,s.cast);
        h.tick(true,false);assertTrue(h.ownsControl());assertFalse(h.shouldAcquireControl(true));
        h.tick(true,true);assertEquals(HealState.LOOK_DOWN,h.state());
        cfg.set(GhostConfig.GHOST_AUTO_HEAL_ENABLED,false);h.tick(true,false);
        assertFalse(h.ownsControl());assertEquals(1,s.restore);
    }

    @Test
    void predictionRejectsInvalidHealthAndMissingTool() {
        var cfg=new MapConfigService();cfg.set(GhostConfig.GHOST_AUTO_HEAL_ENABLED,true);
        var s=new FakeHeal();var h=heal(cfg,s);
        for(double hp:new double[]{Double.NaN,Double.POSITIVE_INFINITY,-1,101,80}){s.hp=hp;assertFalse(h.shouldAcquireControl(true));h.tick(true);assertFalse(h.ownsControl());}
        s.hp=30;s.hyperion=false;assertFalse(h.shouldAcquireControl(true));
        s.hyperion=true;s.available=false;assertFalse(h.shouldAcquireControl(true));
    }

    private static final class FakeHeal implements HealServices {
        boolean available = true;
        boolean hyperion = true;
        double hp = 100;
        boolean clear = true;
        int lookDown, cast, restore;
        @Override public boolean available() {
            return available;
        }
        @Override public boolean hasHyperion() {
            return hyperion;
        }
        @Override public double healthPct() {
            return hp;
        }
        @Override public boolean clearAbove() {
            return clear;
        }
        @Override public void lookDown() {
            lookDown++;
        }
        @Override public void castHyperion() {
            cast++;
        }
        @Override public void restoreLook() {
            restore++;
        }
    }

    private CombatAutoHeal heal(MapConfigService cfg, FakeHeal s) {
        return new CombatAutoHeal(cfg, s);
    }

    @Test
    void disabledNeverLooksDownOrCasts() {
        MapConfigService cfg = new MapConfigService();
        FakeHeal s = new FakeHeal();
        s.hp = 30;
        CombatAutoHeal h = heal(cfg, s);
        h.tick(true);
        assertEquals(0, s.lookDown);
        assertEquals(0, s.cast);
    }

    @Test
    void lowHealthLooksDownThenCasts() {
        MapConfigService cfg = new MapConfigService();
        cfg.set(GhostConfig.GHOST_AUTO_HEAL_ENABLED, true);
        FakeHeal s = new FakeHeal();
        s.hp = 30; // <= 50 activate
        CombatAutoHeal h = heal(cfg, s);
        h.tick(true); // IDLE -> LOOK_DOWN
        assertEquals(1, s.lookDown);
        h.tick(true); // LOOK_DOWN -> CHECK_CLEAR
        h.tick(true); // CHECK_CLEAR (clear) -> CAST
        assertEquals(1, s.cast);
    }

    @Test
    void noHyperionDoesNothing() {
        MapConfigService cfg = new MapConfigService();
        cfg.set(GhostConfig.GHOST_AUTO_HEAL_ENABLED, true);
        FakeHeal s = new FakeHeal();
        s.hp = 30;
        s.hyperion = false;
        CombatAutoHeal h = heal(cfg, s);
        h.tick(true);
        assertEquals(0, s.lookDown);
    }

    @Test
    void notInSkyBlockDoesNothing() {
        MapConfigService cfg = new MapConfigService();
        cfg.set(GhostConfig.GHOST_AUTO_HEAL_ENABLED, true);
        FakeHeal s = new FakeHeal();
        s.hp = 30;
        CombatAutoHeal h = heal(cfg, s);
        h.tick(false); // not in SkyBlock -> available gated off
        assertEquals(0, s.lookDown);
    }
}
