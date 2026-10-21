package dev.veltraph.client.combat.ghost;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import dev.veltraph.client.combat.ghost.GhostSourceWalkFrontier.*;
class GhostSourceWalkFrontierTest {
    private static Cell c(int x,int y,int z){return new Cell(x,y,z);}
    @Test void equalPriorityLatestInsertionPrecedesEarlierWithoutStandardQueueTieAssumption(){
        var f=new GhostSourceWalkFrontier(c(0,0,0),c(0,0,4),6000);var start=f.popVisit();
        f.addObservedEdge(start,c(-1,0,0),1);f.addObservedEdge(start,c(1,0,0),1);
        assertEquals(c(1,0,0),f.popVisit().cell());assertEquals(c(-1,0,0),f.popVisit().cell());
    }
    @Test void cheaperLaterRouteDoesNotRelaxAnAlreadyOpenCoordinate(){
        var f=new GhostSourceWalkFrontier(c(0,0,0),c(5,0,0),6000);var start=f.popVisit();
        f.addObservedEdge(start,c(1,0,0),10);f.addObservedEdge(start,c(0,0,1),1);var detour=f.popVisit();
        assertFalse(f.addObservedEdge(detour,c(1,0,0),.1));var original=f.popVisit();
        assertEquals(10,original.totalCost());assertSame(start,original.parent());
        assertFalse(f.addObservedEdge(original,c(0,0,0),.1));
    }
    @Test void strictVisitLimitIsCheckedBeforePopAndGoalIsCheckedAfterVisit(){
        var f=new GhostSourceWalkFrontier(c(0,0,0),c(1,0,0),1);var start=f.popVisit();
        f.addObservedEdge(start,c(1,0,0),1);assertTrue(f.hasOpen());assertFalse(f.underVisitLimit());
        assertThrows(IllegalStateException.class,f::popVisit);
        var g=new GhostSourceWalkFrontier(c(0,0,0),c(1,0,0),2);var root=g.popVisit();g.addObservedEdge(root,c(1,0,0),1);
        var goal=g.popVisit();assertTrue(g.reached(goal));assertEquals(2,g.visitedCount());assertFalse(g.underVisitLimit());
    }
    @Test void nodeFromAnotherFrontierCannotInjectAnEdgeIntoThisJob(){
        var a=new GhostSourceWalkFrontier(c(0,0,0),c(2,0,0),6000);a.popVisit();
        var b=new GhostSourceWalkFrontier(c(0,0,0),c(2,0,0),6000);
        assertThrows(IllegalArgumentException.class,()->a.addObservedEdge(b.popVisit(),c(1,0,0),1));
    }
    @Test void originalGroundLoopOrderAndDeepDropDifferFromTwentySixNeighborGrid(){
        var offsets=GhostSourceWalkFrontier.offsets(1,1,false,false);assertEquals(36,offsets.size());
        assertEquals(List.of(new Offset(-1,-2,-1),new Offset(-1,-1,-1),new Offset(-1,0,-1),new Offset(-1,1,-1)),offsets.subList(0,4));
        assertEquals(23,offsets.stream().filter(GhostSourceWalkFrontier::structuralMove).count());
        var drops=GhostSourceWalkFrontier.offsets(1,1,false,true);assertEquals(162,drops.size());assertEquals(new Offset(-1,-16,-1),drops.getFirst());
        assertFalse(GhostSourceWalkFrontier.structuralMove(new Offset(1,1,1)));assertTrue(GhostSourceWalkFrontier.structuralMove(new Offset(1,0,1)));
        assertEquals(27,GhostSourceWalkFrontier.offsets(1,1,true,false).size());
    }
    @Test void exhaustedSmallSearchRetriesOnlyOnceAndResetCreatesFreshVisitAccounting(){
        var f=new GhostSourceWalkFrontier(c(0,0,0),c(2,0,0),6000);f.popVisit();assertFalse(f.hasOpen());
        assertTrue(GhostSourceWalkFrontier.sourceDropRetry(false,false,f.visitedCount()));
        assertFalse(GhostSourceWalkFrontier.sourceDropRetry(false,true,1));assertFalse(GhostSourceWalkFrontier.sourceDropRetry(true,false,1));
        assertTrue(GhostSourceWalkFrontier.sourceDropRetry(false,false,499));assertFalse(GhostSourceWalkFrontier.sourceDropRetry(false,false,500));
        var reset=new GhostSourceWalkFrontier(c(0,0,0),c(2,0,0),6000);assertEquals(0,reset.visitedCount());assertEquals(1,reset.openCount());
        assertEquals(1,f.visitedCount());
    }
    @Test void reconstructionPreservesActualParentChainAndSingleNodeResult(){
        var f=new GhostSourceWalkFrontier(c(0,0,0),c(2,0,0),6000);var start=f.popVisit();
        f.addObservedEdge(start,c(1,0,0),1.2);var middle=f.popVisit();f.addObservedEdge(middle,c(2,0,0),1.7);var end=f.popVisit();
        assertEquals(List.of(c(0,0,0),c(1,0,0),c(2,0,0)),GhostSourceWalkFrontier.reconstruct(end));
        assertEquals(2.9,end.totalCost(),1e-12);assertEquals(List.of(c(0,0,0)),GhostSourceWalkFrontier.reconstruct(start));
    }
}
