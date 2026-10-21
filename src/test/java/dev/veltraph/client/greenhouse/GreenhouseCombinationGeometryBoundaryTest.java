package dev.veltraph.client.greenhouse;

import dev.veltraph.client.gui.MenuSnapshot;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static dev.veltraph.client.greenhouse.GreenhouseModel.*;
import static dev.veltraph.client.greenhouse.GreenhouseCombinations.*;

class GreenhouseCombinationGeometryBoundaryTest {
    private static final Context C=new Context("private","bed","world");
    private static final Plant EMPTY=new Plant("",PlantState.EMPTY,WaterState.UNKNOWN,-1,Clock.unknown(),Clock.unknown(),Clock.unknown(),"air","issued private diagnostic");
    private static final Plant WHEAT=new Plant("Wheat",PlantState.GROWING,WaterState.WATERED,1,Clock.unknown(),Clock.unknown(),Clock.unknown(),"wheat","issued private diagnostic");
    private static Validation check(Cell target,Cell donor,Cell offset,String source,String selection){
        var guide=new GreenhouseGuide.Recipe("Dustgrain",Map.of("Wheat",1),Set.of(offset),C,10,source);
        var observed=new Snapshot(C,10,1,Map.of(target,EMPTY,donor,WHEAT),Map.of(),"",-1,0,new Position(0,70,2),true,true,true);
        return new GreenhouseCombinations().validateObserved(guide,observed,new ObservedPattern("Dustgrain",target,Set.of(donor),selection),11,100);
    }
    @Test void nearbyExactNegativeOffsetStillValidates(){assertEquals(Result.VALID_OBSERVED_PATTERN,check(new Cell(0,70,0),new Cell(-1,70,0),new Cell(-1,0,0),"issued Guide","selected").result());}
    @Test void integerWraparoundCannotMakeOppositeWorldEdgesAdjacent(){assertEquals(Result.UNKNOWN_PROXIMITY,check(new Cell(Integer.MAX_VALUE,70,0),new Cell(Integer.MIN_VALUE,70,0),new Cell(1,0,0),"issued Guide","selected").result());}
    @Test void yDifferenceOverflowCannotMasqueradeAsNearbyLayer(){assertEquals(Result.UNKNOWN_PROXIMITY,check(new Cell(0,Integer.MAX_VALUE,0),new Cell(0,Integer.MIN_VALUE,0),new Cell(0,1,0),"issued Guide","selected").result());}
    @Test void malformedDirectGuideCannotBypassBoundedOffsets(){assertEquals(Result.UNKNOWN_PROXIMITY,check(new Cell(0,70,0),new Cell(17,70,0),new Cell(17,0,0),"issued Guide","selected").result());}
    @Test void missingGuideProvenanceOrSelectionCannotBecomeObservedPattern(){for(String s:List.of(""," ")){assertEquals(Result.UNKNOWN_PROXIMITY,check(new Cell(0,70,0),new Cell(1,70,0),new Cell(1,0,0),s,"selected").result());assertEquals(Result.UNKNOWN_PROXIMITY,check(new Cell(0,70,0),new Cell(1,70,0),new Cell(1,0,0),"issued Guide",s).result());}}
    private static Optional<GreenhouseGuide.Recipe> read(String offset){
        var binding=new GreenhouseGuide.Binding("Private Guide","Recipe","minecraft:paper","Crop: ","Profile: private","Plot: bed",Map.of("Wheat","Wheat: "),"Offset: ");
        var menu=new MenuSnapshot(1,"Private Guide",List.of(new MenuSnapshot.Slot(0,"Recipe",List.of("Profile: private","Plot: bed","Crop: Dustgrain","Wheat: 1","Offset: "+offset),false)),1);
        return GreenhouseGuide.read(menu,binding,C,10);
    }
    @Test void integerMinimumOffsetFailsActualGuideParserInsteadOfAbsOverflow(){for(String o:List.of("-2147483648 0 0","0 -2147483648 0","0 0 -2147483648"))assertTrue(read(o).isEmpty());}
    @Test void boundarySixteenOffsetRemainsAcceptedWithoutInferringActualRecipe(){assertEquals(Set.of(new Cell(-16,0,0)),read("-16 0 0").orElseThrow().offsets());}
}
