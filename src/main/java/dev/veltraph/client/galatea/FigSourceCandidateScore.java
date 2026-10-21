package dev.veltraph.client.galatea;

import dev.veltraph.client.world.BlockPos;
import dev.veltraph.client.world.Vec3;

/** Exact readable score weights; supplied observations stay distinct from descriptor atlas data. */
public final class FigSourceCandidateScore {
    private FigSourceCandidateScore(){}
    public static double localPenalty(BlockPos candidate,BlockPos previous,boolean previousPassable,boolean figReset) {
        if(!previousPassable||figReset)return 0;
        int vertical=Math.abs(candidate.y()-previous.y());double distance=point(candidate).distanceTo(point(previous));
        return (vertical<3?(3-vertical)*2:0)+(distance<3?(3-distance)*5:0);
    }
    public static double score(GalateaPlanner.AxeMode mode,BlockPos candidate,BlockPos feet,BlockPos descriptorBase,
                               Vec3 nativePlayer,float nativeYaw,boolean aimHigher,int leafCost,double repeatPenalty,
                               boolean jumpingFeasible,boolean leavesBelow,Integer throwingCount,BlockPos previous) {
        double yawDelta=Math.abs(yawDelta(nativePlayer,nativeYaw,
                mode==GalateaPlanner.AxeMode.STEM||mode==GalateaPlanner.AxeMode.SCAFFOLD
                        ?FigSourceGeometry.center(candidate):point(candidate)));
        return switch(mode) {
            case STEM,SCAFFOLD -> Math.abs(candidate.y()-(descriptorBase.y()+(aimHigher?1:0)))*7.5
                    +yawDelta/15+repeatPenalty+leafCost+horizontal(feet,candidate)
                    +(sameColumn(feet,candidate)?10:0);
            case JUMPING -> horizontal(feet,candidate)+Math.abs(candidate.y()-descriptorBase.y())*7.5
                    +yawDelta/15+repeatPenalty+(jumpingFeasible?0:1000)+(leavesBelow?10:0);
            case THROWING -> point(feet).distanceTo(point(candidate))*.5
                    +Math.abs(candidate.y()-descriptorBase.y())*10+horizontal(candidate,descriptorBase)*2
                    +yawDelta/5+repeatPenalty+(throwingCount==null?0:5-Math.min(5,throwingCount))
                    +(sameColumn(previous,candidate)?10:0);
        };
    }
    private static boolean sameColumn(BlockPos a,BlockPos b){return a.x()==b.x()&&a.z()==b.z();}
    private static Vec3 point(BlockPos p){return new Vec3(p.x(),p.y(),p.z());}
    private static double horizontal(BlockPos a,BlockPos b){return point(a).horizontalDistanceTo(point(b));}
    private static double yawDelta(Vec3 player,float yaw,Vec3 p) {
        double angle=Math.toDegrees(Math.atan2(p.z()-player.z(),p.x()-player.x()))-90-yaw;
        return ((angle+180)%360+360)%360-180;
    }
}
