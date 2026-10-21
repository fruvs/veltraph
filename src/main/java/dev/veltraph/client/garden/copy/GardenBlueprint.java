package dev.veltraph.client.garden.copy;

import dev.veltraph.client.world.BlockPos;
import java.util.*;

/** Versioned local dense block-state mask. Not the original farmbuilder-lastchecks JSON. */
public record GardenBlueprint(String name,GardenRegion source,List<String> states,Set<Integer> blockEntities) {
    public GardenBlueprint {
        if(name==null||!name.matches("[A-Za-z0-9_-]{1,64}"))throw new IllegalArgumentException("Safe blueprint name required");
        Objects.requireNonNull(source);states=List.copyOf(states);blockEntities=Set.copyOf(blockEntities);
        if(states.size()!=source.cells())throw new IllegalArgumentException("Dense mask must retain every source cell, including AIR");
        if(new HashSet<>(states).size()>4096)throw new IllegalArgumentException("Palette exceeds4096 full states");
        for(String state:states)if(state==null||state.length()>512||!state.matches("[a-z0-9_.-]+:[a-z0-9_/.-]+(\\[[a-z0-9_=,.-]+\\])?"))throw new IllegalArgumentException("Invalid full block state");
        for(int i:blockEntities)if(i<0||i>=states.size())throw new IllegalArgumentException("Invalid block-entity cell");
    }
    public Map<String,Long> histogram(){var out=new TreeMap<String,Long>();for(String state:states)out.merge(state,1L,Long::sum);return Collections.unmodifiableMap(out);}
    public BlockPos destination(int index,BlockPos origin,int turns){
        if(turns<0||turns>3)throw new IllegalArgumentException("Rotation must be0..3");var p=source.at(index);int x=p.x()-source.minX(),y=p.y()-source.minY(),z=p.z()-source.minZ();
        var r=switch(turns){case 0->new BlockPos(x,y,z);case 1->new BlockPos(source.depth()-z-1,y,x);case 2->new BlockPos(source.width()-x-1,y,source.depth()-z-1);default->new BlockPos(z,y,source.width()-x-1);};
        return origin.add(r.x(),r.y(),r.z());
    }
    public GardenRegion destinationBounds(BlockPos origin,int turns){int w=turns%2==0?source.width():source.depth(),d=turns%2==0?source.depth():source.width();return new GardenRegion(source.plot(),origin.x(),origin.y(),origin.z(),Math.addExact(origin.x(),w-1),Math.addExact(origin.y(),source.height()-1),Math.addExact(origin.z(),d-1));}
}
