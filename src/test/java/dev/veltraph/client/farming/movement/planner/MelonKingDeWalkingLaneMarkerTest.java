package dev.veltraph.client.farming.movement.planner;
import dev.veltraph.client.service.WorldService;
import dev.veltraph.client.world.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class MelonKingDeWalkingLaneMarkerTest {
    private static final BlockPos CURRENT=new BlockPos(94,1,0);
    private WorldService world(Map<BlockPos,String> blocks,Set<BlockPos> unknown){return new WorldService(){
        public boolean isLoaded(BlockPos p){return !unknown.contains(p);}public boolean isPassable(BlockPos p){return !blocks.containsKey(p)||blockId(p).equals("minecraft:water");}
        public boolean isStandable(BlockPos p){return isLoaded(p)&&!isPassable(p);}public String blockId(BlockPos p){return blocks.getOrDefault(p,"minecraft:air");}
    };}
    @Test void supportedCloserUpperIrrigationCannotOutrankTheWalkingStream(){
        var upper=new BlockPos(93,3,2);var lower=new BlockPos(93,1,3);
        var w=world(Map.of(upper,"minecraft:water",upper.down(),"minecraft:dirt",lower,"minecraft:water",lower.down(),"minecraft:soul_sand"),Set.of());
        assertTrue(w.isSolidFloor(upper.down()));assertTrue(w.isWater(upper));
        assertEquals(3,MelonKingDeWalkingLaneMarker.nextDistance(w,CURRENT,Direction.SOUTH,Direction.WEST));
        assertFalse(MelonKingDeWalkingLaneMarker.walkingWater(w,upper,CURRENT.y()));
    }
    @Test void evenUpperSoulBackedWaterIsNotAtTheActualWalkingHeight(){
        var upper=new BlockPos(93,3,2);
        assertFalse(MelonKingDeWalkingLaneMarker.walkingWater(world(Map.of(upper,"minecraft:water",upper.down(),"minecraft:soul_sand"),Set.of()),upper,1));
    }
    @Test void lowerPoolNeedsAuthoredSoulFloorAndThreeFourDistancesAreObserved(){
        for(int distance:List.of(3,4)) {
            var lower=CURRENT.offset(Direction.SOUTH,distance).offset(Direction.WEST);
            var pool=CURRENT.offset(Direction.SOUTH,2).offset(Direction.WEST);
            var w=world(Map.of(pool,"minecraft:water",pool.down(),"minecraft:dirt",lower,"minecraft:water",lower.down(),"minecraft:soul_sand"),Set.of());
            assertEquals(distance,MelonKingDeWalkingLaneMarker.nextDistance(w,CURRENT,Direction.SOUTH,Direction.WEST));
            for(var unknown:List.of(lower,lower.down()))assertFalse(MelonKingDeWalkingLaneMarker.walkingWater(world(Map.of(lower,"minecraft:water",lower.down(),"minecraft:soul_sand"),Set.of(unknown)),lower,1));
        }
    }
}
