package dev.veltraph.client.farming.movement.planner;

import dev.veltraph.client.service.WorldService;
import dev.veltraph.client.world.BlockPos;
import dev.veltraph.client.world.Direction;
import dev.veltraph.client.world.Vec3;
import java.util.ArrayList;
import static dev.veltraph.client.farming.movement.planner.MushroomRingLaneClassifier.Lane.*;

/** Loaded local guide continuations. Never a coordinate route, waypoint, timed turn or motion override. */
public final class MushroomRingContinuation {
    public enum Phase { CORNER, GUIDE_GAP, STAIR, DROP, LANDING }
    public record Decision(MushroomRingLaneClassifier.Lane lane,Direction travel,Phase phase) {}
    private static final Direction[] HORIZONTAL={Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST};

    public Decision find(WorldService world,Vec3 feet,float yaw,Direction incoming) {
        BlockPos p=feet.toBlockPos();
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)for(int y=-1;y<=2;y++)
            if(!world.isLoaded(new BlockPos(p.x()+x,p.y()+y,p.z()+z)))return null;
        if(!world.bodyClear(feet,.3,1.8)||!world.hasGroundSupport(feet,.3))return null;
        boolean stair=false;
        for(int y=-1;y<=0;y++)stair|=world.isBlock(new BlockPos(p.x(),p.y()+y,p.z()),"minecraft:quartz_stairs");
        if(stair) {
            if(incoming==null||projection(yaw,true,incoming)<=0)return null;
            return new Decision(NON_TRAPDOOR,incoming,Phase.STAIR);
        }
        if(Math.abs(feet.y()-p.y()-.5)>.025||!world.isBlock(p,"minecraft:quartz_slab"))return null;
        var arms=new ArrayList<Direction>();
        for(Direction d:HORIZONTAL) {
            BlockPos q=p.offset(d);
            if(world.isBlock(q,"minecraft:quartz_slab")||world.isBlock(q,"minecraft:quartz_stairs"))arms.add(d);
        }
        Direction next=null;
        if(incoming!=null&&arms.contains(incoming))next=incoming;
        else {
            var exits=arms.stream().filter(d->incoming==null||d!=incoming.opposite()).toList();
            if(exits.size()==1)next=exits.getFirst();
        }
        if(next==null) {
            if(incoming!=null&&arms.size()==1&&arms.getFirst()==incoming.opposite()&&drop(world,feet,incoming))
                return projection(yaw,true,incoming)>0?new Decision(NON_TRAPDOOR,incoming,Phase.DROP):null;
            return null;
        }
        BlockPos q=p.offset(next);
        boolean nextStair=world.isBlock(q,"minecraft:quartz_stairs");
        var lane=nextStair?NON_TRAPDOOR:role(world,q);
        // The original guide has a one-cell air opening immediately before some corners. It is a
        // gap in the same trapdoor lane only when current and farther loaded slab guides agree.
        BlockPos farther=q.offset(next);
        if(!nextStair&&lane==NON_TRAPDOOR&&role(world,p)==TRAPDOOR
                &&world.isLoaded(farther)&&world.isLoaded(farther.up())
                &&world.isBlock(farther,"minecraft:quartz_slab")&&role(world,farther)==TRAPDOOR)lane=TRAPDOOR;
        if(lane!=NON_TRAPDOOR&&lane!=TRAPDOOR)return null;
        if(projection(yaw,lane==NON_TRAPDOOR,next)<=0)return null;
        if(!nextStair&&!facesBed(world,p,q,next,yaw))return null;
        return new Decision(lane,next,nextStair?Phase.STAIR:arms.size()==1?Phase.LANDING:
                arms.getFirst().opposite()==arms.getLast()?Phase.GUIDE_GAP:Phase.CORNER);
    }
    private static MushroomRingLaneClassifier.Lane role(WorldService world,BlockPos p) {
        return switch(world.blockId(p.up())) {
            case "minecraft:air"->NON_TRAPDOOR;
            case "minecraft:oak_trapdoor"->TRAPDOOR;
            default->AMBIGUOUS;
        };
    }
    private static boolean facesBed(WorldService world,BlockPos p,BlockPos q,Direction travel,float yaw) {
        boolean x=travel.dx()!=0;double normal=x?Math.cos(Math.toRadians(yaw)):-Math.sin(Math.toRadians(yaw));
        int dx=x?0:(int)Math.signum(normal),dz=x?(int)Math.signum(normal):0;
        for(BlockPos base:new BlockPos[]{p,q}) {
            BlockPos bed=new BlockPos(base.x()+dx,base.y()+1,base.z()+dz);
            if(!world.isLoaded(bed))return false;
            if(world.isBlock(bed,"minecraft:mycelium"))return true;
        }
        return false;
    }
    /** Exact bounded creator-layer drop: only a loaded clear column ending on another original slab. */
    private static boolean drop(WorldService world,Vec3 feet,Direction travel) {
        BlockPos p=feet.toBlockPos();
        Vec3 ahead=new Vec3(travel.dx()==0?feet.x():p.x()+travel.dx()+.5,feet.y(),
                travel.dz()==0?feet.z():p.z()+travel.dz()+.5);
        for(double fall=.5;fall<=4;fall+=.5) {
            Vec3 lower=new Vec3(ahead.x(),ahead.y()-fall,ahead.z());
            if(!world.bodyClear(lower,.3,1.8))return false;
            if(world.hasGroundSupport(lower,.3))return world.isBlock(lower.toBlockPos(),"minecraft:quartz_slab")
                    &&role(world,lower.toBlockPos())==NON_TRAPDOOR;
        }
        return false;
    }
    public static double projection(float yaw,boolean forward,Direction travel) {
        double a=Math.toRadians(yaw),sign=forward?1:-1;
        return sign*(-Math.sin(a)*travel.dx()+Math.cos(a)*travel.dz());
    }
}
