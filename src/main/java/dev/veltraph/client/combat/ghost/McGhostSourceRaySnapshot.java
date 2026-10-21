package dev.veltraph.client.combat.ghost;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.phys.AABB;

/** Read-only native observations for one client/context; no camera, input, entity or packet mutation. */
public final class McGhostSourceRaySnapshot implements GhostSourceSampledRay.Snapshot {
    private final Minecraft client;
    private final Object player,world,connection,registry;
    private final Entity selected;
    private final List<Entity> actorIdentities;
    private final GhostSourceSampledRay.Box selectedBox;
    private final int tick;
    private final Set<net.minecraft.world.level.block.Block> passable;
    private final List<GhostSourceSampledRay.Actor> actors;
    private McGhostSourceRaySnapshot(Minecraft client,Entity selected,List<Entity> observedActors) {this(client,selected,observedActors,null);}
    private McGhostSourceRaySnapshot(Minecraft client,Entity selected,List<Entity> observedActors,Set<net.minecraft.world.level.block.Block> previouslyObservedRegistry) {
        this.client=client;player=client.player;world=client.level;connection=client.getConnection();registry=client.level.registryAccess();
        this.selected=selected;actorIdentities=List.copyOf(observedActors);selectedBox=box(selected.getBoundingBox());tick=client.player.tickCount;
        if(previouslyObservedRegistry!=null)passable=previouslyObservedRegistry;
        else {var values=new HashSet<net.minecraft.world.level.block.Block>();
        for(var block:BuiltInRegistries.BLOCK){
            try{if(block.defaultBlockState().getCollisionShape(EmptyBlockGetter.INSTANCE,BlockPos.ZERO).isEmpty())values.add(block);}
            catch(RuntimeException unavailable){/* Source classifier treats exception as false. */}
        }
        values.remove(Blocks.SNOW);passable=Set.copyOf(values);}
        actors=observedActors.stream().map(e->new GhostSourceSampledRay.Actor(box(e.getBoundingBox()),
                new GhostSourceSampledRay.Point(e.getX(),e.getY(),e.getZ()),e instanceof LivingEntity,e.isInvisible(),e==client.player,e==selected)).toList();
    }
    public static McGhostSourceRaySnapshot observe(Minecraft client,Entity selected,List<Entity> observedActors){
        if(!client.isSameThread()||client.player==null||client.level==null||client.getConnection()==null||selected==null
                ||selected.level()!=client.level||client.level.getEntity(selected.getId())!=selected
                ||observedActors.stream().anyMatch(e->e==null||e.level()!=client.level||client.level.getEntity(e.getId())!=e))
            throw new IllegalStateException("Current native client-thread ray context required");
        return new McGhostSourceRaySnapshot(client,selected,List.copyOf(observedActors));
    }
    /** Reuse only immutable registry data under the exact original registry/PWC; all actors/target/tick observations are fresh. */
    static McGhostSourceRaySnapshot reobserveRegistry(Minecraft client,Entity selected,List<Entity> actualActors,McGhostSourceRaySnapshot prior){
        if(!client.isSameThread()||prior==null||!prior.registryContextCurrent(client))throw new IllegalStateException("Actual observed registry context changed");
        if(selected==null||selected.level()!=client.level||client.level.getEntity(selected.getId())!=selected
                ||actualActors.stream().anyMatch(e->e==null||e.level()!=client.level||client.level.getEntity(e.getId())!=e))throw new IllegalStateException("Actual current native actors required");
        return new McGhostSourceRaySnapshot(client,selected,List.copyOf(actualActors),prior.passable);
    }
    private boolean registryContextCurrent(Minecraft actual){return actual.player==player&&actual.level==world&&actual.getConnection()==connection&&actual.level.registryAccess()==registry;}
    private boolean current(){return client.isSameThread()&&client.player==player&&client.level==world&&client.getConnection()==connection&&client.level.registryAccess()==registry
            &&client.player.tickCount==tick&&selected.level()==client.level&&client.level.getEntity(selected.getId())==selected
            &&selectedBox.equals(box(selected.getBoundingBox()))
            &&java.util.stream.IntStream.range(0,actorIdentities.size()).allMatch(i->{var e=actorIdentities.get(i);var a=actors.get(i);
                return e.level()==client.level&&client.level.getEntity(e.getId())==e&&box(e.getBoundingBox()).equals(a.box())
                        &&new GhostSourceSampledRay.Point(e.getX(),e.getY(),e.getZ()).equals(a.position())&&e.isInvisible()==a.invisible();});}
    public GhostSourceSampledRay.Box targetBox(){if(!current())throw new IllegalStateException("Native target volume changed");return selectedBox;}
    public boolean passableRegistryObserved(){return current();}
    public List<GhostSourceSampledRay.Actor> actors(){return current()?actors:List.of();}
    public GhostSourceSampledRay.Block block(GhostSourceSampledRay.Cell cell){
        if(!current())return new GhostSourceSampledRay.Block(false,false,null,false);
        var position=new BlockPos(cell.x(),cell.y(),cell.z());
        if(!client.level.isLoaded(position))return new GhostSourceSampledRay.Block(false,false,null,false);
        var state=client.level.getBlockState(position);
        boolean allowed=state.getBlock() instanceof FenceGateBlock?state.getValue(FenceGateBlock.OPEN):
                state.getBlock() instanceof SnowLayerBlock?state.getValue(SnowLayerBlock.LAYERS)==1:
                state.is(Blocks.AIR)||passable.contains(state.getBlock());
        var shape=state.getCollisionShape(client.level,position);
        return new GhostSourceSampledRay.Block(true,allowed,shape.isEmpty()?null:box(shape.bounds().move(position)),!state.getFluidState().isEmpty());
    }
    /** Cheap registry/PWC/tick fence. Actor fingerprints remain the caller's actual captured-frame obligation. */
    boolean capturedRegistryFrameCurrent(){return client.isSameThread()&&registryContextCurrent(client)&&client.player!=null&&client.player.tickCount==tick;}
    GhostSourceSampledRay.Block blockCapturedFrame(GhostSourceSampledRay.Cell cell){
        if(!registryContextCurrent(client)||client.player.tickCount!=tick)return new GhostSourceSampledRay.Block(false,false,null,false);
        var position=new BlockPos(cell.x(),cell.y(),cell.z());
        if(!client.level.isLoaded(position))return new GhostSourceSampledRay.Block(false,false,null,false);
        return blockCapturedState(position,client.level.getBlockState(position),null);
    }
    /** Reuses the caller's current loaded-cell state; native collision math and registry exceptions stay identical. */
    GhostSourceSampledRay.Block blockCapturedState(BlockPos position,net.minecraft.world.level.block.state.BlockState state,GhostSourceWalkReadProfile profile){
        long start=profile==null?0:System.nanoTime();boolean contextCurrent;
        try{contextCurrent=registryContextCurrent(client)&&client.player.tickCount==tick;}
        finally{if(profile!=null)profile.add(GhostSourceWalkReadProfile.Part.CURRENT,System.nanoTime()-start);}
        if(!contextCurrent)return new GhostSourceSampledRay.Block(false,false,null,false);
        start=profile==null?0:System.nanoTime();boolean allowed;
        try{allowed=state.getBlock() instanceof FenceGateBlock?state.getValue(FenceGateBlock.OPEN):
                state.getBlock() instanceof SnowLayerBlock?state.getValue(SnowLayerBlock.LAYERS)==1:
                state.is(Blocks.AIR)||passable.contains(state.getBlock());}
        finally{if(profile!=null)profile.add(GhostSourceWalkReadProfile.Part.PASSABILITY,System.nanoTime()-start);}
        start=profile==null?0:System.nanoTime();GhostSourceSampledRay.Box bounds;boolean fluid;
        try{var shape=state.getCollisionShape(client.level,position);bounds=shape.isEmpty()?null:box(shape.bounds().move(position));fluid=!state.getFluidState().isEmpty();}
        finally{if(profile!=null)profile.add(GhostSourceWalkReadProfile.Part.COLLISION_SHAPE,System.nanoTime()-start);}
        start=profile==null?0:System.nanoTime();boolean stateCurrent;
        try{stateCurrent=registryContextCurrent(client)&&client.player.tickCount==tick&&client.level.isLoaded(position)&&client.level.getBlockState(position)==state;}
        finally{if(profile!=null)profile.add(GhostSourceWalkReadProfile.Part.STATE_RECHECK,System.nanoTime()-start);}
        if(!stateCurrent)return new GhostSourceSampledRay.Block(false,false,null,false);
        return new GhostSourceSampledRay.Block(true,allowed,bounds,fluid);
    }
    private static GhostSourceSampledRay.Box box(AABB b){return new GhostSourceSampledRay.Box(b.minX,b.minY,b.minZ,b.maxX,b.maxY,b.maxZ);}
}
