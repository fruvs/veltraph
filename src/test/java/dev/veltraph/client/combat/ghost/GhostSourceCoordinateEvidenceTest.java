package dev.veltraph.client.combat.ghost;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static dev.veltraph.client.combat.ghost.GhostSourceWalkWorker.*;
import dev.veltraph.client.combat.ghost.GhostSourceWalkFrontier.Cell;
class GhostSourceCoordinateEvidenceTest {
    Result realPureResult(int cap){var start=new Cell(0,76,0);var end=new Cell(1,76,0);
        var inputs=new Inputs(start,end,cap,1,1,false,false,Observed.YES,Observed.YES,Observed.YES,Observed.YES,Map.of());
        return GhostSourceWalkWorker.solve(inputs,new ObservedEdges(){public boolean current(){return true;}
            public Edge observe(EdgeKey edge){return edge.from().equals(start)&&edge.to().equals(end)?new Edge(Observed.YES,1):new Edge(Observed.NO,Double.NaN);}});}
    @Test void actualCompletedPureWorkerFactsSurviveBothCoordinateCaps(){for(int cap:new int[]{400,2000}){
        var result=realPureResult(cap);assertEquals(Status.FOUND,result.status());
        var evidence=GhostSourceCoordinateGraphEvidence.completed(cap,new Cell(0,76,0),new Cell(1,76,0),result);
        assertEquals(cap,evidence.cap());assertEquals(result.status(),evidence.graphStatus());assertEquals(result.visitedCount(),evidence.visitedCount());assertEquals(2,evidence.visitedCount());}}
    @Test void awaitingCaptureIsDistinctFromAnActualCompletedVisitLimit(){var awaiting=GhostSourceCoordinateGraphEvidence.awaiting(400,new Cell(0,76,0),new Cell(1,76,0));assertNull(awaiting.graphStatus());assertEquals(-1,awaiting.visitedCount());
        var result=realPureResult(1);assertEquals(Status.VISIT_LIMIT,result.status());var completed=GhostSourceCoordinateGraphEvidence.completed(1,awaiting.start(),awaiting.endpoint(),result);assertEquals(Status.VISIT_LIMIT,completed.graphStatus());assertEquals(1,completed.visitedCount());}
    @Test void passiveToolDeadlineGetterDoesNotConsumeOrResampleActualLifecycleDelay(){var life=new GhostSourceLeavingLifecycle();life.begin(List.of(new Cell(1,76,1)),n->0);life.captured(true,GhostSourceLeavingLifecycle.Policy.ETHERWARP_OUT,true,100,n->0);
        assertEquals(105,life.cachedToolAtTick());assertEquals(105,life.cachedToolAtTick());assertFalse(life.toolDue(104));assertTrue(life.toolDue(105));life.toolSelected();assertEquals(Long.MIN_VALUE,life.cachedToolAtTick());}
}
