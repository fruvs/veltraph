package dev.veltraph.client.greenhouse;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static dev.veltraph.client.greenhouse.GreenhouseLifecyclePolicy.*;
import static dev.veltraph.client.greenhouse.GreenhouseCareScheduler.*;

class GreenhouseCareScheduleTest {
    private static final GreenhouseModel.Cell CELL=new GreenhouseModel.Cell(0,70,0);
    private static GreenhouseInspectionEstimates.View view(GreenhouseInspectionEstimates.State state,OptionalLong due){
        var estimates=new EnumMap<GreenhouseInspectionEstimates.Timer,GreenhouseInspectionEstimates.Estimate>(GreenhouseInspectionEstimates.Timer.class);
        for(var timer:GreenhouseInspectionEstimates.Timer.values())estimates.put(timer,new GreenhouseInspectionEstimates.Estimate(timer,GreenhouseInspectionEstimates.Timing.UNKNOWN,Optional.empty(),OptionalLong.empty(),OptionalLong.empty(),"private source test"));
        return new GreenhouseInspectionEstimates.View(state,new GreenhouseInspectionEstimates.Scope("private","bed","world",Optional.of(CELL),GreenhouseInspectionEstimates.ClockBasis.LOCAL_OBSERVATION_MILLIS),Optional.empty(),estimates,due,"private read-only schedule test");
    }
    private static Recommendation check(GreenhouseInspectionEstimates.State state,OptionalLong due,Set<Need> needs){return GreenhouseCareSchedule.associated(CELL,view(state,due),new Advice(needs,"current native dependency result",false),100);}
    @Test void missingCurrentDependenciesCannotWaitBehindOlderFutureGrowthTimer(){assertEquals(Care.INSPECT,check(GreenhouseInspectionEstimates.State.FRESH,OptionalLong.of(1000),Set.of(Need.INSPECT)).care());}
    @Test void droughtAndChargeInspectionsTakePriorityWithoutWaterOrUsePermission(){for(var need:List.of(Need.WATERING_INSPECTION,Need.DISCHARGE_INSPECTION)){var recommendation=check(GreenhouseInspectionEstimates.State.FRESH,OptionalLong.of(1000),Set.of(need));assertEquals(Care.INSPECT,recommendation.care());assertTrue(recommendation.serverDeadline().isEmpty());}}
    @Test void fullyKnownPreservedPlantWaitsForFutureInspectionEstimate(){assertEquals(Care.WAIT,check(GreenhouseInspectionEstimates.State.FRESH,OptionalLong.of(1000),Set.of(Need.PRESERVE)).care());}
    @Test void dueAndMissingTimersRequireInspection(){for(var due:List.of(OptionalLong.empty(),OptionalLong.of(100),OptionalLong.of(99)))assertEquals(Care.INSPECT,check(GreenhouseInspectionEstimates.State.FRESH,due,Set.of(Need.PRESERVE)).care());}
    @Test void staleRevokedAndRollbackViewsCannotKeepFutureWait(){for(var state:List.of(GreenhouseInspectionEstimates.State.STALE,GreenhouseInspectionEstimates.State.REVOKED,GreenhouseInspectionEstimates.State.CLOCK_INVALID))assertEquals(Care.INSPECT,check(state,OptionalLong.of(1000),Set.of(Need.PRESERVE)).care());}
    @Test void changedSelectedCellRequiresFreshInspection(){assertEquals(Care.INSPECT,GreenhouseCareSchedule.associated(new GreenhouseModel.Cell(1,70,0),view(GreenhouseInspectionEstimates.State.FRESH,OptionalLong.of(1000)),new Advice(Set.of(Need.PRESERVE),"known",true),100).care());}
}
