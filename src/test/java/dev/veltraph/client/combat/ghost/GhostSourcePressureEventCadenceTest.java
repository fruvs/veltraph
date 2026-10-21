package dev.veltraph.client.combat.ghost;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class GhostSourcePressureEventCadenceTest {
    @Test void onlyEachThirdReachedEventUpdatesAndDoesNotResetAcrossQueries(){
        var counter=new GhostSourceCrowdPressureCounter();var cadence=new GhostSourcePressureEventCadence(counter);
        assertEquals(0,cadence.reachedBaseEvent(false,2,GhostSourceCrowdPressureCounter.SkipUpdate.NO).value());
        assertEquals(0,cadence.reachedBaseEvent(false,2,GhostSourceCrowdPressureCounter.SkipUpdate.NO).value());
        assertEquals(3,cadence.reachedBaseEvent(false,2,GhostSourceCrowdPressureCounter.SkipUpdate.NO).value());
        assertEquals(3,counter.snapshot().value());
        cadence.reachedBaseEvent(false,0,GhostSourceCrowdPressureCounter.SkipUpdate.NO);
        cadence.reachedBaseEvent(false,0,GhostSourceCrowdPressureCounter.SkipUpdate.NO);
        assertEquals(2,cadence.reachedBaseEvent(false,0,GhostSourceCrowdPressureCounter.SkipUpdate.NO).value());
    }
    @Test void gardenFarmBranchSkipsPressureButResetsThirdEventCounter(){
        var counter=new GhostSourceCrowdPressureCounter();var cadence=new GhostSourcePressureEventCadence(counter);
        for(int i=0;i<3;i++)assertEquals(0,cadence.reachedBaseEvent(true,4,GhostSourceCrowdPressureCounter.SkipUpdate.NO).value());
        assertEquals(0,cadence.reachedBaseEvent(false,2,GhostSourceCrowdPressureCounter.SkipUpdate.NO).value());
        assertEquals(0,cadence.reachedBaseEvent(false,2,GhostSourceCrowdPressureCounter.SkipUpdate.NO).value());
        assertEquals(3,cadence.reachedBaseEvent(false,2,GhostSourceCrowdPressureCounter.SkipUpdate.NO).value());
    }
    @Test void transitionSkipPreservesCounterAndUncertaintyPropagates(){
        var counter=new GhostSourceCrowdPressureCounter();counter.assignFromMappedLifecycle(7);var cadence=new GhostSourcePressureEventCadence(counter);
        for(int i=0;i<3;i++)assertEquals(7,cadence.reachedBaseEvent(false,2,GhostSourceCrowdPressureCounter.SkipUpdate.YES).value());
        for(int i=0;i<3;i++)cadence.reachedBaseEvent(false,2,GhostSourceCrowdPressureCounter.SkipUpdate.UNKNOWN);
        assertFalse(counter.snapshot().observed());
        for(int i=0;i<3;i++)assertFalse(cadence.reachedBaseEvent(false,0,GhostSourceCrowdPressureCounter.SkipUpdate.NO).observed());
    }
}
