package dev.veltraph.client.navigation;

import dev.veltraph.client.world.Vec3;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static dev.veltraph.client.navigation.CapturedProfileTrajectory.*;
import static org.junit.jupiter.api.Assertions.*;

/** Synthetic arithmetic/geometry only. These are not native movement or queued-camera receipts. */
class CapturedProfileGroundYawEnvelopeTest {
    private static GroundNativePhysicsProfile vanilla(boolean sprint){
        double speed=.1D*(sprint?1D+(double).3F:1D);
        return GroundNativePhysicsProfile.classify(new GroundNativePhysicsProfile.State(.1D,speed,(float)speed,
                sprint?List.of(new GroundNativePhysicsProfile.Modifier("minecraft:sprinting",(double).3F,2)):List.of(),
                (double).42F,.08D,1D,1D,-1,List.of(),0D,1F)).orElseThrow();
    }
    private static CapturedProfileGroundYawEnvelope.Envelope envelope(GroundNativePhysicsProfile p,Input input,Vec3 origin,Vec3 v){
        return CapturedProfileGroundYawEnvelope.describe(p,origin,v,.6F,0F,30F,input).orElseThrow();
    }
    @Test void normalizedModernDiagonalUsesFloatOrderAndPositiveLeft(){
        float scalar=.7071067094802856F; // Native .98F remap evaluation order, not direct 1/sqrt(2).
        assertEquals(new Vec3(scalar,0D,scalar),CapturedProfileMotionMath.nativeTravelInput(Input.FORWARD_LEFT));
        var diagonal=frame(vanilla(false),new Vec3(0,0,0),new Vec3(0,0,0),.6F,0F,Input.FORWARD_LEFT,Phase.SUPPORTED_GROUND).orElseThrow();
        assertTrue(diagonal.acceleration().x()>0D&&diagonal.acceleration().z()>0D);
        assertEquals(diagonal.acceleration().x(),diagonal.acceleration().z());
        assertTrue(Math.hypot(diagonal.acceleration().x(),diagonal.acceleration().z())>(double).1F*.98D);
    }
    @Test void everyEightWayInputAtEveryHeadingRemainsInsideCompleteStoppingRectangle(){
        var origin=new Vec3(8,71,8);var observed=new Vec3(.12,-.0784,-.08);
        for(var profile:List.of(vanilla(false),vanilla(true),CapturedProfileTrajectoryTest.profile(.36F,false),CapturedProfileTrajectoryTest.profile(.4F,true)))
            for(var input:Input.values())if(input!=Input.NEUTRAL){
                var bounds=envelope(profile,input,origin,observed);
                var inputs=new ArrayList<Input>();inputs.add(input);for(int i=1;i<64;i++)inputs.add(Input.NEUTRAL);
                for(int heading=-720;heading<=720;heading+=3){
                    var t=forecast(profile,origin,observed,.6F,heading,Phase.SUPPORTED_GROUND,inputs,
                            profile.state().effects().isEmpty()?0:100).orElseThrow();
                    for(var sample:t.samples())assertTrue(bounds.contains(sample.position()),"escaped input="+input+" yaw="+heading);
                    var end=t.samples().getLast();var coast=groundCoastBound(end.position(),end.velocity(),.6F).orElseThrow();
                    assertTrue(bounds.contains(coast));
                }
            }
    }
    @Test void angularOvershootIsCoveredWithoutTreatingEndpointAsActualYawRange(){
        var p=vanilla(false);var origin=new Vec3(0,0,0);var v=new Vec3(0,0,0);
        var bounds=envelope(p,Input.FORWARD_LEFT,origin,v);
        var sample=frame(p,origin,v,.6F,160F,Input.FORWARD_LEFT,Phase.SUPPORTED_GROUND).orElseThrow();
        assertTrue(bounds.contains(sample.position()));
        assertFalse(bounds.endpointMatches(160F));assertTrue(bounds.endpointMatches(30F));
    }
    @Test void queuedEndpointMustMatchOriginalAdviceAcrossYawWrap(){
        var p=vanilla(false);var zero=new Vec3(0,0,0);
        var bounds=CapturedProfileGroundYawEnvelope.describe(p,zero,zero,.6F,179F,-176F,Input.FORWARD_RIGHT).orElseThrow();
        assertTrue(bounds.endpointMatches(184F));assertFalse(bounds.endpointMatches(-170F));
    }
    @Test void turnGreaterThanFortyFiveDegreesHasNoDirectionalAdvice(){
        var zero=new Vec3(0,0,0);
        assertTrue(CapturedProfileGroundYawEnvelope.describe(vanilla(false),zero,zero,.6F,0F,46F,Input.FORWARD).isEmpty());
        assertTrue(CapturedProfileGroundYawEnvelope.describe(vanilla(false),zero,zero,.6F,0F,Float.NaN,Input.FORWARD).isEmpty());
    }
    @Test void oneBlockCorridorCannotContainTheFullHeadingBodyEnvelope(){
        var bounds=envelope(vanilla(false),Input.FORWARD_LEFT,new Vec3(.5,71,.5),new Vec3(0,0,0));
        // Native standing body .6 wide and the unchanged geometry uncertainty .025.
        assertTrue(bounds.minimum().x()-.3D-.025D<0D);
        assertTrue(bounds.maximum().x()+.3D+.025D>1D);
    }
    @Test void cornerObstacleBetweenNominalAndTurnedPathsLiesInsideRequiredBodyRectangle(){
        var bounds=envelope(CapturedProfileTrajectoryTest.profile(.4F,false),Input.FORWARD_LEFT,new Vec3(4,71,4),new Vec3(.1,-.0784,.1));
        assertTrue(bounds.minimum().x()<4D&&bounds.maximum().z()>4.6D);
        // The native group checks the entire rectangle, not just two endpoint trajectories.
        double obstacleX=4.2D,obstacleZ=4.6D;
        assertTrue(obstacleX>=bounds.minimum().x()-.3D&&obstacleX<=bounds.maximum().x()+.3D);
        assertTrue(obstacleZ>=bounds.minimum().z()-.3D&&obstacleZ<=bounds.maximum().z()+.3D);
    }
    @Test void momentumAndInfiniteCoastExpandBoundsRatherThanBeingResetForTheTurn(){
        var p=vanilla(false);var origin=new Vec3(4,71,4);
        var stopped=envelope(p,Input.LEFT,origin,new Vec3(0,0,0));
        var moving=envelope(p,Input.LEFT,origin,new Vec3(.4,-.0784,0));
        assertTrue(moving.maximum().x()>stopped.maximum().x()+.8D);
        assertEquals(origin,new Vec3(4,71,4));
    }
    @Test void neutralAirOrUnknownProfileCannotMintGroundEnvelope(){
        var zero=new Vec3(0,0,0);
        assertTrue(CapturedProfileGroundYawEnvelope.describe(vanilla(false),zero,zero,.6F,0,0,Input.NEUTRAL).isEmpty());
        assertTrue(CapturedProfileGroundYawEnvelope.describe(vanilla(false),zero,new Vec3(0,.1,0),.6F,0,0,Input.FORWARD).isEmpty());
        assertTrue(CapturedProfileGroundYawEnvelope.describe(null,zero,zero,.6F,0,0,Input.FORWARD).isEmpty());
        assertTrue(CapturedProfileGroundYawEnvelope.describe(vanilla(false),zero,zero,.98F,0,0,Input.FORWARD).isEmpty());
    }
    @Test void unknownEffectCombinationCannotEnterDirectionalCapturedFamily(){
        var ordinaryAtCapturedSpeed=GroundNativePhysicsProfile.classify(new GroundNativePhysicsProfile.State((double).36F,
                (double).36F,.36F,List.of(),(double).42F,.08D,1D,1D,-1,List.of(),0D,1F)).orElseThrow();
        assertFalse(CapturedProfileGroundYawEnvelope.directionalProfile(ordinaryAtCapturedSpeed));
        assertTrue(CapturedProfileGroundYawEnvelope.directionalProfile(vanilla(false)));
        assertTrue(CapturedProfileGroundYawEnvelope.directionalProfile(CapturedProfileTrajectoryTest.profile(.4F,false)));
    }
    @Test void supportRectangleIncludesNativeFloatWidthAtAnIntegerBoundary(){
        var p=vanilla(false);var zero=new Vec3(0,0,0);
        var initial=envelope(p,Input.FORWARD,zero,zero);
        double originX=.67499999D-initial.maximum().x();
        var bounds=envelope(p,Input.FORWARD,new Vec3(originX,71,0),zero);
        var feet=bounds.footing(.6F).orElseThrow();
        assertEquals(0D,Math.floor(bounds.maximum().x()+.025D+.3D));
        assertEquals(1D,Math.floor(feet.maximum().x()+.3D));
        assertTrue(feet.maximum().x()+.3D>=bounds.maximum().x()+(double).6F/2D+.025D);
        assertTrue(feet.minimum().x()-.3D<=bounds.minimum().x()-(double).6F/2D-.025D);
        assertTrue(bounds.footing(Float.NaN).isEmpty());
    }
    @Test void finiteInputAndImmutableDescriptorAreRequired(){
        var zero=new Vec3(0,0,0);
        assertTrue(CapturedProfileGroundYawEnvelope.describe(vanilla(false),zero,new Vec3(Double.NaN,0,0),.6F,0,0,Input.FORWARD).isEmpty());
        for(var constructor:CapturedProfileGroundYawEnvelope.Envelope.class.getDeclaredConstructors())
            assertTrue(java.lang.reflect.Modifier.isPrivate(constructor.getModifiers()));
    }
    @Test void eachNativeDiagonalHandednessRetainsDistinctFirstPulseDirection(){
        var p=vanilla(false);var zero=new Vec3(0,0,0);
        for(var input:List.of(Input.FORWARD_LEFT,Input.FORWARD_RIGHT,Input.BACKWARD_LEFT,Input.BACKWARD_RIGHT)){
            var sample=frame(p,zero,zero,.6F,0F,input,Phase.SUPPORTED_GROUND).orElseThrow();
            assertEquals(Integer.signum(input.left()),(int)Math.signum(sample.acceleration().x()));
            assertEquals(Integer.signum(input.forward()),(int)Math.signum(sample.acceleration().z()));
        }
    }
}
