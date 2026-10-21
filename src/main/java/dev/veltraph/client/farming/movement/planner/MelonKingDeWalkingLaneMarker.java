package dev.veltraph.client.farming.movement.planner;

import dev.veltraph.client.service.WorldService;
import dev.veltraph.client.world.BlockPos;
import dev.veltraph.client.world.Direction;

/** Creator walking streams are source water above the same SoulSand plane, not upper irrigation. */
public final class MelonKingDeWalkingLaneMarker {
    private MelonKingDeWalkingLaneMarker(){}
    public static boolean walkingWater(WorldService world,BlockPos water,int walkingBodyY) {
        return water.y()==walkingBodyY&&world.isLoaded(water)&&world.isLoaded(water.down())
                &&world.isWater(water)&&!world.isBlock(water,"minecraft:bubble_column")
                &&world.isBlock(water.down(),"minecraft:soul_sand");
    }
    public static int nextDistance(WorldService world,BlockPos current,Direction forward,Direction inward) {
        for(int distance=1;distance<=4;distance++) {
            BlockPos candidate=current.offset(forward,distance).offset(inward);
            if(!world.isLoaded(candidate)||!world.isLoaded(candidate.down()))return -1;
            if(walkingWater(world,candidate,current.y()))return distance;
        }
        return -1;
    }
}
