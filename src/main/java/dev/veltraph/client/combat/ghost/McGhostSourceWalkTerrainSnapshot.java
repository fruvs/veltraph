package dev.veltraph.client.combat.ghost;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.phys.Vec3;
import dev.veltraph.client.combat.ghost.GhostSourceWalkFrontier.*;
import dev.veltraph.client.combat.ghost.GhostSourceOrdinaryWalkTerrain.Kind;

/** Actual Q-job-owned source terrain observations. No world, pose, input, selection or packet mutation. */
public final class McGhostSourceWalkTerrainSnapshot implements GhostSourceOrdinaryWalkTerrain.World {
    private final Minecraft client;private final GhostSourceIssuedSearchJob.Receipt issued;private final Entity target;
    private boolean readBatch;private long batchEpoch;private final Object observedPlayer,observedWorld,observedConnection;
    private final int tick;private final boolean issuanceFrame;
    private final List<Entity> actorIdentities;private final McGhostSourceRaySnapshot ray;private final McGhostSourceSupportRegistrySnapshot support;
    private record Captured(BlockState state,GhostSourceOrdinaryWalkTerrain.Block data,GhostSourceSampledRay.Box rawCollision) {}
    private final Map<Cell,Captured> cells=new HashMap<>();
    private GhostSourceWalkReadProfile readProfile;
    void measureReadsWith(GhostSourceWalkReadProfile profile){readProfile=java.util.Objects.requireNonNull(profile);}
    private McGhostSourceWalkTerrainSnapshot(Minecraft client,GhostSourceIssuedSearchJob.Receipt issued,Entity target,List<Entity> actors,boolean issuanceFrame,McGhostSourceWalkTerrainSnapshot previous){
        this.client=client;this.issued=issued;this.target=target;observedPlayer=client.player;observedWorld=client.level;observedConnection=client.getConnection();this.issuanceFrame=issuanceFrame;tick=client.player.tickCount;actorIdentities=List.copyOf(actors);
        ray=previous==null?McGhostSourceRaySnapshot.observe(client,target,actors):McGhostSourceRaySnapshot.reobserveRegistry(client,target,actors,previous.ray);
        support=previous==null?McGhostSourceSupportRegistrySnapshot.observe(client):McGhostSourceSupportRegistrySnapshot.reobserveRegistry(client,previous.support);
    }
    public static McGhostSourceWalkTerrainSnapshot observe(Minecraft client,GhostSourceIssuedSearchJob.Receipt issued,Entity target){
        if(client==null||issued==null||target==null||!client.isSameThread()||!issued.candidateFrameCurrent(target))
            throw new IllegalStateException("Actual issued candidate frame required for source terrain capture");
        // Source entity-list helper is CollectionsKt.toList(ClientLevel.entitiesForRendering()) on client thread.
        var actors=renderedEntities(client);var captured=new McGhostSourceWalkTerrainSnapshot(client,issued,target,actors,true,null);
        if(!captured.current())throw new IllegalStateException("Native source terrain frame changed during construction");return captured;
    }
    /** Subsequent bounded slices use persistent issued-job scope; never renew the original tick-bound cohort receipt. */
    static McGhostSourceWalkTerrainSnapshot observeContinuation(Minecraft client,GhostSourceIssuedSearchJob.Receipt issued,Entity target,McGhostSourceWalkTerrainSnapshot previous){
        if(client==null||!client.isSameThread()||issued==null||target==null||previous==null||previous.issued!=issued
                ||!issued.scopeCurrent()||!issued.contains(target)||target.level()!=client.level||client.level.getEntity(target.getId())!=target)
            throw new IllegalStateException("Actual persistent issued candidate scope required");
        var fresh=new McGhostSourceWalkTerrainSnapshot(client,issued,target,renderedEntities(client),false,previous);
        if(!fresh.current())throw new IllegalStateException("Slice-native source frame changed");return fresh;
    }
    static McGhostSourceWalkTerrainSnapshot observeCommittedJob(Minecraft client,GhostSourceIssuedSearchJob.Receipt issued,Entity target){
        if(client==null||!client.isSameThread()||issued==null||target==null||!issued.scopeCurrent()||!issued.contains(target)
                ||target.level()!=client.level||client.level.getEntity(target.getId())!=target)throw new IllegalStateException("Actual committed job target required");
        return new McGhostSourceWalkTerrainSnapshot(client,issued,target,renderedEntities(client),false,null);
    }
    boolean protectedStandingRay(Cell cell,float yaw){requireCurrent();var actors=ray.actors();var targetBox=ray.targetBox();var e=target.getEyePosition();var aim=new GhostSourceSampledRay.Point(e.x,e.y,e.z);
        beginReadBatch();boolean hit=false;
        try{var below=new Cell(cell.x(),cell.y()-1,cell.z());var eye=new GhostSourceSampledRay.Point(cell.x()+.5,block(below).supportTop()+1.62,cell.z()+.5);
            hit=GhostSourceSampledRay.hits(eye,aim,targetBox,GhostSourceFloatDirection.between(eye,aim,yaw),GhostSourceSampledRay.Mode.standing(),new GhostSourceSampledRay.Snapshot(){
                public boolean passableRegistryObserved(){return batchCurrent();}
                public List<GhostSourceSampledRay.Actor> actors(){return actors;}
                public GhostSourceSampledRay.Block block(GhostSourceSampledRay.Cell c){return batchCurrent()?ray.blockCapturedFrame(c):new GhostSourceSampledRay.Block(false,false,null,false);}
            });
        }finally{if(!endReadBatch())hit=false;}
        return hit&&current();
    }
    boolean visibleRay(GhostSourceSampledRay.Point eye,GhostSourceSampledRay.Point aim,float yaw){requireCurrent();
        return GhostSourceSampledRay.hits(eye,aim,ray.targetBox(),GhostSourceFloatDirection.between(eye,aim,yaw),GhostSourceSampledRay.Mode.visibility(),ray)&&current();
    }
    public boolean current(){if(readBatch)return batchCurrent();return client.isSameThread()&&client.player.tickCount==tick
            &&(issuanceFrame?issued.candidateFrameCurrent(target):issued.scopeCurrent()&&issued.contains(target))&&ray.passableRegistryObserved()&&support.current()
            &&sameIdentities(actorIdentities,renderedEntities(client));}
    public GhostSourceOrdinaryWalkTerrain.Block block(Cell cell){requireCurrent();var old=cells.get(cell);
        if(old!=null){long start=timingStart();try{var position=pos(cell);if(!client.level.isLoaded(position)||client.level.getBlockState(position)!=old.state())throw new IllegalStateException("Observed terrain cell changed");return old.data();}
            finally{timingEnd(GhostSourceWalkReadProfile.Part.STATE_LOOKUP,start);}}
        long start=timingStart();BlockPos position;BlockState state;
        try{position=pos(cell);if(!client.level.isLoaded(position))throw new IllegalStateException("Required source terrain cell is unloaded");state=client.level.getBlockState(position);}
        finally{timingEnd(GhostSourceWalkReadProfile.Part.STATE_LOOKUP,start);}
        var nativeBlock=state.getBlock();
        // Full current() above already checks actors/registry outside a batch. Native captured-state guard checks PWC/tick/state again.
        var observed=ray.blockCapturedState(position,state,readProfile);
        if(!observed.available())throw new IllegalStateException("Native source cell unavailable");
        start=timingStart();boolean supports;
        try{supports=support.allows(nativeBlock);}finally{timingEnd(GhostSourceWalkReadProfile.Part.SUPPORT,start);}
        start=timingStart();GhostSourceOrdinaryWalkTerrain.Block data;
        try{double top=observed.unionCollisionBounds()==null?(observed.fluidNonEmpty()?cell.y()+1:cell.y()):observed.unionCollisionBounds().maxY();
            var kind=kind(nativeBlock);boolean open=nativeBlock instanceof FenceGateBlock?state.getValue(FenceGateBlock.OPEN):nativeBlock instanceof TrapDoorBlock&&state.getValue(TrapDoorBlock.OPEN);
            int snow=nativeBlock instanceof SnowLayerBlock?state.getValue(SnowLayerBlock.LAYERS):0;
            data=new GhostSourceOrdinaryWalkTerrain.Block(kind,observed.sourcePassable(),observed.fluidNonEmpty(),supports,open,snow,top);
            cells.put(cell,new Captured(state,data,observed.unionCollisionBounds()));}
        finally{timingEnd(GhostSourceWalkReadProfile.Part.CLASSIFY,start);}
        requireCurrent();return data;
    }
    public boolean directionalPassable(Cell cell,Offset direction){
        var b=block(cell);if(b.sourcePassable())return true;
        if(direction==null||Math.abs(direction.x())==1&&Math.abs(direction.z())==1)return false;
        var facing=Direction.getNearest(direction.x(),direction.y(),direction.z(),Direction.UP);
        if(b.kind()==Kind.TRAP_DOOR){
            if(!b.open())return false;var collision=collisionBoundsWithFluid(cell);
            if(collision==null)return true; // Source union helper returns null => obstruction=false.
            double x=(collision.minX()+collision.maxX())/2,y=(collision.minY()+collision.maxY())/2,z=(collision.minZ()+collision.maxZ())/2;
            double mx=cell.x()+.5-facing.getStepX()/2.0,my=cell.y()+.5-facing.getStepY()/2.0,mz=cell.z()+.5-facing.getStepZ()/2.0;
            double distance=facing==Direction.UP||facing==Direction.DOWN?Math.abs(my-y):Math.sqrt(Math.pow(mx-x,2)+Math.pow(mz-z,2));
            return current()&&!(distance<.2);
        }
        if(b.kind()==Kind.COCOA){
            var state=cells.get(cell).state();var attached=state.getValue(CocoaBlock.FACING).getOpposite();var second=relative(cell,attached,1);
            if(block(second).kind()!=Kind.COCOA)return false;
            for(var c:List.of(relative(cell,facing,1),relative(second,facing,1),relative(cell,facing,2),relative(second,facing,2),
                    add(cell,0,-1,0),add(second,0,-1,0),add(cell,0,-2,0),add(second,0,-2,0))){var other=block(c);if(!other.sourcePassable()&&other.kind()!=Kind.COCOA)return false;}
            return current();
        }
        return false;
    }
    public boolean sameStairFacing(Cell from,Cell to){
        if(block(from).kind()!=Kind.STAIR||block(to).kind()!=Kind.STAIR)return false;
        return cells.get(from).state().getValue(StairBlock.FACING)==cells.get(to).state().getValue(StairBlock.FACING);
    }
    public boolean destinationBottomStairFacingMovementOpposite(Cell destination,Cell origin){
        if(block(destination).kind()!=Kind.STAIR||cells.get(destination).state().getValue(StairBlock.HALF)==Half.TOP)return false;
        // Source computes float yaw from destination→origin, then pitch0 lookup-table direction, normalize and int casts.
        float yaw=(float)Math.atan2((double)origin.z()-destination.z(),(double)origin.x()-destination.x());
        yaw=(float)Mth.wrapDegrees((double)(yaw*180.0f)/Math.PI-90.0);
        float a=Mth.cos(-yaw*((float)Math.PI/180)-(float)Math.PI),b=Mth.sin(-yaw*((float)Math.PI/180)-(float)Math.PI);
        var vector=new Vec3((double)(b*-Mth.cos(0)),0,(double)(a*-Mth.cos(0))).normalize();
        var movement=Direction.getNearest((int)vector.x,(int)vector.y,(int)vector.z,null);
        return current()&&cells.get(destination).state().getValue(StairBlock.FACING).getOpposite()==movement;
    }
    /** Recheck actual state and computed union bounds for every cell used, including shape-neighbor drift. */
    public boolean geometryCurrent(){
        if(!current())return false;
        for(var entry:cells.entrySet()){
            var cell=entry.getKey();var prior=entry.getValue();var position=pos(cell);
            if(!client.level.isLoaded(position)||client.level.getBlockState(position)!=prior.state())return false;
            var actual=ray.block(new GhostSourceSampledRay.Cell(cell.x(),cell.y(),cell.z()));
            if(!actual.available()||!java.util.Objects.equals(actual.unionCollisionBounds(),prior.rawCollision())
                    ||actual.sourcePassable()!=prior.data().sourcePassable()||actual.fluidNonEmpty()!=prior.data().fluid())return false;
        }return current();
    }
    public boolean loaded(Cell cell){requireCurrent();return client.level.isLoaded(pos(cell));}
    public GhostSourceSampledRay.Box collisionBoundsWithFluid(Cell cell){var b=block(cell);var raw=cells.get(cell).rawCollision();
        return raw!=null?raw:b.fluid()?new GhostSourceSampledRay.Box(cell.x(),cell.y(),cell.z(),cell.x()+1.0,cell.y()+1.0,cell.z()+1.0):null;}
    public String observedStairFacing(Cell cell){return block(cell).kind()==Kind.STAIR?cells.get(cell).state().getValue(StairBlock.FACING).name():null;}
    public GhostSourceSampledRay.Box rawCollisionBounds(Cell cell){block(cell);return cells.get(cell).rawCollision();}
    public List<Entity> observedActors(){requireCurrent();return actorIdentities;}
    boolean observedDynamicShape(Cell cell){block(cell);return cells.get(cell).state().getBlock().hasDynamicShape();}
    GhostSourceWalkObservedWorld.CellValue captureValue(Cell cell){var data=block(cell);long start=timingStart();
        try{var captured=cells.get(cell);var state=captured.state();
            String facing=data.kind()==Kind.STAIR?state.getValue(StairBlock.FACING).name():data.kind()==Kind.COCOA?state.getValue(CocoaBlock.FACING).name():null;
            boolean bottom=data.kind()==Kind.STAIR&&state.getValue(StairBlock.HALF)!=Half.TOP;
            return new GhostSourceWalkObservedWorld.CellValue(data,captured.rawCollision(),facing,bottom);}
        finally{timingEnd(GhostSourceWalkReadProfile.Part.VALUE_PROPERTIES,start);}
    }
    boolean matchesValue(Cell cell,GhostSourceWalkObservedWorld.CellValue previous){return loaded(cell)&&captureValue(cell).equals(previous);}
    List<GhostSourceSampledRay.Actor> capturedActors(){requireCurrent();return ray.actors();}
    GhostSourceSampledRay.Box capturedTargetBox(){requireCurrent();return ray.targetBox();}
    public int observedCellCount(){return cells.size();}
    private void requireCurrent(){long start=timingStart();try{if(!current())throw new IllegalStateException("Actual native issued terrain frame changed");}finally{timingEnd(GhostSourceWalkReadProfile.Part.CURRENT,start);}}
    private long timingStart(){return readProfile==null?0:System.nanoTime();}
    private void timingEnd(GhostSourceWalkReadProfile.Part part,long start){if(readProfile!=null)readProfile.add(part,System.nanoTime()-start);}
    private static List<Entity> renderedEntities(Minecraft client){var result=new ArrayList<Entity>();client.level.entitiesForRendering().forEach(result::add);return List.copyOf(result);}
    private static boolean sameIdentities(List<Entity> a,List<Entity> b){if(a.size()!=b.size())return false;var identities=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Entity,Boolean>());identities.addAll(b);return a.stream().allMatch(identities::contains);}
    void beginReadBatch(){if(readBatch||!current())throw new IllegalStateException("Actual native read batch unavailable");batchEpoch=dev.veltraph.client.service.mc.McOwnedRotation.epoch(client);readBatch=true;}
    boolean batchCurrent(){var parent=issued.job().parent();return readBatch&&client.isSameThread()&&client.player==observedPlayer&&client.level==observedWorld&&client.getConnection()==observedConnection&&client.player.tickCount==tick
        &&parent.isEnabled()&&parent.sourceRegisteredWalkJob()==issued.job()&&dev.veltraph.client.VeltraphBootstrap.moduleManager().inputOwner().orElse(null)==parent
        &&dev.veltraph.client.service.mc.McOwnedRotation.authority(client)==parent&&dev.veltraph.client.service.mc.McOwnedRotation.epoch(client)==batchEpoch
        &&target.level()==client.level&&client.level.getEntity(target.getId())==target&&client.gui.screen()==null&&client.player.containerMenu==client.player.inventoryMenu&&client.player.containerMenu.getCarried().isEmpty();}
    boolean endReadBatch(){readBatch=false;return current();}
    private static BlockPos pos(Cell c){return new BlockPos(c.x(),c.y(),c.z());}
    private static Cell add(Cell c,int x,int y,int z){return new Cell(Math.addExact(c.x(),x),Math.addExact(c.y(),y),Math.addExact(c.z(),z));}
    private static Cell relative(Cell c,Direction d,int n){return add(c,d.getStepX()*n,d.getStepY()*n,d.getStepZ()*n);}
    private static Kind kind(net.minecraft.world.level.block.Block b){
        if(b instanceof FenceBlock)return Kind.FENCE;if(b instanceof FenceGateBlock)return Kind.FENCE_GATE;if(b instanceof WallBlock)return Kind.WALL;
        if(b instanceof IronBarsBlock)return Kind.IRON_BARS;if(b instanceof SlimeBlock)return Kind.SLIME;if(b instanceof WebBlock)return Kind.WEB;
        if(b instanceof LilyPadBlock)return Kind.LILY_PAD;if(b instanceof TrapDoorBlock)return Kind.TRAP_DOOR;if(b instanceof CocoaBlock)return Kind.COCOA;
        if(b instanceof StairBlock)return Kind.STAIR;if(b==Blocks.LAVA)return Kind.LAVA;if(b instanceof DoorBlock)return Kind.DOOR;
        if(b instanceof FlowerPotBlock)return Kind.FLOWER_POT;if(b instanceof LanternBlock)return Kind.LANTERN;if(b instanceof CarpetBlock)return Kind.CARPET;
        if(b instanceof MossyCarpetBlock)return Kind.MOSSY_CARPET;if(b instanceof SnowLayerBlock)return Kind.SNOW;return Kind.OTHER;
    }
}
