package dev.veltraph.client.combat.ghost;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import dev.veltraph.client.combat.ghost.GhostSourceScopeCosts.Part;

class GhostSourceScopeSamplingTest {
    private static boolean guard(GhostSourceScopeCosts costs,List<String> trace,boolean middle){
        var previous=GhostSourceScopeCosts.attachSampled(costs);long start=GhostSourceScopeCosts.begin(Part.FULL_SUPPLIER);
        try{return GhostSourceScopeCosts.flag(Part.REGISTRATION,()->{trace.add("registration");return true;})
            &&GhostSourceScopeCosts.flag(Part.SETTINGS_VALUES,()->{trace.add("settings");return middle;})
            &&GhostSourceScopeCosts.flag(Part.FINAL_REGISTRATION,()->{trace.add("final");return true;});}
        finally{GhostSourceScopeCosts.end(Part.FULL_SUPPLIER,start);GhostSourceScopeCosts.restore(previous);}
    }
    @Test void allSixtyFourActualInvocationsKeepExactFreshCallbackOrder(){
        var ticks=new AtomicLong(1);var clocks=new AtomicLong();
        var costs=new GhostSourceScopeCosts(64,()->{clocks.incrementAndGet();return ticks.getAndIncrement();});var trace=new ArrayList<String>();
        for(int i=0;i<64;i++)assertTrue(guard(costs,trace,true));
        assertEquals(192,trace.size());for(int i=0;i<64;i++)assertEquals(List.of("registration","settings","final"),trace.subList(i*3,i*3+3));
        var e=costs.cached();assertEquals(64,e.fullInvocations());assertEquals(1,e.measuredFullInvocations());assertEquals(64,e.samplingStride());
        assertEquals(1,e.samples().get(Part.FULL_SUPPLIER).calls());assertEquals(1,e.samples().get(Part.REGISTRATION).calls());assertEquals(8,clocks.get());
    }
    @Test void samplingBoundaryStartsAtFirstAndThenSixtyFifthWithoutExtrapolation(){
        var costs=new GhostSourceScopeCosts(64,()->1);for(int i=0;i<129;i++)assertTrue(guard(costs,new ArrayList<>(),true));
        assertEquals(129,costs.cached().fullInvocations());assertEquals(3,costs.cached().measuredFullInvocations());
        assertEquals(3,costs.cached().samples().get(Part.REGISTRATION).calls());
    }
    @Test void falseGuardStillShortCircuitsExactlyOnEverySampledAndUnsampledCall(){
        var costs=new GhostSourceScopeCosts(64,()->1);var trace=new ArrayList<String>();
        for(int i=0;i<64;i++)assertFalse(guard(costs,trace,false));
        assertEquals(128,trace.size());for(int i=0;i<64;i++)assertEquals(List.of("registration","settings"),trace.subList(i*2,i*2+2));
        assertEquals(0,costs.cached().samples().get(Part.FINAL_REGISTRATION).calls());
    }
    @Test void inactiveWrapperRunsOnceWithoutClockOrRecordingAndPropagatesSameException(){
        var previous=GhostSourceScopeCosts.attach(null);
        try{var calls=new AtomicInteger();var failure=new IllegalStateException("same callback failure");
            assertSame(failure,assertThrows(IllegalStateException.class,()->GhostSourceScopeCosts.value(Part.SETTINGS_VALUES,()->{calls.incrementAndGet();throw failure;})));
            assertEquals(1,calls.get());assertFalse(GhostSourceScopeCosts.flag(Part.REGISTRATION,()->{calls.incrementAndGet();return false;}));assertEquals(2,calls.get());
        }finally{GhostSourceScopeCosts.restore(previous);}
    }
    @Test void unsampledInnerDoesNotInheritSampledOuterAndRestoresIt(){
        var clocks=new AtomicLong(1);var outer=new GhostSourceScopeCosts(1,clocks::getAndIncrement);var inner=new GhostSourceScopeCosts(64,()->{throw new AssertionError("unsampled inner cannot read clock");});
        // Advance only the sampler metadata, without timed wrappers, so the next inner entry is unsampled.
        var p=GhostSourceScopeCosts.attachSampled(inner);GhostSourceScopeCosts.restore(p);
        var prior=GhostSourceScopeCosts.attach(outer);
        try{GhostSourceScopeCosts.value(Part.PARENT_STAMP_FIRST,()->{
            var previous=GhostSourceScopeCosts.attachSampled(inner);
            try{return GhostSourceScopeCosts.value(Part.SETTINGS_VALUES,()->"actual");}
            finally{GhostSourceScopeCosts.restore(previous);}
        });GhostSourceScopeCosts.flag(Part.FINAL_REGISTRATION,()->true);
        }finally{GhostSourceScopeCosts.restore(prior);}
        assertEquals(1,outer.cached().samples().get(Part.PARENT_STAMP_FIRST).calls());assertEquals(1,outer.cached().samples().get(Part.FINAL_REGISTRATION).calls());
        assertEquals(0,outer.cached().samples().get(Part.SETTINGS_VALUES).calls());assertEquals(0,inner.cached().samples().get(Part.SETTINGS_VALUES).calls());
        assertEquals(2,inner.cached().fullInvocations());assertEquals(1,inner.cached().measuredFullInvocations());
    }
    @Test void thrownUnsampledInnerRestoresOuterAndLeavesItsCallbacksFresh(){
        var inner=new GhostSourceScopeCosts(64,()->{throw new AssertionError("no inactive clock");});var p=GhostSourceScopeCosts.attachSampled(inner);GhostSourceScopeCosts.restore(p);
        var outer=new GhostSourceScopeCosts(1,()->1);var prior=GhostSourceScopeCosts.attach(outer);var failure=new IllegalArgumentException("actual failure");
        try{assertSame(failure,assertThrows(IllegalArgumentException.class,()->{
            var previous=GhostSourceScopeCosts.attachSampled(inner);try{GhostSourceScopeCosts.flag(Part.CHILD_HANDOFF,()->{throw failure;});}finally{GhostSourceScopeCosts.restore(previous);}
        }));assertTrue(GhostSourceScopeCosts.flag(Part.FINAL_REGISTRATION,()->true));}
        finally{GhostSourceScopeCosts.restore(prior);}
        assertEquals(1,outer.cached().samples().get(Part.FINAL_REGISTRATION).calls());assertEquals(0,inner.cached().samples().get(Part.CHILD_HANDOFF).calls());
    }
    @Test void manualAttachRemainsFullDetailForExistingPolicyTests(){
        var cost=new GhostSourceScopeCosts(64,()->1);var prior=GhostSourceScopeCosts.attach(cost);
        try{for(int i=0;i<64;i++)assertTrue(GhostSourceScopeCosts.flag(Part.REGISTRATION,()->true));}finally{GhostSourceScopeCosts.restore(prior);}
        assertEquals(64,cost.cached().samples().get(Part.REGISTRATION).calls());assertEquals(0,cost.cached().fullInvocations());
    }
    @Test void actualCoordinateReadFenceRejectsOwnerLossInsideReadEvenWhenDetailIsUnsampled(){
        var costs=new GhostSourceScopeCosts(64,()->1);var owner=new AtomicBoolean(true);var calls=new AtomicInteger();
        assertTrue(guard(costs,new ArrayList<>(),true)); // First measured entry already consumed.
        java.util.function.BooleanSupplier full=()->{var previous=GhostSourceScopeCosts.attachSampled(costs);try{return GhostSourceScopeCosts.flag(Part.INPUT_OWNER,()->{calls.incrementAndGet();return owner.get();});}finally{GhostSourceScopeCosts.restore(previous);}};
        assertFalse(GhostSourceCoordinateFence.read(full,()->true,()->{owner.set(false);return true;}));
        assertEquals(2,calls.get());assertEquals(0,costs.cached().samples().get(Part.INPUT_OWNER).calls());
    }
    @Test void actualPublicationFenceRejectsLateRegistrationLossInUnsampledCurrent(){
        var costs=new GhostSourceScopeCosts(64,()->1);assertTrue(guard(costs,new ArrayList<>(),true));
        var registered=new AtomicBoolean(true);var checks=new AtomicInteger();
        java.util.function.BooleanSupplier full=()->{var previous=GhostSourceScopeCosts.attachSampled(costs);try{return GhostSourceScopeCosts.flag(Part.FINAL_REGISTRATION,()->{checks.incrementAndGet();return registered.get();});}finally{GhostSourceScopeCosts.restore(previous);}};
        assertFalse(GhostSourceCoordinateFence.publication(full,()->true,()->{registered.set(false);return true;},()->true));
        assertEquals(2,checks.get());
    }
    @Test void exceptionalCallbackRetainsFailureAndFinallyTimingWithoutLaterReads(){
        var costs=new GhostSourceScopeCosts(64,()->1);var failure=new IllegalStateException("actual getter failure");var calls=new AtomicInteger();
        for(int i=0;i<64;i++){var previous=GhostSourceScopeCosts.attachSampled(costs);try{
            assertSame(failure,assertThrows(IllegalStateException.class,()->GhostSourceScopeCosts.value(Part.SETTINGS_VALUES,()->{calls.incrementAndGet();throw failure;})));
        }finally{GhostSourceScopeCosts.restore(previous);}}
        assertEquals(64,calls.get());assertEquals(1,costs.cached().samples().get(Part.SETTINGS_VALUES).calls());
    }
    @Test void cachedEvidenceOnlyReportsStoredCountersWithoutReadingClock(){
        var cost=new GhostSourceScopeCosts(64,()->{throw new AssertionError("cached must not touch clock");});
        var p=GhostSourceScopeCosts.attachSampled(cost);GhostSourceScopeCosts.restore(p);
        assertEquals(1,cost.cached().fullInvocations());assertEquals(1,cost.cached().measuredFullInvocations());
    }
}
