package dev.veltraph.client.combat.sven;

import dev.veltraph.client.world.BlockPos;
import java.util.*;

/** Stable route-helper literal edges and its breadth-first search from BIRCH. */
public final class SvenParkRoutes {
    private SvenParkRoutes(){}
    public enum Tree { BIRCH, SPRUCE, DARK_OAK, ACACIA, JUNGLE }
    public record Edge(Tree from,Tree to,BlockPos standingStart,List<BlockPos> etherwarpCandidates){
        public Edge{etherwarpCandidates=List.copyOf(etherwarpCandidates);}
    }
    private static final List<Edge> EDGES=List.of(
        new Edge(Tree.BIRCH,Tree.SPRUCE,new BlockPos(-310,81,-8),List.of(new BlockPos(-336,90,8),new BlockPos(-335,89,10),new BlockPos(-331,90,16),new BlockPos(-338,94,13))),
        new Edge(Tree.BIRCH,Tree.DARK_OAK,new BlockPos(-320,82,-26),List.of(new BlockPos(-363,99,-45),new BlockPos(-365,97,-41),new BlockPos(-361,98,-46),new BlockPos(-358,98,-53),new BlockPos(-359,99,-53))),
        new Edge(Tree.SPRUCE,Tree.ACACIA,new BlockPos(-374,89,7),List.of(new BlockPos(-397,109,12),new BlockPos(-397,109,10),new BlockPos(-399,110,5),new BlockPos(-402,106,-2))),
        new Edge(Tree.DARK_OAK,Tree.JUNGLE,new BlockPos(-404,99,-42),List.of(new BlockPos(-436,120,-61),new BlockPos(-436,120,-61),new BlockPos(-435,120,-63)))
    );
    public static List<Edge> edges(){return EDGES;}
    public static List<Edge> route(SvenCaveProfile profile){
        Tree goal=Tree.valueOf(profile.name());record Step(Tree tree,List<Edge> path){}
        var visited=EnumSet.of(Tree.BIRCH);ArrayDeque<Step> queue=new ArrayDeque<>();queue.add(new Step(Tree.BIRCH,List.of()));
        while(!queue.isEmpty()){var node=queue.removeFirst();for(var edge:EDGES){if(edge.from()!=node.tree()||visited.contains(edge.to()))continue;
            var path=new ArrayList<>(node.path());path.add(edge);if(edge.to()==goal)return List.copyOf(path);visited.add(edge.to());queue.add(new Step(edge.to(),List.copyOf(path)));}}
        return List.of();
    }
}
