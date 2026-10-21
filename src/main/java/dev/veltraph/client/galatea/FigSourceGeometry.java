package dev.veltraph.client.galatea;

import dev.veltraph.client.world.BlockPos;
import dev.veltraph.client.world.Vec3;
import java.util.*;

/** Descriptor algorithms with an explicitly supplied observed base and source world probes. */
public final class FigSourceGeometry {
    public interface World {
        boolean passable(BlockPos p);
        boolean log(BlockPos p);
        boolean leaves(BlockPos p);
        Object blockType(BlockPos p);
        boolean standing(BlockPos p);
        boolean dryStanding(BlockPos p);
        Vec3 standingEye(Vec3 p);
        boolean sourceRay(Vec3 eye, BlockPos candidate, boolean leavesAllowed);
    }
    private final BlockPos base;
    private final List<BlockPos> all, stem, jump, scaffold, pads;
    private final Map<BlockPos,Integer> leafCosts;

    public FigSourceGeometry(BlockPos base, List<BlockPos> descriptorBlocks, World world,
                             int jumpHeight, int observedScaffoldHeight) {
        this.base=Objects.requireNonNull(base);
        int minX=base.x(),maxX=base.x(),minZ=base.z(),maxZ=base.z();
        for(int x=-5;x<6;x++) for(int z=-5;z<6;z++) {
            BlockPos p=base.add(x,0,z);
            if(world.log(p)) {
                minX=Math.min(minX,p.x());maxX=Math.max(maxX,p.x());
                minZ=Math.min(minZ,p.z());maxZ=Math.max(maxZ,p.z());
            }
        }
        List<BlockPos> pads=new ArrayList<>();
        for(int x=minX-3;x<=maxX+3;x++) for(int y=base.y()-1;y<=base.y();y++)
            for(int z=minZ-3;z<=maxZ+3;z++) {
                BlockPos p=new BlockPos(x,y,z);
                if((world.standing(p)&&!world.log(p.down()))
                        ||(world.log(p)&&!world.passable(p.down())&&!world.log(p.down()))) pads.add(p);
            }
        Map<BlockPos,Integer> costs=new LinkedHashMap<>();
        for(BlockPos p:descriptorBlocks) {
            int count=0;
            for(int x=-1;x<2;x++) for(int y=-1;y<2;y++) for(int z=-1;z<2;z++)
                if(world.leaves(p.add(x,y,z)))count++;
            costs.put(p,count);
        }
        leafCosts=Map.copyOf(costs);
        this.pads=List.copyOf(pads);
        all=descriptorBlocks.stream().filter(p->!world.passable(p)&&p.y()>base.y()-2).toList();
        List<BlockPos> s=new ArrayList<>(),j=new ArrayList<>(),c=new ArrayList<>();
        Vec3 centered=new Vec3(base.x()+.5,base.y()+.5,base.z()+.5);
        for(BlockPos p:all) {
            boolean reachable=false;
            for(BlockPos pad:pads) {
                if(p.x()==pad.x()&&p.z()==pad.z()||!world.dryStanding(pad))continue;
                Vec3 eye=new Vec3(pad.x()+.5,pad.y()+1.62,pad.z()+.5);
                if(eye.distanceTo(center(p))<4&&world.sourceRay(eye,p,true)) {reachable=true;break;}
            }
            if(reachable)s.add(p);
            else if(offsetReachable(world,centered,p,jumpHeight))j.add(p);
            else if(offsetReachable(world,centered,p,observedScaffoldHeight))c.add(p);
        }
        stem=List.copyOf(s);jump=List.copyOf(j);scaffold=List.copyOf(c);
    }

    public BlockPos base(){return base;}
    public List<BlockPos> pads(){return pads;}
    public int leafCost(BlockPos p){return leafCosts.getOrDefault(p,0);}
    public List<BlockPos> candidates(GalateaPlanner.AxeMode mode, Set<BlockPos> excluded) {
        List<BlockPos> list=new ArrayList<>();
        if(mode==GalateaPlanner.AxeMode.THROWING)list.addAll(all);
        else {
            list.addAll(stem);
            if(mode!=GalateaPlanner.AxeMode.STEM)list.addAll(jump);
            if(mode==GalateaPlanner.AxeMode.SCAFFOLD)list.addAll(scaffold);
        }
        list.removeAll(excluded);return List.copyOf(list);
    }
    public static Vec3 center(BlockPos p){return new Vec3(p.x()+.5,p.y()+.5,p.z()+.5);}
    private static boolean offsetReachable(World w,Vec3 base,BlockPos p,int height) {
        for(int x=-1;x<2;x++)for(int z=-1;z<2;z++)
            if(raisedReachable(w,new Vec3(base.x()+x,base.y(),base.z()+z),p,height))return true;
        return false;
    }
    public static boolean raisedReachable(World w,Vec3 supplied,BlockPos p,int maxHeight) {
        Vec3 eye=w.standingEye(supplied);
        for(int h=0;h<=maxHeight;h++) {
            Vec3 raised=new Vec3(eye.x(),eye.y()+h,eye.z());
            if(raised.distanceTo(center(p))<4&&w.sourceRay(raised,p,false))return true;
        }
        return false;
    }
    /** Uses actual native jumpPower(1f); original truncates the maximum, without a guessed cap. */
    public static int jumpHeight(float nativeJumpPower) {
        double total=0,max=0;
        for(double velocity=nativeJumpPower;velocity>0;velocity*=.98) {
            total+=velocity;velocity-=.08;if(total>max)max=total;
        }
        return (int)max;
    }
    /** Source builds this map only below 100 remaining entries; same exact block, six faces, 75 cap. */
    public static Map<BlockPos,Integer> throwingCounts(List<BlockPos> remaining,World w) {
        if(remaining.size()>=100)return Map.of();
        Set<BlockPos> pending=new LinkedHashSet<>(remaining);Map<BlockPos,Integer> counts=new HashMap<>();
        int[][] faces={{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};
        while(!pending.isEmpty()) {
            BlockPos seed=pending.iterator().next();
            if(!w.log(seed))throw new IllegalArgumentException("Source throwing seed must be a native log");
            Object type=w.blockType(seed);Set<BlockPos> seen=new HashSet<>();List<BlockPos> group=new ArrayList<>();
            ArrayDeque<BlockPos> open=new ArrayDeque<>();seen.add(seed);open.add(seed);
            while(!open.isEmpty()&&group.size()<75) {
                BlockPos p=open.removeFirst();if(!Objects.equals(type,w.blockType(p)))continue;
                group.add(p);
                for(int[] d:faces){BlockPos next=p.add(d[0],d[1],d[2]);if(seen.add(next))open.addLast(next);}
            }
            for(BlockPos p:group){counts.put(p,group.size());pending.remove(p);}
        }
        return Map.copyOf(counts);
    }
}
