package dev.veltraph.client.navigation;

import dev.veltraph.client.world.BlockPos;
import dev.veltraph.client.world.Vec3;
import java.util.*;

/** Six-neighbor air path, based on the recovered shared Pathfinder/Autoflyer contracts. */
public final class FlightPathfinder {
    public interface Airspace {
        boolean clear(BlockPos feet);
        /** A fractional endpoint need not occupy the same collision volume as its cell center. */
        default boolean poseClear(Vec3 feet) { return clear(feet.toBlockPos()); }
    }
    public record Result(List<Vec3> nodes, String reason) {
        public Result { nodes = List.copyOf(nodes); }
        public boolean found() { return reason.isEmpty(); }
    }
    private record Entry(BlockPos pos, int cost, int score, long order) {}
    private static final int[][] OFFSETS = {{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};
    private FlightPathfinder() {}

    public static Result find(Airspace air, Vec3 from, Vec3 destination, int radius, int maxNodes) {
        return find(air,from,destination,radius,maxNodes,(a,b)->true);
    }
    /** Swept edges include the actual fractional start/end, not assumed cell-center poses. */
    public static Result find(Airspace air, Vec3 from, Vec3 destination, int radius, int maxNodes,
                              java.util.function.BiPredicate<Vec3,Vec3> swept) {
        BlockPos start=from.toBlockPos(), goal=destination.toBlockPos();
        if(radius<1 || maxNodes<1 || !air.poseClear(from) || !air.poseClear(destination))
            return new Result(List.of(),"Start or destination is obstructed/unloaded");
        PriorityQueue<Entry> open=new PriorityQueue<>(Comparator.comparingInt(Entry::score).thenComparingLong(Entry::order));
        Map<BlockPos,Integer> cost=new HashMap<>(); Map<BlockPos,BlockPos> parent=new HashMap<>();
        long order=0; int visited=0;
        open.add(new Entry(start,0,distance(start,goal),order++)); cost.put(start,0);
        while(!open.isEmpty() && visited<maxNodes) {
            Entry e=open.remove();
            if(e.cost()!=cost.getOrDefault(e.pos(),Integer.MAX_VALUE)) continue;
            visited++;
            if(e.pos().equals(goal)) {
                LinkedList<BlockPos> full=new LinkedList<>();
                for(BlockPos p=goal;!p.equals(start);p=parent.get(p)) full.addFirst(p);
                List<Vec3> path=new ArrayList<>();
                for(BlockPos p:full) path.add(p.equals(goal)?destination:center(p));
                if(path.isEmpty()) {
                    if(!swept.test(from,destination)) return new Result(List.of(),"Destination connection obstructed");
                    path.add(destination);
                }
                return new Result(path,"");
            }
            for(int[] d:OFFSETS) {
                BlockPos next=e.pos().add(d[0],d[1],d[2]);
                if(Math.abs(next.x()-start.x())>radius || Math.abs(next.y()-start.y())>radius
                        ||Math.abs(next.z()-start.z())>radius || !(next.equals(goal)?air.poseClear(destination):air.clear(next))) continue;
                Vec3 origin=e.pos().equals(start)?from:center(e.pos());
                Vec3 endpoint=next.equals(goal)?destination:center(next);
                if(!swept.test(origin,endpoint)) continue;
                int g=e.cost()+1;
                if(g>=cost.getOrDefault(next,Integer.MAX_VALUE)) continue;
                cost.put(next,g);parent.put(next,e.pos());
                open.add(new Entry(next,g,g+distance(next,goal),order++));
            }
        }
        return new Result(List.of(), visited>=maxNodes?"Flight search budget reached":"No flight path");
    }
    private static Vec3 center(BlockPos p) { return new Vec3(p.x()+.5,p.y(),p.z()+.5); }
    private static int distance(BlockPos a,BlockPos b) { return Math.abs(a.x()-b.x())+Math.abs(a.y()-b.y())+Math.abs(a.z()-b.z()); }
}
