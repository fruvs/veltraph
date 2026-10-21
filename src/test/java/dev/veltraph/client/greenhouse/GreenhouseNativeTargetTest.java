package dev.veltraph.client.greenhouse;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static dev.veltraph.client.greenhouse.GreenhouseModel.*;

class GreenhouseNativeTargetTest {
    @Test void ordinaryReplantHitsSoilBelowEmptyPlantAndHarvestHitsThePlant(){
        var cell=new Cell(-97,74,-2);var action=new GreenhouseRuntime.Action("plant",GreenhouseRuntime.Kind.REPLANT,cell,new Position(-96.5,74,.5),"seed",5,GreenhouseRuntimeTest.EMPTY,GreenhouseRuntimeTest.GROWING,Map.of("seed",-1));
        assertEquals(new Cell(-97,73,-2),GreenhouseModule.interactionTarget(action));
        assertEquals(cell,GreenhouseModule.interactionTarget(new GreenhouseRuntime.Action("harvest",GreenhouseRuntime.Kind.HARVEST,cell,action.approach(),"stick",4,GreenhouseRuntimeTest.READY,GreenhouseRuntimeTest.EMPTY,Map.of("wheat",1))));
    }
}
