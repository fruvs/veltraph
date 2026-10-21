package dev.veltraph.client.combat.ghost;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.util.Mth;
import dev.veltraph.client.VeltraphBootstrap;
import dev.veltraph.client.config.GhostGrindConfig;
import dev.veltraph.client.combat.ghost.GhostSourceWalkFrontier.*;
import dev.veltraph.client.combat.ghost.GhostSourceWalkWorker.*;

/** Actual native scalar capture, bounded main-thread demand reads, pure off-thread graph/smoothing/edge pipeline. */
public final class McGhostSourceWalkPipeline {
    public static final long MODERN_CAPTURE_BUDGET_NANOS=4_000_000L;
    public enum State { CAPTURING, VALIDATING, READY, REVOKED, FAILED }
    /** Private producer: no public constructor accepts caller-created paths or geometry. */
    public static final class ProducedPath {
        private final McGhostSourceWalkPipeline producer;private final Creeper target;private final List<GhostSourceWalkEdgeGeometry.Edge> edges;
        private final GhostSourcePathJobTransaction.WalkPath path;private final Map<GhostSourceWalkEdgeGeometry.Edge,Double> radii;
        private ProducedPath(McGhostSourceWalkPipeline producer,List<GhostSourceWalkEdgeGeometry.Edge> edges){
            this.producer=producer;target=producer.target;this.edges=List.copyOf(edges);radii=producer.worker.join().radii;
            path=new GhostSourcePathJobTransaction.WalkPath(this,edges.stream().map(e->new GhostSourcePathResultScoring.Edge((int)Math.floor(e.from().y()),e.geometricLength())).toList());
        }
        public GhostSourcePathJobTransaction.WalkPath path(){return path;}public Creeper target(){return target;}
        public List<GhostSourceWalkEdgeGeometry.Edge> edges(){return edges;}
        public CaptureEvidence cachedEvidence(){return producer.cachedEvidence();}
        public ActorAvoidanceEvidence cachedActorAvoidanceEvidence(){return producer.cachedActorAvoidanceEvidence();}
        public GhostSourceWalkPhaseTimings.Evidence cachedPhaseEvidence(){return producer.cachedPhaseEvidence();}
        public GhostSourceWalkReadProfile.Evidence cachedReadEvidence(){return producer.cachedReadEvidence();}
        double sourceRadius(GhostSourceWalkEdgeGeometry.Edge edge){return radii.get(edge);}
        boolean ownedBy(GhostSourceWalkJob job){return producer.job==job&&producer.state==State.READY&&producer.produced==this;}
    }
    private record Initial(Cell playerCell,GhostSourceSampledRay.Point player,List<GhostSourceSampledRay.Point> footCorners,boolean playerGrounded,
        Cell targetCell,GhostSourceSampledRay.Point targetEye,GhostSourceSampledRay.Box targetBox,boolean targetGrounded,float playerYaw,
        List<GhostSourceSampledRay.Actor> actors,boolean jumpBoost,boolean smoothing,int roughStrength,boolean tailVeto,String forbiddenPolicy) {}
    private record Work(Result graph,List<GhostSourceWalkEdgeGeometry.Edge> edges,Map<GhostSourceWalkEdgeGeometry.Edge,Double> radii,Map<Cell,GhostSourceWalkObservedWorld.CellValue> sealedCells,List<Map.Entry<Cell,GhostSourceWalkObservedWorld.CellValue>> observedCells,GhostSourceWalkTerrainEpochs.Footprint footprint) {}
    private final GhostSourceWalkWorker.Progress workerProgress=new GhostSourceWalkWorker.Progress();
    private final GhostSourceWalkPhaseTimings phaseTimings=new GhostSourceWalkPhaseTimings();
    private final GhostSourceWalkReadProfile readProfile=new GhostSourceWalkReadProfile();
    private final Minecraft client;private final GhostSourceWalkJob job;private final Creeper target;private final GhostSourceIssuedSearchJob.Receipt receipt;
    private final Initial initial;private final GhostSourceWalkForbiddenCells.Observation forbidden;
    private final GhostSourceActorReservations issuedActorReservations;private final double actualBodyWidth,actualBodyHeight;
    private volatile boolean actorShortcutRetainedRawSubpath;
    private final GhostSourceWalkDemandCapture<Cell,GhostSourceWalkObservedWorld.CellValue> capture=new GhostSourceWalkDemandCapture<>();
    private McGhostSourceWalkTerrainSnapshot terrain;private final GhostSourceWalkSolverExecutor.Task<Work> workerTask;private final CompletableFuture<Work> worker;private volatile Work finishedWork;
    private final Thread clientThread=Thread.currentThread();private volatile String actualWorkerThread="";private volatile boolean workerUsedClientThread;private volatile List<String> smoothingStages=List.of();
    private long pumpTotalNanos,pumpMaxNanos;private int pumpSlices;
    private volatile State state=State.CAPTURING;private ProducedPath produced;
    private GhostSourceWalkTerrainEpochs.Stamp validationEpoch;private McGhostSourceRelevantChunks relevantChunks;
    private final java.util.Set<Cell> dynamicCells=new java.util.HashSet<>();
    private List<Map.Entry<Cell,GhostSourceWalkObservedWorld.CellValue>> validation;private int validationIndex;
    private McGhostSourceWalkPipeline(Minecraft client,GhostSourceWalkJob job,Creeper target){
        this.client=client;this.job=job;this.target=target;receipt=job.issuedReceipt();
        if(receipt==null||!receipt.candidateFrameCurrent(target))throw new IllegalStateException("Actual issued cohort/candidate frame required");
        var managers=McGhostSourcePathManagerSnapshot.observe(client,job.parent(),receipt);var flags=managers.flags();
        if(flags.minerManagerActive()||flags.skillManagerActiveAndPredicate()||flags.separateEndParentEnabledAndPointsNonempty()||flags.specialManagerActive()||!managers.graphPreferredAreaKnownAbsent())throw new IllegalStateException("Applicable original manager/area modifier producer required");
        var config=VeltraphBootstrap.config();String policy=config.get(GhostGrindConfig.GHOST_WALK_FORBIDDEN_CELLS);
        forbidden=GhostSourceWalkForbiddenCells.fromActualConfiguredJson(policy);forbidden.requireObserved();
        terrain=McGhostSourceWalkTerrainSnapshot.observe(client,receipt,target);terrain.measureReadsWith(readProfile);
        var p=client.player;var box=p.getBoundingBox();var eye=target.getEyePosition();
        var dimensions=p.getDimensions(net.minecraft.world.entity.Pose.STANDING);actualBodyWidth=dimensions.width();actualBodyHeight=dimensions.height();
        issuedActorReservations=new GhostSourceActorReservations(terrain.capturedActors(),actualBodyWidth,actualBodyHeight,.08);
        initial=new Initial(new Cell(Mth.floor(p.getX()),Mth.floor(p.getY()),Mth.floor(p.getZ())),new GhostSourceSampledRay.Point(p.getX(),p.getY(),p.getZ()),
            List.of(new GhostSourceSampledRay.Point(box.minX,box.minY,box.minZ),new GhostSourceSampledRay.Point(box.minX,box.minY,box.maxZ),new GhostSourceSampledRay.Point(box.maxX,box.minY,box.minZ),new GhostSourceSampledRay.Point(box.maxX,box.minY,box.maxZ)),p.onGround(),
            new Cell(target.blockPosition().getX(),target.blockPosition().getY(),target.blockPosition().getZ()),new GhostSourceSampledRay.Point(eye.x,eye.y,eye.z),terrain.capturedTargetBox(),target.onGround(),p.getYRot(),terrain.capturedActors(),
            flags.jumpBoostPresent(),config.get(GhostGrindConfig.GHOST_PATH_SMOOTHING_ENABLED),config.get(GhostGrindConfig.GHOST_PATH_SHORTCUT_DISTANCE),config.get(GhostGrindConfig.GHOST_PATH_KEEP_FINE_ADJUSTMENTS),policy);
        GhostSourceWalkTerrainEpochs.capture(client.level,GhostSourceWalkTerrainEpochs.footprint(java.util.Set.of()));
        workerTask=GhostSourceWalkSolverExecutor.submit(this::solvePure,capture::revoke);worker=workerTask.future();worker.thenAccept(w->finishedWork=w);
    }
    static McGhostSourceWalkPipeline begin(Minecraft client,GhostSourceWalkJob job,Creeper target){
        if(client==null||!client.isSameThread()||job==null||target==null)throw new IllegalStateException("Actual client-thread worker issuance required");return new McGhostSourceWalkPipeline(client,job,target);
    }
    public record CaptureEvidence(Status graphStatus,int visitedCount,int visitLimit,List<String> smoothingStages,int capturedCells,int footprintCells,String workerThread,boolean workerUsedClientThread,long totalPumpNanos,long maximumPumpNanos,int pumpSlices) {}
    public CaptureEvidence cachedEvidence(){var work=finishedWork;return new CaptureEvidence(work==null?null:work.graph.status(),work==null?0:work.graph.visitedCount(),6000,smoothingStages,work==null?0:work.observedCells.size(),work==null?0:work.footprint.cells().size(),actualWorkerThread,workerUsedClientThread,pumpTotalNanos,pumpMaxNanos,pumpSlices);}
    public GhostSourceWalkPhaseTimings.Evidence cachedPhaseEvidence(){return phaseTimings.cached();}
    public GhostSourceWalkReadProfile.Evidence cachedReadEvidence(){return readProfile.cached();}
    public record Diagnostic(State state,CaptureEvidence capture,GhostSourceWalkDemandCapture.Diagnostic demand,GhostSourceWalkWorker.Progress.Evidence worker,GhostSourceWalkPhaseTimings.Evidence phases,GhostSourceWalkReadProfile.Evidence reads) {}
    public Diagnostic cachedDiagnostic(){return new Diagnostic(state,cachedEvidence(),capture.cachedDiagnostic(),workerProgress.cached(),phaseTimings.cached(),readProfile.cached());}
    public State state(){return state;}
    /** Passive evidence of qualified situational overlay; no native ownership or geometry queries. */
    public record ActorAvoidanceEvidence(int issuedBlockers,boolean smoothedShortcutRetainedRawSubpath) {}
    public ActorAvoidanceEvidence cachedActorAvoidanceEvidence(){return new ActorAvoidanceEvidence(issuedActorReservations.blockerCount(),actorShortcutRetainedRawSubpath);}
    private boolean scopeCurrent(){long start=System.nanoTime();try{return actualScopeCurrent();}finally{phaseTimings.add(GhostSourceWalkPhaseTimings.Phase.SCOPE,System.nanoTime()-start);}}
    private boolean actualScopeCurrent(){
        if(!client.isSameThread()||!receipt.scopeCurrent()||!receipt.contains(target)||target.level()!=client.level||client.level.getEntity(target.getId())!=target)return false;
        var manager=McGhostSourcePathManagerSnapshot.observe(client,job.parent(),receipt);var f=manager.flags();var c=VeltraphBootstrap.config();
        return !f.minerManagerActive()&&!f.skillManagerActiveAndPredicate()&&!f.separateEndParentEnabledAndPointsNonempty()&&!f.specialManagerActive()
            &&f.jumpBoostPresent()==initial.jumpBoost&&manager.graphPreferredAreaKnownAbsent()
            &&c.get(GhostGrindConfig.GHOST_PATH_SMOOTHING_ENABLED)==initial.smoothing&&c.get(GhostGrindConfig.GHOST_PATH_SHORTCUT_DISTANCE)==initial.roughStrength
            &&c.get(GhostGrindConfig.GHOST_PATH_KEEP_FINE_ADJUSTMENTS)==initial.tailVeto&&java.util.Objects.equals(c.get(GhostGrindConfig.GHOST_WALK_FORBIDDEN_CELLS),initial.forbiddenPolicy);
    }
    /** Root calls this once per tick while SEARCH owns the registered job. No pose/input writes. */
    public void pump(){pump(MODERN_CAPTURE_BUDGET_NANOS);}
    public void pump(long budgetNanos){
        if(!client.isSameThread())throw new IllegalStateException("Native capture pump requires client thread");
        if(state==State.READY||state==State.FAILED||state==State.REVOKED)return;
        long actualBudget=Math.min(MODERN_CAPTURE_BUDGET_NANOS,budgetNanos);if(actualBudget<1)return;
        long begin=System.nanoTime();try{
            if(!scopeCurrent()){revoke();return;}
            long frameStart=System.nanoTime();terrain=McGhostSourceWalkTerrainSnapshot.observeContinuation(client,receipt,target,terrain);terrain.measureReadsWith(readProfile);phaseTimings.add(GhostSourceWalkPhaseTimings.Phase.NATIVE_FRAME,System.nanoTime()-frameStart);
            if(state==State.CAPTURING){long phaseStart=System.nanoTime();var runtimeBefore=readProfile.beginBatch();terrain.beginReadBatch();try{capture.pump(Math.max(1,actualBudget-(System.nanoTime()-begin)),System::nanoTime,terrain::batchCurrent,cell->{long readStart=System.nanoTime();readProfile.beginRead();try{var value=terrain.captureValue(cell);if(terrain.observedDynamicShape(cell))dynamicCells.add(cell);return value;}finally{long elapsed=System.nanoTime()-readStart;phaseTimings.add(GhostSourceWalkPhaseTimings.Phase.NATIVE_READ,elapsed);readProfile.recordRead(elapsed);}});}finally{if(!terrain.endReadBatch()){revoke();}readProfile.endBatch(runtimeBefore,System.nanoTime()-phaseStart);phaseTimings.add(GhostSourceWalkPhaseTimings.Phase.CAPTURE_BATCH,System.nanoTime()-phaseStart);}
                if(state==State.REVOKED)return;
                if(capture.state()==GhostSourceWalkDemandCapture.State.FAILED){state=State.FAILED;return;}
                if(worker.isDone()){var completed=worker.join();validation=completed.observedCells;validationEpoch=GhostSourceWalkTerrainEpochs.capture(client.level,completed.footprint);relevantChunks=McGhostSourceRelevantChunks.capture(client,completed.footprint);state=State.VALIDATING;}}
            if(state==State.VALIDATING){
                if(!GhostSourceWalkTerrainEpochs.current(validationEpoch)||!relevantChunks.current(client)){revoke();return;}
                long phaseStart=System.nanoTime();terrain.beginReadBatch();
                try{while(validationIndex<validation.size()&&System.nanoTime()-begin<actualBudget){var entry=validation.get(validationIndex++);long readStart=System.nanoTime();boolean matched;
                    try{matched=terrain.matchesValue(entry.getKey(),entry.getValue());}finally{phaseTimings.add(GhostSourceWalkPhaseTimings.Phase.VALIDATION_READ,System.nanoTime()-readStart);}
                    if(!matched){revoke();return;}}
                }finally{if(!terrain.endReadBatch()){revoke();}phaseTimings.add(GhostSourceWalkPhaseTimings.Phase.VALIDATION_BATCH,System.nanoTime()-phaseStart);}
                if(state==State.REVOKED)return;
                if(!scopeCurrent()||!GhostSourceWalkTerrainEpochs.current(validationEpoch)||!relevantChunks.current(client)){revoke();return;}
                if(validationIndex==validation.size()){
                    if(!scopeCurrent()||!GhostSourceWalkTerrainEpochs.current(validationEpoch)||!relevantChunks.current(client)){revoke();return;}long sealStart=System.nanoTime();capture.seal(this::scopeCurrent);var result=worker.join();phaseTimings.add(GhostSourceWalkPhaseTimings.Phase.SEAL,System.nanoTime()-sealStart);
                    if(result.graph.status()==Status.FOUND&& !result.edges.isEmpty())produced=new ProducedPath(this,result.edges);
                    state=State.READY;
                }
            }
        }catch(RuntimeException error){capture.revoke();state=State.FAILED;}finally{long elapsed=System.nanoTime()-begin;pumpTotalNanos+=elapsed;pumpMaxNanos=Math.max(pumpMaxNanos,elapsed);pumpSlices++;}
    }
    public void revoke(){capture.revoke();state=State.REVOKED;workerTask.cancel();}
    boolean remainingGeometryCurrent(){
        if(state!=State.READY||!scopeCurrent()||validationEpoch==null||!GhostSourceWalkTerrainEpochs.current(validationEpoch)||relevantChunks==null||!relevantChunks.current(client))return false;
        // Movement of source protected living actors can invalidate the standing endpoint independently of block epochs.
        long frameStart=System.nanoTime();var fresh=McGhostSourceWalkTerrainSnapshot.observeContinuation(client,receipt,target,terrain);terrain=fresh;fresh.measureReadsWith(readProfile);phaseTimings.add(GhostSourceWalkPhaseTimings.Phase.ACTION_FRAME,System.nanoTime()-frameStart);
        if(produced==null||produced.edges.isEmpty())return false;
        var liveActors=new GhostSourceActorReservations(fresh.capturedActors(),actualBodyWidth,actualBodyHeight,.08);
        int firstRemaining=0;var installed=job.parent().sourceNativeInstalledWalkCursor();
        if(installed!=null&&installed.sourcePathIdentity()==produced){
            firstRemaining=GhostSourceActorReservations.remainingStartIndex(produced.edges,installed.cachedCurrentEdgeEvidence().map(McGhostSourceOwnedWalkCursor.EdgeEvidence::identity));
        }
        for(int i=firstRemaining;i<produced.edges.size();i++)if(!clearActualTravel(liveActors,produced.edges.get(i)))return false;
        long dynamicStart=System.nanoTime();var prior=finishedWork.sealedCells;
        fresh.beginReadBatch();boolean dynamicValid=true;
        try{for(var cell:dynamicCells){if(!prior.containsKey(cell))continue;if(System.nanoTime()-dynamicStart>=MODERN_CAPTURE_BUDGET_NANOS){dynamicValid=false;break;}
            long readStart=System.nanoTime();try{if(!fresh.matchesValue(cell,prior.get(cell))){dynamicValid=false;break;}}finally{phaseTimings.add(GhostSourceWalkPhaseTimings.Phase.DYNAMIC_READ,System.nanoTime()-readStart);}}}
        finally{if(!fresh.endReadBatch())dynamicValid=false;}
        if(!dynamicValid||!scopeCurrent()||!GhostSourceWalkTerrainEpochs.current(validationEpoch)||!relevantChunks.current(client))return false;
var end=produced.edges.getLast().to();
        long rayStart=System.nanoTime();boolean clear;try{clear=fresh.protectedStandingRay(new Cell((int)Math.floor(end.x()),(int)Math.floor(end.y()),(int)Math.floor(end.z())),client.player.getYRot());}finally{phaseTimings.add(GhostSourceWalkPhaseTimings.Phase.PROTECTED_RAY,System.nanoTime()-rayStart);}
        return clear&&scopeCurrent();
    }
    boolean readyNoPath(){return state==State.READY&&scopeCurrent()&&produced==null&&worker.join().graph.status()!=Status.FOUND;}
    ProducedPath produced(){if(state!=State.READY||!scopeCurrent())throw new IllegalStateException("Actual sealed path not ready/current");return produced;}
    private GhostSourceWalkObservedWorld.CellValue requireCell(Cell c){
        if(capture.alreadyObserved(c))return capture.require(c);
        // The local 45-cell batch lets parked workers consume foot/head/support probes without one tick per cell.
        // Required reads retain priority; useful speculation is bounded by the same pump time and finite ceiling.
        capture.prefetch(GhostSourceWalkPrefetch.around(c));
        return capture.require(c);
    }
    private Work solvePure(){workerProgress.stage("STANDING");actualWorkerThread=Thread.currentThread().getName();workerUsedClientThread=Thread.currentThread()==clientThread;
        var world=new GhostSourceWalkObservedWorld(this::requireCell,capture::valid);
        var targetPoint=new GhostSourceOrderedStandingProvider.Point(initial.targetCell.x()+.5,initial.targetCell.y(),initial.targetCell.z()+.5);
        var standing=GhostSourceOrderedStandingProvider.choose(new GhostSourceOrderedStandingProvider.NativeInputs(){
            public boolean current(){return capture.valid();}public GhostSourceOrderedStandingProvider.ParentAreaObservation parentAreaObservation(){return GhostSourceOrderedStandingProvider.ParentAreaObservation.ABSENT;}
            public GhostSourceOrderedStandingProvider.Cell targetBlock(){return new GhostSourceOrderedStandingProvider.Cell(initial.targetCell.x(),initial.targetCell.y(),initial.targetCell.z());}
            public GhostSourceOrderedStandingProvider.Point targetStandingPoint(){return targetPoint;}
            public GhostSourceOrderedStandingProvider.Point actualPlayerPosition(){return new GhostSourceOrderedStandingProvider.Point(initial.player.x(),initial.player.y(),initial.player.z());}
            public boolean targetOnGround(){return initial.targetGrounded;}
            public boolean sourceCellStandable(GhostSourceOrderedStandingProvider.Cell c){var cell=new Cell(c.x(),c.y(),c.z());
                return world.standable(cell)&&issuedActorReservations.clear(standingBodyPoint(world,cell),standingBodyPoint(world,cell));}
            public boolean sourceProtectedTargetRay(GhostSourceOrderedStandingProvider.Cell c){var cell=new Cell(c.x(),c.y(),c.z());var below=new Cell(c.x(),c.y()-1,c.z());
                var eye=new GhostSourceSampledRay.Point(c.x()+.5,world.supportTop(below)+1.62,c.z()+.5);
                return GhostSourceSampledRay.hits(eye,initial.targetEye,initial.targetBox,GhostSourceFloatDirection.between(eye,initial.targetEye,initial.playerYaw),GhostSourceSampledRay.Mode.standing(),new GhostSourceSampledRay.Snapshot(){
                    public boolean passableRegistryObserved(){return capture.valid();}public List<GhostSourceSampledRay.Actor> actors(){return initial.actors;}
                    public GhostSourceSampledRay.Block block(GhostSourceSampledRay.Cell c){var v=requireCell(new Cell(c.x(),c.y(),c.z()));return new GhostSourceSampledRay.Block(true,v.block().sourcePassable(),v.rawCollision(),v.block().fluid());}
                });}
            public double parentFieldValue(GhostSourceOrderedStandingProvider.Cell c){throw new IllegalStateException("Observed parent ABSENT must not read field");}
            public boolean lavaAtOrBelow(GhostSourceOrderedStandingProvider.Cell c){return world.block(new Cell(c.x(),c.y(),c.z())).kind()==GhostSourceOrdinaryWalkTerrain.Kind.LAVA||world.block(new Cell(c.x(),c.y()-1,c.z())).kind()==GhostSourceOrdinaryWalkTerrain.Kind.LAVA;}
            public double customPenalty(GhostSourceOrderedStandingProvider.Cell c){return 0;} // Source worker mask4 supplies null optional custom score.
        });
        if(standing==null)return work(new Result(Status.NO_UNVISITED,List.of(),List.of(),0,false,0),List.of());
        workerProgress.stage("START_END");var end=resolveEnd(world,new Cell(standing.x(),standing.y(),standing.z()));if(end==null)return work(new Result(Status.END_NOT_AIR,List.of(),List.of(),0,false,0),List.of());
        var start=resolveStart(world);
        var in=new Inputs(start,end,6000,1,1,false,false,GhostSourceOrdinaryWalkTerrain.preflightAir(world.block(start))?Observed.YES:Observed.NO,GhostSourceOrdinaryWalkTerrain.preflightAir(world.block(end))?Observed.YES:Observed.NO,Observed.YES,Observed.YES,Map.of());
        var graph=GhostSourceWalkWorker.solve(in,new ObservedEdges(){public boolean current(){return capture.valid();}public Edge observe(EdgeKey key){
            var move=new Offset(key.to().x()-key.from().x(),key.to().y()-key.from().y(),key.to().z()-key.from().z());
            return GhostSourceOrdinaryWalkTerrain.admit(world,key.from(),move,forbidden.requireObserved(),initial.jumpBoost)
                    &&issuedActorReservations.clear(standingBodyPoint(world,key.from()),standingBodyPoint(world,key.to()))?new Edge(Observed.YES,GhostSourceOrdinaryWalkTerrain.terrainCost(world,key.from(),move,initial.jumpBoost,6000)):new Edge(Observed.NO,Double.NaN);}},workerProgress);
        if(graph.status()!=Status.FOUND)return work(graph,List.of());
        var points=graph.orderedNodes().stream().map(c->new GhostSourcePathSmoothing.Cell(c.x(),c.y(),c.z())).toList();
        var context=new GhostSourcePathSmoothing.Context(initial.smoothing?GhostSourcePathSmoothing.Known.YES:GhostSourcePathSmoothing.Known.NO,false,false,GhostSourcePathSmoothing.Known.YES,GhostSourcePathSmoothing.Known.NO,initial.tailVeto?GhostSourcePathSmoothing.Known.YES:GhostSourcePathSmoothing.Known.NO,initial.roughStrength);
        workerProgress.stage("SMOOTHING");var smoothed=GhostSourcePathSmoothing.apply(points,context,new GhostSourceWalkObservedSmoothingWorld(world,this::requireCell));smoothingStages=smoothed.transforms();
        var selectedCells=smoothed.cells();
        Cell previous=start;boolean shortcutClear=true;
        for(var c:selectedCells){var next=new Cell(c.x(),c.y(),c.z());
            if(!issuedActorReservations.clear(standingBodyPoint(world,previous),standingBodyPoint(world,next))){shortcutClear=false;break;}previous=next;}
        if(!shortcutClear){
            // Qualified transform constraint: retain the already source-frontier-produced safe subpath.
            // Never retry the same actor-crossing shortcut or replace it with another planner.
            selectedCells=graph.orderedNodes().stream().map(c->new GhostSourcePathSmoothing.Cell(c.x(),c.y(),c.z())).toList();actorShortcutRetainedRawSubpath=true;
        }
        workerProgress.stage("EDGES");var edges=new java.util.ArrayList<GhostSourceWalkEdgeGeometry.Edge>();previous=start;
        for(var c:selectedCells){var next=new Cell(c.x(),c.y(),c.z());if(next.equals(previous)&&graph.orderedNodes().size()>1)continue;
            var edge=GhostSourceWalkEdgeGeometry.ground(previous,next,world);
            if(!clearActualTravel(issuedActorReservations,edge))throw new IllegalStateException("Actual source travel geometry intersects protected living body");
            edges.add(edge);previous=next;}
        workerProgress.stage("FINISHED");return work(graph,edges);
    }
    private static GhostSourceSampledRay.Point standingBodyPoint(GhostSourceWalkObservedWorld world,Cell cell){
        return new GhostSourceSampledRay.Point(cell.x()+.5,world.supportTop(new Cell(cell.x(),cell.y()-1,cell.z())),cell.z()+.5);
    }
    private static boolean clearActualTravel(GhostSourceActorReservations reservation,GhostSourceWalkEdgeGeometry.Edge edge){
        var points=edge.travelPoints();if(points.isEmpty())return reservation.clear(edge.from(),edge.to());
        var prior=points.getFirst().point();if(!reservation.clear(prior,prior))return false;
        for(var point:points){if(!reservation.clear(prior,point.point()))return false;prior=point.point();}return true;
    }
    private Work work(Result graph,List<GhostSourceWalkEdgeGeometry.Edge> edges){
        var world=new GhostSourceWalkObservedWorld(this::requireCell,capture::valid);var radii=new java.util.HashMap<GhostSourceWalkEdgeGeometry.Edge,Double>();
        for(var edge:edges){var end=edge.to();var cell=new Cell((int)Math.floor(end.x()),(int)Math.floor(end.y()),(int)Math.floor(end.z()));radii.put(edge,GhostSourceOrdinaryWalkTerrain.flatNeighborhood(world,cell)?.75:.5);}
        var values=capture.usedSnapshot();return new Work(graph,List.copyOf(edges),Map.copyOf(radii),values,List.copyOf(values.entrySet()),GhostSourceWalkTerrainEpochs.footprint(values.keySet()));
    }
    private Cell resolveEnd(GhostSourceWalkObservedWorld w,Cell c){if(w.standable(c))return c;var above=add(c,0,1,0);if(w.standable(above))return above;var below=add(c,0,-1,0);return w.standable(below)?below:null;}
    private Cell resolveStart(GhostSourceWalkObservedWorld w){var c=initial.playerCell;if(!initial.playerGrounded&&w.sourcePassable(c)&&GhostSourceOrdinaryWalkTerrain.preflightAir(w.block(add(c,0,-1,0))))return add(c,0,-1,0);
        var kind=w.block(c).kind();if(kind==GhostSourceOrdinaryWalkTerrain.Kind.FENCE||kind==GhostSourceOrdinaryWalkTerrain.Kind.WALL||kind==GhostSourceOrdinaryWalkTerrain.Kind.IRON_BARS||kind==GhostSourceOrdinaryWalkTerrain.Kind.COCOA){
            var corners=new java.util.ArrayList<>(initial.footCorners);corners.sort(java.util.Comparator.comparingDouble(p->Math.sqrt(Math.pow(initial.player.x()-p.x(),2)+Math.pow(initial.player.y()-p.y(),2)+Math.pow(initial.player.z()-p.z(),2))));
            for(var p:corners){var cell=new Cell(Mth.floor(p.x()),Mth.floor(p.y()),Mth.floor(p.z()));if(w.standable(cell))return cell;}}
        return !w.standable(c)&&w.standable(add(c,0,1,0))?add(c,0,1,0):c;
    }
    private static Cell add(Cell c,int x,int y,int z){return new Cell(Math.addExact(c.x(),x),Math.addExact(c.y(),y),Math.addExact(c.z(),z));}
}
