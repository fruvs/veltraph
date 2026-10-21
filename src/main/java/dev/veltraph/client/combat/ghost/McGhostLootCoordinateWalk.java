package dev.veltraph.client.combat.ghost;

import java.util.Objects;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.world.effect.MobEffects;
import dev.veltraph.client.VeltraphBootstrap;
import dev.veltraph.client.config.GhostGrindConfig;
import dev.veltraph.client.combat.ghost.GhostSourceWalkFrontier.*;
import dev.veltraph.client.combat.ghost.GhostSourceSampledRay.Point;

/** Actual private native Item coordinate scan -> R1 source graph/smoothing -> R1 cursor. Qualified modern Ghost loot continuation. */
public final class McGhostLootCoordinateWalk {
    public enum State { ISSUED, SCANNING, VALIDATING_GOAL, CAPTURING_PATH, WALKING, ARRIVED, NO_PATH, REVOKED }
    public static final long MAX_CAPTURE_NANOS=4_000_000L;
    private final Minecraft client;private final GhostGrindModule parent;private final McGhostLootNativeProvider.Session session;
    private final McGhostLootNativeProvider.NativeCoordinateGoal goal;private final Supplier<McGhostLootCoordinateWalk> actualInstalled;
    private final GhostSourceOwnedParentArea.Stamp stamp;private final Point originalPlayer;private final Cell start,itemCell;
    private final int cap,rough;private final boolean jumpBoost,smoothing,tailVeto;private final String policy;private final long developerRevision;
    private final GhostLootStandingScan scan;
    private final LinkedHashMap<Cell,GhostSourceWalkObservedWorld.CellValue> scanCells=new LinkedHashMap<>();
    private List<Map.Entry<Cell,GhostSourceWalkObservedWorld.CellValue>> validation;private int validationIndex;private GhostSourceWalkTerrainEpochs.Stamp goalEpoch;
    private McGhostSourceCoordinateTerrainSnapshot terrain;private McGhostSourceCoordinatePipeline pipeline;private McGhostSourceCoordinateCursor cursor;
    private State state=State.ISSUED;private long totalPumpNanos,maxPumpNanos;private int pumpSlices;
    private McGhostLootCoordinateWalk(Minecraft client,GhostGrindModule parent,McGhostLootNativeProvider.Session session,
            McGhostLootNativeProvider.NativeCoordinateGoal goal,Supplier<McGhostLootCoordinateWalk> actualInstalled){
        this.client=client;this.parent=parent;this.session=session;this.goal=goal;this.actualInstalled=Objects.requireNonNull(actualInstalled);
        stamp=parent.sourceParentAreaStamp(parent).orElseThrow();originalPlayer=point(client.player);start=cell(originalPlayer);itemCell=cell(goal.worldPosition());
        var c=VeltraphBootstrap.config();cap=c.get(GhostGrindConfig.GHOST_SOURCE_LOOT_GRAPH_CAP);if(cap<1||cap>100000)throw new IllegalArgumentException("Explicit modern positive loot graph cap <=100000 required");
        smoothing=c.get(GhostGrindConfig.GHOST_PATH_SMOOTHING_ENABLED);rough=c.get(GhostGrindConfig.GHOST_PATH_SHORTCUT_DISTANCE);tailVeto=c.get(GhostGrindConfig.GHOST_PATH_KEEP_FINE_ADJUSTMENTS);
        policy=c.get(GhostGrindConfig.GHOST_WALK_FORBIDDEN_CELLS);GhostSourceWalkForbiddenCells.fromActualConfiguredJson(policy).requireObserved();
        jumpBoost=client.player.getEffect(MobEffects.JUMP_BOOST)!=null;developerRevision=GhostSourceGraphDeveloperAreaOverride.runtime().revision();
        scan=new GhostLootStandingScan(itemCell,originalPlayer);
    }
    /** Issuance occurs before Root installs the actual field. It accepts only the privately minted, CURRENT native item goal. */
    public static McGhostLootCoordinateWalk issue(Minecraft client,GhostGrindModule parent,McGhostLootNativeProvider.Session actualSession,
            McGhostLootNativeProvider.NativeCoordinateGoal actualGoal,Supplier<McGhostLootCoordinateWalk> actualInstalled){
        if(client==null||parent==null||!client.isSameThread()||actualSession==null||actualGoal==null||!actualGoal.current()||!actualSession.ownsNativeGoal(actualGoal))
            throw new IllegalStateException("Actual private current native loot goal required");
        return new McGhostLootCoordinateWalk(client,parent,actualSession,actualGoal,actualInstalled);
    }
    /** Root assigns its sourceLootWalk field FIRST, with sourceNativeChildIdentity dynamically returning this actual child. */
    public void begin(){if(state!=State.ISSUED||!scopeCurrent())throw new IllegalStateException("Actual installed native loot child required");state=State.SCANNING;}
    public State state(){return state;}public int chosenId(){return session.chosenId();}public String serverId(){return session.serverId();}
    public record Evidence(State state,int scannedCells,int graphCap,long totalPumpNanos,long maximumPumpNanos,int pumpSlices) {}
    public Evidence evidence(){return new Evidence(state,scanCells.size(),cap,totalPumpNanos,maxPumpNanos,pumpSlices);}
    private boolean scopeCurrent(){
        if(state==State.REVOKED||state==State.NO_PATH||!client.isSameThread()||actualInstalled.get()!=this||!session.movementCurrent()||!session.ownsNativeGoal(goal))return false;
        var actual=parent.sourceParentAreaStamp(parent).orElse(null);if(actual==null||!stamp.same(actual)||parent.sourceNativeChildIdentity(parent)!=this)return false;
        var mm=VeltraphBootstrap.moduleManager();var area=parent.sourceParentAreaReceipt(parent).orElse(null);var c=VeltraphBootstrap.config();
        return mm!=null&&mm.inputOwner().orElse(null)==parent&&mm.enabledControllers().size()==1&&mm.enabledControllers().getFirst()==parent
                &&area!=null&&area.current(actual)&&area.observation()==GhostSourceOrderedStandingProvider.ParentAreaObservation.ABSENT
                &&GhostSourceGraphDeveloperAreaOverride.runtime().revision()==developerRevision&&GhostSourceGraphDeveloperAreaOverride.runtime().selected().isEmpty()
                &&c.get(GhostGrindConfig.GHOST_SOURCE_LOOT_GRAPH_CAP)==cap&&(client.player.getEffect(MobEffects.JUMP_BOOST)!=null)==jumpBoost
                &&c.get(GhostGrindConfig.GHOST_PATH_SMOOTHING_ENABLED)==smoothing&&c.get(GhostGrindConfig.GHOST_PATH_SHORTCUT_DISTANCE)==rough
                &&c.get(GhostGrindConfig.GHOST_PATH_KEEP_FINE_ADJUSTMENTS)==tailVeto&&Objects.equals(c.get(GhostGrindConfig.GHOST_WALK_FORBIDDEN_CELLS),policy);
    }
    public boolean current(){return state!=State.REVOKED&&state!=State.NO_PATH&&scopeCurrent();}
    /** One bounded native slice; Root polls actual delivery before this call and before applying any returned intent. */
    public void pump(){
        if(!client.isSameThread())throw new IllegalStateException("Actual client-thread loot capture required");
        if(state==State.REVOKED||state==State.NO_PATH||state==State.ARRIVED||state==State.WALKING)return;
        long began=System.nanoTime();try{
            if(!scopeCurrent()||!originalPlayer.equals(point(client.player))){revoke();return;}
            if(state==State.SCANNING||state==State.VALIDATING_GOAL){terrain=McGhostSourceCoordinateTerrainSnapshot.observe(client,this::scopeCurrent,terrain);
                var world=new ScanWorld();
                while(state==State.SCANNING&&System.nanoTime()-began<MAX_CAPTURE_NANOS){var next=scan.nextCell();
                    if(next==null){if(scan.state()==GhostLootStandingScan.State.READY){validation=List.copyOf(scanCells.entrySet());goalEpoch=GhostSourceWalkTerrainEpochs.capture(client.level,GhostSourceWalkTerrainEpochs.footprint(scanCells.keySet()));state=State.VALIDATING_GOAL;}else state=State.NO_PATH;break;}
                    boolean original=scan.originalProbe(),foot=GhostSourceOrdinaryWalkTerrain.sourceFoot(world,next);
                    boolean neighbor=original&&foot&&accessibleNeighbor(world,next);
                    scan.observe(next,GhostLootStandingScan.Availability.OBSERVED,foot,neighbor);
                }
                if(state==State.VALIDATING_GOAL){if(!GhostSourceWalkTerrainEpochs.current(goalEpoch)){revoke();return;}while(validationIndex<validation.size()&&System.nanoTime()-began<MAX_CAPTURE_NANOS){var e=validation.get(validationIndex++);if(!terrain.matchesValue(e.getKey(),e.getValue())){revoke();return;}}
                    if(validationIndex==validation.size()&&System.nanoTime()-began<MAX_CAPTURE_NANOS){if(!GhostSourceWalkTerrainEpochs.current(goalEpoch)||!scopeCurrent()){revoke();return;}
                        pipeline=McGhostSourceCoordinatePipeline.begin(client,this::scopeCurrent,start,scan.selected(),cap,jumpBoost,smoothing,rough,tailVeto,policy);state=State.CAPTURING_PATH;}}
            }
            if(state==State.CAPTURING_PATH){long left=MAX_CAPTURE_NANOS-(System.nanoTime()-began);if(left>0)pipeline.pump(left);
                if(pipeline.state()==McGhostSourceCoordinatePipeline.State.REVOKED){revoke();return;}
                if(pipeline.state()==McGhostSourceCoordinatePipeline.State.READY){var path=pipeline.path();if(path==null){state=State.NO_PATH;return;}
                    if(!scopeCurrent()||!path.current()){revoke();return;}cursor=new McGhostSourceCoordinateCursor(client,path,this::scopeCurrent,pipeline);state=State.WALKING;}}
        }catch(RuntimeException unavailable){revoke();}
        finally{long elapsed=System.nanoTime()-began;totalPumpNanos+=elapsed;maxPumpNanos=Math.max(maxPumpNanos,elapsed);pumpSlices++;}
    }
    public McGhostSourceCoordinateCursor.Steering observe(){
        if(!current())return new McGhostSourceCoordinateCursor.Steering(McGhostSourceCoordinateCursor.Outcome.STALE,null,false,false);
        if(state==State.ARRIVED)return new McGhostSourceCoordinateCursor.Steering(McGhostSourceCoordinateCursor.Outcome.AT_DESTINATION,null,false,false);
        if(state!=State.WALKING||cursor==null)return new McGhostSourceCoordinateCursor.Steering(McGhostSourceCoordinateCursor.Outcome.STALE,null,false,false);
        McGhostSourceCoordinateCursor.Steering intent;try{intent=cursor.observe(client);}catch(RuntimeException staleGeometry){revoke();return new McGhostSourceCoordinateCursor.Steering(McGhostSourceCoordinateCursor.Outcome.STALE,null,false,false);}if(intent.outcome()==McGhostSourceCoordinateCursor.Outcome.AT_DESTINATION)state=State.ARRIVED;
        else if(intent.outcome()==McGhostSourceCoordinateCursor.Outcome.STALE||intent.outcome()==McGhostSourceCoordinateCursor.Outcome.RESEARCH)revoke();
        return intent;
    }
    public void revoke(){state=State.REVOKED;scan.revoke();if(pipeline!=null)pipeline.revoke();}
    private final class ScanWorld implements GhostSourceOrdinaryWalkTerrain.World {
        public boolean current(){return scopeCurrent()&&terrain.current();}
        public GhostSourceOrdinaryWalkTerrain.Block block(Cell c){var value=terrain.captureValue(c);var old=scanCells.putIfAbsent(c,value);if(old!=null&&!old.equals(value))throw new IllegalStateException("Native scan footprint changed");return value.block();}
        public boolean directionalPassable(Cell c,Offset direction){block(c);return terrain.directionalPassable(c,direction);}
        public boolean sameStairFacing(Cell from,Cell to){block(from);block(to);return terrain.sameStairFacing(from,to);}
        public boolean destinationBottomStairFacingMovementOpposite(Cell destination,Cell origin){block(destination);block(origin);return terrain.destinationBottomStairFacingMovementOpposite(destination,origin);}
    }
    private static boolean accessibleNeighbor(GhostSourceOrdinaryWalkTerrain.World world,Cell c){for(var d:List.of(new Offset(1,0,0),new Offset(-1,0,0),new Offset(0,0,1),new Offset(0,0,-1)))
        for(int y=-1;y<=1;y++)if(GhostSourceOrdinaryWalkTerrain.sourceFoot(world,new Cell(c.x()+d.x(),c.y()+y,c.z()+d.z())))return true;return false;}
    private static Point point(net.minecraft.world.entity.Entity entity){return new Point(entity.getX(),entity.getY(),entity.getZ());}
    private static Cell cell(Point p){return new Cell((int)Math.floor(p.x()),(int)Math.floor(p.y()),(int)Math.floor(p.z()));}
}
