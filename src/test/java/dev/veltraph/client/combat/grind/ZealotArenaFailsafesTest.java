package dev.veltraph.client.combat.grind;

import dev.veltraph.client.config.ZealotConfig;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static dev.veltraph.client.combat.grind.ZealotArenaFailsafes.Decision.*;

class ZealotArenaFailsafesTest {
    private static ZealotArenaFailsafes.Decision step(ZealotArenaFailsafes p,long tick,boolean farming,boolean dragonEnabled,boolean dragon,boolean displaced) {
        return p.tick(tick,farming,dragonEnabled,dragon,true,true,false,true,displaced?2:0);
    }
    @Test void recoveredDefaultsAreIndependentAndEnabled() {
        assertTrue(ZealotConfig.PRECURSOR_DRAGON_FAILSAFE.defaultValue());
        assertTrue(ZealotConfig.PRECURSOR_NOT_AT_POS_FAILSAFE.defaultValue());
    }
    @Test void dragonDoesNotInspectOrAdvanceOutsideFarmingOrWhenDisabled() {
        var p=new ZealotArenaFailsafes();
        for(int i=0;i<20;i++)assertEquals(NONE,step(p,i,false,true,true,false));
        for(int i=20;i<40;i++)assertEquals(NONE,step(p,i,true,false,true,false));
        assertEquals(0,p.dragonInvocations());
    }
    @Test void dragonObservationIsOnlyActionableOnEighthEligibleInvocation() {
        var p=new ZealotArenaFailsafes();
        for(int i=1;i<=7;i++)assertEquals(NONE,step(p,i,true,true,true,false));
        assertEquals(DRAGON_STOP,step(p,8,true,true,true,false));assertEquals(0,p.dragonInvocations());
    }
    @Test void absentDragonStillResetsCadenceAndDoesNotStop() {
        var p=new ZealotArenaFailsafes();
        for(int i=1;i<=8;i++)assertEquals(NONE,step(p,i,true,true,false,false));
        assertEquals(0,p.dragonInvocations());
        for(int i=9;i<=15;i++)assertEquals(NONE,step(p,i,true,true,true,false));
        assertEquals(DRAGON_STOP,step(p,16,true,true,true,false));
    }
    @Test void disablingDragonPreservesPartialEligibleCadence() {
        var p=new ZealotArenaFailsafes();for(int i=1;i<=4;i++)step(p,i,true,true,false,false);
        for(int i=5;i<=12;i++)assertEquals(NONE,step(p,i,true,false,true,false));
        for(int i=13;i<=15;i++)assertEquals(NONE,step(p,i,true,true,true,false));
        assertEquals(DRAGON_STOP,step(p,16,true,true,true,false));
    }
    @Test void displacementSchedulesOneStopAfterExactlyFifteenTicksEvenIfPositionRecovers() {
        var p=new ZealotArenaFailsafes();assertEquals(DISPLACEMENT_PENDING,step(p,10,true,false,false,true));
        for(int i=11;i<25;i++)assertEquals(DISPLACEMENT_PENDING,step(p,i,true,false,false,false));
        assertEquals(DISPLACEMENT_STOP,step(p,25,true,false,false,false));
    }
    @Test void displacementRespectsIndependentOptionGroundAnchorAndSuspensionGates() {
        var p=new ZealotArenaFailsafes();
        assertEquals(NONE,p.tick(1,true,false,false,false,true,false,true,2));
        assertEquals(NONE,p.tick(2,true,false,false,true,false,false,true,2));
        assertEquals(NONE,p.tick(3,true,false,false,true,true,true,true,2));
        assertEquals(NONE,p.tick(4,true,false,false,true,true,false,false,2));
        assertEquals(NONE,p.tick(5,true,false,false,true,true,false,true,1.999));
        assertEquals(DISPLACEMENT_PENDING,p.tick(6,true,false,false,true,true,false,true,2));
    }
    @Test void optionAndStageChangesDoNotCancelAlreadyScheduledStopButExplicitResetDoes() {
        var p=new ZealotArenaFailsafes();step(p,1,true,false,false,true);
        assertEquals(DISPLACEMENT_STOP,p.tick(16,false,false,false,false,false,true,false,0));
        p.reset();assertFalse(p.pending());assertEquals(NONE,step(p,17,true,false,false,false));
    }
    @Test void repeatedDisplacementCannotPushOriginalDeadlineLater() {
        var p=new ZealotArenaFailsafes();for(int i=1;i<16;i++)assertEquals(DISPLACEMENT_PENDING,step(p,i,true,false,false,true));
        assertEquals(DISPLACEMENT_STOP,step(p,16,true,false,false,true));
    }
    @Test void activationResetClearsBothPendingStopAndPartialDragonCadence() {
        var p=new ZealotArenaFailsafes();for(int i=1;i<=4;i++)step(p,i,true,true,false,false);step(p,5,true,false,false,true);p.reset();
        assertFalse(p.pending());assertEquals(0,p.dragonInvocations());
        for(int i=6;i<13;i++)assertEquals(NONE,step(p,i,true,true,true,false));
        assertEquals(DRAGON_STOP,step(p,13,true,true,true,false));
    }
}
