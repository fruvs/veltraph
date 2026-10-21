package dev.veltraph.client.combat.ghost;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import dev.veltraph.client.combat.ghost.GhostSourceWalkFrontier.*;
import dev.veltraph.client.combat.ghost.GhostSourceWalkWorker.*;

/** Pure native-like tick scheduler over declared immutable floor facts; no Minecraft world is opened.
 * This proves result/guard/scheduling contracts, not elapsed native performance or recovery acceptance. */
class GhostSourceResumableTerrainWorkerTest {
    private static GhostSourceWalkObservedWorld.CellValue actualFloor(Cell cell){
        boolean solid=cell.y()==75;
        var block=new GhostSourceOrdinaryWalkTerrain.Block(GhostSourceOrdinaryWalkTerrain.Kind.OTHER,
            cell.y()>=76,false,solid,false,0,solid?76:0);
        return new GhostSourceWalkObservedWorld.CellValue(block,null,null,false);
    }
    private static ObservedEdges edges(GhostSourceWalkObservedWorld world){return new ObservedEdges(){
        public boolean current(){return world.current();}
        public Edge observe(EdgeKey key){
            var move=new Offset(key.to().x()-key.from().x(),key.to().y()-key.from().y(),key.to().z()-key.from().z());
            return GhostSourceOrdinaryWalkTerrain.admit(world,key.from(),move,java.util.Set.of(),false)
                ?new Edge(Observed.YES,GhostSourceOrdinaryWalkTerrain.terrainCost(world,key.from(),move,false,400))
                :new Edge(Observed.NO,Double.NaN);
        }
    };}
    @Test void nineGraphWorkersShareTwoThreadsAndClientTickDemandsWithoutChangingSourceResults() throws Exception {
        var captures=new ArrayList<GhostSourceWalkDemandCapture<Cell,GhostSourceWalkObservedWorld.CellValue>>();
        var tasks=new ArrayList<GhostSourceWalkSolverExecutor.Task<Result>>();var expected=new ArrayList<Result>();
        var direct=new GhostSourceWalkObservedWorld(GhostSourceResumableTerrainWorkerTest::actualFloor,()->true);
        try {
            for(int i=0;i<9;i++){
                var capture=new GhostSourceWalkDemandCapture<Cell,GhostSourceWalkObservedWorld.CellValue>();captures.add(capture);
                var inputs=new Inputs(new Cell(0,76,i*2),new Cell(6,76,i*2),400,1,1,false,false,
                    Observed.YES,Observed.YES,Observed.YES,Observed.YES,Map.of());
                expected.add(GhostSourceWalkWorker.solve(inputs,edges(direct)));
                var world=new GhostSourceWalkObservedWorld(cell->{
                    if(!capture.alreadyObserved(cell))capture.prefetch(GhostSourceWalkPrefetch.around(cell));
                    return capture.requireAvailable(cell);
                },capture::valid);
                var continuation=new GhostSourceWalkGraphContinuation(inputs,edges(world),null);
                tasks.add(GhostSourceWalkSolverExecutor.submitResumable(continuation::advance,capture::revoke));
            }
            int ticks=0;var clock=new AtomicLong();var fenceCalls=new AtomicLong();
            long fixtureDeadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
            while(tasks.stream().anyMatch(task->!task.future().isDone())&&System.nanoTime()<fixtureDeadline){
                ticks++;long frameStart=clock.get();
                for(var capture:captures){long remaining=4_000_000L-(clock.get()-frameStart);if(remaining<=0)break;
                    capture.pump(remaining,clock::get,()->{fenceCalls.incrementAndGet();return capture.valid();},cell->{clock.addAndGet(10_000L);return actualFloor(cell);});}
                // Model a main-thread tick boundary and allow queued pure continuations to run.
                Thread.sleep(1);
            }
            assertTrue(ticks>0);assertTrue(tasks.stream().allMatch(task->task.future().isDone()),"All nine exact graphs must finish without parked solver slots");
            for(int i=0;i<9;i++){
                assertEquals(expected.get(i),tasks.get(i).future().get(1,TimeUnit.SECONDS));
                assertTrue(captures.get(i).valid());assertTrue(captures.get(i).cachedDiagnostic().nativeReads()>0);
                assertFalse(captures.get(i).usedSnapshot().isEmpty());
            }
            assertTrue(fenceCalls.get()>0);
        } finally {for(var task:tasks)task.cancel();}
    }
}
