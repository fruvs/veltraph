package dev.veltraph.client.combat.ghost;

import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import dev.veltraph.client.combat.ghost.GhostSourceScopeCosts.Part;

class GhostSourceScopeNullSlotTest {
    @Test void restoringNullLeavesEveryOutsideCallbackFreshWithoutStaleCollectorOrClock(){
        var clocks=new AtomicLong(1);var calls=new AtomicInteger();var costs=new GhostSourceScopeCosts(64,clocks::getAndIncrement);
        var original=GhostSourceScopeCosts.attach(null);
        try{
            var prior=GhostSourceScopeCosts.attachSampled(costs);assertNull(prior);
            try{assertTrue(GhostSourceScopeCosts.flag(Part.INPUT_OWNER,()->{calls.incrementAndGet();return true;}));}
            finally{GhostSourceScopeCosts.restore(prior);}
            var evidence=costs.cached();long after=clocks.get();
            assertEquals(9,GhostSourceScopeCosts.value(Part.INPUT_OWNER,()->{calls.incrementAndGet();return 9;}).intValue());
            assertEquals(0,GhostSourceScopeCosts.begin(Part.INPUT_OWNER));assertEquals(after,clocks.get());assertEquals(evidence,costs.cached());assertEquals(2,calls.get());
            var observed=GhostSourceScopeCosts.attach(null);assertNull(observed);GhostSourceScopeCosts.restore(observed);
        }finally{GhostSourceScopeCosts.restore(original);}
    }
    @Test void unsampledInnerStillHidesOuterAndRestoresItAfterException(){
        var clocks=new AtomicLong(1);var outer=new GhostSourceScopeCosts(1,clocks::getAndIncrement);
        var inner=new GhostSourceScopeCosts(64,()->{throw new AssertionError("unsampled inner clock");});
        var priming=GhostSourceScopeCosts.attachSampled(inner);GhostSourceScopeCosts.restore(priming);
        var original=GhostSourceScopeCosts.attach(outer);var failure=new IllegalStateException("original inner failure");var trace=new ArrayList<String>();
        try{
            assertTrue(GhostSourceScopeCosts.flag(Part.REGISTRATION,()->{trace.add("outer-before");return true;}));
            var previous=GhostSourceScopeCosts.attachSampled(inner);assertSame(outer,previous);
            try{assertSame(failure,assertThrows(IllegalStateException.class,()->GhostSourceScopeCosts.value(Part.SETTINGS_VALUES,()->{trace.add("inner");throw failure;})));}
            finally{GhostSourceScopeCosts.restore(previous);}
            assertTrue(GhostSourceScopeCosts.flag(Part.FINAL_REGISTRATION,()->{trace.add("outer-after");return true;}));
        }finally{GhostSourceScopeCosts.restore(original);}
        assertEquals(List.of("outer-before","inner","outer-after"),trace);
        assertEquals(1,outer.cached().samples().get(Part.REGISTRATION).calls());assertEquals(1,outer.cached().samples().get(Part.FINAL_REGISTRATION).calls());
        assertEquals(0,outer.cached().samples().get(Part.SETTINGS_VALUES).calls());assertEquals(0,inner.cached().samples().get(Part.SETTINGS_VALUES).calls());
        assertEquals(2,inner.cached().fullInvocations());assertEquals(1,inner.cached().measuredFullInvocations());
    }
    @Test void measuredNestedFailureRecordsInnerThenRestoresExactOuter(){
        var clock=new AtomicLong(1);var outer=new GhostSourceScopeCosts(1,clock::getAndIncrement);var inner=new GhostSourceScopeCosts(64,clock::getAndIncrement);
        var original=GhostSourceScopeCosts.attach(outer);var failure=new AssertionError("same original Error");
        try{
            assertSame(failure,assertThrows(AssertionError.class,()->GhostSourceScopeCosts.value(Part.PARENT_STAMP_FIRST,()->{
                var previous=GhostSourceScopeCosts.attachSampled(inner);try{return GhostSourceScopeCosts.value(Part.CHILD_HANDOFF,()->{throw failure;});}
                finally{GhostSourceScopeCosts.restore(previous);}
            })));
            assertTrue(GhostSourceScopeCosts.flag(Part.FINAL_REGISTRATION,()->true));
        }finally{GhostSourceScopeCosts.restore(original);}
        assertEquals(1,outer.cached().samples().get(Part.PARENT_STAMP_FIRST).calls());assertEquals(1,outer.cached().samples().get(Part.FINAL_REGISTRATION).calls());
        assertEquals(0,outer.cached().samples().get(Part.CHILD_HANDOFF).calls());assertEquals(1,inner.cached().samples().get(Part.CHILD_HANDOFF).calls());
    }
    @Test void originalStrideAndShortCircuitRemainExactThroughRepeatedNullRestoration(){
        var clock=new AtomicLong(1);var costs=new GhostSourceScopeCosts(64,clock::getAndIncrement);var trace=new ArrayList<String>();
        var original=GhostSourceScopeCosts.attach(null);
        try{for(int i=0;i<129;i++){
            var previous=GhostSourceScopeCosts.attachSampled(costs);long began=GhostSourceScopeCosts.begin(Part.FULL_SUPPLIER);
            try{assertFalse(GhostSourceScopeCosts.flag(Part.REGISTRATION,()->{trace.add("registration");return true;})
                &&GhostSourceScopeCosts.flag(Part.SETTINGS_VALUES,()->{trace.add("settings");return false;})
                &&GhostSourceScopeCosts.flag(Part.FINAL_REGISTRATION,()->{fail("short circuit callback");return true;}));}
            finally{GhostSourceScopeCosts.end(Part.FULL_SUPPLIER,began);GhostSourceScopeCosts.restore(previous);}
            assertEquals(0,GhostSourceScopeCosts.begin(Part.REGISTRATION));
        }}finally{GhostSourceScopeCosts.restore(original);}
        assertEquals(258,trace.size());for(int i=0;i<129;i++)assertEquals(List.of("registration","settings"),trace.subList(2*i,2*i+2));
        var e=costs.cached();assertEquals(129,e.fullInvocations());assertEquals(3,e.measuredFullInvocations());assertEquals(64,e.samplingStride());
        assertEquals(3,e.samples().get(Part.FULL_SUPPLIER).calls());assertEquals(3,e.samples().get(Part.SETTINGS_VALUES).calls());assertEquals(0,e.samples().get(Part.FINAL_REGISTRATION).calls());
    }
    @Test void repeatedEmptyCleanupCannotRetainThePriorCollector(){
        var costs=new GhostSourceScopeCosts(1,()->1);var original=GhostSourceScopeCosts.attach(null);
        try{
            var previous=GhostSourceScopeCosts.attach(costs);try{assertTrue(GhostSourceScopeCosts.flag(Part.INPUT_OWNER,()->true));}finally{GhostSourceScopeCosts.restore(previous);}
            var before=costs.cached();for(int i=0;i<10000;i++){
                GhostSourceScopeCosts.restore(null);assertEquals(7,GhostSourceScopeCosts.value(Part.INPUT_OWNER,()->7).intValue());
            }
            assertEquals(before,costs.cached());assertNull(GhostSourceScopeCosts.attach(null));
        }finally{GhostSourceScopeCosts.restore(original);}
    }
    @Test void anotherThreadNeitherInheritsOuterCollectorNorLeaksItsOwnContext() throws InterruptedException {
        var outer=new GhostSourceScopeCosts(1,()->1);var inner=new GhostSourceScopeCosts(1,()->1);var failure=new AtomicReference<Throwable>();
        var original=GhostSourceScopeCosts.attach(outer);
        try{
            var worker=new Thread(()->{try{
                assertEquals(0,GhostSourceScopeCosts.begin(Part.REGISTRATION));var previous=GhostSourceScopeCosts.attach(inner);assertNull(previous);
                try{assertTrue(GhostSourceScopeCosts.flag(Part.REGISTRATION,()->true));}finally{GhostSourceScopeCosts.restore(previous);}
                assertEquals(0,GhostSourceScopeCosts.begin(Part.REGISTRATION));assertTrue(GhostSourceScopeCosts.flag(Part.SETTINGS_VALUES,()->true));
            }catch(Throwable error){failure.set(error);}},"Veltraph-Scope-NullSlot-Test");
            worker.setDaemon(true);worker.start();worker.join(5000);assertFalse(worker.isAlive(),"bounded diagnostic context thread");
            assertNull(failure.get());assertTrue(GhostSourceScopeCosts.flag(Part.FINAL_REGISTRATION,()->true));
        }finally{GhostSourceScopeCosts.restore(original);}
        assertEquals(0,outer.cached().samples().get(Part.REGISTRATION).calls());assertEquals(1,outer.cached().samples().get(Part.FINAL_REGISTRATION).calls());
        assertEquals(1,inner.cached().samples().get(Part.REGISTRATION).calls());assertEquals(0,inner.cached().samples().get(Part.SETTINGS_VALUES).calls());
    }
}
