package dev.veltraph.client.foraging;

import dev.veltraph.client.world.BlockPos;
import dev.veltraph.client.world.Vec3;
import dev.veltraph.client.navigation.WalkController;
import java.util.*;

/** Historical Fig/Galatea cost seam from the class formerly aliased ForagingParkController.
 * This is not the registered Park finder: actual Park decisions use {@link ParkTreePlanner}.
 * Unrecovered atlas/positioning fields remain explicit neutral metadata in this legacy seam. */
public final class TreeSelector {
    public record Candidate(BlockPos trunk,boolean loaded,boolean broken,boolean disabled,
                            double approachDistance,double routeCost,double intrinsicCost,double regionBias,
                            boolean shulkerNear,boolean otherRegion,double regionDistance) {
        public Candidate(BlockPos trunk,boolean loaded,boolean broken,boolean disabled) {
            this(trunk,loaded,broken,disabled,Double.NaN,0,0,0,false,false,0);
        }
    }
    private TreeSelector() {}
    public static List<BlockPos> ranked(List<Candidate> candidates,Vec3 player,float yaw,Set<BlockPos> blacklist) {
        return candidates.stream().filter(t->t.loaded()&&!t.broken()&&!t.disabled()&&!blacklist.contains(t.trunk()))
                .sorted(Comparator.comparingDouble((Candidate c)->score(c,player,yaw))
                        .thenComparingInt(c->c.trunk().x()).thenComparingInt(c->c.trunk().y()).thenComparingInt(c->c.trunk().z()))
                .map(Candidate::trunk).distinct().toList();
    }
    public static double score(Candidate tree,Vec3 player,float yaw) {
        Vec3 point=new Vec3(tree.trunk().x()+.5,tree.trunk().y()+.5,tree.trunk().z()+.5);
        double distance=point.distanceTo(player);
        float target=(float)Math.toDegrees(Math.atan2(player.x()-point.x(),point.z()-player.z()));
        double approach=Double.isFinite(tree.approachDistance())?Math.max(0,tree.approachDistance()):distance;
        return distance+1.5*approach+Math.max(0,tree.routeCost())+tree.intrinsicCost()+tree.regionBias()
                +Math.abs(WalkController.wrap(target-yaw))/20d+(tree.shulkerNear()?50:0)
                +(tree.otherRegion()?200+2*Math.max(0,tree.regionDistance()):0);
    }
    public static double score(BlockPos trunk,Vec3 player,float yaw) {
        return score(new Candidate(trunk,true,false,false),player,yaw);
    }
}
