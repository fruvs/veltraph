package dev.veltraph.client.combat.ghost;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import dev.veltraph.client.combat.ghost.GhostSourceWalkFrontier.*;
import dev.veltraph.client.combat.ghost.GhostSourceWalkWorker.*;

/** Privately produced source coordinate Graph(start,end,cap); bounded native capture, original pure graph and seven smoothing passes. */
final class McGhostSourceCoordinatePipeline {
    enum State { CAPTURING, VALIDATING, READY, REVOKED }
    static final class Path {
        private final McGhostSourceCoordinatePipeline producer;private final List<GhostSourceWalkEdgeGeometry.Edge> edges;private final Map<GhostSourceWalkEdgeGeometry.Edge,Double> radii;
        private Path(McGhostSourceCoordinatePipeline producer,Work actual){this.producer=producer;edges=actual.edges;radii=actual.radii;}
        List<GhostSourceWalkEdgeGeometry.Edge> edges(){return edges;}double radius(GhostSourceWalkEdgeGeometry.Edge e){return radii.get(e);}
        boolean current(){return producer.currentPath(this);}Object sourceIdentity(){return this;}
        GhostSourceCoordinateGraphEvidence cachedGraphEvidence(){return producer.cachedGraphEvidence();}
    }
    private record Work(Result graph,List<GhostSourceWalkEdgeGeometry.Edge> edges,Map<GhostSourceWalkEdgeGeometry.Edge,Double> radii,List<Map.Entry<Cell,GhostSourceWalkObservedWorld.CellValue>> cells,GhostSourceWalkTerrainEpochs.Footprint footprint) {}
    private final Minecraft client;private final java.util.function.BooleanSupplier scope;
    private final Cell start,end;private final int cap;private final boolean jumpBoost,smoothing,tailVeto;private final int rough;
    private final GhostSourceWalkForbiddenCells.Observation forbidden;
    private final GhostSourceWalkDemandCapture<Cell,GhostSourceWalkObservedWorld.CellValue> capture=new GhostSourceWalkDemandCapture<>();
    private McGhostSourceCoordinateTerrainSnapshot terrain;private final GhostSourceWalkSolverExecutor.Task<Work> workerTask;private final CompletableFuture<Work> worker;
    private GhostSourceCoordinateGraphEvidence cachedGraph;
    private final GhostSourceWalkWorker.Progress workerProgress=new GhostSourceWalkWorker.Progress();
    private final GhostSourceWalkPhaseTimings phases=new GhostSourceWalkPhaseTimings();private final GhostSourceWalkReadProfile reads=new GhostSourceWalkReadProfile();
    private long totalPumpNanos,maximumPumpNanos;private int pumpSlices;
    private State state=State.CAPTURING;private Path path;private Work completed;private int validationIndex;private GhostSourceWalkTerrainEpochs.Stamp epoch;private McGhostSourceRelevantChunks relevantChunks;
    private McGhostSourceCoordinatePipeline(Minecraft client,java.util.function.BooleanSupplier scope,Cell start,Cell end,int cap,boolean jumpBoost,boolean smoothing,int rough,boolean tailVeto,String policy){
        this.client=client;this.scope=scope;this.start=start;this.end=end;this.cap=cap;this.jumpBoost=jumpBoost;this.smoothing=smoothing;this.rough=rough;this.tailVeto=tailVeto;
        cachedGraph=GhostSourceCoordinateGraphEvidence.awaiting(cap,start,end);
        forbidden=GhostSourceWalkForbiddenCells.fromActualConfiguredJson(policy);forbidden.requireObserved();
        terrain=McGhostSourceCoordinateTerrainSnapshot.observe(client,scope,null);terrain.measureReadsWith(reads);
        GhostSourceWalkTerrainEpochs.capture(client.level,GhostSourceWalkTerrainEpochs.footprint(java.util.Set.of()));
        // The exact start/end preflight dependencies are fixed at issuance and are consumed by
        // every entered worker's Inputs expression. Prime before submission so queued candidates
        // can obtain these native observations without occupying either solver thread.
        capture.primeRequired(List.of(start,end));
        workerTask=GhostSourceWalkSolverExecutor.submitResumable(this::solve,capture::revoke);worker=workerTask.future();
    }
    static McGhostSourceCoordinatePipeline begin(Minecraft client,java.util.function.BooleanSupplier scope,Cell start,Cell end,int cap,boolean jumpBoost,boolean smoothing,int rough,boolean tailVeto,String policy){
        if(client==null||!client.isSameThread()||!scope.getAsBoolean()||cap<1)throw new IllegalStateException("Actual native coordinate issuance required");
        return new McGhostSourceCoordinatePipeline(client,scope,start,end,cap,jumpBoost,smoothing,rough,tailVeto,policy);
    }
    /** Pure passive getter: no scope, native reads, future join or solve/pump. */
    GhostSourceCoordinateGraphEvidence cachedGraphEvidence(){return cachedGraph;}
    record Diagnostic(State state,int cap,Cell start,Cell end,int validationIndex,int validationCount,int pumpSlices,long totalPumpNanos,long maximumPumpNanos,GhostSourceCoordinateGraphEvidence graph,GhostSourceWalkDemandCapture.Diagnostic demand,GhostSourceWalkWorker.Progress.Evidence worker,GhostSourceWalkPhaseTimings.Evidence phases,GhostSourceWalkReadProfile.Evidence reads) {}
    Diagnostic cachedDiagnostic(){return new Diagnostic(state,cap,start,end,validationIndex,completed==null?0:completed.cells.size(),pumpSlices,totalPumpNanos,maximumPumpNanos,cachedGraph,capture.cachedDiagnostic(),workerProgress.cached(),phases.cached(),reads.cached());}
    private boolean measuredScope(){long start=System.nanoTime();try{return scope.getAsBoolean();}finally{phases.add(GhostSourceWalkPhaseTimings.Phase.SCOPE,System.nanoTime()-start);}}
    State state(){return state;}Path path(){if(state!=State.READY||!scope.getAsBoolean())throw new IllegalStateException("Native coordinate result not ready/current");return path;}
    void pump(long budget){
        if(!client.isSameThread())throw new IllegalStateException("Coordinate capture requires client thread");if(state==State.READY||state==State.REVOKED||budget<1)return;
        long began=System.nanoTime(),bounded=Math.min(4_000_000L,budget);
        try{if(!measuredScope()){revoke();return;}long frameStart=System.nanoTime();terrain=McGhostSourceCoordinateTerrainSnapshot.observe(client,scope,terrain);terrain.measureReadsWith(reads);phases.add(GhostSourceWalkPhaseTimings.Phase.NATIVE_FRAME,System.nanoTime()-frameStart);
            if(state==State.CAPTURING){long batchStart=System.nanoTime();var runtimeBefore=reads.beginBatch();
                terrain.beginReadBatch();try{capture.pumpCoordinate(Math.max(1,bounded-(System.nanoTime()-began)),System::nanoTime,()->GhostSourceCoordinateFence.current(this::measuredScope,terrain::batchCurrent),cell->{long readStart=System.nanoTime();reads.beginRead();try{return terrain.captureValue(cell);}finally{long elapsed=System.nanoTime()-readStart;phases.add(GhostSourceWalkPhaseTimings.Phase.NATIVE_READ,elapsed);reads.recordRead(elapsed);}},workerTask::executing);}
                finally{if(!terrain.endReadBatch())revoke();reads.endBatch(runtimeBefore,System.nanoTime()-batchStart);phases.add(GhostSourceWalkPhaseTimings.Phase.CAPTURE_BATCH,System.nanoTime()-batchStart);}
                if(state==State.REVOKED)return;
                if(capture.state()==GhostSourceWalkDemandCapture.State.FAILED){revoke();return;}
                if(worker.isDone()){completed=worker.join();cachedGraph=GhostSourceCoordinateGraphEvidence.completed(cap,start,end,completed.graph);epoch=GhostSourceWalkTerrainEpochs.capture(client.level,completed.footprint);relevantChunks=McGhostSourceRelevantChunks.capture(client,completed.footprint);state=State.VALIDATING;}}
            if(state==State.VALIDATING){if(!GhostSourceWalkTerrainEpochs.current(epoch)||!relevantChunks.current(client)){revoke();return;}
                long validationStart=System.nanoTime();terrain.beginReadBatch();
                try{while(validationIndex<completed.cells.size()&&System.nanoTime()-began<bounded){
                    // Retain exact producer/config/registration checks on both sides of every validation value.
                    var entry=completed.cells.get(validationIndex++);long readStart=System.nanoTime();boolean matches;
                    try{matches=GhostSourceCoordinateFence.read(this::measuredScope,terrain::batchCurrent,()->terrain.matchesValue(entry.getKey(),entry.getValue()));}finally{phases.add(GhostSourceWalkPhaseTimings.Phase.VALIDATION_READ,System.nanoTime()-readStart);}
                    if(!matches){revoke();return;}
                }}finally{if(!terrain.endReadBatch())revoke();phases.add(GhostSourceWalkPhaseTimings.Phase.VALIDATION_BATCH,System.nanoTime()-validationStart);}
                if(state==State.REVOKED)return;
                if(!scope.getAsBoolean()||!GhostSourceWalkTerrainEpochs.current(epoch)||!relevantChunks.current(client)){revoke();return;}
                if(validationIndex==completed.cells.size()){capture.seal(()->scope.getAsBoolean()&&terrain.current()&&GhostSourceWalkTerrainEpochs.current(epoch)&&relevantChunks.current(client));
                    if(!GhostSourceCoordinateFence.publication(scope,terrain::current,()->GhostSourceWalkTerrainEpochs.current(epoch)&&relevantChunks.current(client),()->capture.valid()&&capture.state()==GhostSourceWalkDemandCapture.State.SEALED)){revoke();return;}
                    if(completed.graph.status()==Status.FOUND&&!completed.edges.isEmpty())path=new Path(this,completed);state=State.READY;}}
        }catch(RuntimeException unavailable){revoke();}finally{long elapsed=System.nanoTime()-began;totalPumpNanos+=elapsed;maximumPumpNanos=Math.max(maximumPumpNanos,elapsed);pumpSlices++;}
    }
    void revoke(){state=State.REVOKED;capture.revoke();workerTask.cancel();}
    private boolean currentPath(Path actual){return client.isSameThread()&&state==State.READY&&path==actual&&scope.getAsBoolean()&&epoch!=null&&GhostSourceWalkTerrainEpochs.current(epoch)&&relevantChunks!=null&&relevantChunks.current(client);}
    McGhostSourceCoordinateTerrainSnapshot freshTerrain(){if(!scope.getAsBoolean())throw new IllegalStateException("Coordinate scope revoked");return terrain=McGhostSourceCoordinateTerrainSnapshot.observe(client,scope,terrain);}
    private GhostSourceWalkObservedWorld.CellValue cell(Cell c){if(!capture.alreadyObserved(c))capture.prefetch(GhostSourceWalkPrefetch.around(c));return capture.requireAvailable(c);}
    private final CoordinateWorkContinuation pureWork=new CoordinateWorkContinuation();
    private Work solve(){return pureWork.advance();}

    /** Off-thread continuation only. Native facts still originate in the unchanged guarded pump. */
    private final class CoordinateWorkContinuation {
        private final GhostSourceWalkObservedWorld world=new GhostSourceWalkObservedWorld(McGhostSourceCoordinatePipeline.this::cell,capture::valid);
        private GhostSourceWalkGraphContinuation graph;
        private Result graphResult;
        private GhostSourceWalkSmoothingContinuation smoothingWork;
        private GhostSourcePathSmoothing.Result smoothed;
        private final java.util.ArrayList<GhostSourceWalkEdgeGeometry.Edge> edges=new java.util.ArrayList<>();
        private final java.util.HashMap<GhostSourceWalkEdgeGeometry.Edge,Double> radii=new java.util.HashMap<>();
        private Cell prior;
        private int edgeIndex;
        private boolean started,edgeStage;
        private Work terminal;
        Work advance(){
            if(terminal!=null)return terminal;
            if(!capture.valid())throw new IllegalStateException("Actual source coordinate capture revoked");
            if(!started){workerProgress.stage("PREFLIGHT");started=true;}
            if(graph==null){
                // Same start/end evaluation and flags as the original coordinate Graph inputs.
                var inputs=new Inputs(start,end,cap,1,1,false,false,
                    GhostSourceOrdinaryWalkTerrain.preflightAir(world.block(start))?Observed.YES:Observed.NO,
                    GhostSourceOrdinaryWalkTerrain.preflightAir(world.block(end))?Observed.YES:Observed.NO,
                    Observed.YES,Observed.YES,Map.of());
                graph=new GhostSourceWalkGraphContinuation(inputs,new ObservedEdges(){
                    public boolean current(){return capture.valid();}
                    public Edge observe(EdgeKey key){
                        var move=new Offset(key.to().x()-key.from().x(),key.to().y()-key.from().y(),key.to().z()-key.from().z());
                        return GhostSourceOrdinaryWalkTerrain.admit(world,key.from(),move,forbidden.requireObserved(),jumpBoost)
                            ?new Edge(Observed.YES,GhostSourceOrdinaryWalkTerrain.terrainCost(world,key.from(),move,jumpBoost,cap))
                            :new Edge(Observed.NO,Double.NaN);
                    }
                },workerProgress);
            }
            if(graphResult==null)graphResult=graph.advance();
            if(graphResult.status()==Status.FOUND){
                if(smoothingWork==null){
                    var context=new GhostSourcePathSmoothing.Context(smoothing?GhostSourcePathSmoothing.Known.YES:GhostSourcePathSmoothing.Known.NO,
                        false,false,GhostSourcePathSmoothing.Known.YES,GhostSourcePathSmoothing.Known.NO,
                        tailVeto?GhostSourcePathSmoothing.Known.YES:GhostSourcePathSmoothing.Known.NO,rough);
                    var points=graphResult.orderedNodes().stream().map(c->new GhostSourcePathSmoothing.Cell(c.x(),c.y(),c.z())).toList();
                    workerProgress.stage("SMOOTHING");
                    smoothingWork=new GhostSourceWalkSmoothingContinuation(points,context,
                        new GhostSourceWalkObservedSmoothingWorld(world,McGhostSourceCoordinatePipeline.this::cell));
                }
                if(smoothed==null)smoothed=smoothingWork.advance();
                if(!edgeStage){workerProgress.stage("EDGES");edgeStage=true;prior=start;}
                while(edgeIndex<smoothed.cells().size()){
                    var c=smoothed.cells().get(edgeIndex);var next=new Cell(c.x(),c.y(),c.z());
                    if(next.equals(prior)&&graphResult.orderedNodes().size()>1){edgeIndex++;continue;}
                    // Retain this same edge until ALL geometry and radius probes succeed.
                    // A pending probe discards only local pure temporaries, never committed edges.
                    var edge=GhostSourceWalkEdgeGeometry.ground(prior,next,world);
                    double radius=GhostSourceOrdinaryWalkTerrain.flatNeighborhood(world,next)?.75:.5;
                    edges.add(edge);radii.put(edge,radius);prior=next;edgeIndex++;
                }
            }
            if(!capture.valid())throw new IllegalStateException("Actual source coordinate capture revoked");
            workerProgress.stage("FINISHED");var used=capture.usedSnapshot();
            return terminal=new Work(graphResult,List.copyOf(edges),Map.copyOf(radii),List.copyOf(used.entrySet()),
                GhostSourceWalkTerrainEpochs.footprint(used.keySet()));
        }
    }
}
