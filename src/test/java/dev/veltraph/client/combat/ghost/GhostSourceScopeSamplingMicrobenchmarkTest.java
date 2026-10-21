package dev.veltraph.client.combat.ghost;

import java.util.EnumMap;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import dev.veltraph.client.combat.ghost.GhostSourceScopeCosts.Part;

/** Actual profiler APIs over a declared mock successful guard. CPU timings are MOCK_ONLY,
 * not native measurements, throughput acceptance, or permission to change a recovery deadline. */
class GhostSourceScopeSamplingMicrobenchmarkTest {
    private static final class MockFacts {
        final EnumMap<Part,Long> values=new EnumMap<>(Part.class);long callbacks,checksum;
        MockFacts(){for(var part:Part.values())values.put(part,(long)part.ordinal()+1);}
        long read(Part part){callbacks++;long value=values.get(part);checksum+=value;return value;}
    }
    private static void value(Part part,MockFacts facts){GhostSourceScopeCosts.value(part,()->facts.read(part));}
    private static void parent(MockFacts facts){long began=GhostSourceScopeCosts.begin(Part.PARENT_STAMP_ALL);try{
        value(Part.ROOT_AUTHORITY,facts);value(Part.SETTINGS_VALUES,facts);value(Part.JUMP_OBSERVATION,facts);value(Part.STAMP_ALLOCATION,facts);
    }finally{GhostSourceScopeCosts.end(Part.PARENT_STAMP_ALL,began);}}
    private static void actualProfilerMockGuard(GhostSourceScopeCosts costs,MockFacts facts){
        var previous=GhostSourceScopeCosts.attachSampled(costs);long began=GhostSourceScopeCosts.begin(Part.FULL_SUPPLIER);
        try{value(Part.REGISTRATION,facts);GhostSourceScopeCosts.value(Part.PARENT_STAMP_FIRST,()->{facts.read(Part.PARENT_STAMP_FIRST);parent(facts);return true;});
            for(var part:new Part[]{Part.CHILD_GETTER,Part.CHILD_HANDOFF,Part.INPUT_OWNER,Part.ENABLED_CONTROLLERS_SIZE,Part.ENABLED_CONTROLLERS_FIRST,
                Part.CAMERA_OWNER,Part.CAMERA_EPOCH,Part.AREA_CURRENT,Part.DEVELOPER_REVISION,Part.DEVELOPER_SELECTION,Part.NATIVE_JUMP_FLAG,
                Part.WALK_SETTING,Part.ROUGH_SETTING,Part.TAIL_SETTING,Part.FORBIDDEN_SETTING})value(part,facts);
            value(Part.FINAL_REGISTRATION,facts);GhostSourceScopeCosts.value(Part.FINAL_PARENT_STAMP,()->{facts.read(Part.FINAL_PARENT_STAMP);parent(facts);return true;});
            value(Part.FINAL_FACTS,facts);value(Part.FINAL_REGISTRATION,facts);
        }finally{GhostSourceScopeCosts.end(Part.FULL_SUPPLIER,began);GhostSourceScopeCosts.restore(previous);}
    }
    @Test void modeledSuccessfulGuardKeepsAllTwentyNineCallbacksButSamplesThirtyTwoTimerSpans(){
        var fullClock=new AtomicLong(1);var sampledClock=new AtomicLong(1);var fullFacts=new MockFacts();var sampledFacts=new MockFacts();
        var full=new GhostSourceScopeCosts(1,fullClock::getAndIncrement);var sampled=new GhostSourceScopeCosts(64,sampledClock::getAndIncrement);
        for(int i=0;i<64;i++){actualProfilerMockGuard(full,fullFacts);actualProfilerMockGuard(sampled,sampledFacts);}
        assertEquals(64*29,fullFacts.callbacks);assertEquals(fullFacts.callbacks,sampledFacts.callbacks);assertEquals(fullFacts.checksum,sampledFacts.checksum);
        assertEquals(64*64,fullClock.get()-1);assertEquals(64,sampledClock.get()-1);
        assertEquals(64,full.cached().measuredFullInvocations());assertEquals(1,sampled.cached().measuredFullInvocations());
    }
    @Test void boundedActualProfilerMockMicrobenchmarkReportsTimesWithoutAssertingNativeGain(){
        for(int warm=0;warm<3;warm++){var warmFacts=new MockFacts();var warmCosts=new GhostSourceScopeCosts(warm%2==0?1:64);for(int i=0;i<512;i++)actualProfilerMockGuard(warmCosts,warmFacts);}
        int invocations=4096;var fullFacts=new MockFacts();var sampledFacts=new MockFacts();var full=new GhostSourceScopeCosts(1);var sampled=new GhostSourceScopeCosts(64);
        long began=System.nanoTime();for(int i=0;i<invocations;i++)actualProfilerMockGuard(full,fullFacts);long fullNanos=System.nanoTime()-began;
        began=System.nanoTime();for(int i=0;i<invocations;i++)actualProfilerMockGuard(sampled,sampledFacts);long sampledNanos=System.nanoTime()-began;
        assertEquals(invocations*29L,fullFacts.callbacks);assertEquals(fullFacts.callbacks,sampledFacts.callbacks);assertEquals(fullFacts.checksum,sampledFacts.checksum);
        assertEquals(invocations,full.cached().measuredFullInvocations());assertEquals(invocations/64,sampled.cached().measuredFullInvocations());
        System.out.println("GHOST_SCOPE_PROFILE_MOCK_BENCHMARK invocations="+invocations+" callbacks="+fullFacts.callbacks+" fullNanos="+fullNanos+" sampledNanos="+sampledNanos+" qualification=MOCK_ONLY_NO_NATIVE_GAIN_OR_DEADLINE_CLAIM");
    }
}
