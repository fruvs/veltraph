package dev.veltraph.client.combat.ghost;
import java.util.Map;
import java.util.HashMap;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import dev.veltraph.client.combat.ghost.GhostSourceWalkFrontier.*;
import dev.veltraph.client.combat.ghost.GhostSourceWalkWorker.*;
class GhostSourceWalkWorkerTest {
    private static final Cell START=new Cell(0,0,0);
    private static Inputs input(Cell end,int cap,Map<EdgeKey,Edge> edges){return new Inputs(START,end,cap,1,1,false,false,Observed.YES,Observed.YES,Observed.YES,Observed.YES,edges);}
    private static Map<EdgeKey,Edge> blocked(Cell from,boolean retry){
        var edges=new HashMap<EdgeKey,Edge>();
        for(var o:GhostSourceWalkFrontier.offsets(1,1,false,retry))if(GhostSourceWalkFrontier.structuralMove(o))edges.put(new EdgeKey(from,new Cell(from.x()+o.x(),from.y()+o.y(),from.z()+o.z()),retry),new Edge(Observed.NO,Double.NaN));
        return edges;
    }
    @Test void exactCapStillAllowsGoalWhenItIsTheLastPermittedPop(){
        var end=new Cell(1,0,0);var edges=blocked(START,false);edges.put(new EdgeKey(START,end,false),new Edge(Observed.YES,1));
        var result=GhostSourceWalkWorker.solve(input(end,2,edges));assertEquals(Status.FOUND,result.status());assertEquals(2,result.visitedCount());
        assertEquals(java.util.List.of(START,end),result.orderedNodes());assertEquals(java.util.List.of(1.0),result.graphEdgeCosts());
        assertEquals(Status.VISIT_LIMIT,GhostSourceWalkWorker.solve(input(end,1,edges)).status());
    }
    @Test void unknownEdgesDoNotBecomeBlockedOrInventAPath(){
        var result=GhostSourceWalkWorker.solve(input(new Cell(1,0,0),6000,Map.of()));
        assertEquals(Status.UNKNOWN_INPUT,result.status());assertEquals(1,result.visitedCount());assertTrue(result.orderedNodes().isEmpty());assertFalse(result.dropRetry());
    }
    @Test void exhaustedFirstAttemptResetPreservesCapAndEnablesSourceDeepDrop(){
        var end=new Cell(0,-3,0);var edges=blocked(START,false);edges.putAll(blocked(START,true));
        edges.put(new EdgeKey(START,end,true),new Edge(Observed.YES,7));
        var result=GhostSourceWalkWorker.solve(input(end,2,edges));assertEquals(Status.FOUND,result.status());
        assertTrue(result.dropRetry());assertEquals(1,result.firstAttemptVisited());assertEquals(2,result.visitedCount());assertEquals(java.util.List.of(7.0),result.graphEdgeCosts());
    }
    @Test void emptyFrontierAfterDropTerminatesAndDoesNotKeepRetrying(){
        var edges=blocked(START,false);edges.putAll(blocked(START,true));var result=GhostSourceWalkWorker.solve(input(new Cell(2,0,0),6000,edges));
        assertEquals(Status.NO_UNVISITED,result.status());assertTrue(result.dropRetry());assertEquals(1,result.firstAttemptVisited());assertEquals(1,result.visitedCount());
    }
    @Test void sourcePreflightFailureOrderAndSingleNodePathAreRetained(){
        var in=new Inputs(START,new Cell(1,0,0),6000,1,1,false,false,Observed.NO,Observed.UNKNOWN,Observed.NO,Observed.UNKNOWN,Map.of());
        assertEquals(Status.START_NOT_AIR,GhostSourceWalkWorker.solve(in).status());
        var result=GhostSourceWalkWorker.solve(input(START,1,Map.of()));assertEquals(Status.FOUND,result.status());assertEquals(1,result.visitedCount());
        assertEquals(java.util.List.of(START),result.orderedNodes());assertTrue(result.graphEdgeCosts().isEmpty());
    }
}
