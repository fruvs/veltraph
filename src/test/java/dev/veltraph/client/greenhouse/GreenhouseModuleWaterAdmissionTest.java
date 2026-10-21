package dev.veltraph.client.greenhouse;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static dev.veltraph.client.greenhouse.GreenhouseModel.*;

/** Exercises the exact module plan-builder admission invoked before resources or plan publication. */
class GreenhouseModuleWaterAdmissionTest {
    static Plant plant(PlantState state,WaterState water){return new Plant(state==PlantState.EMPTY?"":"Wheat",state,water,-1,Clock.unknown(),Clock.unknown(),Clock.unknown(),"exact native block","independently associated diagnostic");}
    @Test void builderAllowsOnlyIdentifiedDryGrowingOrHarvestReadyPlants(){for(var state:PlantState.values())for(var water:WaterState.values()){var p=plant(state,water);if((state==PlantState.GROWING||state==PlantState.HARVEST_READY)&&water==WaterState.DRY)assertDoesNotThrow(()->GreenhouseModule.requireWaterablePlant(p));else assertThrows(IllegalArgumentException.class,()->GreenhouseModule.requireWaterablePlant(p),state+"/"+water);}}
    @Test void builderRejectsDryDeadPlantBeforeAResourceOrPlanCanBeSelected(){assertThrows(IllegalArgumentException.class,()->GreenhouseModule.requireWaterablePlant(plant(PlantState.DEAD,WaterState.DRY)));}
    @Test void builderRejectsMissingAndUnidentifiedLivingObservation(){assertThrows(IllegalArgumentException.class,()->GreenhouseModule.requireWaterablePlant(null));var unknown=new Plant("Wheat",PlantState.GROWING,WaterState.DRY,0,Clock.unknown(),Clock.unknown(),Clock.unknown(),"","unbound menu");assertThrows(IllegalArgumentException.class,()->GreenhouseModule.requireWaterablePlant(unknown));}
}
