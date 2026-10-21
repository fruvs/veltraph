package dev.veltraph.client.combat.ghost;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import dev.veltraph.client.combat.ghost.GhostSourceWalkFrontier.Cell;
import dev.veltraph.client.combat.ghost.GhostSourceWalkWorker.*;
final class GhostRecoveryDiagnosticTest {
    @Test void progressRecordingPreservesActualWorkerResultAndNodeCap(){var point=new Cell(0,76,0);var in=new Inputs(point,point,6000,1,1,false,false,Observed.YES,Observed.YES,Observed.YES,Observed.YES,Map.of());
        var edges=new ObservedEdges(){public boolean current(){return true;}public Edge observe(EdgeKey key){return new Edge(Observed.NO,Double.NaN);}};
        var before=GhostSourceWalkWorker.solve(in,edges);var progress=new GhostSourceWalkWorker.Progress();var after=GhostSourceWalkWorker.solve(in,edges,progress);
        assertEquals(before,after);assertEquals(1,progress.cached().visited());assertEquals("GRAPH_FOUND",progress.cached().stage());assertTrue(progress.cached().startedNanos()>0);}
    @Test void cachedDemandCountersDoNotReadNativeDataOrConsumeQueuedFutures(){var capture=new GhostSourceWalkDemandCapture<Integer,Integer>();capture.prefetch(List.of(1,2,3));
        var before=capture.cachedDiagnostic();assertEquals(3,before.publishedKeys());assertEquals(0,before.nativeReads());assertEquals(3,before.pendingSpeculative());
        assertEquals(before,capture.cachedDiagnostic());assertEquals(3,capture.pump(1,()->0,()->true,key->key));var after=capture.cachedDiagnostic();assertEquals(3,after.nativeReads());assertEquals(3,after.observedKeys());assertEquals(0,after.pendingSpeculative());capture.revoke();assertFalse(capture.cachedDiagnostic().valid());}
}
