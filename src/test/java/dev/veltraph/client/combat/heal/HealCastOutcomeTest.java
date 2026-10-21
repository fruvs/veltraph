package dev.veltraph.client.combat.heal;

import dev.veltraph.client.combat.ghost.GhostHealReturn;
import dev.veltraph.client.config.*;
import dev.veltraph.client.world.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HealCastOutcomeTest {
    private static final class Port implements HealServices {
        HealCastOutcome outcome=HealCastOutcome.REFUSED_COOLDOWN;
        int invocations, uses, restores;
        public boolean available(){return true;} public boolean hasHyperion(){return true;}
        public double healthPct(){return 30;} public boolean clearAbove(){return true;}
        public void lookDown(){} public void castHyperion(){throw new AssertionError("Outcome seam was bypassed");}
        public HealCastOutcome castWithOutcome(){invocations++;if(outcome==HealCastOutcome.EMITTED)uses++;return outcome;}
        public void restoreLook(){restores++;}
    }
    @Test void nativeCooldownRefusalRestoresWithoutCountPostUseWaitOrArmingGlobalCooldown() {
        var config=new MapConfigService();config.set(GhostConfig.GHOST_AUTO_HEAL_ENABLED,true);
        config.set(GhostConfig.GHOST_HEAL_POST_CAST_WAIT_MS,60000L);
        var port=new Port();var heal=new CombatAutoHeal(config,port);
        heal.tick(true);heal.tick(true);heal.tick(true);
        assertEquals(1,port.invocations);assertEquals(0,port.uses);assertEquals(0,heal.healCount());
        heal.tick(true);assertEquals(1,port.restores);assertEquals(HealState.RESTORE,heal.state());
        heal.tick(true);assertEquals(HealState.IDLE,heal.state());assertTrue(heal.shouldAcquireControl(true));
    }
    @Test void nativeCustodyRefusalAlsoCannotReportCast() {
        var port=new Port();port.outcome=HealCastOutcome.REFUSED_CUSTODY;
        var config=new MapConfigService();config.set(GhostConfig.GHOST_AUTO_HEAL_ENABLED,true);
        var heal=new CombatAutoHeal(config,port);
        heal.tick(true);heal.tick(true);heal.tick(true);heal.tick(true);
        assertEquals(0,heal.healCount());assertEquals(0,port.uses);assertEquals(1,port.restores);
    }
    @Test void simultaneousSharedHandlerUseRefusesGhostAttemptAndRestoresThenLeavesParentReadyToContinue() {
        var ledger=new HealUseLedger();var ghostSelector=new HealItemSelector(ledger);var sharedSelector=new HealItemSelector(ledger);
        var stack=new HealItemSelector.Item("minecraft:iron_sword","Hyperion",false);
        assertTrue(ghostSelector.select(java.util.List.of(stack),HealStackingMode.OFF,100000).isPresent());
        var home=new Vec3(.5,70,.5);var ghost=new GhostHealReturn();
        ghost.start(new GhostHealReturn.Pose(home,30,5,2,40,0),100000);
        assertEquals(GhostHealReturn.Action.CAST,ghost.tick(home,30,80,40,0,true,true,0,100001,1200,15000));
        sharedSelector.recordEmittedUse(HealItemType.HYPERION,100001);
        assertTrue(ghostSelector.cooling(HealItemType.HYPERION,100002));
        ghost.refuseCast();
        assertEquals(100001,ledger.lastEmittedUse(HealItemType.HYPERION));
        assertEquals(GhostHealReturn.Action.RESTORE_VIEW,ghost.tick(home,30,80,40,0,true,true,0,100003,1200,15000));
        assertEquals(GhostHealReturn.Action.COMPLETE,ghost.tick(home,30,5,40,0,true,true,0,100004,1200,15000));
        assertTrue(ghost.castRefused());
        assertTrue(ghostSelector.select(java.util.List.of(stack),HealStackingMode.OFF,100005).isEmpty());
        assertTrue(ghostSelector.select(java.util.List.of(stack),HealStackingMode.OFF,105501).isPresent());
    }
}
