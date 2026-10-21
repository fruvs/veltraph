package dev.veltraph.client.greenhouse;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static dev.veltraph.client.greenhouse.GreenhouseModel.*;
import static dev.veltraph.client.greenhouse.GreenhouseCareScheduler.*;

/** Typed server clocks are explicit fixture observations; actual captured relative timers stay local estimates. */
class GreenhouseCareDeadlineTest {
    private static Clock epoch(long value){return new Clock(ClockKind.SERVER_EPOCH,value,"explicit fixture diagnostic");}
    private static Plant plant(Clock growth,Clock water,Clock decay){return new Plant("Wheat",PlantState.GROWING,WaterState.WATERED,1,growth,water,decay,"observed state","explicit fixture diagnostic");}
    private static Snapshot snapshot(Plant plant){return new Snapshot(new Context("fixture","bed","dimension"),10,1,Map.of(new Cell(0,70,0),plant),Map.of("protected resource",7),"",-1,0,new Position(0,70,1),true,true,true);}
    private static Recommendation evaluate(Plant plant,OptionalLong serverNow){return new GreenhouseCareScheduler().evaluate(snapshot(plant),11,5000,serverNow).getFirst();}

    @Test void growthInspectionCanPrecedeStillWateredExpiry(){var p=plant(epoch(200),epoch(500),epoch(300));var r=evaluate(p,OptionalLong.of(100));assertEquals(200,r.serverDeadline().orElseThrow());assertEquals(Care.WAIT,r.care());assertEquals(PlantState.GROWING,p.state());}
    @Test void decayInspectionCanPrecedeWaterAndGrowth(){var r=evaluate(plant(epoch(300),epoch(500),epoch(200)),OptionalLong.of(100));assertEquals(200,r.serverDeadline().orElseThrow());assertEquals(Care.WAIT,r.care());}
    @Test void exactGrowthDeadlineRequiresInspectionNeverPredictedHarvestOrWater(){var p=plant(epoch(200),epoch(500),epoch(400));var s=snapshot(p);var r=new GreenhouseCareScheduler().evaluate(s,11,5000,OptionalLong.of(200)).getFirst();assertEquals(Care.INSPECT,r.care());assertTrue(r.reason().contains("growth"));assertEquals(PlantState.GROWING,p.state());assertEquals(WaterState.WATERED,p.water());assertEquals(Map.of("protected resource",7),s.inventory());}
    @Test void growthCycleIsNotComparableToServerEpoch(){var r=evaluate(plant(new Clock(ClockKind.GROWTH_CYCLE,1,"explicit fixture cycle"),epoch(500),Clock.unknown()),OptionalLong.of(400));assertEquals(500,r.serverDeadline().orElseThrow());assertEquals(Care.WAIT,r.care());}
    @Test void absentServerClockCannotExpireAnObservedDeadline(){var r=evaluate(plant(epoch(0),epoch(500),epoch(400)),OptionalLong.empty());assertEquals(0,r.serverDeadline().orElseThrow());assertEquals(Care.WAIT,r.care());}
    @Test void absentSourceNegativeAndUnknownClocksProduceNoEpochDeadline(){var r=evaluate(plant(new Clock(ClockKind.SERVER_EPOCH,1,""),new Clock(ClockKind.SERVER_EPOCH,-1,"fixture"),Clock.unknown()),OptionalLong.of(500));assertTrue(r.serverDeadline().isEmpty());assertEquals(Care.WAIT,r.care());}
    @Test void actualObservedReadinessKeepsItsExistingRecommendation(){var base=plant(epoch(0),epoch(500),epoch(400));var p=new Plant(base.crop(),PlantState.HARVEST_READY,base.water(),8,base.nextGrowth(),base.waterExpiry(),base.decayExpiry(),base.fingerprint(),base.source());assertEquals(Care.HARVEST,evaluate(p,OptionalLong.of(500)).care());}
}
