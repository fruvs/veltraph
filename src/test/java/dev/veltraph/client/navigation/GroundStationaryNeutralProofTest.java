package dev.veltraph.client.navigation;

import dev.veltraph.client.world.Vec3;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import static dev.veltraph.client.navigation.CapturedProfileTrajectory.*;
import static org.junit.jupiter.api.Assertions.*;

/** Synthetic mathematical/adversarial fixtures only; these do not establish native support. */
class GroundStationaryNeutralProofTest {
    private static final Vec3 ORIGIN=new Vec3(.5,70,.12);
    private static final double DOWN=-.08D*(double).98F;
    private static GroundNativePhysicsProfile profile(float speed){
        return GroundNativePhysicsProfile.classify(new GroundNativePhysicsProfile.State((double)speed,(double)speed,speed,
                List.of(),(double).42F,.08D,1D,1D,-1,List.of(),0D,1F)).orElseThrow();
    }
    private static Trace trace(Vec3 v){return forecast(profile(.36F),ORIGIN,v,.6F,23F,Phase.SUPPORTED_GROUND,
            Collections.nCopies(33,Input.NEUTRAL),0).orElseThrow();}
    private static GroundProfileForecast.Brake brake(Vec3 v){return GroundProfileForecast.groundBrake(profile(.36F),ORIGIN,v,.6F,32).orElseThrow();}
    @Test void entireActualStyleThirtyThreeAndThirtyTwoHorizonsAreStationaryWithoutLosingCaps(){
        var v=new Vec3(0,DOWN,0);var t=trace(v);var b=brake(v);
        var d=GroundStationaryNeutralProof.describe(t,b).orElseThrow();
        assertEquals(33,d.traceFrames());assertEquals(32,d.brakeFrames());assertEquals(33,d.maxHorizon());
        assertEquals(ORIGIN,d.origin());assertEquals(v,d.observedVelocity());assertEquals(t.profileState(),d.profileState());
        assertEquals(23F,d.yaw());assertEquals(.6F,d.friction());assertEquals(List.of(v),d.collisionMoves());
        assertEquals(33,t.samples().size());assertEquals(32,b.samples().size());
    }
    @Test void firstZeroAndSubsequentNativeDownwardIntentsAreBothRetainedExactly(){
        var v=new Vec3(0,0,0);var t=trace(v);var d=GroundStationaryNeutralProof.describe(t,brake(v)).orElseThrow();
        assertEquals(0D,t.samples().getFirst().intendedMove().y());assertEquals(DOWN,t.samples().get(1).intendedMove().y());
        assertEquals(List.of(new Vec3(0,0,0),new Vec3(0,DOWN,0)),d.collisionMoves());
    }
    @Test void nonzeroNearZeroSubnormalAndSignedHorizontalZeroNeverQualify(){
        for(double x:new double[]{.02,1E-12,Double.MIN_VALUE,-Double.MIN_VALUE,-0D}){
            var v=new Vec3(x,DOWN,0);assertTrue(GroundStationaryNeutralProof.describe(trace(v),brake(v)).isEmpty());
        }
        var z=new Vec3(0,DOWN,Double.MIN_VALUE);assertTrue(GroundStationaryNeutralProof.describe(trace(z),brake(z)).isEmpty());
    }
    @Test void differentOriginalVerticalObservationCannotBeReplacedWithTraceTerminalVelocity(){
        assertTrue(GroundStationaryNeutralProof.describe(trace(new Vec3(0,0,0)),brake(new Vec3(0,DOWN,0))).isEmpty());
    }
    @Test void changedProfileFrictionOriginAndUnboundedYawRefuse(){
        var v=new Vec3(0,DOWN,0);var t=trace(v);
        assertTrue(GroundStationaryNeutralProof.describe(t,GroundProfileForecast.groundBrake(profile(.4F),ORIGIN,v,.6F,32).orElseThrow()).isEmpty());
        assertTrue(GroundStationaryNeutralProof.describe(t,GroundProfileForecast.groundBrake(profile(.36F),ORIGIN,v,.5F,32).orElseThrow()).isEmpty());
        assertTrue(GroundStationaryNeutralProof.describe(t,GroundProfileForecast.groundBrake(profile(.36F),new Vec3(.5,70,.13),v,.6F,32).orElseThrow()).isEmpty());
        assertTrue(GroundStationaryNeutralProof.describe(copyTrace(t,t.samples(),Float.NaN),brake(v)).isEmpty());
    }
    @Test void asymmetricCoastResidualRefusesEvenWhenAllFiniteBrakeSamplesAppearStationary(){
        var v=new Vec3(0,DOWN,0);var b=brake(v);
        var drift=copyBrake(b,b.samples(),new Vec3(Math.nextUp(ORIGIN.x()),ORIGIN.y(),ORIGIN.z()));
        assertTrue(GroundStationaryNeutralProof.describe(trace(v),drift).isEmpty());
    }
    @Test void lastTraceFrameControlPhaseConditionAndVerticalMathAreCheckedAcrossWholeHorizon(){
        var v=new Vec3(0,DOWN,0);var t=trace(v);var last=t.samples().getLast();
        for(var changed:List.of(
                sample(last,last.position(),last.velocity(),last.intendedMove(),Phase.SUPPORTED_GROUND,Input.FORWARD,Condition.FRESH_FLAT_SUPPORT_CLAMP,JumpIntent.RELEASE,SprintIntent.RELEASE),
                sample(last,last.position(),last.velocity(),last.intendedMove(),Phase.SUPPORTED_GROUND,Input.NEUTRAL,Condition.FRESH_FLAT_SUPPORT_CLAMP,JumpIntent.PRESS,SprintIntent.RELEASE),
                sample(last,last.position(),last.velocity(),last.intendedMove(),Phase.SUPPORTED_GROUND,Input.NEUTRAL,Condition.FRESH_FLAT_SUPPORT_CLAMP,JumpIntent.RELEASE,SprintIntent.PRESS),
                sample(last,last.position(),last.velocity(),last.intendedMove(),Phase.OBSERVED_AIRBORNE,Input.NEUTRAL,Condition.FRESH_FLAT_SUPPORT_CLAMP,JumpIntent.RELEASE,SprintIntent.RELEASE),
                sample(last,last.position(),last.velocity(),last.intendedMove(),Phase.SUPPORTED_GROUND,Input.NEUTRAL,Condition.UNCLAMPED_FLIGHT,JumpIntent.RELEASE,SprintIntent.RELEASE),
                sample(last,last.position(),new Vec3(0,Math.nextDown(DOWN),0),last.intendedMove(),Phase.SUPPORTED_GROUND,Input.NEUTRAL,Condition.FRESH_FLAT_SUPPORT_CLAMP,JumpIntent.RELEASE,SprintIntent.RELEASE),
                sample(last,last.position(),last.velocity(),new Vec3(0,Math.nextDown(DOWN),0),Phase.SUPPORTED_GROUND,Input.NEUTRAL,Condition.FRESH_FLAT_SUPPORT_CLAMP,JumpIntent.RELEASE,SprintIntent.RELEASE),
                sample(last,new Vec3(.5,Math.nextUp(70D),.12),last.velocity(),last.intendedMove(),Phase.SUPPORTED_GROUND,Input.NEUTRAL,Condition.FRESH_FLAT_SUPPORT_CLAMP,JumpIntent.RELEASE,SprintIntent.RELEASE))){
            var samples=new ArrayList<>(t.samples());samples.set(samples.size()-1,changed);
            assertTrue(GroundStationaryNeutralProof.describe(copyTrace(t,samples,t.yaw()),brake(v)).isEmpty());
        }
    }
    @Test void lastBrakeStepPositionVelocityAndNativeStepSequenceCannotHideBehindStationaryTrace(){
        var v=new Vec3(0,DOWN,0);var b=brake(v);
        for(var bad:List.of(new GroundProfileForecast.Sample(32,new Vec3(Math.nextUp(.5),70,.12),new Vec3(0,0,0)),
                new GroundProfileForecast.Sample(32,ORIGIN,new Vec3(0,Double.MIN_VALUE,0)),
                new GroundProfileForecast.Sample(31,ORIGIN,new Vec3(0,0,0)))){
            var samples=new ArrayList<>(b.samples());samples.set(31,bad);
            assertTrue(GroundStationaryNeutralProof.describe(trace(v),copyBrake(b,samples,b.asymptoticCoast())).isEmpty());
        }
    }
    @Test void validDescriptorCannotReviveExpiredOriginalQueryFence(){
        var v=new Vec3(0,DOWN,0);var time=new AtomicLong();var fence=new DynamicRouteQueryFence(time::get,()->true,50_000_000L);
        assertTrue(GroundStationaryNeutralProof.describe(trace(v),brake(v)).isPresent());
        time.set(51_490_000L);var called=new java.util.concurrent.atomic.AtomicBoolean();
        assertFalse(fence.query(()->{called.set(true);return GroundStationaryNeutralProof.describe(trace(v),brake(v)).isPresent();}));
        assertFalse(called.get());assertFalse(fence.available());
    }
    @Test void descriptorsRemainPrivatelyConstructedImmutableAndWholeHorizonBounded(){
        var v=new Vec3(0,DOWN,0);var t=trace(v);var d=GroundStationaryNeutralProof.describe(t,brake(v)).orElseThrow();
        assertThrows(UnsupportedOperationException.class,()->d.collisionMoves().clear());
        for(var constructor:GroundStationaryNeutralProof.Descriptor.class.getDeclaredConstructors())
            assertTrue(java.lang.reflect.Modifier.isPrivate(constructor.getModifiers()));
        assertTrue(GroundStationaryNeutralProof.describe(copyTrace(t,Collections.nCopies(65,t.samples().getFirst()),t.yaw()),brake(v)).isEmpty());
        assertTrue(GroundStationaryNeutralProof.describe(copyTrace(t,List.of(),t.yaw()),brake(v)).isEmpty());
        assertTrue(GroundStationaryNeutralProof.describe(t,copyBrake(brake(v),List.of(),ORIGIN)).isEmpty());
        assertTrue(GroundStationaryNeutralProof.describe(null,brake(v)).isEmpty());
        assertTrue(GroundStationaryNeutralProof.describe(t,null).isEmpty());
    }
    @Test void explicitOriginalMathProfileAndCompatibilityEntryHaveExactDescriptorParity(){
        for(double y:new double[]{0D,DOWN}){
            var v=new Vec3(0,y,0);var t=trace(v);var b=brake(v);
            var old=GroundStationaryNeutralProof.describe(t,b).orElseThrow();
            var explicit=GroundStationaryNeutralProof.describe(profile(.36F),t,b).orElseThrow();
            assertEquals(old.origin(),explicit.origin());assertEquals(old.observedVelocity(),explicit.observedVelocity());
            assertEquals(old.profileState(),explicit.profileState());assertEquals(old.friction(),explicit.friction());
            assertEquals(old.yaw(),explicit.yaw());assertEquals(old.traceFrames(),explicit.traceFrames());
            assertEquals(old.brakeFrames(),explicit.brakeFrames());assertEquals(old.maxHorizon(),explicit.maxHorizon());
            assertEquals(old.collisionMoves(),explicit.collisionMoves());
        }
    }
    @Test void explicitProfileMustMatchEveryStateFieldAndCannotFallBackToTraceClassifier(){
        var v=new Vec3(0,DOWN,0);var t=trace(v);var b=brake(v);var s=profile(.36F).state();
        var differentEfficiency=GroundNativePhysicsProfile.classify(new GroundNativePhysicsProfile.State(s.baseSpeed(),
                s.effectiveSpeed(),s.travelSpeed(),s.movementModifiers(),s.jumpStrength(),s.gravity(),s.frictionModifier(),
                s.airDragModifier(),s.jumpAmplifier(),s.effects(),.5D,s.blockSpeedFactor())).orElseThrow();
        assertTrue(GroundStationaryNeutralProof.describe(differentEfficiency,t,b).isEmpty());
        assertTrue(GroundStationaryNeutralProof.describe(profile(.4F),t,b).isEmpty());
        assertTrue(GroundStationaryNeutralProof.describe(null,t,b).isEmpty());
        assertTrue(GroundStationaryNeutralProof.describe(profile(.36F),null,b).isEmpty());
        assertTrue(GroundStationaryNeutralProof.describe(profile(.36F),t,null).isEmpty());
        var differentBrake=GroundProfileForecast.groundBrake(profile(.4F),ORIGIN,v,.6F,32).orElseThrow();
        assertTrue(GroundStationaryNeutralProof.describe(profile(.36F),t,differentBrake).isEmpty());
        assertTrue(GroundStationaryNeutralProof.describe(profile(.4F),t,differentBrake).isEmpty());
    }
    @Test void explicitProfileOverloadStillRefusesResidueControlsCoastAndExpiredFence(){
        var profile=profile(.36F);var tiny=new Vec3(Double.MIN_VALUE,DOWN,0);
        assertTrue(GroundStationaryNeutralProof.describe(profile,trace(tiny),brake(tiny)).isEmpty());
        var v=new Vec3(0,DOWN,0);var t=trace(v);var b=brake(v);
        var inputs=new ArrayList<>(Collections.nCopies(33,Input.NEUTRAL));inputs.set(32,Input.FORWARD);
        var changed=forecast(profile,ORIGIN,v,.6F,23F,Phase.SUPPORTED_GROUND,inputs,0).orElseThrow();
        assertTrue(GroundStationaryNeutralProof.describe(profile,changed,b).isEmpty());
        assertTrue(GroundStationaryNeutralProof.describe(profile,t,copyBrake(b,b.samples(),
                new Vec3(Math.nextUp(.5),70,.12))).isEmpty());
        var time=new AtomicLong();var fence=new DynamicRouteQueryFence(time::get,()->true,50_000_000L);
        time.set(51_490_000L);assertFalse(fence.query(()->GroundStationaryNeutralProof.describe(profile,t,b).isPresent()));
    }
    // Reflection is confined to adversarial test construction, never production authority/API.
    private static Sample sample(Sample s,Vec3 p,Vec3 v,Vec3 m,Phase phase,Input input,Condition condition,JumpIntent jump,SprintIntent sprint){
        return construct(Sample.class,p,v,m,s.acceleration(),phase,input,condition,jump,sprint);
    }
    private static Trace copyTrace(Trace t,List<Sample> samples,float yaw){return construct(Trace.class,samples,t.origin(),t.observedVelocity(),t.profileState(),yaw,t.friction());}
    private static GroundProfileForecast.Brake copyBrake(GroundProfileForecast.Brake b,List<GroundProfileForecast.Sample> samples,Vec3 coast){
        return construct(GroundProfileForecast.Brake.class,samples,coast,b.blockFriction(),b.origin(),b.observedVelocity(),b.profileState());
    }
    private static <T>T construct(Class<T> type,Object...args){
        try{var constructor=type.getDeclaredConstructors()[0];constructor.setAccessible(true);return type.cast(constructor.newInstance(args));}
        catch(ReflectiveOperationException failure){throw new AssertionError(failure);}
    }
}
