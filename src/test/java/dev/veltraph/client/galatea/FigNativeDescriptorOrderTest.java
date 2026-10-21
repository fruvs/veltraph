package dev.veltraph.client.galatea;

import dev.veltraph.client.world.BlockPos;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FigNativeDescriptorOrderTest {
    @Test void nativeCoordinateHashDiffersFromRecordHashAndUsesActualRuntimeSet() {
        var p=new BlockPos(1,2,3);var nativeP=new net.minecraft.core.BlockPos(1,2,3);
        assertEquals((2+3*31)*31+1,nativeP.hashCode());assertNotEquals(p.hashCode(),nativeP.hashCode());
        var input=List.of(new BlockPos(7,70,5),new BlockPos(0,70,0),new BlockPos(-1,75,3),new BlockPos(2,70,-1));
        var nativeSet=new HashSet<net.minecraft.core.BlockPos>();for(var v:input)nativeSet.add(new net.minecraft.core.BlockPos(v.x(),v.y(),v.z()));
        assertEquals(nativeSet.stream().map(v->new BlockPos(v.getX(),v.getY(),v.getZ())).toList(),FigNativeDescriptorOrder.observe(input));
    }
    @Test void tiedSourceScoresAndFailedFirstFiveFallBackToNativeSetEncounter() {
        var input=new ArrayList<BlockPos>();for(int i=0;i<8;i++)input.add(new BlockPos(i,70,i*3));
        var order=FigNativeDescriptorOrder.observe(input);var rays=new ArrayList<BlockPos>();
        var selected=FigCandidateSelection.select(order,p->true,(a,b)->0,p->{rays.add(p);return false;}).orElseThrow();
        assertEquals(order.getFirst(),selected);assertEquals(order.subList(0,5),rays);
    }
}
