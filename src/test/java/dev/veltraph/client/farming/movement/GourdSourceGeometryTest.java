package dev.veltraph.client.farming.movement;

import dev.veltraph.client.world.*;
import dev.veltraph.client.service.WorldService;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GourdSourceGeometryTest {
    private final Vec3 position=new Vec3(.5,70.9375,.5);
    @Test void bytecodeSideOffsetsUsePositivePointFifteenBodyAndActualCardinalView(){
        assertEquals(new BlockPos(0,71,0),GourdSourceGeometry.body(position));
        for(float yaw:new float[]{0,90,180,270,-90}) {
            var facing=Direction.fromYaw(yaw);
            assertEquals(new BlockPos(0,71,0).offset(facing.rotateYCounterclockwise()),GourdSourceGeometry.sideProbe(position,yaw,TurnDirection.LEFT));
            assertEquals(new BlockPos(0,71,0).offset(facing.rotateYClockwise()),GourdSourceGeometry.sideProbe(position,yaw,TurnDirection.RIGHT));
        }
    }
    @Test void sourceEndRequiresLoadedAirOrCarpetAtBothPressedSideCells(){
        BlockPos probe=GourdSourceGeometry.sideProbe(position,0,TurnDirection.LEFT);
        var blocks=new HashMap<BlockPos,String>();var unloaded=new HashSet<BlockPos>();
        WorldService world=new WorldService(){
            public boolean isLoaded(BlockPos p){return !unloaded.contains(p);}
            public boolean isPassable(BlockPos p){return !blocks.containsKey(p);}
            public boolean isStandable(BlockPos p){return blocks.containsKey(p);}
            public String blockId(BlockPos p){return blocks.getOrDefault(p,"minecraft:air");}
        };
        assertTrue(GourdSourceGeometry.endAir(world,position,0,TurnDirection.LEFT));
        blocks.put(probe,"minecraft:white_carpet");assertTrue(GourdSourceGeometry.endAir(world,position,0,TurnDirection.LEFT));
        blocks.put(probe.up(),"minecraft:quartz_slab");assertFalse(GourdSourceGeometry.endAir(world,position,0,TurnDirection.LEFT));
        blocks.clear();unloaded.add(probe);assertFalse(GourdSourceGeometry.endAir(world,position,0,TurnDirection.LEFT));
    }
    @Test void sourceCornerRequiresCurrentAndPreviousClampedCoordinates(){
        var corner=new Vec3(-2.3,71,12.70000001);
        assertTrue(GourdSourceGeometry.corner(corner,corner));
        assertFalse(GourdSourceGeometry.corner(corner,new Vec3(-2.5,71,12.7)));
        assertFalse(GourdSourceGeometry.corner(new Vec3(-2.301,71,12.7),corner));
        assertFalse(GourdSourceGeometry.corner(corner,null));
    }
}
