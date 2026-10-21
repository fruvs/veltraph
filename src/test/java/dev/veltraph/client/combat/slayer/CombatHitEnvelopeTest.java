package dev.veltraph.client.combat.slayer;
import dev.veltraph.client.world.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class CombatHitEnvelopeTest {
    private final Vec3 eye=new Vec3(0,1.6,0),min=new Vec3(-.3,0,2),max=new Vec3(.3,2,2.6);
    @Test void anyReachableBodyEdgeIsValidWithoutCenterLock(){
        assertTrue(CombatHitEnvelope.intersects(eye,new Vec3(.28,-.2,2),min,max,3));
        assertTrue(CombatHitEnvelope.intersects(eye,new Vec3(-.28,.3,2),min,max,3));
    }
    @Test void newViewOutsideCachedBodyCannotAuthorizeAttack(){
        assertFalse(CombatHitEnvelope.intersects(eye,new Vec3(.5,0,2),min,max,3));
        assertFalse(CombatHitEnvelope.intersects(eye,new Vec3(0,1,2),min,max,3));
        assertFalse(CombatHitEnvelope.intersects(eye,new Vec3(0,0,-1),min,max,3));
    }
    @Test void eyeInsideActualVolumeIsReachableRatherThanAnEmptyEntryClip(){
        assertTrue(CombatHitEnvelope.intersects(new Vec3(0,1.6,2.3),new Vec3(1,0,0),min,max,3));
    }
    @Test void normalizationDoesNotExtendReachAndInvalidInputsFailClosed(){
        assertFalse(CombatHitEnvelope.intersects(eye,new Vec3(0,0,100),min,max,1.9));
        assertFalse(CombatHitEnvelope.intersects(eye,new Vec3(0,0,0),min,max,3));
        assertFalse(CombatHitEnvelope.intersects(eye,new Vec3(Double.NaN,0,1),min,max,3));
    }
}
