package dev.veltraph.client.navigation;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Data-domain State normalization parity. Exact original S71 reference is renamed only.
 * Output identity/class checks are grounded in Root's pinned Java25 source, not a portable API promise.
 * No native query, authority, duration, timing, class priming or gameplay result is established. */
class GroundCanonicalEmptyStateParityTest {
    private static GroundNativePhysicsProfile.State actual(List<GroundCapturedEffects.Effect> effects){
        return new GroundNativePhysicsProfile.State(.1D,.1D,.1F,List.of(),.42D,.08D,1D,1D,-1,effects,.15D,1F);
    }
    private static OriginalSortedGroundNativePhysicsProfile.State original(List<GroundCapturedEffects.Effect> effects){
        return new OriginalSortedGroundNativePhysicsProfile.State(.1D,.1D,.1F,List.of(),.42D,.08D,1D,1D,-1,effects,.15D,1F);
    }
    private static final class Foreign extends AbstractList<GroundCapturedEffects.Effect> {
        final List<GroundCapturedEffects.Effect> values;final List<String> trace=new ArrayList<>();final Error streamFailure;
        Foreign(List<GroundCapturedEffects.Effect> values,Error failure){this.values=values;streamFailure=failure;}
        public int size(){trace.add("size");return values.size();}
        public boolean isEmpty(){throw new AssertionError("Foreign isEmpty must never be introduced");}
        public GroundCapturedEffects.Effect get(int index){trace.add("get:"+index);return values.get(index);}
        public Stream<GroundCapturedEffects.Effect> stream(){trace.add("stream");if(streamFailure!=null)throw streamFailure;return super.stream();}
    }
    private static Throwable failure(Runnable action){try{action.run();return null;}catch(Throwable problem){return problem;}}
    @Test void canonicalEmptyUsesSameActualJava25StreamResultAndAllEmptyListSemantics(){
        var a=original(List.of()).effects();var b=actual(List.of()).effects();
        assertSame(a,b);assertEquals(a.getClass(),b.getClass());assertEquals(a,b);assertEquals(a.hashCode(),b.hashCode());
        assertFalse(a.contains(null));assertFalse(b.contains(null));assertEquals(a.indexOf(null),b.indexOf(null));assertEquals(a.lastIndexOf(null),b.lastIndexOf(null));
        assertArrayEquals(a.toArray(),b.toArray());assertEquals(a.spliterator().characteristics(),b.spliterator().characteristics());
        assertEquals(a.spliterator().estimateSize(),b.spliterator().estimateSize());assertEquals(a.subList(0,0),b.subList(0,0));
        assertEquals(a.listIterator().hasNext(),b.listIterator().hasNext());assertEquals(a.listIterator().hasPrevious(),b.listIterator().hasPrevious());
        assertEquals(failure(()->a.add(null)).getClass(),failure(()->b.add(null)).getClass());
        assertEquals(failure(()->a.remove(null)).getClass(),failure(()->b.remove(null)).getClass());
        assertEquals(failure(a::clear).getClass(),failure(b::clear).getClass());
        assertEquals(failure(()->a.get(0)).getClass(),failure(()->b.get(0)).getClass());
    }
    @Test void everyForeignEmptyListRetainsOriginalStreamCallbacksAndEmptyStreamResult(){
        var a=new Foreign(List.of(),null);var b=new Foreign(List.of(),null);
        assertSame(original(a).effects(),actual(b).effects());assertEquals(a.trace,b.trace);assertEquals("stream",a.trace.getFirst());
        for(List<GroundCapturedEffects.Effect> values:List.of(new ArrayList<GroundCapturedEffects.Effect>(),java.util.Collections.<GroundCapturedEffects.Effect>emptyList(),Stream.<GroundCapturedEffects.Effect>empty().toList())){
            var expected=original(values).effects();var observed=actual(values).effects();assertSame(expected,observed);
        }
    }
    @Test void nonemptyForeignSortingDuplicateAndNullFailuresRetainOriginalCallbackPrefix(){
        var z=new GroundCapturedEffects.Effect("z",1,false,true,true);var a=new GroundCapturedEffects.Effect("a",0,false,true,true);
        for(List<GroundCapturedEffects.Effect> values:List.of(List.of(z,a),List.of(z,a,z),Arrays.asList(z,null),Arrays.asList(z,new GroundCapturedEffects.Effect(null,0,false,true,true)))){
            var before=new Foreign(values,null);var after=new Foreign(values,null);
            final List<?>[] resultA={null},resultB={null};var fa=failure(()->resultA[0]=original(before).effects());var fb=failure(()->resultB[0]=actual(after).effects());
            assertEquals(fa==null?null:fa.getClass(),fb==null?null:fb.getClass());assertEquals(resultA[0],resultB[0]);assertEquals(before.trace,after.trace);
        }
    }
    @Test void nullAndExactForeignStreamFailureKeepOriginalResultAndThrowableIdentity(){
        assertNull(original(null).effects());assertNull(actual(null).effects());
        Error originalFailure=new AssertionError("foreign stream");var a=new Foreign(List.of(),originalFailure);var b=new Foreign(List.of(),originalFailure);
        assertSame(originalFailure,failure(()->original(a)));assertSame(originalFailure,failure(()->actual(b)));assertEquals(List.of("stream"),a.trace);assertEquals(a.trace,b.trace);
        var mutable=new ArrayList<GroundCapturedEffects.Effect>();assertSame(original(mutable).effects(),actual(mutable).effects());
        mutable.add(new GroundCapturedEffects.Effect("z",0,false,true,true));assertEquals(original(mutable).effects(),actual(mutable).effects());
        mutable.clear();assertSame(original(mutable).effects(),actual(mutable).effects());
    }
}
