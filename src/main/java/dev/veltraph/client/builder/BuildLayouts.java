package dev.veltraph.client.builder;

import dev.veltraph.client.world.BlockPos;
import java.util.*;

/** Exact recoverable subsets; source labels describe provenance, never an unrelated public design. */
public final class BuildLayouts {
    private BuildLayouts() {}
    public static final String SOURCE_95_CLASS="lIllIlIIIIIlllllIllIIlIIIlIlIllIIlIIIIllllIIIllIlllIIIlIlIIlIIll";
    public static final String SOURCE_95_ENDCAPS="Veltraph generated 95x287 endcaps (38 placements; partial builder)";
    public static final int SOURCE_WIDTH=95,SOURCE_HEIGHT=1,SOURCE_DEPTH=287;
    public record Region(BlockPos first,BlockPos second) {
        public boolean contains(BlockPos p){
            return p.x()>=Math.min(first.x(),second.x())&&p.x()<=Math.max(first.x(),second.x())
                    &&p.y()>=Math.min(first.y(),second.y())&&p.y()<=Math.max(first.y(),second.y())
                    &&p.z()>=Math.min(first.z(),second.z())&&p.z()<=Math.max(first.z(),second.z());
        }
    }
    /** Direct PLACE subprocesses, in original group order; material is BUILDING_BLOCK_2 parameter. */
    public static BuildLayout recovered95Endcaps(String buildingBlock) {
        var entries=new ArrayList<BuildLayout.Entry>();
        for(int z:new int[]{0,287})for(int x=4;x<96;x+=5)
            entries.add(new BuildLayout.Entry(BuildLayout.Operation.PLACE,new BlockPos(x,0,z),buildingBlock));
        return new BuildLayout(SOURCE_95_ENDCAPS,entries);
    }
    /** Exact reported work rectangles. Metadata alone does not authorize fabricating placement fills. */
    public static List<Region> recovered95WorkRegions() {
        var regions=new ArrayList<Region>();
        regions.add(new Region(new BlockPos(0,0,287),new BlockPos(95,0,287)));
        regions.add(new Region(new BlockPos(0,0,0),new BlockPos(95,0,0)));
        regions.add(new Region(new BlockPos(95,0,286),new BlockPos(95,0,1)));
        for(int i=0;i<19;i++){
            int x=93-i*5;
            regions.add(new Region(new BlockPos(x,0,i%2==0?286:285),
                    new BlockPos(x-3,0,i%2==0&&i!=18?2:1)));
        }
        return List.copyOf(regions);
    }
}
