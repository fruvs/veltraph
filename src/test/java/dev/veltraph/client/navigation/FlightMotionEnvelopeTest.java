package dev.veltraph.client.navigation;
import dev.veltraph.client.world.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class FlightMotionEnvelopeTest {
 private static final Vec3 P=new Vec3(0,80,0);
 private static FlightController.Output out(boolean horizontal,boolean up,boolean down){return new FlightController.Output(FlightController.State.FLYING,0,horizontal,false,false,false,up,down);}
 @Test void nativeDefaultAirDecayRetainsFloatConstant(){assertEquals((double).91f,FlightMotionEnvelope.horizontalDecay(1));}
 @Test void observedNativeDragModifierChangesPassiveEnvelope(){assertTrue(FlightMotionEnvelope.horizontalDecay(.5)>FlightMotionEnvelope.horizontalDecay(1));}
 @Test void nativeAllHeadingHorizontalImpulseFitsBound(){var b=FlightMotionEnvelope.bounds(P,new Vec3(0,0,0),out(true,false,false),.05f,.1f,.1,1,false,false,false,.42);assertNotNull(b);assertTrue(b.min().x()<=-1.1&&b.max().x()>=1.1);assertEquals(b.min().x(),b.min().z());}
 @Test void neutralOutputStillCoversReceivedInertiaBeyondWaypoint(){var b=FlightMotionEnvelope.bounds(P,new Vec3(.2,0,0),out(false,false,false),.05f,.1f,.1,1,false,false,false,.42);assertNotNull(b);assertTrue(b.max().x()>2);}
 @Test void verticalNativeImpulseUsesThreeAndPointSixTail(){var b=FlightMotionEnvelope.bounds(P,new Vec3(0,0,0),out(false,true,false),.05f,.1f,.1,1,false,false,false,.42);assertNotNull(b);assertTrue(b.max().y()>=80.375);}
 @Test void actualJumpBoostTakeoffAlsoFitsVerticalEnvelope(){var b=FlightMotionEnvelope.bounds(P,new Vec3(0,0,0),out(false,true,false),.05f,.4f,.4,1,true,false,false,1.02);assertNotNull(b);assertTrue(b.max().y()>=82.55);}
 @Test void unsupportedInfinitePassiveCoastRefused(){assertNull(FlightMotionEnvelope.bounds(P,new Vec3(0,0,0),out(true,false,false),.05f,.1f,.1,0,false,false,false,.42));}
 @Test void nonfiniteMotionCannotBePublished(){assertNull(FlightMotionEnvelope.bounds(P,new Vec3(Double.NaN,0,0),out(true,false,false),.05f,.1f,.1,1,false,false,false,.42));}
 @Test void actualGroundSprintJumpImpulseIsIncludedWithoutForwardKey(){var b=FlightMotionEnvelope.bounds(P,new Vec3(0,0,0),out(false,true,false),.05f,.13f,.13,1,true,true,false,.42);assertNotNull(b);assertTrue(b.max().x()>2&&b.max().z()>2);}
 @Test void groundSprintSpeedBoundCoversNativeFloatRounding(){var b=FlightMotionEnvelope.bounds(P,new Vec3(0,0,0),out(true,false,false),.05f,.42f,.42,1,true,false,false,.42);assertNotNull(b);double actual=(double)(float)(.42*1.3);assertTrue(b.max().x()>=actual/(1-(double).91f));}
 @Test void actualDiscardFrictionNativeBranchCannotCertifyPassiveCoast(){assertNull(FlightMotionEnvelope.bounds(P,new Vec3(.2,0,0),out(true,false,false),.05f,.1f,.1,1,false,false,true,.42));}
 @Test void discardedFrictionGroundedBranchIsAlsoUnavailable(){assertNull(FlightMotionEnvelope.bounds(P,new Vec3(.2,0,0),out(true,false,false),.05f,.1f,.1,1,true,false,true,.42));}
 @Test void arbitraryClampedGroundFrictionCannotExceedAirOnlyTail(){for(float block:new float[]{0,.6f,.98f,1,2})for(float modifier:new float[]{-1,0,1,10}){float ground=Math.max(0,Math.min(1,1-(1-block)*modifier));double air=FlightMotionEnvelope.horizontalDecay(.7);assertTrue(ground*air<=air);}}
}
