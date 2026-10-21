package dev.veltraph.client.combat.heal;

import dev.veltraph.client.config.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HealSourceConsumerTest {
    private static final class Port implements HealServices {
        boolean sourceReady=true,repeatReady=true,ready=true,delayRepeatCallback;int looks,uses,restores,repeats,normalReadinessCalls;
        public boolean available(){return true;}public boolean hasHyperion(){return true;}
        public double healthPct(){return 90;}public boolean clearAbove(){return true;}
        public boolean usesSourceReadiness(){return true;}
        public boolean sourceReady(boolean force,boolean emergency){if(!force)normalReadinessCalls++;return force||sourceReady;}
        public boolean prepareSourceRepeat(){repeats++;if(delayRepeatCallback)ready=false;return repeatReady;}
        public boolean cameraReady(){return ready;}
        public int sampleSourcePhaseDelay(){return 0;}
        public void lookDown(){looks++;}public void castHyperion(){uses++;}public void restoreLook(){restores++;}
    }
    private static CombatAutoHeal create(MapConfigService config,Port p,HealStackingMode mode){
        config.set(GhostConfig.GHOST_AUTO_HEAL_ENABLED,true);config.set(GhostConfig.AUTO_HEAL_STACKING,mode);
        config.set(GhostConfig.GHOST_HEAL_POST_CAST_WAIT_MS,0L);return new CombatAutoHeal(config,p,new HealSourceEndLedger(),()->100000L);
    }
    @Test void sourceRawReadinessControlsAcquisitionEvenWhenLegacyPercentWouldRejectIt(){
        var p=new Port();var c=new MapConfigService();var h=create(c,p,HealStackingMode.OFF);
        assertTrue(h.shouldAcquireControl(true));h.tick(true);assertEquals(1,p.looks);
        h.tick(true);h.tick(true);assertEquals(1,p.uses);assertEquals(1,h.healCount());
    }
    @Test void lazyUsesOneRepeatInsideSameSnapshotThenRestores(){
        var p=new Port();var c=new MapConfigService();var h=create(c,p,HealStackingMode.LAZY);
        for(int i=0;i<12&&p.restores==0;i++)h.tick(true);
        assertEquals(2,p.uses);assertEquals(1,p.looks);assertEquals(1,p.repeats);assertEquals(1,p.restores);
    }
    @Test void spamDecisionCanRepeatWithRecoveredHealthButNeedsRepeatPreparation(){
        var p=new Port();var c=new MapConfigService();var h=create(c,p,HealStackingMode.SPAM);
        h.tick(true);h.tick(true);h.tick(true);p.sourceReady=false;p.repeatReady=false;
        for(int i=0;i<5&&p.restores==0;i++)h.tick(true);assertEquals(1,p.repeats);assertEquals(1,p.uses);assertEquals(1,p.restores);
    }
    @Test void disableWhileWaitingCannotSelectOrPrepareAnotherHeal(){
        var p=new Port();var c=new MapConfigService();var h=create(c,p,HealStackingMode.SPAM);
        h.tick(true);h.tick(true);h.tick(true);c.set(GhostConfig.GHOST_AUTO_HEAL_ENABLED,false);h.tick(true);
        assertEquals(0,p.repeats);assertEquals(1,p.uses);assertEquals(1,p.restores);
    }
    @Test void productiveStatusUsesNativeRawHealthUnitsAndRetainsLegacyPercentageStatus(){
        var c=new MapConfigService();var p=new Port();var nativeHeal=create(c,p,HealStackingMode.OFF);
        assertTrue(nativeHeal.status().contains("activate<=1500 HP (emergency<=800 HP)"));
        assertFalse(nativeHeal.status().contains("50%"));
        var legacy=new CombatAutoHeal(c,HealServices.NONE);
        assertTrue(legacy.status().contains("activate<=50% (emergency<=25%)"));
    }
    @Test void productiveSpamShortCircuitsNormalReadinessCallWhileLazyEvaluatesItOnce(){
        for(var mode:new HealStackingMode[]{HealStackingMode.SPAM,HealStackingMode.LAZY}){
            var p=new Port();var c=new MapConfigService();var h=create(c,p,mode);
            h.tick(true);h.tick(true);h.tick(true);p.normalReadinessCalls=0;
            for(int i=0;i<4&&p.repeats==0;i++)h.tick(true);
            assertEquals(mode==HealStackingMode.SPAM?0:1,p.normalReadinessCalls);
            assertEquals(1,p.repeats);assertEquals(1,p.looks);
        }
    }
    @Test void repeatCannotAdvanceLookDownUsingPreviousCastsCompletedCallback(){
        var p=new Port();p.delayRepeatCallback=true;var c=new MapConfigService();var h=create(c,p,HealStackingMode.LAZY);
        h.tick(true);h.tick(true);h.tick(true);assertEquals(1,p.uses);
        for(int i=0;i<4&&p.repeats==0;i++)h.tick(true);assertEquals(1,p.repeats);assertEquals(HealState.LOOK_DOWN,h.state());assertEquals(1,p.uses);
        for(int tick=0;tick<4;tick++){h.tick(true);assertEquals(HealState.LOOK_DOWN,h.state());assertEquals(1,p.uses);}
        p.ready=true;h.tick(true);assertEquals(HealState.CHECK_CLEAR,h.state());assertEquals(1,p.uses);
        h.tick(true);assertEquals(2,p.uses);assertEquals(HealState.WAIT,h.state());
        assertEquals(1,p.looks,"Repeat must retain original owned snapshot");
    }
    @Test void sharedLedgerBlocksSecondHandlerAtTwoHundredAllowsTwoHundredOneAndResetDoesNotWrite(){
        var ledger=new HealSourceEndLedger();var time=new java.util.concurrent.atomic.AtomicLong(100000);
        var c=new MapConfigService();c.set(GhostConfig.GHOST_AUTO_HEAL_ENABLED,true);
        c.set(GhostConfig.AUTO_HEAL_STACKING,HealStackingMode.OFF);c.set(GhostConfig.GHOST_HEAL_POST_CAST_WAIT_MS,0L);
        var first=new CombatAutoHeal(c,new Port(),ledger,time::get);
        first.tick(true);first.tick(true);first.tick(true);first.tick(true);first.tick(true);first.tick(true);
        assertEquals(100000,ledger.endedAt());
        var secondPort=new Port();var second=new CombatAutoHeal(c,secondPort,ledger,time::get);
        time.set(100200);assertFalse(second.shouldAcquireControl(true));second.tick(true);
        assertEquals(HealState.IDLE,second.state());assertEquals(0,secondPort.looks);
        first.reset();second.reset();assertEquals(100000,ledger.endedAt());
        time.set(100201);assertTrue(second.shouldAcquireControl(true));second.tick(true);
        assertEquals(HealState.LOOK_DOWN,second.state());assertEquals(1,secondPort.looks);
        assertEquals(100000,ledger.endedAt());
    }
    @Test void defaultProcessLedgerReferenceIsStableAndReadsNeverReplaceIt(){
        var original=SharedHealSourceEnd.processLedger();
        assertSame(original,SharedHealSourceEnd.processLedger());
        SharedHealSourceEnd.mayAcquire(100000);
        assertSame(original,SharedHealSourceEnd.processLedger());
    }
}
