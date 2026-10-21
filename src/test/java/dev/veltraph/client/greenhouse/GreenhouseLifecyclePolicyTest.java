package dev.veltraph.client.greenhouse;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static dev.veltraph.client.greenhouse.GreenhouseLifecyclePolicy.*;
import static dev.veltraph.client.greenhouse.GreenhouseModel.*;

class GreenhouseLifecyclePolicyTest {
    private static final Context C=new Context("observed-profile","observed-plot","observed-world");
    private static final Cell A=new Cell(0,70,0),B=new Cell(1,70,0);
    private static GroupCounter group(String species,long count,boolean complete){return new GroupCounter(C,species,Set.of(A,B),OptionalLong.of(count),100,"Independent group receipt",complete);}
    private static Observation observation(String species,PlantState state,Observed drought,Observed charge,long decay,GroupCounter counter){
        return new Observation(C,A,species,100,"Independent selected native receipt",state,drought,charge,OptionalLong.of(decay),Optional.ofNullable(counter));
    }
    @Test void droughtSchedulesWaterInspectionWithoutDeathOrActionAuthority(){var r=evaluate(observation("Wheat",PlantState.GROWING,Observed.YES,Observed.NO,900,group("Wheat",3,true)),110,1000);assertTrue(r.needs().contains(Need.WATERING_INSPECTION));assertFalse(r.authorizesAction());assertTrue(r.reason().contains("do not infer death"));}
    @Test void zeroTimerWithPositiveGroupCounterDoesNotInferDecayOrSynthesizeNewTimer(){var r=evaluate(observation("Wheat",PlantState.GROWING,Observed.NO,Observed.NO,0,group("Wheat",3,true)),110,1000);assertTrue(r.decayDependenciesObserved());assertTrue(r.needs().contains(Need.INSPECT));assertFalse(r.authorizesAction());assertTrue(r.reason().contains("refresh the actual timer"));}
    @Test void zeroGroupWithPositiveTimerStillPreservesDonors(){var r=evaluate(observation("Wheat",PlantState.GROWING,Observed.NO,Observed.NO,900,group("Wheat",0,true)),110,1000);assertEquals(Set.of(Need.PRESERVE),r.needs());assertTrue(r.decayDependenciesObserved());}
    @Test void bothExhaustedDependenciesOnlyRequestActualTransitionReceipt(){var r=evaluate(observation("Wheat",PlantState.GROWING,Observed.NO,Observed.NO,0,group("Wheat",0,true)),110,1000);assertTrue(r.needs().contains(Need.INSPECT));assertTrue(r.reason().contains("never infer DEAD"));assertFalse(r.authorizesAction());}
    @Test void incompletePerCellCounterAndForeignSpeciesNeverEstablishGroup(){for(var g:List.of(group("Wheat",0,false),group("Dustgrain",0,true))){var r=evaluate(observation("Wheat",PlantState.GROWING,Observed.NO,Observed.NO,0,g),110,1000);assertFalse(r.decayDependenciesObserved());assertTrue(r.needs().contains(Need.INSPECT));}}
    @Test void groupCannotBorrowAnotherPlotOrOmitTheSelectedCell(){for(var g:List.of(new GroupCounter(new Context("p","other","w"),"Wheat",Set.of(A),OptionalLong.of(0),100,"receipt",true),new GroupCounter(C,"Wheat",Set.of(B),OptionalLong.of(0),100,"receipt",true))){assertFalse(evaluate(observation("Wheat",PlantState.GROWING,Observed.NO,Observed.NO,0,g),110,1000).decayDependenciesObserved());}}
    @Test void missingNegativeAndStaleCountersRemainUnknown(){for(var g:List.of(new GroupCounter(C,"Wheat",Set.of(A),OptionalLong.empty(),100,"receipt",true),new GroupCounter(C,"Wheat",Set.of(A),OptionalLong.of(-1),100,"receipt",true),new GroupCounter(C,"Wheat",Set.of(A),OptionalLong.of(0),0,"receipt",true))){assertFalse(evaluate(observation("Wheat",PlantState.GROWING,Observed.NO,Observed.NO,0,g),110,100).decayDependenciesObserved());}}
    @Test void thunderlingOverchargeRequestsIndependentDischargeRatherThanDestruction(){var r=evaluate(observation("Thunderling",PlantState.GROWING,Observed.NO,Observed.YES,900,group("Thunderling",3,true)),110,1000);assertTrue(r.needs().contains(Need.DISCHARGE_INSPECTION));assertFalse(r.needs().contains(Need.WATERING_INSPECTION));assertFalse(r.authorizesAction());}
    @Test void simultaneousDroughtAndOverchargeRemainIndependentNeeds(){var r=evaluate(observation("Thunderling",PlantState.GROWING,Observed.YES,Observed.YES,900,group("Thunderling",3,true)),110,1000);assertTrue(r.needs().containsAll(Set.of(Need.WATERING_INSPECTION,Need.DISCHARGE_INSPECTION)));}
    @Test void unknownChargeDoesNotBecomeReadyOrDischarged(){var r=evaluate(observation("Thunderling",PlantState.GROWING,Observed.NO,Observed.UNKNOWN,900,group("Thunderling",3,true)),110,1000);assertTrue(r.needs().contains(Need.INSPECT));assertFalse(r.needs().contains(Need.DISCHARGE_INSPECTION));}
    @Test void deadDonorStillNeedsFreshGroupAndTimerAndNeverOrdinaryWatering(){var r=evaluate(observation("Dustgrain",PlantState.DEAD,Observed.YES,Observed.YES,0,null),110,1000);assertEquals(Set.of(Need.PRESERVE,Need.INSPECT),r.needs());assertFalse(r.authorizesAction());}
    @Test void futureStaleUnknownAndClockRollbackRefuseDependencies(){var o=observation("Wheat",PlantState.GROWING,Observed.NO,Observed.NO,0,group("Wheat",0,true));for(long now:List.of(99L,1200L)){var r=evaluate(o,now,1000);assertEquals(Set.of(Need.INSPECT),r.needs());assertFalse(r.decayDependenciesObserved());}assertFalse(evaluate(observation("Wheat",PlantState.UNKNOWN,Observed.NO,Observed.NO,0,group("Wheat",0,true)),110,1000).decayDependenciesObserved());}
    @Test void elapsedRelativeTimerOnlySchedulesInspectionAndDoesNotConsumeCounter(){var g=group("Wheat",3,true);var r=evaluate(observation("Wheat",PlantState.GROWING,Observed.NO,Observed.NO,5,g),110,1000);assertTrue(r.needs().contains(Need.INSPECT));assertTrue(r.reason().contains("no counter decrement"));assertEquals(3,g.remaining().orElseThrow());assertFalse(r.authorizesAction());}
    @Test void releaseMinimumCannotInitializeMissingObservedGroupCounter(){var r=evaluate(observation("Wheat",PlantState.GROWING,Observed.NO,Observed.NO,0,null),110,1000);assertFalse(r.decayDependenciesObserved());assertTrue(r.reason().contains("release minimums cannot initialize"));}
}
