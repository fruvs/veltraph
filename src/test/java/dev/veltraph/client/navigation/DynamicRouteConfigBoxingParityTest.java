package dev.veltraph.client.navigation;

import dev.veltraph.client.config.*;
import java.util.*;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Actual production current vs source-pinned original; ConfigService is an actual extensible API. */
final class DynamicRouteConfigBoxingParityTest {
    private static final long ROTATION_REVISION=7,APPROACH_REVISION=11;
    private static final class Store implements ConfigService {
        final ArrayList<String> trace=new ArrayList<>();
        Object rotation=Double.valueOf(1D);Object approach=ApproachMode.AUTO;
        boolean rotationPresent=true,approachPresent=true;
        long initialRotation=ROTATION_REVISION,initialApproach=APPROACH_REVISION,finalRotation=ROTATION_REVISION,finalApproach=APPROACH_REVISION;
        int rotationReads,approachReads;String faultEvent;Throwable fault;
        void event(String name){trace.add(name);if(name.equals(faultEvent))raise(fault);}
        @Override public OptionalLong revision(ConfigKey<?> key){
            if(key==RotationConfig.ROTATION_SPEED_MULTIPLIER){int n=++rotationReads;event("rev:rotation:"+n);return rotationPresent?OptionalLong.of(n==1?initialRotation:finalRotation):OptionalLong.empty();}
            assertSame(TravelConfig.TRAVEL_APPROACH_MODE,key);int n=++approachReads;event("rev:approach:"+n);return approachPresent?OptionalLong.of(n==1?initialApproach:finalApproach):OptionalLong.empty();
        }
        @SuppressWarnings("unchecked") @Override public <T>T get(ConfigKey<T> key){
            if(key==RotationConfig.ROTATION_SPEED_MULTIPLIER){event("get:rotation");return (T)rotation;}
            assertSame(TravelConfig.TRAVEL_APPROACH_MODE,key);event("get:approach");return (T)approach;
        }
        @Override public <T>void set(ConfigKey<T> key,T value){throw new UnsupportedOperationException();}
        @Override public boolean isSet(ConfigKey<?> key){throw new UnsupportedOperationException();}
        @Override public void reset(ConfigKey<?> key){throw new UnsupportedOperationException();}
    }
    private static void raise(Throwable original){if(original instanceof RuntimeException e)throw e;if(original instanceof Error e)throw e;throw new AssertionError("test requires original unchecked fault");}
    private static BooleanSupplier check(boolean actual,Store store,double expected){
        return actual?()->new DynamicRouteConfigStamp(store,ROTATION_REVISION,expected,APPROACH_REVISION,ApproachMode.AUTO).current(store)
                :()->new FrozenAJDynamicRouteConfigStamp(store,ROTATION_REVISION,expected,APPROACH_REVISION,ApproachMode.AUTO).current(store);
    }
    private static final List<String> FULL=List.of("rev:rotation:1","rev:approach:1","get:rotation","get:approach","rev:rotation:2","rev:approach:2");
    private static void pair(Store old,Store actual,double expected){
        assertEquals(check(false,old,expected).getAsBoolean(),check(true,actual,expected).getAsBoolean());assertEquals(old.trace,actual.trace);
    }
    @Test void closedDoubleValuesRetainCanonicalNaNsSignedZerosAndFiniteEquality(){
        double[] values={0D,-0D,1D,-1D,Double.MIN_VALUE,Double.MAX_VALUE,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY,
                Double.longBitsToDouble(0x7ff8000000000001L),Double.longBitsToDouble(0xfff8000000000042L)};
        for(double expected:values)for(double sampled:values){var old=new Store();var actual=new Store();old.rotation=actual.rotation=Double.valueOf(sampled);pair(old,actual,expected);
            assertEquals(Double.valueOf(sampled).equals(Double.valueOf(expected)),check(true,newStore(sampled),expected).getAsBoolean());}
        Random random=new Random(0xA1438L);
        for(int i=0;i<1000;i++){double expected=Double.longBitsToDouble(random.nextLong()),sampled=i%2==0?expected:Double.longBitsToDouble(random.nextLong());var old=newStore(sampled);var actual=newStore(sampled);pair(old,actual,expected);}
    }
    private static Store newStore(double value){var s=new Store();s.rotation=Double.valueOf(value);return s;}
    @Test void missingOrMismatchedInitialRevisionsNeverReadValues(){
        for(int scenario=0;scenario<4;scenario++){var old=new Store();var actual=new Store();
            if(scenario==0)old.rotationPresent=actual.rotationPresent=false;
            if(scenario==1)old.approachPresent=actual.approachPresent=false;
            if(scenario==2)old.initialRotation=actual.initialRotation=99;
            if(scenario==3)old.initialApproach=actual.initialApproach=99;
            pair(old,actual,1D);assertEquals(List.of("rev:rotation:1","rev:approach:1"),actual.trace);}
    }
    @Test void differentStoreAndNullStoreRetainOriginalEarlyPrefix(){
        var expectedStore=new Store();var other=new Store();
        assertFalse(new FrozenAJDynamicRouteConfigStamp(expectedStore,7,1D,11,ApproachMode.AUTO).current(other));
        assertFalse(new DynamicRouteConfigStamp(expectedStore,7,1D,11,ApproachMode.AUTO).current(other));
        assertFalse(new DynamicRouteConfigStamp(expectedStore,7,1D,11,ApproachMode.AUTO).current(null));assertTrue(other.trace.isEmpty());assertTrue(expectedStore.trace.isEmpty());
        assertThrows(NullPointerException.class,()->new FrozenAJDynamicRouteConfigStamp(null,7,1D,11,ApproachMode.AUTO).current(null));
        assertThrows(NullPointerException.class,()->new DynamicRouteConfigStamp(null,7,1D,11,ApproachMode.AUTO).current(null));
    }
    @Test void falseRotationNullAndWrongApproachKeepOriginalReadShortCircuit(){
        for(int scenario=0;scenario<3;scenario++){var old=new Store();var actual=new Store();
            if(scenario==0)old.rotation=actual.rotation=Double.valueOf(2D);
            if(scenario==1)old.rotation=actual.rotation=null;
            if(scenario==2)old.approach=actual.approach=ApproachMode.FLY;
            pair(old,actual,1D);assertEquals(scenario==2?FULL.subList(0,4):FULL.subList(0,3),actual.trace);}
    }
    @Test void finalRevisionReadsStillRefuseChangesAndRevertedPolicies(){
        for(int scenario=0;scenario<2;scenario++){var old=new Store();var actual=new Store();
            if(scenario==0)old.finalRotation=actual.finalRotation=ROTATION_REVISION+2;
            else old.finalApproach=actual.finalApproach=APPROACH_REVISION+2;
            pair(old,actual,1D);assertEquals(scenario==0?FULL.subList(0,5):FULL,actual.trace);}
    }
    private static final class ForeignEquality {
        final Store store;final Class<?> currentClass;final ArrayList<Object> expectedBoxes=new ArrayList<>();boolean answer=true;Runnable after=()->{};
        ForeignEquality(Store store,boolean actual){this.store=store;currentClass=actual?DynamicRouteConfigStamp.class:FrozenAJDynamicRouteConfigStamp.class;}
        @Override public boolean equals(Object expected){
            expectedBoxes.add(expected);assertInstanceOf(Double.class,expected);
            var frames=StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(s->s.limit(5).toList());
            assertEquals(Objects.class,frames.get(1).getDeclaringClass());assertEquals("equals",frames.get(1).getMethodName());
            assertEquals(currentClass,frames.get(2).getDeclaringClass());assertEquals("current",frames.get(2).getMethodName());
            assertEquals("(Ldev/veltraph/client/config/ConfigService;)Z",frames.get(2).getDescriptor());
            store.event("equals:foreign");after.run();return answer;
        }
        @Override public int hashCode(){return System.identityHashCode(this);}
    }
    @Test void foreignWrongKindKeepsSameCurrentFrameAndFreshExpectedBoxOnEachCall(){
        for(boolean actual:new boolean[]{false,true}){var s=new Store();var foreign=new ForeignEquality(s,actual);s.rotation=foreign;var check=check(actual,s,1D);
            assertTrue(check.getAsBoolean());assertTrue(check.getAsBoolean());assertEquals(2,foreign.expectedBoxes.size());assertNotSame(foreign.expectedBoxes.get(0),foreign.expectedBoxes.get(1));
            assertEquals(Double.valueOf(1D),foreign.expectedBoxes.get(0));assertEquals(Double.valueOf(1D),foreign.expectedBoxes.get(1));
            foreign.answer=false;s.trace.clear();assertFalse(check.getAsBoolean());assertEquals(List.of("rev:rotation:5","rev:approach:5","get:rotation","equals:foreign"),s.trace);}
    }
    @Test void originalProviderAndForeignEqualityFaultObjectsSurviveUnchanged(){
        for(String site:List.of("rev:rotation:1","rev:approach:1","get:rotation","equals:foreign","get:approach","rev:rotation:2","rev:approach:2"))
            for(Throwable fault:List.of(new IllegalStateException("original "+site),new AssertionError("original "+site))){
                List<String> first=null;for(boolean actual:new boolean[]{false,true}){var s=new Store();s.faultEvent=site;s.fault=fault;
                    if(site.equals("equals:foreign"))s.rotation=new ForeignEquality(s,actual);
                    var thrown=assertThrows(Throwable.class,()->check(actual,s,1D).getAsBoolean());assertSame(fault,thrown);
                    if(first==null)first=List.copyOf(s.trace);else assertEquals(first,s.trace);}}
    }
    @Test void foreignEqualsPolicyMutationRemainsVisibleToFinalRevisionCheck(){
        for(boolean actual:new boolean[]{false,true}){var s=new Store();var foreign=new ForeignEquality(s,actual);foreign.after=()->s.finalRotation=ROTATION_REVISION+1;s.rotation=foreign;
            assertFalse(check(actual,s,1D).getAsBoolean());assertEquals(List.of("rev:rotation:1","rev:approach:1","get:rotation","equals:foreign","get:approach","rev:rotation:2"),s.trace);}
    }
}
