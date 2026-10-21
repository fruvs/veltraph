package dev.veltraph.client.combat.ghost;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class GhostSourceCrowdPressureCounterTest {
    @Test void sourceDefaultZeroAndEmptyObservedHookDoNotInventPositivePressure(){
        var c=new GhostSourceCrowdPressureCounter();assertEquals(0,c.snapshot().value());
        assertEquals(0,c.updateFromMappedHook(0,GhostSourceCrowdPressureCounter.SkipUpdate.NO).value());
    }
    @Test void updateAddsTwiceNearbyCountThenDecaysPositivePressureExactlyOne(){
        var c=new GhostSourceCrowdPressureCounter();assertEquals(3,c.updateFromMappedHook(2,GhostSourceCrowdPressureCounter.SkipUpdate.NO).value());
        assertEquals(2,c.updateFromMappedHook(0,GhostSourceCrowdPressureCounter.SkipUpdate.NO).value());
        assertEquals(1,c.updateFromMappedHook(0,GhostSourceCrowdPressureCounter.SkipUpdate.NO).value());
        assertEquals(0,c.updateFromMappedHook(0,GhostSourceCrowdPressureCounter.SkipUpdate.NO).value());
    }
    @Test void knownSkipDoesNotMutateButUnknownPersistsAcrossLaterSnapshots(){
        var c=new GhostSourceCrowdPressureCounter();c.assignFromMappedLifecycle(7);var before=c.snapshot();
        var skipped=c.updateFromMappedHook(2,GhostSourceCrowdPressureCounter.SkipUpdate.YES);
        assertTrue(skipped.observed());assertEquals(before.value(),skipped.value());assertEquals(before.revision(),skipped.revision());
        var unknown=c.updateFromMappedHook(2,GhostSourceCrowdPressureCounter.SkipUpdate.UNKNOWN);
        assertFalse(unknown.observed());assertFalse(c.snapshot().observed());assertFalse(before.observed());
        assertThrows(IllegalStateException.class,unknown::value);assertThrows(IllegalStateException.class,()->c.snapshot().value());
    }
    @Test void exactAssignmentAndResetAreExplicitLifecycleOperations(){
        var c=new GhostSourceCrowdPressureCounter();c.assignFromMappedLifecycle(-4);
        assertEquals(-4,c.updateFromMappedHook(0,GhostSourceCrowdPressureCounter.SkipUpdate.NO).value());
        c.resetFromMappedLifecycle();assertEquals(0,c.snapshot().value());
    }
    @Test void knownSkipAndKnownDeltaCannotRecoverUnknownEarlierOffset(){
        var c=new GhostSourceCrowdPressureCounter();c.assignFromMappedLifecycle(7);
        var lost=c.updateFromMappedHook(2,GhostSourceCrowdPressureCounter.SkipUpdate.UNKNOWN);
        assertFalse(c.updateFromMappedHook(2,GhostSourceCrowdPressureCounter.SkipUpdate.YES).observed());
        var later=c.updateFromMappedHook(3,GhostSourceCrowdPressureCounter.SkipUpdate.NO);
        assertFalse(later.observed());assertFalse(c.snapshot().observed());assertTrue(later.revision()>lost.revision());
    }
    @Test void mappedResetAndAssignmentRecoverKnowledgeButNeverReviveEarlierReceipts(){
        for(boolean reset:new boolean[]{false,true}){var c=new GhostSourceCrowdPressureCounter();var old=c.snapshot();
            var lost=c.updateFromMappedHook(2,GhostSourceCrowdPressureCounter.SkipUpdate.UNKNOWN);
            if(reset)c.resetFromMappedLifecycle();else c.assignFromMappedLifecycle(9);
            var current=c.snapshot();assertTrue(current.observed());assertEquals(reset?0:9,current.value());
            assertFalse(old.observed());assertFalse(lost.observed());assertTrue(current.revision()>lost.revision());}
    }
    @Test void everyKnownMutationAlsoRevokesPreviouslyCapturedValue(){
        var c=new GhostSourceCrowdPressureCounter();var before=c.snapshot();c.updateFromMappedHook(2,GhostSourceCrowdPressureCounter.SkipUpdate.NO);
        assertFalse(before.observed());assertTrue(c.snapshot().observed());
    }
}
