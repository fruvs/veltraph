package dev.veltraph.client.garden.pests;

import dev.veltraph.client.config.PestTrapMaintenanceConfig;
import dev.veltraph.client.garden.jacob.JacobContestTracker;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

final class PestTrapContestDispatchTest {
    private final JacobContestTracker tracker=new JacobContestTracker();
    private final JacobContestTracker.Scope scope=new JacobContestTracker.Scope(new Object(),new Object(),new Object(),"actual-farm-profile");
    private final List<String> traps=List.of("Garden","§aPrivate ready: ##--");
    private void sample(long first,String clock){for(long tick=first;tick<first+20;tick++)tracker.tick(scope,true,List.of("Jacob's Contest","Wheat "+clock),tick);}
    private boolean preview(long tick,List<String> rows,String prefix,long cutoff){return PestTrapContestDispatch.ready(tracker,scope,tick,true,true,rows,prefix,cutoff);}
    private boolean start(long tick,boolean nativeReady,boolean accepted,AtomicInteger calls){return PestTrapContestDispatch.start(tracker,scope,tick,true,true,traps,"Private ready:",11,()->nativeReady,()->{calls.incrementAndGet();return accepted;});}
    @Test void unknownNativePrefixAndCutoffNeverQualify(){
        assertEquals("",PestTrapMaintenanceConfig.PEST_TRAP_READY_TAB_PREFIX.defaultValue());
        assertEquals(-1L,PestTrapMaintenanceConfig.PEST_TRAP_CONTEST_CUTOFF_SECONDS.defaultValue());
        sample(0,"0m10s");assertFalse(preview(19,traps,"",11));assertFalse(preview(19,traps,"Private ready:",-1));
        assertFalse(tracker.trapConsumed());
    }
    @Test void exactFormattedTabPrefixAndLiteralHashCountAreRequired(){
        var observation=PestTrapReadiness.read(traps,"Private ready:");assertTrue(observation.known());assertEquals(2,observation.readyTraps());assertEquals("Private ready: ##--",observation.row());
        assertEquals(0,PestTrapReadiness.readyTraps(List.of("Other ready: ####","Private ready: --"),"Private ready:"));
        assertEquals(0,PestTrapReadiness.readyTraps(List.of("private ready: ##"),"Private ready:"));
        assertFalse(PestTrapReadiness.read(List.of("Private ready: ##","Private ready: #"),"Private ready:").known());
        assertFalse(PestTrapReadiness.read(traps,"§aPrivate ready:").known());assertFalse(PestTrapReadiness.read(null,"Private ready:").known());
    }
    @Test void firstNineteenCallbacksAndGlobalTickGapsCannotDispatch(){
        var calls=new AtomicInteger();for(int callback=0;callback<19;callback++){long tick=callback*100L;tracker.tick(scope,true,List.of("Jacob's Contest","Wheat 0m10s"),tick);assertFalse(start(tick,true,true,calls));}
        assertEquals(0,calls.get());tracker.tick(scope,true,List.of("Jacob's Contest","Wheat 0m10s"),1900);assertTrue(start(1900,true,true,calls));assertEquals(1,calls.get());
    }
    @Test void previewNeverConsumesAndCutoffIsStrict(){
        sample(0,"0m10s");assertFalse(preview(19,traps,"Private ready:",10));for(int i=0;i<5;i++)assertTrue(preview(19,traps,"Private ready:",11));assertFalse(tracker.trapConsumed());
    }
    @Test void disabledCollectionNoncontestPolicyAndZeroTrapsDoNotReadNativeActivation(){
        sample(0,"0m10s");var calls=new AtomicInteger();
        assertFalse(PestTrapContestDispatch.start(tracker,scope,19,false,true,traps,"Private ready:",11,()->{calls.incrementAndGet();return true;},()->true));
        assertFalse(PestTrapContestDispatch.start(tracker,scope,19,true,false,traps,"Private ready:",11,()->{calls.incrementAndGet();return true;},()->true));
        assertFalse(preview(19,List.of("Private ready: --"),"Private ready:",11));assertEquals(0,calls.get());assertFalse(tracker.trapConsumed());
    }
    @Test void nativeReadinessRefusalPreservesSampleButGuardDenialConsumesIt(){
        sample(0,"0m10s");var calls=new AtomicInteger();assertFalse(start(19,false,true,calls));assertFalse(tracker.trapConsumed());assertEquals(0,calls.get());
        assertFalse(start(19,true,false,calls));assertTrue(tracker.trapConsumed());assertEquals(1,calls.get());assertFalse(start(19,true,true,calls));assertEquals(1,calls.get());
    }
    @Test void acceptedStartOccursOnlyOnceAndNextActualTwentiethSampleReopensLatch(){
        sample(0,"0m10s");var calls=new AtomicInteger();assertTrue(start(19,true,true,calls));assertFalse(start(19,true,true,calls));
        for(long tick=20;tick<39;tick++)tracker.tick(scope,true,List.of("Jacob's Contest","Wheat 0m09s"),tick);
        assertFalse(start(38,true,true,calls));tracker.tick(scope,true,List.of("Jacob's Contest","Wheat 0m09s"),39);assertTrue(start(39,true,true,calls));assertEquals(2,calls.get());
    }
    @Test void unknownCropClockUnrelatedClockExpiredReadAndEndMessageCannotActivate(){
        sample(0,"unknown");assertFalse(preview(19,traps,"Private ready:",11));
        tracker.reset();for(long tick=0;tick<20;tick++)tracker.tick(scope,true,List.of("Jacob's Contest","Wheat","Time Left 0m01s"),tick);
        assertFalse(preview(19,traps,"Private ready:",11));tracker.reset();sample(0,"0m10s");assertFalse(preview(39,traps,"Private ready:",11));
        tracker.message(scope,JacobContestTracker.ENDED_MESSAGE);assertFalse(preview(19,traps,"Private ready:",11));
    }
    @Test void everyNativeIdentityAndActualFarmProfileMustMatch(){
        sample(0,"0m10s");for(var changed:List.of(new JacobContestTracker.Scope(new Object(),scope.world(),scope.connection(),scope.profile()),new JacobContestTracker.Scope(scope.player(),new Object(),scope.connection(),scope.profile()),new JacobContestTracker.Scope(scope.player(),scope.world(),new Object(),scope.profile()),new JacobContestTracker.Scope(scope.player(),scope.world(),scope.connection(),"other-farm"))){
            assertFalse(PestTrapContestDispatch.ready(tracker,changed,19,true,true,traps,"Private ready:",11));
        }assertFalse(tracker.trapConsumed());
    }
    @Test void activationExceptionCannotRetryConsumedRecord(){
        sample(0,"0m10s");assertThrows(IllegalStateException.class,()->PestTrapContestDispatch.start(tracker,scope,19,true,true,traps,"Private ready:",11,()->true,()->{throw new IllegalStateException("guard unavailable");}));
        assertTrue(tracker.trapConsumed());assertFalse(preview(19,traps,"Private ready:",11));
    }
}
