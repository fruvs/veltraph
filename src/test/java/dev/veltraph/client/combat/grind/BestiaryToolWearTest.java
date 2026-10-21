package dev.veltraph.client.combat.grind;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class BestiaryToolWearTest {
    @Test void ownIssuedAttackAllowsBoundedSwordAndAxeWear() {
        assertTrue(BestiaryToolWear.allowed(1,1,0,1,1,true));
        assertTrue(BestiaryToolWear.allowed(1,1,0,2,1,true));
        assertTrue(BestiaryToolWear.allowed(1,1,9,9,1,true));
    }
    @Test void noAttackCannotAuthorizeDamageAndCountOrComponentDriftNeverPasses() {
        assertFalse(BestiaryToolWear.allowed(1,1,0,1,0,true));
        assertFalse(BestiaryToolWear.allowed(1,1,0,3,1,true));
        assertFalse(BestiaryToolWear.allowed(2,1,0,1,1,true));
        assertFalse(BestiaryToolWear.allowed(1,1,0,1,1,false));
    }
    @Test void repairNegativeAndOverflowObservationsFailClosed() {
        assertFalse(BestiaryToolWear.allowed(1,1,2,1,3,true));
        assertFalse(BestiaryToolWear.allowed(1,1,-1,0,3,true));
        assertFalse(BestiaryToolWear.allowed(0,0,0,0,3,true));
        assertFalse(BestiaryToolWear.allowed(1,1,0,0,-1,true));
        assertTrue(BestiaryToolWear.allowed(1,1,0,Integer.MAX_VALUE,Long.MAX_VALUE,true));
    }
}
