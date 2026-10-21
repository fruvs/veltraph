package dev.veltraph.client.combat.ghost;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import dev.veltraph.client.combat.ghost.GhostSourceWalkFrontier.*;
class GhostSourceWalkDeepChainTest {
    @Test void sixThousandNodeChainPreservesOriginalLeftAssociatedCostsWithoutRecursiveStack(){
        var frontier=new GhostSourceWalkFrontier(new Cell(0,76,0),new Cell(5999,76,0),6000);
        double expected=0;Node last=null;
        for(int x=0;x<6000;x++){
            last=frontier.popVisit();assertEquals(expected,last.totalCost(),0);
            if(x<5999){frontier.addObservedEdge(last,new Cell(x+1,76,0),.1);expected+=.1;}
        }
        assertTrue(frontier.reached(last));assertEquals(6000,frontier.visitedCount());assertFalse(frontier.underVisitLimit());
        assertEquals(6000,GhostSourceWalkFrontier.reconstruct(last).size());assertEquals(expected,last.priority(),0);
    }
}
