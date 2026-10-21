package dev.veltraph.client.greenhouse;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static dev.veltraph.client.greenhouse.GreenhouseModel.*;
import static dev.veltraph.client.greenhouse.GreenhouseRuntime.*;

class GreenhouseRuntimeTest {
    static final Context CONTEXT=new Context("local-profile","greenhouse-bed","world");
    static final Cell CELL=new Cell(0,70,0),PROTECTED=new Cell(1,70,0);
    static final Position POSE=new Position(0,70,2);
    static Plant plant(String crop,PlantState state,WaterState water,String block){return new Plant(crop,state,water,0,Clock.unknown(),Clock.unknown(),Clock.unknown(),block,"native menu fixture");}
    static final Plant READY=plant("Wheat",PlantState.HARVEST_READY,WaterState.WATERED,"wheat[age=7]"),EMPTY=plant("",PlantState.EMPTY,WaterState.UNKNOWN,"air"),GROWING=plant("Wheat",PlantState.GROWING,WaterState.UNKNOWN,"wheat[age=0]");
    static Snapshot snapshot(long at,long rev,Plant p,Map<String,Integer> inv){return new Snapshot(CONTEXT,at,rev,Map.of(CELL,p,PROTECTED,GROWING),inv,"tool",2,2,POSE,true,true,true);}
    static final Settings SETTINGS=new Settings(true,16,5000,1000,2000);
    static Action harvest(String id){return new Action(id,Kind.HARVEST,CELL,POSE,"tool",2,READY,EMPTY,Map.of("wheat",2));}
    static Plan plan(Action... actions){return new Plan("selected-local-bed",CONTEXT,List.of(actions),"User selected bounded care");}
    static class Port implements GreenhouseRuntime.Port {int issues,releases,selections,moves;boolean accepted=true;public boolean approach(Position p){moves++;return true;}public boolean arrived(Position p){return true;}public boolean select(int s,String i){selections++;return true;}public boolean aim(Action a){return true;}public boolean issue(Action a,Snapshot s){issues++;return accepted;}public void stopAction(){}public void release(){releases++;}}
    static void issue(GreenhouseRuntime r,Snapshot s){r.tick(s,SETTINGS,11);r.tick(s,SETTINGS,12);r.tick(s,SETTINGS,13);assertEquals(State.WAIT_ACK,r.state());}
    @Test void exactReceiptThenPhysicalReturn(){var port=new Port();var r=new GreenhouseRuntime(port);var s=snapshot(10,1,READY,Map.of("tool",1,"protected",5));assertTrue(r.start(plan(harvest("a")),s,SETTINGS,10));issue(r,s);
        var ack=snapshot(20,2,EMPTY,Map.of("tool",1,"protected",5,"wheat",2));r.tick(ack,SETTINGS,20);assertEquals(State.RETURN,r.state());assertEquals(1,port.issues);r.tick(ack,SETTINGS,21);assertEquals(State.COMPLETE,r.state());assertEquals(2,port.moves);assertTrue(r.unresolvedActions().isEmpty());}
    @Test void sameRevisionPredictionCannotAcknowledge(){var p=new Port();var r=new GreenhouseRuntime(p);var s=snapshot(10,1,READY,Map.of("tool",1));assertTrue(r.start(plan(harvest("a")),s,SETTINGS,10));issue(r,s);
        r.tick(snapshot(20,1,EMPTY,Map.of("tool",1,"wheat",2)),SETTINGS,20);assertEquals(State.WAIT_ACK,r.state());r.tick(s,SETTINGS,1014);assertEquals(State.UNKNOWN_OUTCOME,r.state());assertEquals(1,p.issues);assertFalse(r.start(plan(harvest("renamed")),s,SETTINGS,1015));}
    @Test void nativeFalseOutcomeCannotReplayAcrossRuntimeRestart(){var p=new Port();p.accepted=false;var r=new GreenhouseRuntime(p);var s=snapshot(10,1,READY,Map.of("tool",1));assertTrue(r.start(plan(harvest("a")),s,SETTINGS,10));r.tick(s,SETTINGS,11);r.tick(s,SETTINGS,12);r.tick(s,SETTINGS,13);assertEquals(State.UNKNOWN_OUTCOME,r.state());
        var next=new GreenhouseRuntime(new Port());next.restoreUnresolved(r.unresolvedActions());assertFalse(next.start(plan(harvest("different-id")),s,SETTINGS,14));assertEquals(1,p.issues);}
    @Test void inventoryChangeOutsideReceiptStopsWithoutSecondAction(){var p=new Port();var r=new GreenhouseRuntime(p);var s=snapshot(10,1,READY,Map.of("tool",1,"protected",8));assertTrue(r.start(plan(harvest("a")),s,SETTINGS,10));issue(r,s);r.tick(snapshot(20,2,EMPTY,Map.of("tool",1,"protected",7,"wheat",2)),SETTINGS,20);assertEquals(State.UNKNOWN_OUTCOME,r.state());assertEquals(1,p.issues);}
    @Test void prematureHarvestAndMissingSeedReceiptRejected(){var r=new GreenhouseRuntime(new Port());var s=snapshot(10,1,READY,Map.of("tool",1));assertFalse(r.start(plan(new Action("a",Kind.HARVEST,CELL,POSE,"tool",2,GROWING,EMPTY,Map.of("wheat",2))),s,SETTINGS,10));assertFalse(r.start(plan(new Action("a",Kind.REPLANT,CELL,POSE,"tool",2,EMPTY,GROWING,Map.of())),s,SETTINGS,10));}
    @Test void freshWaterOutcomeNeedsIndependentKnownExpiry(){var p=new Port();var r=new GreenhouseRuntime(p);var dry=plant("Wheat",PlantState.GROWING,WaterState.DRY,"wheat[age=0]");var wet=plant("Wheat",PlantState.GROWING,WaterState.WATERED,"wheat[age=0]");var action=new Action("water",Kind.WATER,CELL,POSE,"tool",2,dry,wet,Map.of("water",-1));var s=snapshot(10,1,dry,Map.of("tool",1,"water",3));assertTrue(r.start(plan(action),s,SETTINGS,10));issue(r,s);
        r.tick(snapshot(20,2,wet,Map.of("tool",1,"water",2)),SETTINGS,20);assertEquals(State.WAIT_ACK,r.state());var known=new Plant(wet.crop(),wet.state(),wet.water(),wet.growthStage(),Clock.unknown(),new Clock(ClockKind.SERVER_EPOCH,10000,"observed"),Clock.unknown(),wet.fingerprint(),wet.source());r.tick(snapshot(21,3,known,Map.of("tool",1,"water",2)),SETTINGS,21);assertEquals(State.RETURN,r.state());}
    @Test void cancellationAfterIssueRetainsUnresolvedAndReleases(){var p=new Port();var r=new GreenhouseRuntime(p);var s=snapshot(10,1,READY,Map.of("tool",1));assertTrue(r.start(plan(harvest("a")),s,SETTINGS,10));issue(r,s);r.cancel();assertEquals(State.UNKNOWN_OUTCOME,r.state());assertEquals(1,p.releases);assertFalse(r.unresolvedActions().isEmpty());}
    @Test void staleContextAndDuplicatePlanCannotStart(){var r=new GreenhouseRuntime(new Port());var s=snapshot(10,1,READY,Map.of("tool",1));assertFalse(r.start(plan(harvest("a")),s,SETTINGS,5011));assertFalse(r.start(plan(harvest("a"),harvest("a")),s,SETTINGS,10));assertFalse(r.start(plan(harvest("a")),s,new Settings(false,16,5000,1000,1000),10));}
    @Test void harvestThenOneSeedReplantUsesIndependentReceipts(){var p=new Port();var r=new GreenhouseRuntime(p);var initial=snapshot(10,1,READY,Map.of("tool",1,"seed",3));var plant=new Action("plant",Kind.REPLANT,CELL,POSE,"tool",2,EMPTY,GROWING,Map.of("seed",-1));assertTrue(r.start(plan(harvest("harvest"),plant),initial,SETTINGS,10));issue(r,initial);var harvested=snapshot(20,2,EMPTY,Map.of("tool",1,"seed",3,"wheat",2));r.tick(harvested,SETTINGS,20);r.tick(harvested,SETTINGS,21);r.tick(harvested,SETTINGS,22);r.tick(harvested,SETTINGS,23);assertEquals(State.WAIT_ACK,r.state());assertEquals(2,p.issues);var replanted=snapshot(30,3,GROWING,Map.of("tool",1,"seed",2,"wheat",2));r.tick(replanted,SETTINGS,30);assertEquals(State.RETURN,r.state());r.tick(replanted,SETTINGS,31);assertEquals(State.COMPLETE,r.state());}
    @Test void resourceBudgetAndOverflowAreRejectedBeforeTransport(){var p=new Port();var r=new GreenhouseRuntime(p);var s=snapshot(10,1,EMPTY,Map.of("tool",1));var planting=new Action("plant",Kind.REPLANT,CELL,POSE,"tool",2,EMPTY,GROWING,Map.of("seed",-1));assertFalse(r.start(plan(planting),s,SETTINGS,10));assertFalse(r.start(plan(harvest("huge")),snapshot(10,1,READY,Map.of("tool",1,"wheat",Integer.MAX_VALUE)),SETTINGS,10));assertEquals(0,p.issues);}
    @Test void staleOrForeignInspectionCannotReleaseUnresolvedCell(){var p=new Port();var r=new GreenhouseRuntime(p);var s=snapshot(10,1,READY,Map.of("tool",1));assertTrue(r.start(plan(harvest("a")),s,SETTINGS,10));issue(r,s);r.cancel();assertFalse(r.reconcileInspectedCell(CONTEXT,CELL,s,6000,5000,"not-applied"));assertFalse(r.reconcileInspectedCell(new Context("other","plot","world"),CELL,s,14,5000,"not-applied"));assertFalse(r.reconcileInspectedCell(CONTEXT,CELL,s,14,5000,"guess"));assertFalse(r.reconcileInspectedCell(CONTEXT,CELL,s,14,5000,"not-applied"));assertTrue(r.reconcileInspectedCell(CONTEXT,CELL,snapshot(14,2,READY,Map.of("tool",1)),14,5000,"not-applied"));assertTrue(r.unresolvedActions().isEmpty());assertEquals(1,p.issues);}
    @Test void observationNewerThanBaselineButOlderThanIssueCannotAcknowledge(){
        var port=new Port();var runtime=new GreenhouseRuntime(port);var initial=snapshot(10,1,READY,Map.of("tool",1));
        assertTrue(runtime.start(plan(harvest("late-issue")),initial,SETTINGS,10));issue(runtime,initial);
        runtime.tick(snapshot(12,2,EMPTY,Map.of("tool",1,"wheat",2)),SETTINGS,20);
        assertEquals(State.WAIT_ACK,runtime.state(),"Observation predates actual issue at13");
        runtime.tick(snapshot(21,3,EMPTY,Map.of("tool",1,"wheat",2)),SETTINGS,21);
        assertEquals(State.RETURN,runtime.state());assertEquals(1,port.issues);
    }
    @Test void replantCannotEraseProtectedSeedBySelectingItsTool(){
        var port=new Port();var runtime=new GreenhouseRuntime(port);var initial=new Snapshot(CONTEXT,10,1,Map.of(CELL,EMPTY,PROTECTED,GROWING),Map.of("seed",1),"seed",2,2,POSE,true,true,true);
        var action=new Action("last-seed",Kind.REPLANT,CELL,POSE,"seed",2,EMPTY,GROWING,Map.of("seed",-1));
        assertTrue(runtime.start(plan(action),initial,SETTINGS,10));issue(runtime,initial);
        var receipt=new Snapshot(CONTEXT,20,2,Map.of(CELL,GROWING,PROTECTED,GROWING),Map.of(),"",2,2,POSE,true,true,true);
        runtime.tick(receipt,SETTINGS,20);assertEquals(State.RETURN,runtime.state(),"Exact one seed consumption is the expected receipt");
        runtime.tick(receipt,SETTINGS,21);assertEquals(State.COMPLETE,runtime.state());assertEquals(1,port.issues);
    }
    @Test void protectedInventoryDriftOnSameRevisionIsNotDeferredUntilAnotherMenu(){
        var port=new Port();var runtime=new GreenhouseRuntime(port);var initial=snapshot(10,1,READY,Map.of("tool",1,"protected",8));
        assertTrue(runtime.start(plan(harvest("protected")),initial,SETTINGS,10));issue(runtime,initial);
        runtime.tick(snapshot(14,1,READY,Map.of("tool",1,"protected",7)),SETTINGS,14);
        assertEquals(State.UNKNOWN_OUTCOME,runtime.state());assertEquals(1,port.issues);assertFalse(runtime.unresolvedActions().isEmpty());
    }

    @Test void completeWaitsForActualSelectionAndViewReturn(){
        var restored=new java.util.concurrent.atomic.AtomicBoolean();var selected=new java.util.concurrent.atomic.AtomicInteger(2);
        var port=new Port(){public boolean restoreSelectionAndView(){selected.set(0);return restored.get();}};
        var runtime=new GreenhouseRuntime(port);
        var initial=new Snapshot(CONTEXT,10,1,Map.of(CELL,EMPTY,PROTECTED,GROWING),Map.of("seed",1),"seed",2,2,POSE,true,true,true);
        var action=new Action("view-seed",Kind.REPLANT,CELL,POSE,"seed",2,EMPTY,GROWING,Map.of("seed",-1));
        assertTrue(runtime.start(plan(action),initial,SETTINGS,10));issue(runtime,initial);
        var ack=new Snapshot(CONTEXT,20,2,Map.of(CELL,GROWING,PROTECTED,GROWING),Map.of(),"",2,2,POSE,true,true,true);
        runtime.tick(ack,SETTINGS,20);runtime.tick(ack,SETTINGS,21);
        assertEquals(State.RETURN,runtime.state());assertEquals(0,port.releases);assertEquals(0,selected.get());
        // Native snapshot still reads the consumed planned tool in slot2 while selectedSlot is now0.
        var originalSelected=new Snapshot(CONTEXT,20,2,ack.plants(),ack.inventory(),"",2,selected.get(),POSE,true,true,true);
        runtime.tick(originalSelected,SETTINGS,22);assertEquals(State.RETURN,runtime.state());assertEquals(0,port.releases);
        restored.set(true);runtime.tick(originalSelected,SETTINGS,23);assertEquals(State.COMPLETE,runtime.state());assertEquals(1,port.releases);
    }

    @Test void sharedTravelCannotSelectOrIssueBeforeItsActualArrivalWitness(){
        var arrived=new java.util.concurrent.atomic.AtomicBoolean();
        var port=new Port(){public boolean arrived(Position p){return arrived.get();}};
        var r=new GreenhouseRuntime(port);var initial=snapshot(10,1,READY,Map.of("tool",1));
        assertTrue(r.start(plan(harvest("travel")),initial,SETTINGS,10));
        for(int at=11;at<30;at++)r.tick(initial,SETTINGS,at);
        assertEquals(State.APPROACH,r.state());assertEquals(1,port.moves);assertEquals(0,port.selections);assertEquals(0,port.issues);
        arrived.set(true);r.tick(initial,SETTINGS,30);r.tick(initial,SETTINGS,31);r.tick(initial,SETTINGS,32);
        assertEquals(State.WAIT_ACK,r.state());assertEquals(1,port.issues);
    }
    @Test void careApproachNeverRelaxesProtectedInventoryLease(){
        var port=new Port(){public boolean arrived(Position p){return false;}};
        var r=new GreenhouseRuntime(port);var initial=snapshot(10,1,READY,Map.of("tool",1,"protected",4));
        assertTrue(r.start(plan(harvest("care")),initial,SETTINGS,10));r.tick(initial,SETTINGS,11);
        r.tick(snapshot(12,1,READY,Map.of("tool",1,"protected",3)),SETTINGS,12);
        assertEquals(State.CANCELLED,r.state());assertEquals(0,port.issues);assertEquals(1,port.releases);
    }
    @Test void travelReplanningCannotResetOriginalCareTimeout(){
        var port=new Port(){public boolean arrived(Position p){return false;}};
        var r=new GreenhouseRuntime(port);var initial=snapshot(10,1,READY,Map.of("tool",1));
        assertTrue(r.start(plan(harvest("bounded")),initial,SETTINGS,10));
        r.tick(initial,SETTINGS,11);r.tick(initial,SETTINGS,1009);assertEquals(State.APPROACH,r.state());
        r.tick(initial,SETTINGS,1010);assertEquals(State.FAILED,r.state());assertEquals(1,port.moves);assertEquals(0,port.issues);
    }

}
