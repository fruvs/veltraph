package dev.veltraph.client.combat.heal;

import dev.veltraph.client.config.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HealHyperionWatchdogTest {
    @Test void sentinelIsUnarmedAndResetIsNotAnElapsedDuration(){
        var w=new HealHyperionWatchdog();assertEquals(HealHyperionWatchdog.Decision.WAIT,w.read(100000,true,true,false));
        w.emitted(100000);w.reset();assertEquals(HealHyperionWatchdog.Decision.WAIT,w.read(999999,true,true,false));
    }
    @Test void hyperionPermissionBufferWaitsAtFiveHundredAndTimesOutOnlyAboveIt(){
        var w=new HealHyperionWatchdog();w.emitted(100000);
        assertEquals(HealHyperionWatchdog.Decision.WAIT,w.read(100500,true,true,false));
        assertEquals(HealHyperionWatchdog.Decision.TIMED_OUT,w.read(100501,true,true,false));
        assertEquals(HealHyperionWatchdog.Decision.WAIT,w.read(100501,true,false,false));
    }
    @Test void nonHyperionOrPermissionFalseDoesNotUseWatchdogAndNativeEventCanFinishEarly(){
        var w=new HealHyperionWatchdog();w.emitted(100000);
        assertEquals(HealHyperionWatchdog.Decision.CONTINUE,w.read(100001,false,true,false));
        assertEquals(HealHyperionWatchdog.Decision.CONTINUE,w.read(100001,true,true,true));
    }
    static final class Port implements HealServices {
        int uses,restores,cancels;boolean conditional=true,position;
        public boolean available(){return true;}public boolean hasHyperion(){return true;}public double healthPct(){return 10;}
        public boolean clearAbove(){return true;}public void lookDown(){}public void castHyperion(){uses++;}public void restoreLook(){restores++;}
        public boolean usesSourceReadiness(){return true;}public boolean sourceReady(boolean a,boolean b){return true;}
        public boolean sourceHyperionLookDown(){return conditional;}public boolean sourcePostUsePositionReceived(){return position;}
        public void cancelOwnedHealRotation(){cancels++;}
        public int sampleSourcePhaseDelay(){return 0;}
    }
    @Test void productiveSourceWaitIgnoresLegacyZeroPostWaitUntilConditionalWatchdogOrEvent(){
        var time=new java.util.concurrent.atomic.AtomicLong(100000);var c=new MapConfigService();c.set(GhostConfig.GHOST_AUTO_HEAL_ENABLED,true);
        c.set(GhostConfig.AUTO_HEAL_STACKING,HealStackingMode.OFF);c.set(GhostConfig.GHOST_HEAL_POST_CAST_WAIT_MS,0L);
        var p=new Port();var h=new CombatAutoHeal(c,p,new HealSourceEndLedger(),time::get);
        h.tick(true);h.tick(true);h.tick(true);assertEquals(1,p.uses);
        time.set(100500);h.tick(true);assertEquals(HealState.WAIT,h.state());assertEquals(0,p.restores);
        time.set(100501);h.tick(true);assertEquals(HealState.WAIT,h.state());assertEquals(1,p.cancels);assertEquals(0,p.restores);
        h.tick(true);assertEquals(HealState.WAIT,h.state());h.tick(true);assertEquals(HealState.RESTORE,h.state());assertEquals(1,p.cancels);assertEquals(1,p.restores);
    }
    @Test void productivePermissionFalseContinuesWithoutAUniversalFiveHundredDelay(){
        var time=new java.util.concurrent.atomic.AtomicLong(100000);var c=new MapConfigService();c.set(GhostConfig.GHOST_AUTO_HEAL_ENABLED,true);
        c.set(GhostConfig.AUTO_HEAL_STACKING,HealStackingMode.OFF);c.set(GhostConfig.GHOST_HEAL_POST_CAST_WAIT_MS,9999L);
        var p=new Port();p.conditional=false;var h=new CombatAutoHeal(c,p,new HealSourceEndLedger(),time::get);
        h.tick(true);h.tick(true);h.tick(true);time.incrementAndGet();h.tick(true);
        assertEquals(HealState.WAIT,h.state());h.tick(true);assertEquals(HealState.WAIT,h.state());h.tick(true);
        assertEquals(HealState.RESTORE,h.state());assertEquals(0,p.cancels);assertEquals(1,p.restores);
    }
}
