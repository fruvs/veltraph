package dev.veltraph.client.galatea;

import dev.veltraph.client.world.BlockPos;
import dev.veltraph.client.world.Vec3;
import java.util.function.DoubleSupplier;

/** Distinct source central stand helper. Bytecode confirms scoring pose is BASE, not offset.
 * Offsets affect dry admission and edge/neighbor terms; stable minimum retains native loop order.
 */
public final class FigSourceCentralStandPolicy {
    private FigSourceCentralStandPolicy(){}
    public static BlockPos choose(BlockPos base,BlockPos selectedSeed,FigSourceGeometry.World world,
                                  Vec3 nativePlayer,float nativeYaw,DoubleSupplier randomHalfOpen) {
        Vec3 basePose=new Vec3(base.x()+.5,base.y(),base.z()+.5);
        Vec3 seedPose=new Vec3(selectedSeed.x()+.5,selectedSeed.y(),selectedSeed.z()+.5);
        double yaw=Math.toDegrees(Math.atan2(seedPose.z()-basePose.z(),seedPose.x()-basePose.x()))-90;
        double difference=((yaw-nativeYaw+180)%360+360)%360-180;
        double fixed=basePose.horizontalDistanceTo(basePose)
            +Math.pow(basePose.horizontalDistanceTo(nativePlayer),2)+Math.abs(difference)/40;
        BlockPos best=null;double bestScore=Double.POSITIVE_INFINITY;
        for(int x=-1;x<2;x++)for(int z=-1;z<2;z++) {
            BlockPos offset=base.add(x,0,z);
            if(!world.dryStanding(offset))continue;
            double random=randomHalfOpen.getAsDouble();
            if(!Double.isFinite(random)||random<0||random>=.5)
                throw new IllegalArgumentException("Source central-stand random[0,.5) required");
            double score=fixed+random;
            if(FigSourceStandPolicy.edge(world,offset))score+=10;
            if(!FigSourceStandPolicy.levelNeighbors(world,offset))score+=4;
            if(Double.compare(bestScore,score)>0){best=offset;bestScore=score;}
        }
        return best==null?base:best;
    }
    /** Source source2150 compares three-axis eye-to-log-center only, strictly<4.25. */
    public static int reachableCount(Vec3 eye,java.util.List<BlockPos> scaffoldLogs) {
        int n=0;for(BlockPos p:scaffoldLogs)if(eye.distanceTo(FigSourceGeometry.center(p))<4.25)n++;return n;
    }
}
