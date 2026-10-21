package dev.veltraph.client.foraging.hub;
import dev.veltraph.client.foraging.*;
import dev.veltraph.client.world.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class HubTreePlannerTest {
    private static TreeDiscovery.Tree tree(BlockPos p,String species,int count){return new TreeDiscovery.Tree(p,species,java.util.stream.IntStream.range(0,count).mapToObj(p::up).toList());}
    private static class World implements HubTreePlanner.World {
        Map<BlockPos,TreeDiscovery.Tree> trees=new HashMap<>();Set<BlockPos> blocked=new HashSet<>(),noStand=new HashSet<>();
        public TreeDiscovery.Tree treeAt(BlockPos p){return trees.get(p);}
        public BlockPos standingPosition(BlockPos p){return noStand.contains(p)?null:p.add(1,0,0);}
        public double checkpointJitter(){return 0;}
        public boolean blacklisted(BlockPos p,long now){return blocked.contains(p);}
    }
    @Test void exactStableTableAndAnchors(){assertEquals(67,HubTreeRoute.CHECKPOINTS.size());assertEquals(67,new HashSet<>(HubTreeRoute.CHECKPOINTS).size());assertEquals(List.of(new BlockPos(-20,71,-27),new BlockPos(-64,70,-30)),HubTreeRoute.ENTRY_ANCHORS);}
    @Test void examinesAll67AndDoesNotStopAtThreeNearestEmptyCheckpoints(){
        var world=new World();var p=HubTreeRoute.CHECKPOINTS.getLast();world.trees.put(p,tree(p,"oak",3));
        assertEquals(p,HubTreePlanner.select(world,HubTreeRoute.CHECKPOINTS.getFirst(),null,0).orElseThrow().checkpoint());
    }
    @Test void usesSquaredDistanceRatherThanYawOrRouteScore(){
        var world=new World();var near=new BlockPos(1,0,0);var far=new BlockPos(4,0,0);
        world.trees.put(near,tree(near,"oak",3));world.trees.put(far,tree(far,"oak",3));
        assertEquals(near,HubTreePlanner.select(List.of(far,near),world,new BlockPos(0,0,0),null,0).orElseThrow().checkpoint());
    }
    @Test void rejectsTwoLogsUnknownSpeciesBlacklistStandAndPreviousWithinFour(){
        var world=new World();var candidates=java.util.stream.IntStream.range(0,6).mapToObj(i->new BlockPos(i*10,0,0)).toList();
        for(var p:candidates)world.trees.put(p,tree(p,"oak",3));
        world.trees.put(candidates.get(0),tree(candidates.get(0),"oak",2));world.trees.put(candidates.get(1),tree(candidates.get(1),"",3));world.blocked.add(candidates.get(2));world.noStand.add(candidates.get(3));
        assertEquals(candidates.get(5),HubTreePlanner.select(candidates,world,candidates.get(0),candidates.get(4).up(20),0).orElseThrow().checkpoint());
    }
    @Test void checkpointTagContractDoesNotInventOakOnlyRestriction(){var world=new World();var p=HubTreeRoute.CHECKPOINTS.getFirst();world.trees.put(p,tree(p,"birch",3));assertEquals("birch",HubTreePlanner.select(world,p,null,0).orElseThrow().tree().species());}
    @Test void previousDistanceExactlyFourIsEligible(){var world=new World();var p=new BlockPos(4,0,0);world.trees.put(p,tree(p,"oak",3));assertTrue(HubTreePlanner.select(List.of(p),world,p,new BlockPos(0,30,0),0).isPresent());}
    @Test void candidateNeedsActualCheckpointLog(){var world=new World();var p=new BlockPos(0,0,0);world.trees.put(p,tree(p.up(),"oak",3));assertTrue(HubTreePlanner.select(List.of(p),world,p,null,0).isEmpty());}
    @Test void sourceJitterChangesNearTieAndAlternateTargetIsPreserved(){
        var first=new BlockPos(0,0,0);var second=new BlockPos(1,0,0);
        var world=new World(){int samples;public double checkpointJitter(){return samples++==0?5:0;}public BlockPos targetLog(BlockPos p,BlockPos stand){return p.up();}};
        world.trees.put(first,tree(first,"oak",3));world.trees.put(second,tree(second,"oak",3));
        var result=HubTreePlanner.select(List.of(first,second),world,first,null,0).orElseThrow();
        assertEquals(second,result.checkpoint());assertEquals(second.up(),result.targetLog());
    }
    @Test void invalidInjectedJitterCannotForgeOrdering(){var world=new World(){public double checkpointJitter(){return 6;}};assertThrows(IllegalArgumentException.class,()->HubTreePlanner.select(world,new BlockPos(0,0,0),null,0));}
    @Test void entryUsesNextSegmentAndSkipsFinishedAnchor(){assertEquals(1,HubTreePlanner.entryIndex(new Vec3(-30,71,-28)));assertEquals(1,HubTreePlanner.entryIndex(new Vec3(-64,70,-30)));assertEquals(0,HubTreePlanner.entryIndex(new Vec3(0,71,-27)));}
}
