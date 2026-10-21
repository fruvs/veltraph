package dev.veltraph.client.greenhouse;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static dev.veltraph.client.greenhouse.GreenhouseModel.*;

class GreenhouseAssociatedPatternTest {
    @Test void countsAloneStayUnknownButExactFreshGuideOffsetsAndBoundPlantsValidate(){
        var ledger=new GreenhouseMenuLedger();assertEquals(GreenhouseCombinations.Result.UNKNOWN_PROXIMITY,ledger.validateCounts("Dustgrain",Map.of("Wheat",2)).result());
        var target=new Cell(0,70,0);var left=new Cell(-1,70,0);var right=new Cell(1,70,0);var scope=new Context("profile","plot","world");
        var source=new GreenhouseGuide.Recipe("Dustgrain",Map.of("Wheat",2),Set.of(new Cell(-1,0,0),new Cell(1,0,0)),scope,10,"Exact private native Guide offsets; no inferred world grid");
        var plant=new Plant("Wheat",PlantState.GROWING,WaterState.DRY,1,Clock.unknown(),Clock.unknown(),Clock.unknown(),"wheat","issued native request receipt");
        var empty=new Plant("",PlantState.EMPTY,WaterState.UNKNOWN,-1,Clock.unknown(),Clock.unknown(),Clock.unknown(),"air","issued native request receipt");
        var snapshot=new Snapshot(scope,10,1,Map.of(target,empty,left,plant,right,plant),Map.of(),"",-1,0,new Position(0,70,2),true,true,true);
        var selection=new GreenhouseCombinations.ObservedPattern("Dustgrain",target,Set.of(left,right),"User selected exact observed Guide pattern");
        assertEquals(GreenhouseCombinations.Result.VALID_OBSERVED_PATTERN,ledger.validateAssociatedPattern(source,snapshot,selection,11,100).result());
        assertEquals(GreenhouseCombinations.Result.STALE_OBSERVATIONS,ledger.validateAssociatedPattern(source,null,selection,11,100).result());
        assertEquals(GreenhouseCombinations.Result.UNKNOWN_PROXIMITY,ledger.validateAssociatedPattern(null,snapshot,selection,11,100).result());
        assertEquals(GreenhouseCombinations.Result.UNKNOWN_RECIPE,ledger.validateAssociatedPattern(source,snapshot,new GreenhouseCombinations.ObservedPattern("Godseed",target,Set.of(left,right),"Opaque recipe"),11,100).result());
    }
}
