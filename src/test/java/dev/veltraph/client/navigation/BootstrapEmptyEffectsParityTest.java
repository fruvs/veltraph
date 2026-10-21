package dev.veltraph.client.navigation;

import java.lang.reflect.Proxy;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.function.BiFunction;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Exact original-S81 source differential; metadata only, never native/current authority. */
class BootstrapEmptyEffectsParityTest {
    private static final List<GroundCapturedEffects.Effect> ALL=List.of(
        new GroundCapturedEffects.Effect("minecraft:haste",0,false,true,true),
        new GroundCapturedEffects.Effect("minecraft:mining_fatigue",255,false,true,true),
        new GroundCapturedEffects.Effect("minecraft:strength",7,false,true,true),
        new GroundCapturedEffects.Effect("minecraft:night_vision",0,false,true,true),
        new GroundCapturedEffects.Effect("minecraft:regeneration",8,false,true,true),
        new GroundCapturedEffects.Effect("minecraft:absorption",0,false,true,true),
        new GroundCapturedEffects.Effect("minecraft:jump_boost",5,false,true,true));
    private static boolean old(List<GroundCapturedEffects.Effect> values,int jump){
        return OriginalBootstrapEmptyGroundCapturedEffects.supported(values,jump);
    }
    private static boolean now(List<GroundCapturedEffects.Effect> values,int jump){
        return GroundCapturedEffects.supported(values,jump);
    }
    private static void parity(List<GroundCapturedEffects.Effect> values,int jump){
        assertEquals(old(values,jump),now(values,jump));
    }
    private static final class Foreign extends AbstractList<GroundCapturedEffects.Effect> {
        final List<GroundCapturedEffects.Effect> values;
        final List<String> trace=new ArrayList<>();
        Error sizeFailure,iteratorFailure,readFailure;Runnable nested;
        Foreign(List<GroundCapturedEffects.Effect> values){this.values=values;}
        public int size(){trace.add("size");if(sizeFailure!=null)throw sizeFailure;return values.size();}
        public boolean isEmpty(){throw new AssertionError("Foreign isEmpty must never run");}
        public GroundCapturedEffects.Effect get(int index){trace.add("get:"+index);return values.get(index);}
        public Iterator<GroundCapturedEffects.Effect> iterator(){
            trace.add("iterator");if(iteratorFailure!=null)throw iteratorFailure;
            return new Iterator<>() {
                int index;boolean entered;
                public boolean hasNext(){trace.add("hasNext:"+index);return index<values.size();}
                public GroundCapturedEffects.Effect next(){
                    trace.add("next:"+index);
                    if(nested!=null&&!entered){entered=true;nested.run();}
                    if(readFailure!=null&&index==1)throw readFailure;
                    return values.get(index++);
                }
            };
        }
    }
    private record Result(Boolean value,Throwable failure) {}
    private static Result run(Foreign f,int amp,boolean original){
        try{return new Result(original?old(f,amp):now(f,amp),null);}
        catch(Throwable failure){return new Result(null,failure);}
    }
    private static void foreignParity(Foreign before,Foreign after,int amp){
        var a=run(before,amp,true);var b=run(after,amp,false);
        assertEquals(a.value,b.value);assertSame(a.failure,b.failure);assertEquals(before.trace,after.trace);
    }
    @Test void canonicalEmptyStreamAndFactoryHaveActualBootstrapIdentityAndEveryJumpOutcome(){
        List<GroundCapturedEffects.Effect> stream=Stream.<GroundCapturedEffects.Effect>empty().toList();
        assertSame(List.of().getClass(),stream.getClass());assertNull(stream.getClass().getClassLoader());
        for(int amp:new int[]{Integer.MIN_VALUE,-2,-1,0,5,6,Integer.MAX_VALUE}){
            parity(stream,amp);parity(List.of(),amp);assertEquals(amp==-1,now(stream,amp));
        }
    }
    @Test void allNonemptyCapturedSubsetsAndEveryFlagCombinationRetainOriginalResults(){
        for(int mask=1;mask<128;mask++)for(int flags=0;flags<8;flags++){
            var rows=new ArrayList<GroundCapturedEffects.Effect>();
            for(int i=0;i<7;i++)if((mask&(1<<i))!=0){var e=ALL.get(i);rows.add(new GroundCapturedEffects.Effect(e.id(),e.amplifier(),(flags&1)!=0,(flags&2)!=0,(flags&4)!=0));}
            for(int amp:new int[]{-2,-1,0,5,6})parity(rows.stream().toList(),amp);
        }
    }
    @Test void nullSingleNullUnknownDuplicateAndMalformedIdsAmplifiersKeepOriginalDecision(){
        for(List<GroundCapturedEffects.Effect> rows:Arrays.<List<GroundCapturedEffects.Effect>>asList(
                null,Arrays.asList((GroundCapturedEffects.Effect)null),List.of(ALL.getFirst(),ALL.getFirst()),
                List.of(new GroundCapturedEffects.Effect(null,0,false,true,true)),
                List.of(new GroundCapturedEffects.Effect("foreign",0,false,true,true)),
                List.of(new GroundCapturedEffects.Effect("minecraft:haste",1,false,true,true)))){
            for(int amp:new int[]{-1,0,5})parity(rows,amp);
            if(rows!=null)for(int amp:new int[]{-1,0,5})parity(rows.stream().toList(),amp);
        }
    }
    @Test void sizeLimitRetainsOriginalSingleSizeCallbackAndNoIteratorRead(){
        var rows=new ArrayList<>(ALL);rows.add(ALL.getFirst());var a=new Foreign(rows);var b=new Foreign(rows);
        foreignParity(a,b,5);assertEquals(List.of("size"),a.trace);
    }
    @Test void foreignEmptyRetainsOriginalIteratorAndHasNextCallbacks(){
        for(int amp:new int[]{-2,-1,0,5}){var a=new Foreign(List.of());var b=new Foreign(List.of());
            foreignParity(a,b,amp);assertEquals(List.of("size","iterator","hasNext:0"),a.trace);}
    }
    @Test void foreignNonemptyKeepsCompleteOriginalOrderedReads(){
        var a=new Foreign(ALL);var b=new Foreign(ALL);foreignParity(a,b,5);
        assertEquals("size",a.trace.getFirst());assertTrue(a.trace.contains("next:6"));assertEquals("hasNext:7",a.trace.getLast());
    }
    @Test void exactSizeFailureRetainsThrowableIdentityAndOriginalPrefix(){
        Error error=new AssertionError("size");var a=new Foreign(List.of());var b=new Foreign(List.of());
        a.sizeFailure=b.sizeFailure=error;foreignParity(a,b,-1);assertEquals(List.of("size"),a.trace);
    }
    @Test void exactIteratorFailureRetainsThrowableIdentityAndOriginalPrefix(){
        Error error=new AssertionError("iterator");var a=new Foreign(List.of());var b=new Foreign(List.of());
        a.iteratorFailure=b.iteratorFailure=error;foreignParity(a,b,-1);assertEquals(List.of("size","iterator"),a.trace);
    }
    @Test void readFailureAfterOneElementKeepsOriginalPartialReadPrefix(){
        Error error=new AssertionError("second");var a=new Foreign(ALL);var b=new Foreign(ALL);
        a.readFailure=b.readFailure=error;foreignParity(a,b,5);assertEquals("next:1",a.trace.getLast());
    }
    @Test void nestedPureValidationKeepsOuterCallbackOrderAndIndependentResult(){
        var a=new Foreign(ALL);var b=new Foreign(ALL);
        a.nested=()->a.trace.add("nested:"+old(List.of(),-1));b.nested=()->b.trace.add("nested:"+now(List.of(),-1));
        foreignParity(a,b,5);assertEquals(1,a.trace.stream().filter(s->s.startsWith("nested:")).count());
    }
    private static final class Escaped extends AbstractList<GroundCapturedEffects.Effect> {
        final Foreign returned;final List<String> trace=new ArrayList<>();
        Escaped(Foreign returned){this.returned=returned;}
        public int size(){throw new AssertionError("Input size not required by stream constructor");}
        public GroundCapturedEffects.Effect get(int i){throw new AssertionError("Input get not required");}
        @SuppressWarnings("unchecked")
        public Stream<GroundCapturedEffects.Effect> stream(){
            trace.add("stream");final Object[] self={null};
            self[0]=Proxy.newProxyInstance(Stream.class.getClassLoader(),new Class<?>[]{Stream.class},(proxy,method,args)->{
                trace.add(method.getName());
                if(method.getName().equals("sorted"))return self[0];
                if(method.getName().equals("toList"))return returned;
                throw new AssertionError("Unexpected stream method "+method.getName());
            });
            return (Stream<GroundCapturedEffects.Effect>)self[0];
        }
    }
    private static GroundNativePhysicsProfile.State state(List<GroundCapturedEffects.Effect> effects){
        return new GroundNativePhysicsProfile.State(.1D,.1D,.1F,List.of(),.42D,.08D,1D,1D,-1,effects,.15D,1F);
    }
    @Test void customStreamToListEscapeKeepsForeignCallbacksInsteadOfAssumingRecordImmutability(){
        for(List<GroundCapturedEffects.Effect> rows:List.of(List.<GroundCapturedEffects.Effect>of(),ALL)){
            var a=new Foreign(rows);var b=new Foreign(rows);var inputA=new Escaped(a);var inputB=new Escaped(b);
            var before=state(inputA);var after=state(inputB);
            assertSame(a,before.effects());assertSame(b,after.effects());
            assertEquals(List.of("stream","sorted","toList"),inputA.trace);assertEquals(inputA.trace,inputB.trace);
            foreignParity(a,b,-1);
        }
    }
    @Test void repeatedForeignCallsObserveMutationAndNeverReuseAnEarlierValidation(){
        var rowsA=new ArrayList<GroundCapturedEffects.Effect>();var rowsB=new ArrayList<GroundCapturedEffects.Effect>();
        var a=new Foreign(rowsA);var b=new Foreign(rowsB);foreignParity(a,b,-1);assertTrue(now(List.of(),-1));
        rowsA.add(ALL.getLast());rowsB.add(ALL.getLast());foreignParity(a,b,-1);
        rowsA.clear();rowsB.clear();foreignParity(a,b,-1);
        assertEquals(old(a,-1),now(b,-1));
    }
}
