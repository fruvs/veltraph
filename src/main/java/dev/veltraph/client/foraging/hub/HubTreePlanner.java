package dev.veltraph.client.foraging.hub;

import dev.veltraph.client.foraging.HubTreeRoute;
import dev.veltraph.client.foraging.TreeDiscovery;
import dev.veltraph.client.world.BlockPos;
import dev.veltraph.client.world.Vec3;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Stable 4.0.30 Hub find-tree selection. The checkpoint table is a candidate set of logs,
 * not a patrol graph. Source jitter is injectable so tests can assert exact decisions. */
public final class HubTreePlanner {
    public interface World {
        TreeDiscovery.Tree treeAt(BlockPos checkpoint);
        BlockPos standingPosition(BlockPos checkpoint);
        boolean blacklisted(BlockPos checkpoint,long now);
        default double checkpointJitter(){return java.util.concurrent.ThreadLocalRandom.current().nextDouble(6);}
        default BlockPos targetLog(BlockPos checkpoint,BlockPos standingPosition){return checkpoint;}
    }
    public record Selection(BlockPos checkpoint,TreeDiscovery.Tree tree,BlockPos standingPosition,BlockPos targetLog) {}
    private HubTreePlanner() {}
    public static Optional<Selection> select(World world,BlockPos player,BlockPos previous,long now) {
        return select(HubTreeRoute.CHECKPOINTS,world,player,previous,now);
    }
    public static Optional<Selection> select(List<BlockPos> checkpoints,World world,BlockPos player,BlockPos previous,long now) {
        record Ranked(BlockPos position,double score){}
        var ranked=checkpoints.stream().map(c->{double jitter=world.checkpointJitter();if(!Double.isFinite(jitter)||jitter<0||jitter>=6)throw new IllegalArgumentException("Hub checkpoint jitter outside source [0,6)");return new Ranked(c,squared(c,player)+jitter);}).sorted(Comparator.comparingDouble(Ranked::score)).toList();
        for(var candidate:ranked) {
            BlockPos p=candidate.position();
            var tree=world.treeAt(p);
            if(tree==null||tree.species().isBlank()||tree.logs().size()<=2||!tree.logs().contains(p))continue;
            var stand=world.standingPosition(p);
            if(stand==null||world.blacklisted(p,now)||previous!=null&&horizontal(p,previous)<4)continue;
            BlockPos target=world.targetLog(p,stand);
            if(target==null||!tree.logs().contains(target))target=p;
            return Optional.of(new Selection(p,tree,stand,target));
        }
        return Optional.empty();
    }
    public static double squared(BlockPos a,BlockPos b) {
        double x=(double)a.x()-b.x(),y=(double)a.y()-b.y(),z=(double)a.z()-b.z();return x*x+y*y+z*z;
    }
    public static double horizontal(BlockPos a,BlockPos b) {return Math.hypot((double)a.x()-b.x(),(double)a.z()-b.z());}
    /** Same nearest/next segment selection as the readable anchor route helper. */
    public static int entryIndex(Vec3 player) {
        var anchors=HubTreeRoute.ENTRY_ANCHORS;
        int nearest=0;double best=Double.MAX_VALUE;
        for(int i=0;i<anchors.size();i++) {
            double distance=distance(player,anchors.get(i));if(distance<best){best=distance;nearest=i;}
        }
        int next=nearest+1;if(next>=anchors.size())return anchors.size()-1;
        return distance(anchors.get(nearest),anchors.get(next))>distance(player,anchors.get(next))?next:nearest;
    }
    private static double distance(Vec3 p,BlockPos b){return p.distanceTo(new Vec3(b.x(),b.y(),b.z()));}
    private static double distance(BlockPos a,BlockPos b){return Math.sqrt(squared(a,b));}
}
