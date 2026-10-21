package dev.veltraph.client.navigation;
import dev.veltraph.client.world.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class GroundLocomotionMotionTest {
    private static final Vec3 A=new Vec3(.5,70,2.9),B=new Vec3(.5,71,4.5),V=new Vec3(0,-.0784,.117);
    @Test void finitePositiveNativeSpeedTraitMustBeWithinSupportedRange(){
        for(double speed:new double[]{Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY,0,-.1,.09,.14,.2}){
            assertFalse(GroundLocomotionMotion.speedAttribute(speed,false));assertFalse(GroundLocomotionMotion.speedAttribute(speed,true));
        }
        assertTrue(GroundLocomotionMotion.speedAttribute(.1,false));assertFalse(GroundLocomotionMotion.speedAttribute(.13,false));assertTrue(GroundLocomotionMotion.speedAttribute(.13,true));
    }
    @Test void supportedGroundedNoSprintTakeoffRequiresObservedFiniteVelocity(){
        assertTrue(GroundLocomotionMotion.takeoff(V,.1,true,false));
        assertFalse(GroundLocomotionMotion.takeoff(V,.1,false,false));assertFalse(GroundLocomotionMotion.takeoff(V,.1,true,true));
        for(Vec3 v:new Vec3[]{new Vec3(.2,0,0),new Vec3(0,.02,0),new Vec3(0,-.1,0),new Vec3(0,Double.NaN,0),new Vec3(Double.POSITIVE_INFINITY,0,0)})assertFalse(GroundLocomotionMotion.takeoff(v,.1,true,false));
        assertFalse(GroundLocomotionMotion.takeoff(V,.2,true,false));
    }
    @Test void ordinaryAirborneObservationRemainsWithinOriginalSweptMomentumEnvelope(){
        assertTrue(GroundLocomotionMotion.airborne(new Vec3(.5,70.75,3.5),new Vec3(0,.24,.12),A,B,.1));
        assertTrue(GroundLocomotionMotion.airborne(new Vec3(.5,71.1,4.5),new Vec3(0,-.1,.12),A,B,.1));
    }
    @Test void serverImpulseAndChangedSpeedCannotBorrowOriginalJumpProof(){
        Vec3 p=new Vec3(.5,70.75,3.5);
        assertFalse(GroundLocomotionMotion.airborne(p,new Vec3(.4,.24,.12),A,B,.1));
        assertFalse(GroundLocomotionMotion.airborne(p,new Vec3(0,.7,.12),A,B,.1));
        assertFalse(GroundLocomotionMotion.airborne(p,new Vec3(0,.24,.12),A,B,.2));
        assertFalse(GroundLocomotionMotion.airborne(p,new Vec3(0,Double.NaN,.12),A,B,.1));
    }
    @Test void actualAndProjectedMomentumCannotLeaveTakeoffLandingRectangle(){
        assertFalse(GroundLocomotionMotion.airborne(new Vec3(.69,70.75,3.5),new Vec3(0,.24,.12),A,B,.1));
        assertFalse(GroundLocomotionMotion.airborne(new Vec3(.65,70.75,3.5),new Vec3(.04,.24,.12),A,B,.1));
        assertFalse(GroundLocomotionMotion.airborne(new Vec3(.5,71.1,4.7),new Vec3(0,-.1,.12),A,B,.1));
        assertFalse(GroundLocomotionMotion.airborne(new Vec3(.5,71.27,3.5),new Vec3(0,0,.12),A,B,.1));
    }
    @Test void sprintNeedsFreshFiniteSupportedTraitAndCurrentVelocity(){
        assertTrue(GroundLocomotionMotion.sprint(new Vec3(0,-.0784,.155),.13));
        assertFalse(GroundLocomotionMotion.sprint(new Vec3(0,-.0784,.3),.13));
        assertFalse(GroundLocomotionMotion.sprint(new Vec3(0,.1,.155),.13));
        assertFalse(GroundLocomotionMotion.sprint(new Vec3(0,0,Double.NaN),.13));
        assertFalse(GroundLocomotionMotion.sprint(new Vec3(0,0,.155),-.13));
    }
}
