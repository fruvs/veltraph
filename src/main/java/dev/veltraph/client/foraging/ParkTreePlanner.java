package dev.veltraph.client.foraging;

import dev.veltraph.client.navigation.WalkController;
import dev.veltraph.client.world.BlockPos;
import dev.veltraph.client.world.Vec3;
import java.util.*;
import java.util.function.Function;
import java.util.random.RandomGenerator;

/** Readable stable-4.0.30 Park utility, not the historical Fig controller misnamed ParkTreeScore.
 * Source utility IIlIIIlllllIIlIll... L267-450 ranks available yield, then tries five paths.
 * Native atlas anchors are unavailable: the adapter supplies explicit loaded-world anchors. */
public final class ParkTreePlanner {
    public record Candidate(TreeDiscovery.Tree tree, Vec3 anchor, boolean loaded, boolean complete,
                            boolean disabled, boolean blacklisted) {}
    public record Snapshot(Vec3 player, float yaw, ForagingRegion region, ForagingRegion.Density density,
                           boolean useRegionBounds, boolean crowded, List<Vec3> otherPlayers) {
        public Snapshot { otherPlayers=List.copyOf(otherPlayers); }
    }
    public record Scored(Candidate candidate, double cost, double variation, int playersWithinFive,
                         int playersWithinTen, double birchCliffPenalty) {}
    public record Plan(Scored scored, BlockPos destination) {}
    public interface ApproachTerrain {
        boolean standable(BlockPos spot);
        Vec3 feet(BlockPos spot);
        /** Visibility from the prospective ordinary standing eye to the actual log center. */
        boolean visible(Vec3 eye, BlockPos log);
        boolean surroundingStandable(BlockPos spot);
    }
    private ParkTreePlanner() {}

    public static List<Scored> rank(List<Candidate> candidates, Snapshot snapshot, RandomGenerator random) {
        List<Scored> ranked=new ArrayList<>();
        for(Candidate candidate:candidates) {
            var tree=candidate.tree();
            if(!candidate.loaded()||!candidate.complete()||candidate.disabled()||candidate.blacklisted()
                    ||tree.logs().size()<snapshot.region().minimumLogs(snapshot.density())
                    ||!snapshot.region().matches("minecraft:"+tree.species()+"_log")
                    ||snapshot.useRegionBounds()&&!snapshot.region().contains(tree.trunk()))continue;
            double variation=random.nextDouble()*10;
            if(snapshot.crowded())variation+=random.nextDouble()*30;
            int near=playersInBox(snapshot.otherPlayers(),candidate.anchor(),5);
            int far=playersInBox(snapshot.otherPlayers(),candidate.anchor(),10);
            double cliff=birchCliffPenalty(candidate.anchor(),snapshot.player(),snapshot.region());
            double targetYaw=Math.toDegrees(Math.atan2(snapshot.player().x()-candidate.anchor().x(),candidate.anchor().z()-snapshot.player().z()));
            double cost=candidate.anchor().distanceTo(snapshot.player())+variation
                    +Math.abs(tree.trunk().y()-Math.floor(snapshot.player().y()))*1.25
                    +Math.abs(WalkController.wrap((float)targetYaw-snapshot.yaw()))/15d+near*6+far*3+cliff;
            ranked.add(new Scored(candidate,cost,variation,near,far,cliff));
        }
        // Source compares ascending costs; position tie-break only makes equal fixture RNG reproducible.
        ranked.sort(Comparator.comparingDouble(Scored::cost).thenComparingInt(s->s.candidate().tree().trunk().x())
                .thenComparingInt(s->s.candidate().tree().trunk().y()).thenComparingInt(s->s.candidate().tree().trunk().z()));
        return List.copyOf(ranked);
    }

    /** Source checks at most five ranked trees and keeps the first successful path, without reranking. */
    public static Optional<Plan> firstReachable(List<Scored> ranked, Function<Scored, Optional<BlockPos>> route) {
        for(Scored tree:ranked.stream().limit(5).toList()) {
            Optional<BlockPos> spot=route.apply(tree);
            if(spot.isPresent())return Optional.of(new Plan(tree,spot.get()));
        }
        return Optional.empty();
    }

    public static int playersInBox(List<Vec3> players,Vec3 anchor,double radius) {
        return (int)players.stream().filter(p->p.x()>=anchor.x()-radius&&p.x()<anchor.x()+radius
                &&p.y()>=anchor.y()-radius&&p.y()<anchor.y()+radius
                &&p.z()>=anchor.z()-radius&&p.z()<anchor.z()+radius).count();
    }
    public static double birchCliffPenalty(Vec3 anchor,Vec3 player,ForagingRegion region) {
        return region==ForagingRegion.BIRCH&&anchor.z()<-53&&anchor.y()<78
                &&player.x()<-304&&player.z()<-34&&player.y()>80?75:0;
    }

    /** Source approach helper lIIllIllII... searches +/-3 X/Z, +/-1 Y, visible eye reach <=3.5.
     * The missing atlas anchor is an explicit parameter, rather than invented hidden metadata. */
    public static Optional<BlockPos> approach(BlockPos anchor,BlockPos log,Vec3 player,double eyeHeight,
                                              ApproachTerrain terrain,RandomGenerator random) {
        BlockPos best=null;double bestCost=Double.POSITIVE_INFINITY;
        Vec3 logCenter=new Vec3(log.x()+.5,log.y()+.5,log.z()+.5);
        for(int x=-3;x<=3;x++)for(int y=-1;y<=1;y++)for(int z=-3;z<=3;z++) {
            BlockPos spot=anchor.add(x,y,z);
            if(!terrain.standable(spot))continue;
            Vec3 feet=terrain.feet(spot),eye=new Vec3(feet.x(),feet.y()+eyeHeight,feet.z());
            if(eye.distanceTo(logCenter)>3.5||!terrain.visible(eye,log))continue;
            double cost=feet.distanceTo(player)+Math.abs(feet.distanceTo(logCenter)-3)+random.nextDouble()*1.25
                    +(terrain.surroundingStandable(spot)?0:2);
            if(cost<bestCost){bestCost=cost;best=spot;}
        }
        return Optional.ofNullable(best);
    }
}
