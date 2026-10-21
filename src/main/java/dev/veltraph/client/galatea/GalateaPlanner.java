package dev.veltraph.client.galatea;

import dev.veltraph.client.world.BlockPos;
import dev.veltraph.client.world.Vec3;
import java.util.*;
import java.util.function.Predicate;

/** Whole-tree geometry and recoverable scoring; never invents the missing native atlas. */
public final class GalateaPlanner {
    public enum Kind { FIG, MANGROVE, MATERIALS }
    public enum AxeMode { STEM, JUMPING, SCAFFOLD, THROWING }
    public enum EtherwarpUsage { FULL, MEDIUM, LOW, NONE }
    public enum ClickType { LEFT, RIGHT }
    public record Bounds(BlockPos min, BlockPos max) {
        public boolean contains(BlockPos p) {
            return p.x()>=min.x()&&p.x()<=max.x()&&p.y()>=min.y()&&p.y()<=max.y()&&p.z()>=min.z()&&p.z()<=max.z();
        }
        public Vec3 center() { return new Vec3(((double)min.x()+max.x())/2,((double)min.y()+max.y())/2,((double)min.z()+max.z())/2); }
        public static Optional<Bounds> parse(String value) {
            if(value==null||value.isBlank()) return Optional.empty();
            String[] a=value.strip().split("[,\\s]+");
            if(a.length!=6) return Optional.empty();
            try {
                int[] n=Arrays.stream(a).mapToInt(Integer::parseInt).toArray();
                if(n[0]>n[3]||n[1]>n[4]||n[2]>n[5]) return Optional.empty();
                for(int i:new int[]{0,2,3,5}) if(Math.abs((long)n[i])>30_000_000) return Optional.empty();
                if(n[1]<-2048||n[4]>2048) return Optional.empty();
                return Optional.of(new Bounds(new BlockPos(n[0],n[1],n[2]),new BlockPos(n[3],n[4],n[5])));
            } catch(NumberFormatException ex) { return Optional.empty(); }
        }
    }
    public record Tree(BlockPos root,List<BlockPos> logs,int region,double intrinsicCost,boolean occupied) {
        public Tree { logs=List.copyOf(logs); if(logs.isEmpty()||!logs.contains(root)) throw new IllegalArgumentException("Tree needs its root"); }
        public int height() { return logs.stream().mapToInt(BlockPos::y).max().orElse(root.y())-root.y()+1; }
    }
    private GalateaPlanner() {}
    /** Fig compatibility entry point. Score: distance + abs(yaw delta)/10 + intrinsic cost,
     * +30 region transition except FIVE, +200 and preferred-center horizontal distance*2
     * outside preferred region. Random terms omitted; other families declare their kind. */
    public static double score(Tree tree,Vec3 player,float yaw,Tree previous,Bounds preferred,boolean preferSmall) {
        return score(Kind.FIG,tree,player,yaw,previous,preferred,preferSmall);
    }
    public static double score(Kind kind,Tree tree,Vec3 player,float yaw,Tree previous,Bounds preferred,boolean preferSmall) {
        if(!eligible(kind,tree,preferSmall)) return Double.POSITIVE_INFINITY;
        double dx=tree.root().x()+.5-player.x(),dy=tree.root().y()+.5-player.y(),dz=tree.root().z()+.5-player.z();
        double angle=Math.toDegrees(Math.atan2(-dx,dz))-yaw;
        angle=((angle+180)%360+360)%360-180;
        double cost=Math.sqrt(dx*dx+dy*dy+dz*dz)+Math.abs(angle)/10+tree.intrinsicCost();
        if(previous!=null&&previous.region()!=tree.region()&&tree.region()!=5) cost+=30;
        if(preferred!=null&&!preferred.contains(tree.root())) {
            Vec3 c=preferred.center();cost+=200+Math.hypot(c.x()-player.x(),c.z()-player.z())*2;
        }
        return cost;
    }
    /** Source Fig descriptor tests its current log set, not its distinct branch list. At local
     * admission this is the complete loaded component, not the later session remaining counter. */
    public static boolean eligible(Kind kind,Tree tree,boolean preferSmall) {
        return kind!=Kind.FIG||!preferSmall||tree.logs().size()<200;
    }
    /** 26-neighbour logs include diagonal branches. Rejects truncated components instead of
     * pretending the 256th log completes a larger tree. Scan boundary supplied by the adapter. */
    public static Optional<Tree> component(BlockPos seed,Predicate<BlockPos> log,Predicate<BlockPos> allowed,int limit) {
        if(limit<1||!allowed.test(seed)||!log.test(seed)) return Optional.empty();
        LinkedHashSet<BlockPos> found=new LinkedHashSet<>();ArrayDeque<BlockPos> open=new ArrayDeque<>();
        found.add(seed);open.add(seed);
        while(!open.isEmpty()) {
            BlockPos p=open.removeFirst();
            for(int x=-1;x<=1;x++) for(int y=-1;y<=1;y++) for(int z=-1;z<=1;z++) {
                if(x==0&&y==0&&z==0) continue;
                BlockPos n=p.add(x,y,z);
                if(found.contains(n)||!log.test(n)) continue;
                if(!allowed.test(n)) return Optional.empty();
                if(found.size()>=limit) return Optional.empty();
                found.add(n);open.add(n);
            }
        }
        BlockPos root=found.stream().min(Comparator.comparingInt(BlockPos::y).thenComparingInt(BlockPos::x).thenComparingInt(BlockPos::z)).orElseThrow();
        return Optional.of(new Tree(root,new ArrayList<>(found),0,0,false));
    }
    public static AxeMode nextMode(AxeMode mode,boolean jump,boolean scaffold,boolean skipThrow) {
        return switch(mode) {
            case STEM -> jump?AxeMode.JUMPING:scaffold?AxeMode.SCAFFOLD:skipThrow?null:AxeMode.THROWING;
            case JUMPING -> scaffold?AxeMode.SCAFFOLD:skipThrow?null:AxeMode.THROWING;
            case SCAFFOLD -> skipThrow?null:AxeMode.THROWING;
            case THROWING -> null;
        };
    }
}
