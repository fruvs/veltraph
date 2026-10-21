package dev.veltraph.client.foraging;

import dev.veltraph.client.world.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ParkTreePlannerTest {
    private static final class Samples extends Random {
        private final ArrayDeque<Double> values=new ArrayDeque<>();
        Samples(double... values){for(double value:values)this.values.add(value);}
        @Override public double nextDouble(){return values.isEmpty()?0:values.remove();}
    }
    private static ParkTreePlanner.Candidate tree(int x,int y,int z,int logs) {
        BlockPos root=new BlockPos(x,y,z);List<BlockPos> positions=new ArrayList<>();
        for(int i=0;i<logs;i++)positions.add(root.add(0,i,0));
        return new ParkTreePlanner.Candidate(new TreeDiscovery.Tree(root,"birch",positions),new Vec3(x+.5,y+.5,z+.5),true,true,false,false);
    }
    private static ParkTreePlanner.Snapshot snapshot(Vec3 player,float yaw,boolean crowded,List<Vec3> others) {
        return new ParkTreePlanner.Snapshot(player,yaw,ForagingRegion.BIRCH,ForagingRegion.Density.LOW,false,crowded,others);
    }
    @Test void actualParkCostUsesHeightTurningBoxesAndOneSamplePerGeneration() {
        var candidate=tree(0,72,4,4);var player=new Vec3(.5,70,.5);
        var input=snapshot(player,0,true,List.of(new Vec3(4.5,72.5,4.5),new Vec3(8.5,72.5,4.5)));
        var scored=ParkTreePlanner.rank(List.of(candidate),input,new Samples(.2,.3)).getFirst();
        assertEquals(11,scored.variation());assertEquals(1,scored.playersWithinFive());assertEquals(2,scored.playersWithinTen());
        assertEquals(candidate.anchor().distanceTo(player)+2*1.25+11+6+6,scored.cost(),1e-9);
    }
    @Test void oneOccupantInsideFiveCostsNineAndBoxBoundaryIsHalfOpen() {
        var center=new Vec3(0,0,0);
        assertEquals(2,ParkTreePlanner.playersInBox(List.of(new Vec3(-5,0,0),new Vec3(5,0,0),new Vec3(0,4.99,0)),center,5));
        var candidate=tree(0,70,4,4);var player=new Vec3(.5,70,.5);
        double empty=ParkTreePlanner.rank(List.of(candidate),snapshot(player,0,false,List.of()),new Samples()).getFirst().cost();
        double occupied=ParkTreePlanner.rank(List.of(candidate),snapshot(player,0,false,List.of(candidate.anchor())),new Samples()).getFirst().cost();
        assertEquals(9,occupied-empty,1e-9);
    }
    @Test void birchCliffCostUsesSourceStrictCoordinateConditions() {
        assertEquals(75,ParkTreePlanner.birchCliffPenalty(new Vec3(-290,77,-54),new Vec3(-305,81,-35),ForagingRegion.BIRCH));
        assertEquals(0,ParkTreePlanner.birchCliffPenalty(new Vec3(-290,78,-54),new Vec3(-305,81,-35),ForagingRegion.BIRCH));
        assertEquals(0,ParkTreePlanner.birchCliffPenalty(new Vec3(-290,77,-53),new Vec3(-305,81,-35),ForagingRegion.BIRCH));
        assertEquals(0,ParkTreePlanner.birchCliffPenalty(new Vec3(-290,77,-54),new Vec3(-304,81,-35),ForagingRegion.BIRCH));
        assertEquals(0,ParkTreePlanner.birchCliffPenalty(new Vec3(-290,77,-54),new Vec3(-305,81,-35),ForagingRegion.SPRUCE));
    }
    @Test void sourceFiveCandidateRouteAttemptsReachFourthAndNeverSixth() {
        var candidates=List.of(tree(1,70,0,4),tree(3,70,0,4),tree(5,70,0,4),tree(7,70,0,4),tree(9,70,0,4),tree(11,70,0,4));
        var ranked=ParkTreePlanner.rank(candidates,snapshot(new Vec3(.5,70,.5),-90,false,List.of()),new Samples());
        List<Integer> attempts=new ArrayList<>();
        var fourth=ParkTreePlanner.firstReachable(ranked,s->{int x=s.candidate().tree().trunk().x();attempts.add(x);return x==7?Optional.of(new BlockPos(6,70,0)):Optional.empty();});
        assertEquals(List.of(1,3,5,7),attempts);assertEquals(7,fourth.orElseThrow().scored().candidate().tree().trunk().x());
        attempts.clear();
        assertTrue(ParkTreePlanner.firstReachable(ranked,s->{attempts.add(s.candidate().tree().trunk().x());return Optional.empty();}).isEmpty());
        assertEquals(List.of(1,3,5,7,9),attempts);
    }
    @Test void occupancyAndSampledVariationCanChangeTheSelectedRoute() {
        var near=tree(1,70,0,4);var far=tree(3,70,0,4);var player=new Vec3(.5,70,.5);
        assertEquals(far,ParkTreePlanner.rank(List.of(near,far),snapshot(player,-90,false,List.of()),new Samples(.99,0)).getFirst().candidate());
        assertEquals(near,ParkTreePlanner.rank(List.of(near,far),snapshot(player,-90,false,List.of()),new Samples(0,.99)).getFirst().candidate());
    }
    @Test void incompleteOrUnavailableYieldDoesNotBecomeAValidTree() {
        var good=tree(1,70,0,4);var blocked=new ParkTreePlanner.Candidate(good.tree(),good.anchor(),true,true,false,true);
        var partial=new ParkTreePlanner.Candidate(good.tree(),good.anchor(),true,false,false,false);
        var unloaded=new ParkTreePlanner.Candidate(good.tree(),good.anchor(),false,true,false,false);
        assertEquals(List.of(good),ParkTreePlanner.rank(List.of(tree(0,70,0,3),blocked,partial,unloaded,good),snapshot(new Vec3(0,70,0),0,false,List.of()),new Samples()).stream().map(ParkTreePlanner.Scored::candidate).toList());
    }
    @Test void sampledApproachRequiresStandingCollisionVisibilityAndActualEyeReach() {
        BlockPos log=new BlockPos(0,70,0),valid=new BlockPos(2,70,0),occluded=new BlockPos(-2,70,0),high=new BlockPos(0,71,2);
        var terrain=new ParkTreePlanner.ApproachTerrain(){
            public boolean standable(BlockPos p){return p.equals(valid)||p.equals(occluded)||p.equals(high);}
            public Vec3 feet(BlockPos p){return new Vec3(p.x()+.5,p.y(),p.z()+.5);}
            public boolean visible(Vec3 eye,BlockPos target){return eye.x()>1;}
            public boolean surroundingStandable(BlockPos p){return true;}
        };
        assertEquals(Optional.of(valid),ParkTreePlanner.approach(log,log,new Vec3(-2,70,0),1.62,terrain,new Samples()));
        assertTrue(ParkTreePlanner.approach(log,log,new Vec3(0,70,0),10,terrain,new Samples()).isEmpty());
    }
    @Test void sourceTwoPointPenaltyPrefersStandableThreeByThreeSurroundings() {
        BlockPos log=new BlockPos(0,70,0),narrow=new BlockPos(1,70,0),wide=new BlockPos(2,70,0);
        var terrain=new ParkTreePlanner.ApproachTerrain(){
            public boolean standable(BlockPos p){return p.equals(narrow)||p.equals(wide);}
            public Vec3 feet(BlockPos p){return new Vec3(p.x()+.5,p.y(),p.z()+.5);}
            public boolean visible(Vec3 eye,BlockPos target){return true;}
            public boolean surroundingStandable(BlockPos p){return p.equals(wide);}
        };
        assertEquals(Optional.of(wide),ParkTreePlanner.approach(log,log,new Vec3(1.5,70,.5),1.62,terrain,new Samples()));
    }
    @Test void regionBoundsAndDensityAreSourceValues() {
        assertEquals(7,ForagingRegion.BIRCH.minimumLogs(ForagingRegion.Density.MEDIUM));
        assertEquals(20,ForagingRegion.DARK_OAK.minimumLogs(ForagingRegion.Density.HIGH));
        var bounded=new ParkTreePlanner.Snapshot(new Vec3(0,70,0),0,ForagingRegion.BIRCH,ForagingRegion.Density.LOW,true,false,List.of());
        assertTrue(ParkTreePlanner.rank(List.of(tree(0,70,0,4)),bounded,new Samples()).isEmpty());
    }
}
