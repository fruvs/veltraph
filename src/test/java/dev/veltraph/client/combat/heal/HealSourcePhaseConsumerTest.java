package dev.veltraph.client.combat.heal;

import dev.veltraph.client.config.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HealSourcePhaseConsumerTest {
    static final class Port implements HealServices {
        long tick;int uses,slots,views,samples,discards;boolean valid=true;Object world=new Object();
        final Object player=new Object(),connection=new Object();
        public boolean available(){return true;}public boolean hasHyperion(){return true;}public double healthPct(){return 10;}
        public boolean clearAbove(){return true;}public void lookDown(){}public void castHyperion(){uses++;}public void restoreLook(){views++;}
        public boolean usesSourceReadiness(){return true;}public boolean sourceReady(boolean a,boolean b){return true;}
        public long sourceClientTick(){return tick;}public int sampleSourcePhaseDelay(){samples++;return 1;}
        public SourceTickScope sourceTickScope(){return new SourceTickScope(player,world,connection);}
        public boolean custodyValid(){return valid;}public void discardSnapshot(){discards++;}
        public boolean restoreSourceSavedSlot(){slots++;return true;}
    }
    @Test void nativeDuplicateTickCannotConsumePhaseAndSlotRestorePrecedesLaterEndView(){
        var c=new MapConfigService();c.set(GhostConfig.GHOST_AUTO_HEAL_ENABLED,true);c.set(GhostConfig.AUTO_HEAL_STACKING,HealStackingMode.OFF);
        var end=new HealSourceEndLedger();var p=new Port();var h=new CombatAutoHeal(c,p,end,()->100000);
        h.tick(true);h.tick(true);assertEquals(HealState.LOOK_DOWN,h.state());
        p.tick=1;h.tick(true);p.tick=2;h.tick(true);assertEquals(1,p.uses);
        p.tick=3;h.tick(true);h.tick(true);assertEquals(1,p.samples);assertEquals(0,p.slots);
        p.tick=4;h.tick(true);assertEquals(0,p.slots);
        p.tick=5;h.tick(true);assertEquals(1,p.slots);assertEquals(0,end.endedAt());assertEquals(0,p.views);assertEquals(2,p.samples);
        h.tick(true);assertEquals(0,end.endedAt());
        p.tick=6;h.tick(true);assertEquals(0,end.endedAt());
        p.tick=7;h.tick(true);assertEquals(100000,end.endedAt());assertEquals(1,p.views);assertEquals(1,p.uses);
    }
    @Test void invalidCustodyOnDuplicateFrameIsReleasedBeforeCounterDedup(){
        var c=new MapConfigService();c.set(GhostConfig.GHOST_AUTO_HEAL_ENABLED,true);var p=new Port();
        var h=new CombatAutoHeal(c,p,new HealSourceEndLedger(),()->100000);h.tick(true);
        p.valid=false;h.tick(true);assertEquals(HealState.IDLE,h.state());assertEquals(1,p.discards);assertEquals(0,p.uses);
    }
    @Test void sameCountNewWorldDoesNotSuppressFreshAttemptAfterOwnedReset(){
        var c=new MapConfigService();c.set(GhostConfig.GHOST_AUTO_HEAL_ENABLED,true);var p=new Port();
        var h=new CombatAutoHeal(c,p,new HealSourceEndLedger(),()->100000);h.tick(true);h.reset();
        p.world=new Object();h.tick(true);assertEquals(HealState.LOOK_DOWN,h.state());assertEquals(0,p.uses);
    }
}
