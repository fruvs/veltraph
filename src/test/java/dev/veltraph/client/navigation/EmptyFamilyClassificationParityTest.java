package dev.veltraph.client.navigation;

import java.lang.reflect.Proxy;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Frozen original metadata classifier, including legitimate foreign effect stream/list callbacks. */
final class EmptyFamilyClassificationParityTest {
    private static GroundNativePhysicsProfile.State state(double base,List<GroundNativePhysicsProfile.Modifier> modifiers,
            int jump,List<GroundCapturedEffects.Effect> effects){
        double speed=base;
        if(modifiers!=null&&modifiers.size()==1)speed*=1+modifiers.getFirst().amount();
        return new GroundNativePhysicsProfile.State(base,speed,(float)speed,modifiers,.42,.08,1,1,jump,effects,0,1F);
    }
    private static void parity(GroundNativePhysicsProfile.State s){
        assertEquals(FrozenAEEmptyFamilySprintTransition.directionalProfileSupported(s),GroundOwnedSprintTransition.directionalProfileSupported(s));
    }
    @Test void closedBootstrapEmptyFamiliesAndNativeFloatSpeedsMatchOriginal(){
        for(double base:new double[]{.1,(double).1F,GroundNativePhysicsProfile.CAPTURED_360,GroundNativePhysicsProfile.CAPTURED_400})
            for(List<GroundCapturedEffects.Effect> effects:List.of(List.<GroundCapturedEffects.Effect>of(),Stream.<GroundCapturedEffects.Effect>empty().toList()))
                for(int jump:new int[]{-1,5,0}){
                    var s=state(base,List.of(),jump,effects);assertSame(List.of().getClass(),s.effects().getClass());parity(s);
                }
        assertTrue(GroundOwnedSprintTransition.directionalProfileSupported(state(.1,List.of(),-1,List.of())));
        assertFalse(GroundOwnedSprintTransition.directionalProfileSupported(state(.36,List.of(),-1,List.of())));
    }
    @Test void nullUnsupportedScalarNaNAndAttributeBoundariesMatchOriginal(){
        parity(null);
        for(double scalar:new double[]{Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY,-0.0,0.0,.09,Math.nextUp(.1)}){
            parity(state(scalar,List.of(),-1,List.of()));
            parity(new GroundNativePhysicsProfile.State(.1,.1,.1F,List.of(),scalar,.08,1,1,-1,List.of(),0,1F));
            parity(new GroundNativePhysicsProfile.State(.1,.1,.1F,List.of(),.42,scalar,1,1,-1,List.of(),0,1F));
            parity(new GroundNativePhysicsProfile.State(.1,.1,.1F,List.of(),.42,.08,scalar,1,-1,List.of(),0,1F));
            parity(new GroundNativePhysicsProfile.State(.1,.1,.1F,List.of(),.42,.08,1,1,-1,List.of(),scalar,1F));
        }
        parity(state(.1,null,-1,List.of()));parity(state(.1,List.of(),-1,null));
    }
    @Test void allModifierVariantsRetainOriginalStrictMetadata(){
        for(var modifiers:List.of(List.<GroundNativePhysicsProfile.Modifier>of(),
                List.of(new GroundNativePhysicsProfile.Modifier("minecraft:sprinting",GroundNativePhysicsProfile.CAPTURED_SPRINT_AMOUNT,2)),
                List.of(new GroundNativePhysicsProfile.Modifier("minecraft:sprinting",.3,2)),
                List.of(new GroundNativePhysicsProfile.Modifier("minecraft:sprinting",.3,1)),
                List.of(new GroundNativePhysicsProfile.Modifier(null,.3,2)),
                List.of(new GroundNativePhysicsProfile.Modifier("unknown",.3,2)),
                List.of(new GroundNativePhysicsProfile.Modifier("minecraft:sprinting",Double.NaN,2)),
                List.of(new GroundNativePhysicsProfile.Modifier("unknown",.3,2),new GroundNativePhysicsProfile.Modifier("unknown",.3,2))))
            parity(state(.1,modifiers,-1,List.of()));
    }
    private static List<GroundCapturedEffects.Effect> captured(){return List.of(
            new GroundCapturedEffects.Effect("minecraft:haste",0,false,true,true),
            new GroundCapturedEffects.Effect("minecraft:mining_fatigue",255,false,true,true),
            new GroundCapturedEffects.Effect("minecraft:strength",7,false,true,true),
            new GroundCapturedEffects.Effect("minecraft:night_vision",0,false,true,true),
            new GroundCapturedEffects.Effect("minecraft:regeneration",8,false,true,true),
            new GroundCapturedEffects.Effect("minecraft:absorption",0,false,true,true),
            new GroundCapturedEffects.Effect("minecraft:jump_boost",5,false,true,true));}
    @Test void nonemptyCapturedPartialAndUnknownEffectsUseOriginalPath(){
        for(double base:new double[]{.1,GroundNativePhysicsProfile.CAPTURED_360,GroundNativePhysicsProfile.CAPTURED_400})
            for(List<GroundCapturedEffects.Effect> effects:List.of(captured(),captured().subList(0,6),
                    List.of(new GroundCapturedEffects.Effect("unknown",0,false,true,true)),
                    List.of(new GroundCapturedEffects.Effect("minecraft:jump_boost",5,false,true,true))))
                for(int jump:new int[]{-1,5})parity(state(base,List.of(),jump,effects));
        assertTrue(GroundOwnedSprintTransition.directionalProfileSupported(state(GroundNativePhysicsProfile.CAPTURED_360,List.of(),5,captured())));
    }
    private static final class ForeignEffects extends AbstractList<GroundCapturedEffects.Effect> {
        final List<GroundCapturedEffects.Effect> values;final List<String> calls=new ArrayList<>();
        int sizeCalls,faultAt=-1;Throwable failure;
        ForeignEffects(List<GroundCapturedEffects.Effect> values){this.values=values;}
        public int size(){calls.add("size");if(++sizeCalls==faultAt){if(failure instanceof RuntimeException runtime)throw runtime;if(failure instanceof Error error)throw error;}return values.size();}
        public GroundCapturedEffects.Effect get(int i){calls.add("get:"+i);return values.get(i);}
        public boolean isEmpty(){calls.add("isEmpty");return values.isEmpty();}
        @SuppressWarnings("unchecked") public Stream<GroundCapturedEffects.Effect> stream(){
            calls.add("stream");return (Stream<GroundCapturedEffects.Effect>)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{Stream.class},
                    (proxy,method,args)->{calls.add(method.getName());if(method.getName().equals("sorted"))return proxy;if(method.getName().equals("toList"))return this;throw new AssertionError("Unexpected stream callback "+method);});
        }
        void reset(){calls.clear();sizeCalls=0;}
    }
    @Test void foreignEscapedEmptyAndNonemptyListsKeepExactCallbackTrace(){
        for(List<GroundCapturedEffects.Effect> effects:List.of(List.<GroundCapturedEffects.Effect>of(),captured(),
                List.of(new GroundCapturedEffects.Effect("unknown",0,false,true,true)))){
            var foreign=new ForeignEffects(effects);var s=state(.1,List.of(),-1,foreign);assertSame(foreign,s.effects());
            foreign.reset();boolean old=FrozenAEEmptyFamilySprintTransition.directionalProfileSupported(s);var trace=List.copyOf(foreign.calls);
            foreign.reset();assertEquals(old,GroundOwnedSprintTransition.directionalProfileSupported(s));assertEquals(trace,foreign.calls);
        }
    }
    @Test void foreignSizeFaultPreservesOriginalExceptionAndCallbackPrefix(){
        for(Throwable failure:List.of(new IllegalArgumentException("original"),new AssertionError("original")))
            for(int at:new int[]{1,2}){
                var foreign=new ForeignEffects(List.of());var s=state(.1,List.of(),-1,foreign);foreign.failure=failure;foreign.faultAt=at;
                foreign.reset();assertSame(failure,assertThrows(failure.getClass(),()->FrozenAEEmptyFamilySprintTransition.directionalProfileSupported(s)));var trace=List.copyOf(foreign.calls);
                foreign.reset();assertSame(failure,assertThrows(failure.getClass(),()->GroundOwnedSprintTransition.directionalProfileSupported(s)));assertEquals(trace,foreign.calls);
            }
    }
    @Test void foreignModifierCollectionIsNormalizedAtConstructionThenNeverCalledByClassifier(){
        var calls=new ArrayList<String>();
        var source=new AbstractList<GroundNativePhysicsProfile.Modifier>(){
            public int size(){calls.add("size");return 0;}
            public GroundNativePhysicsProfile.Modifier get(int i){calls.add("get");throw new AssertionError("empty");}
            public boolean isEmpty(){calls.add("isEmpty");return true;}
        };
        var s=state(.1,source,-1,List.of());assertNotSame(source,s.movementModifiers());
        assertSame(List.of().getClass(),s.movementModifiers().getClass());calls.clear();parity(s);assertTrue(calls.isEmpty());
    }
}
