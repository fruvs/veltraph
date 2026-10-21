package dev.veltraph.client.navigation.mc;

import dev.veltraph.client.config.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Actual private pure selector via reflection and actual data producer; no Minecraft is constructed. */
final class ConfigurationWrapperFactoryParityTest {
    private static final List<ConfigKey<?>> KEYS=List.of(new ConfigKey<Object>("a",1D),new ConfigKey<Object>("b","same"));
    private static Object select(OrderedConfigurationData.Data data,Object prior){
        try{Class<?> pair=Class.forName("dev.veltraph.client.navigation.mc.LegacyTravelPublicationLease$ConfigurationView");
            Method selector=LegacyTravelPublicationLease.class.getDeclaredMethod("configurationView",OrderedConfigurationData.Data.class,pair);selector.setAccessible(true);return selector.invoke(null,data,prior);
        }catch(InvocationTargetException e){if(e.getCause() instanceof RuntimeException original)throw original;if(e.getCause() instanceof Error original)throw original;throw new AssertionError(e.getCause());}
        catch(ReflectiveOperationException e){throw new AssertionError(e);}
    }
    private static Object property(Object object,String name){
        try{Method getter=object.getClass().getDeclaredMethod(name);getter.setAccessible(true);return getter.invoke(object);}
        catch(ReflectiveOperationException e){throw new AssertionError(e);}
    }
    @SuppressWarnings("unchecked") private static List<Long> epochs(Object wrapper){return (List<Long>)property(wrapper,"epochs");}
    @SuppressWarnings("unchecked") private static List<Object> values(Object wrapper){return (List<Object>)property(wrapper,"values");}
    private static final class Store implements ConfigService {
        final Map<String,Object> values=new LinkedHashMap<>();final Map<String,Long> epochs=new LinkedHashMap<>();final ArrayList<String> trace=new ArrayList<>();
        String absent,throwAt;Throwable fault;Consumer<ConfigKey<?>> onGet=key->{};
        Store(){for(var key:KEYS){values.put(key.name(),key.defaultValue());epochs.put(key.name(),0L);}}
        void event(String name){trace.add(name);if(name.equals(throwAt)){if(fault instanceof RuntimeException e)throw e;if(fault instanceof Error e)throw e;throw new AssertionError("unchecked fault required");}}
        public OptionalLong revision(ConfigKey<?> key){event("revision:"+key.name());return key.name().equals(absent)?OptionalLong.empty():OptionalLong.of(epochs.get(key.name()));}
        @SuppressWarnings("unchecked") public <T>T get(ConfigKey<T> key){event("get:"+key.name());onGet.accept(key);return (T)values.get(key.name());}
        public <T>void set(ConfigKey<T> key,T value){values.put(key.name(),value);epochs.merge(key.name(),1L,Long::sum);}
        public boolean isSet(ConfigKey<?> key){return values.containsKey(key.name());}
        public void reset(ConfigKey<?> key){values.put(key.name(),key.defaultValue());epochs.merge(key.name(),1L,Long::sum);}
    }
    /** Composition control only; source proof separately binds the real configuration() capture/field publication. */
    private static final class Driver {
        final OrderedConfigurationData producer=new OrderedConfigurationData(KEYS);Object pair;
        Object capture(Store store){var data=producer.capture(store);if(data==null)return null;var next=select(data,pair);pair=next;return property(next,"configuration");}
    }
    private static final List<String> FULL=List.of("revision:a","get:a","revision:b","get:b");
    @Test void exactReturnedDataIdentityReusesOnePrivatePairAndWrapper(){
        var producer=new OrderedConfigurationData(KEYS);var s=new Store();var data=producer.capture(s);var first=select(data,null);var second=select(data,first);
        assertSame(first,second);assertSame(data,property(first,"data"));assertSame(property(first,"configuration"),property(second,"configuration"));
        var frozen=FrozenAMConfigurationFactory.create(data);assertEquals(frozen.epochs(),epochs(property(first,"configuration")));assertEquals(frozen.values(),values(property(first,"configuration")));
        assertNotSame(frozen,FrozenAMConfigurationFactory.create(data));
    }
    @Test void valueEqualDistinctDataCannotSelectStalePair(){
        var producer=new OrderedConfigurationData(KEYS);var s=new Store();var data=producer.capture(s);var pair=select(data,null);
        var sameValues=new OrderedConfigurationData.Data(data.epochs(),data.values());var next=select(sameValues,pair);assertNotSame(pair,next);assertSame(sameValues,property(next,"data"));
        assertNotSame(property(pair,"configuration"),property(next,"configuration"));assertEquals(epochs(property(pair,"configuration")),epochs(property(next,"configuration")));
    }
    @Test void everyWrapperHitStillFollowsAllFreshOrderedReads(){
        var driver=new Driver();var s=new Store();var first=driver.capture(s);assertEquals(FULL,s.trace);
        for(int n=0;n<5;n++){s.trace.clear();assertSame(first,driver.capture(s));assertEquals(FULL,s.trace);}
        s.epochs.put("b",1L);s.trace.clear();var next=driver.capture(s);assertNotSame(first,next);assertEquals(FULL,s.trace);assertEquals(List.of(0L,1L),epochs(next));
    }
    private static final class ForeignValue {
        int comparisons;
        public boolean equals(Object other){comparisons++;return other==this;}
        public int hashCode(){return System.identityHashCode(this);}
    }
    @Test void noncacheableForeignCaptureKeepsFreshWrapperAndOriginalRecordComparison(){
        var driver=new Driver();var s=new Store();var foreign=new ForeignValue();s.values.put("b",foreign);var first=driver.capture(s);var second=driver.capture(s);
        assertNotSame(first,second);assertSame(foreign,values(first).get(1));assertSame(foreign,values(second).get(1));assertEquals(0,foreign.comparisons);
        assertTrue(first.equals(second));assertEquals(0,foreign.comparisons); // original Objects.equals sees same element identity
        var other=new ForeignValue();s.values.put("b",other);var third=driver.capture(s);assertNotSame(second,third);assertFalse(second.equals(third));assertEquals(1,foreign.comparisons);
    }
    @Test void invalidNullAndThrownCaptureCannotReturnCachedWrapper(){
        var d=new Driver();var s=new Store();var first=d.capture(s);var heldPair=d.pair;s.absent="a";s.trace.clear();assertNull(d.capture(s));assertSame(heldPair,d.pair);assertEquals(List.of("revision:a"),s.trace);
        s.absent=null;s.values.put("b",null);s.trace.clear();assertThrows(NullPointerException.class,()->d.capture(s));assertSame(heldPair,d.pair);assertEquals(FULL,s.trace);
        s.values.put("b","same");for(String event:FULL)for(Throwable original:List.of(new IllegalStateException(event),new AssertionError(event))){s.throwAt=event;s.fault=original;s.trace.clear();assertSame(original,assertThrows(Throwable.class,()->d.capture(s)));assertSame(heldPair,d.pair);}
        s.throwAt=null;assertSame(first,d.capture(s));assertThrows(NullPointerException.class,()->select(null,heldPair));
    }
    @Test void reentrantGetCannotPairNestedDataWithOuterWrapper(){
        var d=new Driver();var outer=new Store();var original=d.capture(outer);var nested=new Store();nested.values.put("b","nested");final Object[] inner={null};
        outer.trace.clear();outer.onGet=key->{if(key==KEYS.getFirst()){outer.onGet=k->{};inner[0]=d.capture(nested);}};
        var restored=d.capture(outer);assertEquals(FULL,outer.trace);assertEquals(FULL,nested.trace);assertNotSame(inner[0],restored);assertEquals(values(original),values(restored));
        assertEquals("same",values(restored).get(1));assertEquals("nested",values(inner[0]).get(1));assertSame(restored,property(d.pair,"configuration"));
    }
    @Test void immutablePairArgumentsCannotBeCrossPublished(){
        var a=new OrderedConfigurationData.Data(List.of(1L),List.of("a"));var b=new OrderedConfigurationData.Data(List.of(2L),List.of("b"));
        var first=select(a,null);var other=select(b,first);var back=select(a,other);
        assertSame(a,property(first,"data"));assertSame(b,property(other,"data"));assertSame(a,property(back,"data"));
        assertEquals(List.of("a"),values(property(first,"configuration")));assertEquals(List.of("b"),values(property(other,"configuration")));assertEquals(List.of("a"),values(property(back,"configuration")));
    }
    @Test void emptyDataAndImmutableListsKeepOriginalFactoryValues(){
        var empty=new OrderedConfigurationData(List.of()).capture(new Store());var pair=select(empty,null);var wrapper=property(pair,"configuration");assertTrue(epochs(wrapper).isEmpty());assertTrue(values(wrapper).isEmpty());
        assertThrows(UnsupportedOperationException.class,()->values(wrapper).add("bad"));assertThrows(UnsupportedOperationException.class,()->epochs(wrapper).add(3L));assertSame(pair,select(empty,pair));
    }
}
