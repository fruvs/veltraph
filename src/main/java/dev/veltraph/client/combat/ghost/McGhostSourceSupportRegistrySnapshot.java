package dev.veltraph.client.combat.ghost;

import java.util.HashMap;
import java.util.Map;
import java.util.HashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.*;

/** Read-only observed registry for one native client frame; no world, pose, input or packet writes. */
public final class McGhostSourceSupportRegistrySnapshot {
    private final Minecraft client;
    private final Object player,world,connection,registry;
    private final int tick;
    private final Map<Block,Boolean> values;
    private McGhostSourceSupportRegistrySnapshot(Minecraft client){this(client,null);}
    private McGhostSourceSupportRegistrySnapshot(Minecraft client,Map<Block,Boolean> previouslyObservedRegistry){
        this.client=client;player=client.player;world=client.level;connection=client.getConnection();
        registry=client.level.registryAccess();tick=client.player.tickCount;
        if(previouslyObservedRegistry!=null){values=previouslyObservedRegistry;return;}
        var passable=new HashSet<Block>();
        for(var block:BuiltInRegistries.BLOCK){
            try{if(block.defaultBlockState().getCollisionShape(EmptyBlockGetter.INSTANCE,BlockPos.ZERO).isEmpty())passable.add(block);}
            catch(RuntimeException unavailable){/* Source raw classifier returns false on exception. */}
        }
        passable.remove(Blocks.SNOW);
        var observed=new HashMap<Block,Boolean>();
        for(var block:BuiltInRegistries.BLOCK){
            boolean sourceRegistryPassable=block==Blocks.AIR||passable.contains(block);
            observed.put(block,GhostSourceSupportPolicy.allows(sourceRegistryPassable,kind(block)));
        }
        values=Map.copyOf(observed);
    }
    public static McGhostSourceSupportRegistrySnapshot observe(Minecraft client){
        if(client==null||!client.isSameThread()||client.player==null||client.level==null||client.getConnection()==null)
            throw new IllegalStateException("Current native support registry context required");
        return new McGhostSourceSupportRegistrySnapshot(client);
    }
    static McGhostSourceSupportRegistrySnapshot reobserveRegistry(Minecraft client,McGhostSourceSupportRegistrySnapshot prior){
        if(client==null||!client.isSameThread()||prior==null||client.player!=prior.player||client.level!=prior.world
                ||client.getConnection()!=prior.connection||client.level.registryAccess()!=prior.registry)throw new IllegalStateException("Actual observed support registry context changed");
        return new McGhostSourceSupportRegistrySnapshot(client,prior.values);
    }
    public boolean current(){return client.isSameThread()&&client.player==player&&client.level==world
            &&client.getConnection()==connection&&client.level.registryAccess()==registry&&client.player.tickCount==tick;}
    /** False includes unknown block, matching source getOrDefault; stale snapshot is unavailable, not false. */
    public boolean allows(Block observedBlock){
        if(!current()||observedBlock==null)throw new IllegalStateException("Current observed support registry required");
        return values.getOrDefault(observedBlock,false);
    }
    private static GhostSourceSupportPolicy.Kind kind(Block block){
        if(block instanceof FenceBlock)return GhostSourceSupportPolicy.Kind.FENCE;
        if(block instanceof FenceGateBlock)return GhostSourceSupportPolicy.Kind.FENCE_GATE;
        if(block instanceof WallBlock)return GhostSourceSupportPolicy.Kind.WALL;
        if(block instanceof WebBlock)return GhostSourceSupportPolicy.Kind.WEB;
        if(block instanceof StandingSignBlock)return GhostSourceSupportPolicy.Kind.STANDING_SIGN;
        if(block instanceof CarpetBlock)return GhostSourceSupportPolicy.Kind.CARPET;
        if(block instanceof MossyCarpetBlock)return GhostSourceSupportPolicy.Kind.MOSSY_CARPET;
        if(block==Blocks.SNOW)return GhostSourceSupportPolicy.Kind.SNOW;
        if(block==Blocks.LILY_PAD)return GhostSourceSupportPolicy.Kind.LILY_PAD;
        return GhostSourceSupportPolicy.Kind.OTHER;
    }
}
