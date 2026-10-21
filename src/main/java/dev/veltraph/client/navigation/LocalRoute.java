package dev.veltraph.client.navigation;
import dev.veltraph.client.game.GameArea;
import dev.veltraph.client.world.Vec3;
import java.util.List;
import dev.veltraph.client.world.BlockPos;
import dev.veltraph.client.world.Direction;

/** Local replacement schema; original GhostRouteLoader's complete file layout was not recovered. */
public record LocalRoute(GameArea area,String dimension,boolean loop,List<Node> nodes) {
    public enum Kind { WALK, FLY, ETHERWARP }
    public record Node(Kind kind,Vec3 position,float yaw,float pitch,BlockPos target,Direction face) {
        public Node(Vec3 position,float yaw,float pitch) { this(Kind.WALK,position,yaw,pitch,null,null); }
        public Node(Kind kind,Vec3 position,float yaw,float pitch) { this(kind,position,yaw,pitch,null,null); }
        public Node {
            if(kind==null) throw new IllegalArgumentException("Checkpoint type required");
            if(kind==Kind.ETHERWARP && (target==null||face==null)) throw new IllegalArgumentException("Etherwarp requires an explicit target block and face");
            if(kind!=Kind.ETHERWARP && (target!=null||face!=null)) throw new IllegalArgumentException("Only etherwarp checkpoints have target blocks");
            if(position==null||!Double.isFinite(position.x())||!Double.isFinite(position.y())||!Double.isFinite(position.z())
                    ||Math.abs(position.x())>30_000_000||Math.abs(position.z())>30_000_000||Math.abs(position.y())>2048
                    ||!Float.isFinite(yaw)||!Float.isFinite(pitch)||Math.abs(pitch)>90) throw new IllegalArgumentException("Invalid route coordinates or view angle");
        }
    }
    public LocalRoute {
        if(area==null||!area.isSkyBlock()||dimension==null||!dimension.matches("[a-z0-9_.-]+:[a-z0-9_/.-]+"))
            throw new IllegalArgumentException("Route requires a known SkyBlock area and dimension");
        nodes=List.copyOf(nodes);
        if(nodes.isEmpty()||nodes.size()>4096) throw new IllegalArgumentException("Route must contain 1-4096 checkpoints");
    }
}
