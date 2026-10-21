package dev.veltraph.client.navigation;

import dev.veltraph.client.world.BlockPos;
import dev.veltraph.client.world.Vec3;
import java.util.*;

/** Bounded local A* rebuilt from MACRO_DECODE_movement_engines.md §3.
 * Swept-clear level diagonals plus cardinal one-block steps/drops; no unloaded chunks or fluid travel.
 */
public final class GroundPathfinder {
    public interface Terrain {
        boolean clear(BlockPos pos);
        boolean floor(BlockPos pos);
        /** A node denotes the cell above support; actual collision tops may be fractional. */
        default Vec3 position(BlockPos pos) { return new Vec3(pos.x()+.5,pos.y(),pos.z()+.5); }
        /** Project a generated course point onto its real collision top on mixed-height surfaces. */
        default Vec3 surfacePosition(Vec3 pos) { return pos; }
        default boolean standable(BlockPos pos) { return clear(pos)&&clear(pos.up())&&floor(pos.down()); }
        default boolean standingClear(Vec3 position) { return standable(node(position)); }
        default boolean walkClear(Vec3 a,Vec3 b) { return TravelPath.gridWalkClear(this,a,b); }
        /** Validate a physical step, including its swept body, rather than only its landing cell. */
        default boolean stepClear(Vec3 from,Vec3 to) {
            BlockPos a=node(from),b=node(to);
            if(b.y()>a.y()&&!clear(a.up(2)))return false;
            return b.y()>=a.y()||clear(b.up(2));
        }
    }
    public record Result(List<BlockPos> steps, String reason, int visited) {
        public Result { steps = List.copyOf(steps); }
        public boolean found() { return reason.isEmpty(); }
    }
    private record Entry(BlockPos pos, double cost, double score, long order) {}
    private static final int[][] DIRECTIONS = {{1,0},{-1,0},{0,1},{0,-1},{1,1},{1,-1},{-1,1},{-1,-1}};
    private GroundPathfinder() {}

    public static boolean standable(Terrain t, BlockPos p) {
        return t.standable(p);
    }
    public static BlockPos node(Vec3 position) {
        return new BlockPos((int)Math.floor(position.x()),(int)Math.ceil(position.y()-.001),(int)Math.floor(position.z()));
    }
    /** Pure Y projection when the caller discards node X/Z. The caller still evaluates the
     * original immutable x/y/z accessors in order; no terrain/getter/custody result is retained. */
    public static int nodeCeiling(double originalX,double originalY,double originalZ) {
        return (int)Math.ceil(originalY-.001);
    }
    public static boolean canStep(Terrain t, BlockPos from, BlockPos to) {
        int dx=Math.abs(from.x()-to.x()),dz=Math.abs(from.z()-to.z());
        if(dx>1||dz>1||dx+dz==0||Math.abs(from.y()-to.y())>1||!standable(t,to))return false;
        if(dx==1&&dz==1) {
            // Diagonal jumps/drops keep the existing cardinal takeoff/landing certificates.
            // Both orthogonal corner cells must be supported and body-clear even when a
            // sampled diagonal would barely miss the corner. Then prove the actual sweep.
            if(from.y()!=to.y()||!standable(t,from)
                    ||!standable(t,new BlockPos(to.x(),from.y(),from.z()))
                    ||!standable(t,new BlockPos(from.x(),from.y(),to.z())))return false;
            return t.walkClear(t.position(from),t.position(to))&&t.stepClear(t.position(from),t.position(to));
        }
        return t.stepClear(t.position(from),t.position(to));
    }
    /** Explicit caller preference. Reserve half for a same-level route, then share the original remaining
     * node budget with ordinary fallback. All terrain proofs and the default find method remain unchanged. */
    public static Result findLevelFirst(Terrain terrain,BlockPos start,BlockPos goal,int maxNodes,int radius) {
        if(start.y()!=goal.y()||maxNodes<2)return find(terrain,start,goal,maxNodes,radius);
        Terrain level=new Terrain(){
            public boolean clear(BlockPos p){return terrain.clear(p);}
            public boolean floor(BlockPos p){return terrain.floor(p);}
            public Vec3 position(BlockPos p){return terrain.position(p);}
            public Vec3 surfacePosition(Vec3 p){return terrain.surfacePosition(p);}
            public boolean standable(BlockPos p){return p.y()==start.y()&&terrain.standable(p);}
            public boolean standingClear(Vec3 p){return terrain.standingClear(p);}
            public boolean walkClear(Vec3 a,Vec3 b){return terrain.walkClear(a,b);}
            public boolean stepClear(Vec3 a,Vec3 b){return terrain.stepClear(a,b);}
        };
        Result flat=find(level,start,goal,Math.max(1,maxNodes/2),radius);
        if(flat.found())return flat;
        int remaining=maxNodes-flat.visited();
        if(remaining<=0)return new Result(List.of(),"Path search budget reached",flat.visited());
        Result fallback=find(terrain,start,goal,remaining,radius);
        return new Result(fallback.steps(),fallback.reason(),flat.visited()+fallback.visited());
    }
    public static Result find(Terrain terrain, BlockPos start, BlockPos goal, int maxNodes, int radius) {
        if (maxNodes < 1 || radius < 1 || !standable(terrain,start) || !standable(terrain,goal))
            return new Result(List.of(), "Start or destination is not walkable",0);
        PriorityQueue<Entry> open = new PriorityQueue<>(Comparator.comparingDouble(Entry::score)
                .thenComparingLong(Entry::order));
        Map<BlockPos,Double> costs = new HashMap<>();
        Map<BlockPos,BlockPos> parents = new HashMap<>();
        long order=0;
        int visited=0;
        costs.put(start,0d);
        open.add(new Entry(start,0,heuristic(start,goal),order++));
        while (!open.isEmpty() && visited < maxNodes) {
            Entry entry = open.remove();
            if (entry.cost() != costs.getOrDefault(entry.pos(),Double.POSITIVE_INFINITY)) continue;
            visited++;
            if (entry.pos().equals(goal)) {
                LinkedList<BlockPos> path = new LinkedList<>();
                for (BlockPos p=goal; !p.equals(start); p=parents.get(p)) path.addFirst(p);
                return new Result(path,"",visited);
            }
            for (int[] dir : DIRECTIONS) for (int dyIndex=0;dyIndex<3;dyIndex++) {
                int dy=dyIndex==0?0:dyIndex==1?1:-1;
                BlockPos next=entry.pos().add(dir[0],dy,dir[1]);
                if (Math.abs(next.x()-start.x()) > radius || Math.abs(next.z()-start.z()) > radius
                        || Math.abs(next.y()-start.y()) > radius || !canStep(terrain,entry.pos(),next)) continue;
                double cost=entry.cost()+Math.hypot(dir[0],dir[1])+Math.abs(dy)*0.5;
                if (cost >= costs.getOrDefault(next,Double.POSITIVE_INFINITY)) continue;
                costs.put(next,cost); parents.put(next,entry.pos());
                open.add(new Entry(next,cost,cost+heuristic(next,goal),order++));
            }
        }
        return new Result(List.of(), visited >= maxNodes ? "Path search budget reached" : "No walkable path",visited);
    }
    private static double heuristic(BlockPos a, BlockPos b) {
        return Math.hypot(a.x()-b.x(),a.z()-b.z());
    }
}
