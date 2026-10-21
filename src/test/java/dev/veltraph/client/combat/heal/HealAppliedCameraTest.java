package dev.veltraph.client.combat.heal;

import dev.veltraph.client.config.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HealAppliedCameraTest {
    static final class Port implements HealServices {
        boolean ready, restored, valid=true; int casts, turns, returns, discards;
        public boolean available(){return true;} public boolean hasHyperion(){return true;}
        public double healthPct(){return 20;} public boolean clearAbove(){return true;}
        public void lookDown(){turns++;} public void castHyperion(){casts++;}
        public void restoreLook(){returns++;} public void discardSnapshot(){discards++;}
        public boolean cameraReady(){return ready;} public boolean restorationComplete(){return restored;}
        public boolean custodyValid(){return valid;}
    }
    static CombatAutoHeal healer(Port p){var c=new MapConfigService();c.set(GhostConfig.GHOST_AUTO_HEAL_ENABLED,true);return new CombatAutoHeal(c,p);}
    @Test void withheldApplicationCannotCast(){var p=new Port();var h=healer(p);for(int i=0;i<20;i++)h.tick(true);assertEquals(1,p.turns);assertEquals(0,p.casts);assertEquals(HealState.LOOK_DOWN,h.state());p.ready=true;h.tick(true);h.tick(true);assertEquals(1,p.casts);}
    @Test void replacementCustodyDiscardsWithoutRestoreOrUse(){var p=new Port();var h=healer(p);h.tick(true);p.valid=false;p.ready=true;h.tick(true);h.tick(true);assertEquals(0,p.casts);assertEquals(0,p.returns);assertTrue(p.discards>0);p.valid=true;for(int i=0;i<10;i++)h.tick(true);assertEquals(1,p.turns);assertFalse(h.shouldAcquireControl(true));}
    @Test void restorationHoldsSoleOwnerUntilApplied(){var h=new HyperionAutoHeal();var t=new HealTuning(50,25,0,3000,15000);h.tick(new HealInputs(true,true,true,20,true,0),t);h.tick(new HealInputs(true,true,true,20,true,1),t);h.tick(new HealInputs(true,true,true,20,true,2),t);h.tick(new HealInputs(true,true,true,20,true,3),t);assertEquals(HealState.RESTORE,h.state());h.tick(new HealInputs(true,true,true,20,true,4,true,false),t);assertEquals(HealState.RESTORE,h.state());h.tick(new HealInputs(true,true,true,20,true,5,true,true),t);assertEquals(HealState.IDLE,h.state());}
}
