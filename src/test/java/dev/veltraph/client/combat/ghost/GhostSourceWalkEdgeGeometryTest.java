package dev.veltraph.client.combat.ghost;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import dev.veltraph.client.combat.ghost.GhostSourceWalkFrontier.Cell;
import dev.veltraph.client.combat.ghost.GhostSourceSampledRay.Point;
class GhostSourceWalkEdgeGeometryTest {
    private static class Floor implements GhostSourceWalkEdgeGeometry.World {
        public boolean current(){return true;}public boolean sourcePassable(Cell c){return c.y()>=76;}
        public boolean standable(Cell c){return c.y()==76;}public boolean fluid(Cell c){return false;}public double supportTop(Cell c){return c.y()+1;}
    }
    @Test void ordinaryGroundEdgeRetainsDuplicateRawEndpointAndProfiles(){
        var edge=GhostSourceWalkEdgeGeometry.ground(new Cell(0,76,0),new Cell(2,76,0),new Floor());
        assertEquals(2,edge.geometricLength());assertEquals(4,edge.rawSamples().size());assertEquals(edge.to(),edge.rawSamples().get(2));assertEquals(edge.to(),edge.rawSamples().get(3));
        assertEquals(3,edge.travelPoints().size());assertEquals(.75,edge.travelPoints().getFirst().bands().getFirst().radius());
    }
    @Test void cornerHeightEqualityAndRadiusInclusiveAreIndependentFromCursorStrictReach(){
        var e=GhostSourceWalkEdgeGeometry.ground(new Cell(0,76,0),new Cell(1,76,0),new Floor());var t=e.travelPoints().getFirst();
        assertTrue(GhostSourceWalkEdgeGeometry.cornerHit(t,new Point(1.25,76,.5),76));
        assertFalse(GhostSourceWalkEdgeGeometry.cornerHit(t,new Point(1.25001,76,.5),76));assertFalse(GhostSourceWalkEdgeGeometry.cornerHit(t,new Point(.5,76,.5),76.00001));
    }
    @Test void validZeroLengthPathRetainsFiniteEndpointWithoutNaNAdornment(){
        var e=GhostSourceWalkEdgeGeometry.ground(new Cell(0,76,0),new Cell(0,76,0),new Floor());assertTrue(e.zeroLength());assertTrue(e.rawSamples().isEmpty());assertEquals(1,e.travelPoints().size());assertEquals(new Point(.5,76,.5),e.travelPoints().getFirst().point());
    }
}
