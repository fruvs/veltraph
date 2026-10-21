package dev.veltraph.client.combat.heal;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HealSourceContractsTest {
    private static HealSourceReadiness.Input in(int mana,int hp,boolean cooling,boolean force,boolean emergency) {
        return new HealSourceReadiness.Input(true,true,false,HealItemType.HYPERION,mana,hp,cooling,force,emergency,1000,500);
    }
    @Test void usesAbsoluteHealthNotPercentageAndUsesInclusiveThreshold() {
        assertEquals(1000,HealSourceStatSegment.rawHealth("1,000/20,000\uE010").orElseThrow());
        assertTrue(HealSourceReadiness.ready(in(300,1000,false,false,false)));
        assertFalse(HealSourceReadiness.ready(in(300,1001,false,false,false)));
        assertFalse(HealSourceReadiness.ready(in(300,1000,false,false,true)));
        assertTrue(HealSourceReadiness.ready(in(300,500,false,false,true)));
    }
    @Test void forceBypassesOnlyHealthNeverManaOrCooldown() {
        assertTrue(HealSourceReadiness.ready(in(300,99999,false,true,false)));
        assertFalse(HealSourceReadiness.ready(in(299,1,false,true,false)));
        assertFalse(HealSourceReadiness.ready(in(300,1,true,true,false)));
    }
    @Test void sourcePreconditionsAreRequiredEvenWhenForced() {
        var good=in(300,1,false,true,false);
        assertFalse(HealSourceReadiness.ready(new HealSourceReadiness.Input(false,true,false,good.selectedType(),300,1,false,true,false,1000,500)));
        assertFalse(HealSourceReadiness.ready(new HealSourceReadiness.Input(true,false,false,good.selectedType(),300,1,false,true,false,1000,500)));
        assertFalse(HealSourceReadiness.ready(new HealSourceReadiness.Input(true,true,true,good.selectedType(),300,1,false,true,false,1000,500)));
        assertFalse(HealSourceReadiness.ready(new HealSourceReadiness.Input(true,true,false,null,300,1,false,true,false,1000,500)));
    }
    @Test void manaIncludesOverflowAndNeverUsesPercentOrMaximumAsCostBudget() {
        assertEquals(325,HealSourceStatSegment.mana("250/1,000\uE003 75\uE017ⓩ").orElseThrow());
        assertEquals(299,HealSourceStatSegment.mana("299/100,000\uE003").orElseThrow());
        assertFalse(HealSourceReadiness.ready(in(299,1,false,true,false)));
        assertEquals(1234,HealSourceStatSegment.rawHealth("1,234/9,999\uE010").orElseThrow());
    }
    @Test void malformedSegmentReturnsNoObservation() {
        assertTrue(HealSourceStatSegment.rawHealth("unknown").isEmpty());
        assertTrue(HealSourceStatSegment.mana("250/1,000\uE003 bad\uE017").isEmpty());
        assertTrue(HealSourceStatSegment.mana(null).isEmpty());
    }
    @Test void offNeverRepeatsAndLazyRequiresNormalReadiness() {
        var repeat=new HealSourceRepeat();
        assertEquals(HealSourceRepeat.Decision.FINISH,repeat.afterUse(HealStackingMode.OFF,true));
        assertEquals(0,repeat.repeatDecisionCount());
        assertEquals(HealSourceRepeat.Decision.FINISH,repeat.afterUse(HealStackingMode.LAZY,false));
        assertEquals(0,repeat.repeatDecisionCount());
    }
    @Test void lazyHasExactlyOneRepeatDecisionThenFinishesAndResetsPerAttempt() {
        var repeat=new HealSourceRepeat();
        assertEquals(HealSourceRepeat.Decision.HEAL_AGAIN,repeat.afterUse(HealStackingMode.LAZY,true));
        assertEquals(HealSourceRepeat.Decision.FINISH,repeat.afterUse(HealStackingMode.LAZY,true));
        repeat.beginAttempt();
        assertEquals(HealSourceRepeat.Decision.HEAL_AGAIN,repeat.afterUse(HealStackingMode.LAZY,true));
    }
    @Test void spamShortCircuitsNormalReadinessButStillHasOneRepeatLimit() {
        var repeat=new HealSourceRepeat();
        assertEquals(HealSourceRepeat.Decision.HEAL_AGAIN,repeat.afterUse(HealStackingMode.SPAM,false));
        assertEquals(HealSourceRepeat.Decision.FINISH,repeat.afterUse(HealStackingMode.SPAM,false));
        assertEquals(2,repeat.repeatDecisionCount());
    }
}
