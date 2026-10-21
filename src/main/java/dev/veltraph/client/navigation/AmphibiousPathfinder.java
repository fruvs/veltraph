package dev.veltraph.client.navigation;

import dev.veltraph.client.world.BlockPos;
import dev.veltraph.client.world.Vec3;
import java.util.*;

/** Loaded, body-clear dry/water nodes. Large drops require a separate explicit caller policy. */
public final class AmphibiousPathfinder {
    public interface Terrain {
        Vec3 position(BlockPos node);
        boolean occupiable(Vec3 pose);
        boolean startClear(Vec3 pose);
        boolean water(Vec3 pose);
        /** Only an owned, observed refill column may start briefly above native water flotation. */
        default boolean airRecovery(Vec3 pose) { return false; }
        boolean connection(Vec3 from,Vec3 to,int maxDrop);
    }
    public record Result(List<Vec3> nodes,String reason,int visited) {
        public Result { nodes=List.copyOf(nodes); }
        public boolean found() { return reason.isEmpty(); }
    }
    private record Entry(BlockPos node,double cost,double score,long order) {}
    private static final int[][] HORIZONTAL={{1,0},{-1,0},{0,1},{0,-1}};
    private AmphibiousPathfinder() {}
    public static Result find(Terrain terrain,Vec3 from,Vec3 goal,int radius,int budget,int maxDrop) {
        if(radius<1||budget<1||maxDrop<0||maxDrop>32||!finite(from)||!finite(goal)
                ||!terrain.startClear(from)||!terrain.occupiable(goal))return new Result(List.of(),"Unsupported, hazardous or unloaded start/destination",0);
        var start=GroundPathfinder.node(from);var finish=GroundPathfinder.node(goal);
        var queue=new PriorityQueue<Entry>(Comparator.comparingDouble(Entry::score).thenComparingLong(Entry::order));
        Map<BlockPos,Double> costs=new HashMap<>();Map<BlockPos,BlockPos> parents=new HashMap<>();
        long order=0;int visited=0;queue.add(new Entry(start,0,distance(from,goal),order++));costs.put(start,0d);
        while(!queue.isEmpty()&&visited<budget) {
            var entry=queue.remove();if(entry.cost()!=costs.getOrDefault(entry.node(),Double.POSITIVE_INFINITY))continue;
            visited++;
            Vec3 origin=entry.node().equals(start)?from:entry.node().equals(finish)?goal:terrain.position(entry.node());
            if(entry.node().equals(finish)) {
                if(!terrain.connection(origin,goal,maxDrop))return new Result(List.of(),"Exact destination connection obstructed",visited);
                var route=new LinkedList<Vec3>();
                for(BlockPos p=finish;!p.equals(start);p=parents.get(p))route.addFirst(p.equals(finish)?goal:terrain.position(p));
                if(route.isEmpty())route.add(goal);return new Result(route,"",visited);
            }
            var neighbors=new ArrayList<BlockPos>();
            for(int[] d:HORIZONTAL)for(int dy=1;dy>=-Math.max(3,maxDrop);dy--)neighbors.add(entry.node().add(d[0],dy,d[1]));
            if(terrain.water(origin)) { neighbors.add(entry.node().up());neighbors.add(entry.node().down()); }
            else if(terrain.airRecovery(origin))for(int dy=1;dy<=3;dy++)neighbors.add(entry.node().add(0,-dy,0));
            for(BlockPos next:neighbors) {
                if(Math.abs(next.x()-start.x())>radius||Math.abs(next.y()-start.y())>radius||Math.abs(next.z()-start.z())>radius)continue;
                Vec3 endpoint=next.equals(finish)?goal:terrain.position(next);
                if(!terrain.occupiable(endpoint)||!terrain.connection(origin,endpoint,maxDrop))continue;
                double cost=entry.cost()+distance(origin,endpoint)+(terrain.water(endpoint)?.15:0);
                if(cost>=costs.getOrDefault(next,Double.POSITIVE_INFINITY))continue;
                costs.put(next,cost);parents.put(next,entry.node());queue.add(new Entry(next,cost,cost+distance(endpoint,goal),order++));
            }
        }
        return new Result(List.of(),visited>=budget?"Water/drop search budget reached":"No safe dry/water route",visited);
    }
    private static double distance(Vec3 a,Vec3 b) { return Math.abs(a.x()-b.x())+Math.abs(a.y()-b.y())+Math.abs(a.z()-b.z()); }
    private static boolean finite(Vec3 p) { return p!=null&&Double.isFinite(p.x())&&Double.isFinite(p.y())&&Double.isFinite(p.z()); }
}
