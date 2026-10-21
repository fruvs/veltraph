package dev.veltraph.client.galatea;

import dev.veltraph.client.world.BlockPos;
import java.util.*;

/** MC26.3 native HashSet encounter order; original producer capacity/insertion history is unbound. */
public final class FigNativeDescriptorOrder {
    private FigNativeDescriptorOrder(){}
    public static List<BlockPos> observe(List<BlockPos> loadedComponent) {
        HashSet<net.minecraft.core.BlockPos> nativeSet=new HashSet<>();
        for(BlockPos p:loadedComponent)nativeSet.add(new net.minecraft.core.BlockPos(p.x(),p.y(),p.z()));
        return nativeSet.stream().map(p->new BlockPos(p.getX(),p.getY(),p.getZ())).toList();
    }
}
