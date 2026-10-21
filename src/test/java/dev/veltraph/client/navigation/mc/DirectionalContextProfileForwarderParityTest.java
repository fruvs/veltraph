package dev.veltraph.client.navigation.mc;

import dev.veltraph.client.navigation.CapturedProfileGroundYawEnvelope;
import dev.veltraph.client.navigation.GroundCapturedEffects;
import dev.veltraph.client.navigation.GroundNativePhysicsProfile;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Actual private helper parity. Reflection/class initialization happens only in the unit JVM;
 * no client construction, native query, native warmup or timing claim. Helper stack shape intentionally
 * differs; ordinary non-stack-inspecting foreign metadata retains calls and Throwable identity. */
class DirectionalContextProfileForwarderParityTest {
    private static final List<GroundCapturedEffects.Effect> CAPTURED=List.of(
            new GroundCapturedEffects.Effect("minecraft:haste",0,false,true,true),
            new GroundCapturedEffects.Effect("minecraft:mining_fatigue",255,false,true,true),
            new GroundCapturedEffects.Effect("minecraft:strength",7,false,true,true),
            new GroundCapturedEffects.Effect("minecraft:night_vision",0,false,true,true),
            new GroundCapturedEffects.Effect("minecraft:regeneration",8,false,true,true),
            new GroundCapturedEffects.Effect("minecraft:absorption",0,false,true,true),
            new GroundCapturedEffects.Effect("minecraft:jump_boost",5,false,true,true));

    private static boolean actual(GroundNativePhysicsProfile profile) throws Throwable {
        Method helper=McCapturedProfileLocomotion.class.getDeclaredMethod("directionalProfileSupported",GroundNativePhysicsProfile.class);
        helper.setAccessible(true);
        try { return (Boolean)helper.invoke(null,profile); }
        catch (InvocationTargetException failure) { throw failure.getCause(); }
    }
    private static GroundNativePhysicsProfile.State state(double base,Double sprint,int jump,List<GroundCapturedEffects.Effect> effects) {
        var modifiers=sprint==null?List.<GroundNativePhysicsProfile.Modifier>of():
                List.of(new GroundNativePhysicsProfile.Modifier("minecraft:sprinting",sprint,2));
        double effective=sprint==null?base:base*(1D+sprint);
        return new GroundNativePhysicsProfile.State(base,effective,(float)effective,modifiers,.42D,.08D,1D,1D,jump,effects,-0D,1F);
    }
    private static GroundNativePhysicsProfile profile(GroundNativePhysicsProfile.State state) {
        return GroundNativePhysicsProfile.classify(state).orElseThrow();
    }
    private static void parity(GroundNativePhysicsProfile profile,boolean expected) throws Throwable {
        assertEquals(expected,CapturedProfileGroundYawEnvelope.directionalProfile(profile));
        assertEquals(expected,actual(profile));
    }

    @Test void nullRemainsRefusedWithoutStateOrNativeReads() throws Throwable { parity(null,false); }

    @Test void ordinaryVanillaAndSprintDoubleFloatDistinctionStayExact() throws Throwable {
        for (double base:new double[]{.1D,(double).1F}) {
            parity(profile(state(base,null,-1,List.of())),true);
            parity(profile(state(base,(double).3F,-1,List.of())),true);
            // General classification accepts this amount, but native directional sprint deliberately does not.
            parity(profile(state(base,.3D,-1,List.of())),false);
        }
    }

    @Test void bothCapturedSevenEffectFamiliesKeepOriginalSelection() throws Throwable {
        for (double base:new double[]{GroundNativePhysicsProfile.CAPTURED_360,GroundNativePhysicsProfile.CAPTURED_400})
            for (Double sprint:new Double[]{null,(double).3F,.3D}) parity(profile(state(base,sprint,5,CAPTURED)),true);
    }

    @Test void generallySupportedMetadataDoesNotBecomeDirectionalAdmission() throws Throwable {
        parity(profile(state(GroundNativePhysicsProfile.CAPTURED_360,null,-1,List.of())),false);
        parity(profile(state(GroundNativePhysicsProfile.CAPTURED_400,null,-1,List.of())),false);
        parity(profile(state(.1D,null,-1,List.of(CAPTURED.getFirst()))),false);
        parity(profile(state(.1D,null,5,CAPTURED)),false);
        var malformed=state(.1D,null,-1,List.of());
        // Metadata-only private profile permits testing defensive unsupported traits, not actor custody.
        var ctor=GroundNativePhysicsProfile.class.getDeclaredConstructor(GroundNativePhysicsProfile.State.class);ctor.setAccessible(true);
        var bad=new GroundNativePhysicsProfile.State(malformed.baseSpeed(),malformed.effectiveSpeed(),malformed.travelSpeed(),
                malformed.movementModifiers(),malformed.jumpStrength(),.081D,1D,1D,-1,List.of(),0D,1F);
        parity(ctor.newInstance(bad),false);
    }

    @Test void signedRawMetadataAndImmutableProfileRemainUnmodified() throws Throwable {
        var input=state((double).1F,(double).3F,-1,List.of());var p=profile(input);
        var effects=input.effects();var modifiers=input.movementModifiers();int hash=input.hashCode();
        long efficiency=Double.doubleToRawLongBits(input.movementEfficiency());int speed=Float.floatToRawIntBits(input.travelSpeed());
        parity(p,true);parity(p,true);
        assertSame(input,p.state());assertSame(effects,p.state().effects());assertSame(modifiers,p.state().movementModifiers());
        assertEquals(hash,input.hashCode());assertEquals(Double.doubleToRawLongBits(-0D),efficiency);
        assertEquals(efficiency,Double.doubleToRawLongBits(input.movementEfficiency()));assertEquals(speed,Float.floatToRawIntBits(input.travelSpeed()));
        assertThrows(UnsupportedOperationException.class,()->input.effects().add(CAPTURED.getFirst()));
    }

    @Test void escapedForeignEffectsRetainCallbacksAndUnwrappedThrowableIdentity() throws Throwable {
        for (List<GroundCapturedEffects.Effect> rows:List.of(List.<GroundCapturedEffects.Effect>of(),CAPTURED)) {
            var escaped=new ForeignEffects(rows);var p=profile(state(rows.isEmpty()?.1D:GroundNativePhysicsProfile.CAPTURED_360,
                    null,rows.isEmpty()?-1:5,escapingInput(escaped)));
            assertSame(escaped,p.state().effects()); // Constructor's stream/toList result remains the actual foreign list.
            escaped.armed=true;escaped.trace.clear();boolean expected=CapturedProfileGroundYawEnvelope.directionalProfile(p);
            var trace=List.copyOf(escaped.trace);escaped.trace.clear();assertEquals(expected,actual(p));assertEquals(trace,escaped.trace);
            for (Throwable marker:new Throwable[]{new IllegalStateException("foreign size"),new AssertionError("foreign size")}) {
                escaped.failure=marker;escaped.trace.clear();assertSame(marker,failure(()->CapturedProfileGroundYawEnvelope.directionalProfile(p)));
                var failedTrace=List.copyOf(escaped.trace);escaped.trace.clear();assertSame(marker,failure(()->actual(p)));assertEquals(failedTrace,escaped.trace);
            }
        }
    }

    @FunctionalInterface private interface ThrowingBoolean { boolean call() throws Throwable; }
    private static Throwable failure(ThrowingBoolean operation) {
        try { operation.call();return null; } catch(Throwable failure) { return failure; }
    }
    @SuppressWarnings("unchecked") private static List<GroundCapturedEffects.Effect> escapingInput(ForeignEffects result) {
        var stream=(Stream<GroundCapturedEffects.Effect>)Proxy.newProxyInstance(Stream.class.getClassLoader(),new Class<?>[]{Stream.class},
                (proxy,method,args)->switch(method.getName()) {
                    case "sorted" -> proxy;
                    case "toList" -> result;
                    default -> throw new AssertionError("Unexpected construction stream call: "+method.getName());
                });
        return new AbstractList<>() {
            @Override public int size(){return result.values.size();}
            @Override public GroundCapturedEffects.Effect get(int index){return result.values.get(index);}
            @Override public Stream<GroundCapturedEffects.Effect> stream(){return stream;}
        };
    }
    private static final class ForeignEffects extends AbstractList<GroundCapturedEffects.Effect> {
        final List<GroundCapturedEffects.Effect> values;final List<String> trace=new ArrayList<>();boolean armed;Throwable failure;
        ForeignEffects(List<GroundCapturedEffects.Effect> values){this.values=values;}
        private void note(String site){if(armed)trace.add(site);if(armed&&failure!=null){if(failure instanceof RuntimeException e)throw e;if(failure instanceof Error e)throw e;throw new AssertionError(failure);}}
        @Override public int size(){note("size");return values.size();}
        @Override public boolean isEmpty(){note("isEmpty");return values.isEmpty();}
        @Override public GroundCapturedEffects.Effect get(int index){note("get:"+index);return values.get(index);}
    }
}
