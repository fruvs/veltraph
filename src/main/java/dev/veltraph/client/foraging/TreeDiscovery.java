package dev.veltraph.client.foraging;

import dev.veltraph.client.world.BlockPos;
import java.util.*;

/** Bounded connected-log discovery. Leaves and decorations never become break targets. */
public final class TreeDiscovery {
    public record Tree(BlockPos trunk,String species,List<BlockPos> logs) {
        public Tree { logs=List.copyOf(logs); }
    }
    private TreeDiscovery() {}
    public static List<Tree> discover(Map<BlockPos,String> sampledLogs,int maximumLogs) {
        Set<BlockPos> unseen=new HashSet<>(sampledLogs.keySet());
        List<Tree> result=new ArrayList<>();
        Comparator<BlockPos> order=Comparator.comparingInt(BlockPos::y).thenComparingInt(BlockPos::x).thenComparingInt(BlockPos::z);
        for(BlockPos root:sampledLogs.keySet().stream().sorted(order).toList()) {
            if(!unseen.remove(root)) continue;
            String species=ForagingRegion.speciesOf(sampledLogs.get(root));
            if(species.isEmpty()) continue;
            ArrayDeque<BlockPos> queue=new ArrayDeque<>();queue.add(root);
            List<BlockPos> logs=new ArrayList<>();
            // 26-neighbor logs support diagonal branches. Species boundaries remain separate trees.
            while(!queue.isEmpty()) {
                BlockPos p=queue.remove();logs.add(p);
                for(int x=-1;x<=1;x++) for(int y=-1;y<=1;y++) for(int z=-1;z<=1;z++) {
                    if(x==0&&y==0&&z==0) continue;
                    BlockPos next=p.add(x,y,z);
                    if(unseen.contains(next)&&species.equals(ForagingRegion.speciesOf(sampledLogs.get(next)))) {
                        unseen.remove(next);queue.add(next);
                    }
                }
            }
            // Oversized components are rejected as a whole, rather than split into artificial trees.
            if(logs.size()<=Math.max(1,maximumLogs)) result.add(new Tree(root,species,logs.stream().sorted(order).toList()));
        }
        return List.copyOf(result);
    }
}
