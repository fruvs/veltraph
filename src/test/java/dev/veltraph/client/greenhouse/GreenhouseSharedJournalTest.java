package dev.veltraph.client.greenhouse;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GreenhouseSharedJournalTest {
    @Test void staleEmptyInstanceCannotOverwriteOtherPendingCell(){assertEquals(Set.of("parent-pending"),GreenhouseSharedJournal.merge(Set.of("parent-pending"),Set.of(),Set.of()));}
    @Test void independentPendingCellsMergeWithoutEviction(){assertEquals(Set.of("a","b"),GreenhouseSharedJournal.merge(Set.of("a"),Set.of("b"),Set.of()));}
    @Test void freshResolutionRetiresOnlyItsExactCell(){assertEquals(Set.of("b"),GreenhouseSharedJournal.merge(Set.of("a","b"),Set.of(),Set.of("a")));}
    @Test void stillPendingLocalOutcomeCannotBeRemovedByAnOlderResolution(){assertEquals(Set.of("a"),GreenhouseSharedJournal.merge(Set.of("a"),Set.of("a"),Set.of("a")));}
    @Test void emptyAbsentAndDuplicateJournalsStayBounded(){assertEquals(Set.of(),GreenhouseSharedJournal.read(""));assertEquals(Set.of("a"),GreenhouseSharedJournal.read("[\"a\",\"a\"]"));}
    @Test void malformedAndOversizedSharedJournalRefusesWithoutDroppingPending(){for(var json:List.of("null","[null]","[\" \"]","{}"))assertThrows(RuntimeException.class,()->GreenhouseSharedJournal.read(json));assertThrows(IllegalArgumentException.class,()->GreenhouseSharedJournal.read("x".repeat(65537)));}
    @Test void runtimeMintedResolutionIsOneShotAndCannotComeFromUnresolvedRestore(){var runtime=new GreenhouseRuntime(new GreenhouseRuntimeTest.Port());runtime.restoreUnresolved(Set.of("a"));assertTrue(runtime.takeJournalResolutions().isEmpty());assertEquals(Set.of("a"),runtime.unresolvedActions());}
    @Test void actualNewerRuntimeAckProducesOneScopedRetirementThenCannotRepeat(){
        var runtime=new GreenhouseRuntime(new GreenhouseRuntimeTest.Port());
        var initial=GreenhouseRuntimeTest.snapshot(10,1,GreenhouseRuntimeTest.READY,Map.of("tool",1));
        assertTrue(runtime.start(GreenhouseRuntimeTest.plan(GreenhouseRuntimeTest.harvest("journal-native-ack")),initial,GreenhouseRuntimeTest.SETTINGS,10));
        GreenhouseRuntimeTest.issue(runtime,initial);
        String pending=runtime.unresolvedActions().iterator().next();assertTrue(runtime.takeJournalResolutions().isEmpty());
        runtime.tick(GreenhouseRuntimeTest.snapshot(20,2,GreenhouseRuntimeTest.EMPTY,Map.of("tool",1,"wheat",2)),GreenhouseRuntimeTest.SETTINGS,20);
        assertEquals(Set.of(pending),runtime.takeJournalResolutions());assertTrue(runtime.takeJournalResolutions().isEmpty());
        assertTrue(runtime.unresolvedActions().isEmpty());
    }
    @Test void newlyImportedOtherInstancePendingRequiresLaterInspectionNotOldCachedObservation(){
        var runtime=new GreenhouseRuntime(new GreenhouseRuntimeTest.Port());
        String cell=GreenhouseRuntimeTest.CONTEXT+"|"+GreenhouseRuntimeTest.CELL;
        runtime.mergeSharedUnresolved(Set.of(cell),100,1);
        var old=GreenhouseRuntimeTest.snapshot(99,3,GreenhouseRuntimeTest.READY,Map.of("tool",1));
        assertFalse(runtime.reconcileInspectedCell(GreenhouseRuntimeTest.CONTEXT,GreenhouseRuntimeTest.CELL,old,101,5000,"not-applied"));
        runtime.mergeSharedUnresolved(Set.of(cell),102,1); // An unchanged import cannot renew the original floor.
        var fresh=GreenhouseRuntimeTest.snapshot(101,4,GreenhouseRuntimeTest.READY,Map.of("tool",1));
        assertTrue(runtime.reconcileInspectedCell(GreenhouseRuntimeTest.CONTEXT,GreenhouseRuntimeTest.CELL,fresh,103,5000,"not-applied"));
        assertEquals(Set.of(cell),runtime.takeJournalResolutions());
    }
    @Test void sameCellClearedAndReissuedCannotUsePriorGenerationCachedInspection(){
        var stale=new GreenhouseRuntime(new GreenhouseRuntimeTest.Port());
        String cell=GreenhouseRuntimeTest.CONTEXT+"|"+GreenhouseRuntimeTest.CELL;
        stale.mergeSharedUnresolved(Set.of(cell),10,1);
        var prior=GreenhouseRuntimeTest.snapshot(15,2,GreenhouseRuntimeTest.READY,Map.of("tool",1));
        // Another instance reconciles and reissues this exact cell: journal history advances twice.
        stale.mergeSharedUnresolved(Set.of(cell),21,3);
        assertFalse(stale.reconcileInspectedCell(GreenhouseRuntimeTest.CONTEXT,GreenhouseRuntimeTest.CELL,prior,21,5000,"not-applied"));
        stale.mergeSharedUnresolved(Set.of(cell),23,3); // unchanged-read history cannot postpone new inspection.
        var after=GreenhouseRuntimeTest.snapshot(22,3,GreenhouseRuntimeTest.READY,Map.of("tool",1));
        assertTrue(stale.reconcileInspectedCell(GreenhouseRuntimeTest.CONTEXT,GreenhouseRuntimeTest.CELL,after,24,5000,"not-applied"));
    }
    @Test void actualSharedStoreAndTwoRuntimeReissueHistoryRejectStaleReconciliation(){
        var store=new dev.veltraph.client.config.MapConfigService();var key=dev.veltraph.client.config.GreenhouseConfig.GREENHOUSE_UNRESOLVED_ACTIONS;
        var owner=new GreenhouseRuntime(new GreenhouseRuntimeTest.Port());var standby=new GreenhouseRuntime(new GreenhouseRuntimeTest.Port());
        var before=GreenhouseRuntimeTest.snapshot(10,1,GreenhouseRuntimeTest.READY,Map.of("tool",1));
        assertTrue(owner.start(GreenhouseRuntimeTest.plan(GreenhouseRuntimeTest.harvest("first")),before,GreenhouseRuntimeTest.SETTINGS,10));
        GreenhouseRuntimeTest.issue(owner,before);owner.cancel();
        store.set(key,new com.google.gson.Gson().toJson(owner.unresolvedActions()));
        standby.mergeSharedUnresolved(GreenhouseSharedJournal.read(store.get(key)),14,store.revision(key).orElseThrow());
        var old=GreenhouseRuntimeTest.snapshot(15,2,GreenhouseRuntimeTest.READY,Map.of("tool",1));
        assertTrue(owner.reconcileInspectedCell(GreenhouseRuntimeTest.CONTEXT,GreenhouseRuntimeTest.CELL,old,16,5000,"not-applied"));
        store.set(key,new com.google.gson.Gson().toJson(GreenhouseSharedJournal.merge(GreenhouseSharedJournal.read(store.get(key)),owner.unresolvedActions(),owner.takeJournalResolutions())));
        assertTrue(owner.start(GreenhouseRuntimeTest.plan(GreenhouseRuntimeTest.harvest("second")),old,GreenhouseRuntimeTest.SETTINGS,17));
        owner.tick(old,GreenhouseRuntimeTest.SETTINGS,18);owner.tick(old,GreenhouseRuntimeTest.SETTINGS,19);owner.tick(old,GreenhouseRuntimeTest.SETTINGS,20);owner.cancel();
        store.set(key,new com.google.gson.Gson().toJson(owner.unresolvedActions()));
        standby.mergeSharedUnresolved(GreenhouseSharedJournal.read(store.get(key)),21,store.revision(key).orElseThrow());
        assertFalse(standby.reconcileInspectedCell(GreenhouseRuntimeTest.CONTEXT,GreenhouseRuntimeTest.CELL,old,21,5000,"not-applied"));
        assertEquals(owner.unresolvedActions(),standby.unresolvedActions());
    }
}
