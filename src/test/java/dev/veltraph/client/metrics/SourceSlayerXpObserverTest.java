package dev.veltraph.client.metrics;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SourceSlayerXpObserverTest {
    private final SourceSlayerXpObserver observer=new SourceSlayerXpObserver();
    private final SourceSlayerXpObserver.Context context=new SourceSlayerXpObserver.Context(new Object(),new Object(),new Object());
    private void observe(String title,String xp,long time){observer.observe(context,SourceSlayerXpProjectionTest.rows(title,xp),time);}
    private SourceSlayerXpObserver.Snapshot state(){return observer.snapshot().orElseThrow();}
    @Test void firstDisplayedTotalIsOnlyBaselineAndStrictIncreaseCannotReplayOrRegress(){
        observer.bind(context);observe("Zombie Slayer VII","100k/1M",1);assertEquals(0,state().withinLevelObservedIncrease());var generation=state().generation();
        observe("Zombie Slayer VII","101k/1M",2);observe("Zombie Slayer VII","101k/1M",3);observe("Zombie Slayer VII","99k/1M",4);
        assertEquals(1000,state().withinLevelObservedIncrease());assertEquals(101000,state().displayedCurrentXp());assertEquals(2,state().lastAcceptedAt());assertEquals(4,state().lastPolledAt());assertEquals(generation,state().generation());
        assertEquals(SourceSlayerXpObserver.Provenance.SOURCE_POLLED_TAB_WIDGET,state().provenance());
    }
    @Test void sourceLevelIncreaseClearsThenBaselinesNextConsistentObservation(){
        observer.bind(context);observe("Zombie Slayer VII","100k/1M",1);observe("Zombie Slayer VII","101k/1M",2);var old=state().generation();
        observe("Zombie Slayer VIII","3k/2M",3);assertTrue(observer.snapshot().isEmpty());assertEquals(SourceSlayerXpObserver.Availability.LEVEL_RESET,observer.availability());
        observe("Zombie Slayer VIII","3k/2M",4);assertEquals(0,state().withinLevelObservedIncrease());assertEquals(3000,state().baselineDisplayedXp());assertNotEquals(old,state().generation());
        observe("Zombie Slayer VIII","4k/2M",5);assertEquals(1000,state().withinLevelObservedIncrease());
    }
    @Test void selectionLevelDecreaseAndUnknownGapBreakContinuityInsteadOfMixingRewardStreams(){
        observer.bind(context);observe("Zombie Slayer VIII","3k/2M",1);var first=state().generation();
        observe("Wolf Slayer IV","800/1000",2);assertEquals(0,state().withinLevelObservedIncrease());assertNotEquals(first,state().generation());
        observe("Wolf Slayer III","500/1000",3);assertTrue(observer.snapshot().isEmpty());observe("Wolf Slayer III","500/1000",4);assertEquals(0,state().withinLevelObservedIncrease());
        observer.observe(context,List.of(),5);assertTrue(observer.snapshot().isEmpty());observe("Wolf Slayer III","900/1000",6);assertEquals(0,state().withinLevelObservedIncrease());
    }
    @Test void maxThenNumericDataDoesNotCreditASentinelOrTheMissingInterval(){
        observer.bind(context);observe("Zombie Slayer IX","MAX",1);assertEquals(SourceSlayerXpObserver.Availability.MAX,observer.availability());assertTrue(observer.snapshot().isEmpty());
        observe("Zombie Slayer IX","500k/1M",2);assertEquals(0,state().withinLevelObservedIncrease());assertEquals(500000,state().displayedCurrentXp());
    }
    @Test void goalRefreshDoesNotCreateEarnedXpAndRetainsCompactObservationProvenance(){
        observer.bind(context);observe("Zombie Slayer VII","100k/1M",1);observe("Zombie Slayer VII","100000/2000000",2);
        assertEquals(2000000,state().displayedGoalXp());assertEquals(0,state().withinLevelObservedIncrease());assertEquals(1,state().lastAcceptedAt());assertTrue(state().compactDisplay());
    }
    @Test void exactNativeReferenceChangesRebaselineAndForeignScopeOrClockRegressionCannotMutate(){
        observer.bind(context);observe("Zombie Slayer VII","100k/1M",10);observe("Zombie Slayer VII","101k/1M",11);var original=state();
        var foreign=new SourceSlayerXpObserver.Context(new Object(),context.world(),context.connection());observer.observe(foreign,SourceSlayerXpProjectionTest.rows("Zombie Slayer VII","102k/1M"),12);
        observe("Zombie Slayer VII","102k/1M",9);assertEquals(original,state());
        for(int changed=0;changed<3;changed++){
            var next=new SourceSlayerXpObserver.Context(changed==0?new Object():context.player(),changed==1?new Object():context.world(),changed==2?new Object():context.connection());
            observer.bind(next);observer.observe(next,SourceSlayerXpProjectionTest.rows("Zombie Slayer VII","900k/1M"),20);assertEquals(0,state().withinLevelObservedIncrease());
        }
        observer.close();assertTrue(observer.snapshot().isEmpty());assertEquals(SourceSlayerXpObserver.Availability.UNAVAILABLE,observer.availability());
    }
}
