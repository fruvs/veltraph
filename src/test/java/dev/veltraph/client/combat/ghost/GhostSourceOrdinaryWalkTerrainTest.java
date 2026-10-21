package dev.veltraph.client.combat.ghost;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import dev.veltraph.client.combat.ghost.GhostSourceWalkFrontier.*;
import dev.veltraph.client.combat.ghost.GhostSourceOrdinaryWalkTerrain.Kind;
class GhostSourceOrdinaryWalkTerrainTest {
    private static Cell c(int x,int y,int z){return new Cell(x,y,z);}
    private static GhostSourceWalkObservedWorld.CellValue value(Kind k,boolean pass,boolean fluid,boolean support,double top){return new GhostSourceWalkObservedWorld.CellValue(new GhostSourceOrdinaryWalkTerrain.Block(k,pass,fluid,support,false,0,top),null,null,false);}
    private static class Plane {
        final Map<Cell,GhostSourceWalkObservedWorld.CellValue> changed=new HashMap<>();boolean valid=true;
        final GhostSourceWalkObservedWorld world=new GhostSourceWalkObservedWorld(c->changed.getOrDefault(c,c.y()<=75?value(Kind.OTHER,false,false,true,c.y()+1):value(Kind.OTHER,true,false,false,c.y())),()->valid);
    }
    @Test void actualFlatGroundHasOneCostAndExplicitForbiddenCellsRejectBodyOrSupport(){
        var p=new Plane();var from=c(0,76,0);var move=new Offset(1,0,0);
        assertTrue(GhostSourceOrdinaryWalkTerrain.admit(p.world,from,move,Set.of(),false));assertEquals(1,GhostSourceOrdinaryWalkTerrain.terrainCost(p.world,from,move,false,6000));
        assertFalse(GhostSourceOrdinaryWalkTerrain.admit(p.world,from,move,Set.of(c(1,76,0)),false));
        assertFalse(GhostSourceOrdinaryWalkTerrain.admit(p.world,from,move,Set.of(c(1,75,0)),false));
    }
    @Test void diagonalCornerAndStepHeadClearanceUseTheOriginalDirectionalChecks(){
        var p=new Plane();p.changed.put(c(1,76,0),value(Kind.OTHER,false,false,true,77));
        assertFalse(GhostSourceOrdinaryWalkTerrain.admit(p.world,c(0,76,0),new Offset(1,0,1),Set.of(),false));
        assertTrue(GhostSourceOrdinaryWalkTerrain.admit(p.world,c(0,76,0),new Offset(1,1,0),Set.of(),false));
        assertEquals(4.41,GhostSourceOrdinaryWalkTerrain.terrainCost(p.world,c(0,76,0),new Offset(1,1,0),false,6000),1e-12);
        assertEquals(7.41,GhostSourceOrdinaryWalkTerrain.terrainCost(p.world,c(0,76,0),new Offset(1,1,0),true,6000),1e-12);
        p.changed.put(c(0,78,0),value(Kind.OTHER,false,false,true,79));assertFalse(GhostSourceOrdinaryWalkTerrain.admit(p.world,c(0,76,0),new Offset(1,1,0),Set.of(),false));
    }
    @Test void groundFootRejectsFluidBelowEvenWhenStandabilityAllowsTwoFluidCells(){
        var p=new Plane();p.changed.put(c(1,76,0),value(Kind.OTHER,true,true,false,77));p.changed.put(c(1,75,0),value(Kind.OTHER,true,true,false,76));
        assertTrue(GhostSourceOrdinaryWalkTerrain.standable(p.world,c(1,76,0),null));assertFalse(GhostSourceOrdinaryWalkTerrain.sourceFoot(p.world,c(1,76,0)));
        assertEquals(76.5,GhostSourceOrdinaryWalkTerrain.standingHeight(p.world,c(1,76,0))); // Fluid standing-height differs from raw support top.
    }
    @Test void jumpBoostSlimeAdmissionAndLowCeilingCostAreSeparateSourceRules(){
        var p=new Plane();p.changed.put(c(1,75,0),value(Kind.SLIME,false,false,true,76));
        assertTrue(GhostSourceOrdinaryWalkTerrain.admit(p.world,c(0,76,0),new Offset(1,0,0),Set.of(),false));assertFalse(GhostSourceOrdinaryWalkTerrain.admit(p.world,c(0,76,0),new Offset(1,0,0),Set.of(),true));
        var roof=new Plane();roof.changed.put(c(1,78,0),value(Kind.OTHER,false,false,true,79));
        assertEquals(1.8,GhostSourceOrdinaryWalkTerrain.terrainCost(roof.world,c(0,76,0),new Offset(1,0,0),false,6000),1e-12);
        assertTrue(GhostSourceOrdinaryWalkTerrain.flatNeighborhood(roof.world,c(1,76,0))); // V head alias is neighborhood, not overhead voxel.
    }
    @Test void sourcePreflightAllowsThinObstacleClassesWithoutGrantingBodyPassage(){
        var fence=new GhostSourceOrdinaryWalkTerrain.Block(Kind.FENCE,false,false,false,false,0,77.5);
        assertTrue(GhostSourceOrdinaryWalkTerrain.preflightAir(fence));var p=new Plane();p.changed.put(c(1,76,0),new GhostSourceWalkObservedWorld.CellValue(fence,null,null,false));
        assertFalse(GhostSourceOrdinaryWalkTerrain.standable(p.world,c(1,76,0),null));
    }
}
