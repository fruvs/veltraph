package dev.veltraph.client.navigation;
import dev.veltraph.client.world.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
/** Actual CORE5 numeric counterexample. Native collision/support acceptance remains a separate gate. */
class GroundJumpLandingForecastTest {
    private static final Vec3 ORIGIN=new Vec3(.5,70,3.262479782250013),TARGET=new Vec3(.5,71,4.5),ACTUAL=new Vec3(.5,71.02442408821369,4.57142936314265),VELOCITY=new Vec3(0,-.22768848754498797,.15666526098475375);
    @Test void supportedNormalFrictionTailFitsTheFreshTwoStepGroundCoast(){
        assertTrue(GroundJumpLandingForecast.supportedBrakingFriction((double).6F));
        double multiplier=(double).6F*(double).91F;assertTrue(multiplier/(1-multiplier)<2);
        for(double friction:new double[]{.98,.8,0,-.6,Double.NaN,Double.POSITIVE_INFINITY})assertFalse(GroundJumpLandingForecast.supportedBrakingFriction(friction));
    }
    @Test void actualCore5MotionFalseIsHorizontalTwoStepProjectionNotVerticalCap(){
        assertTrue(VELOCITY.y()>-.62);assertTrue(VELOCITY.y()<.421);assertTrue(VELOCITY.horizontalDistanceTo(new Vec3(0,VELOCITY.y(),0))<.16);
        assertTrue(GroundLocomotionMotion.airborneCurrent(ACTUAL,VELOCITY,ORIGIN,TARGET,.1));assertFalse(GroundLocomotionMotion.airborne(ACTUAL,VELOCITY,ORIGIN,TARGET,.1));
        assertEquals(4.884759885112158,ACTUAL.z()+VELOCITY.z()*2,1e-12);assertEquals(.034759885112158,ACTUAL.z()+VELOCITY.z()*2-(TARGET.z()+.35),1e-12);
    }
    @Test void ordinaryFloatNativeJumpStepEightMatchesActualHeightAndVelocity(){
        var v=GroundJumpLandingForecast.ordinaryVertical(8).orElseThrow();assertEquals(ACTUAL.y()-ORIGIN.y(),v.height(),1e-12);assertEquals(VELOCITY.y(),v.velocity(),1e-12);
        var apex=GroundJumpLandingForecast.ordinaryVertical(6).orElseThrow();assertTrue(apex.height()<1.26);
        assertTrue(GroundJumpLandingForecast.ordinaryVertical(0).isEmpty());assertTrue(GroundJumpLandingForecast.ordinaryVertical(13).isEmpty());
    }
    @Test void nextNativeStepCanOnlyForecastLandingAndFreshlyProvedGroundCoast(){
        var f=GroundJumpLandingForecast.forecast(ACTUAL,VELOCITY,ORIGIN,TARGET,.1,8).orElseThrow();
        assertEquals(TARGET.y(),f.landing().y());assertEquals(ACTUAL.z()+VELOCITY.z(),f.landing().z(),1e-12);
        assertTrue(f.landing().z()<TARGET.z()+.35);assertEquals(f.landing().z()+2*VELOCITY.z(),f.groundCoast().z(),1e-12);
        assertTrue(f.groundCoast().z()>TARGET.z()+.35); // Requires a new native supported ground sweep, never a larger flight cap.
    }
    @Test void incorrectNativeAgeHeightVelocityOrSpeedCannotReceiveLandingForecast(){
        assertTrue(GroundJumpLandingForecast.forecast(ACTUAL,VELOCITY,ORIGIN,TARGET,.1,7).isEmpty());
        assertTrue(GroundJumpLandingForecast.forecast(new Vec3(.5,71.1,ACTUAL.z()),VELOCITY,ORIGIN,TARGET,.1,8).isEmpty());
        assertTrue(GroundJumpLandingForecast.forecast(ACTUAL,new Vec3(0,-.4,VELOCITY.z()),ORIGIN,TARGET,.1,8).isEmpty());
        assertTrue(GroundJumpLandingForecast.forecast(ACTUAL,VELOCITY,ORIGIN,TARGET,.2,8).isEmpty());
        assertTrue(GroundJumpLandingForecast.forecast(ACTUAL,new Vec3(.3,VELOCITY.y(),VELOCITY.z()),ORIGIN,TARGET,.1,8).isEmpty());
    }
    @Test void forecastDoesNotAdmitBodyAlreadyOutsideOriginalAirborneRectangle(){
        assertTrue(GroundJumpLandingForecast.forecast(new Vec3(.5,ACTUAL.y(),4.9),VELOCITY,ORIGIN,TARGET,.1,8).isEmpty());
        assertTrue(GroundJumpLandingForecast.forecast(new Vec3(.7,ACTUAL.y(),ACTUAL.z()),VELOCITY,ORIGIN,TARGET,.1,8).isEmpty());
    }
}
