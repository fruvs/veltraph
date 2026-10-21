package dev.veltraph.client.farming.movement.planner;

import dev.veltraph.client.service.WorldService;
import dev.veltraph.client.world.BlockPos;
import dev.veltraph.client.world.Vec3;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static dev.veltraph.client.farming.movement.planner.MushroomRingLaneClassifier.Lane.*;

class MushroomRingLaneClassifierTest {
    private final MushroomRingLaneClassifier classifier=new MushroomRingLaneClassifier();
    private static final class World implements WorldService {
        final Map<BlockPos,String> blocks=new HashMap<>();BlockPos missing;boolean clear=true,support=true;
        public boolean isLoaded(BlockPos p){return !p.equals(missing);}
        public String blockId(BlockPos p){return isLoaded(p)?blocks.getOrDefault(p,"minecraft:air"):"";}
        public boolean isPassable(BlockPos p){return blockId(p).equals("minecraft:air")||blockId(p).equals("minecraft:oak_trapdoor");}
        public boolean isStandable(BlockPos p){return blockId(p).equals("minecraft:quartz_slab");}
        public boolean bodyClear(Vec3 p,double w,double h){return clear;}
        public boolean hasGroundSupport(Vec3 p,double w){return support;}
        void lane(int x,int y,int z,boolean axisX,boolean trapdoor) {
            for(int i=-2;i<=2;i++) {
                int xx=x+(axisX?i:0),zz=z+(axisX?0:i);
                blocks.put(new BlockPos(xx,y,zz),"minecraft:quartz_slab");
                if(trapdoor)blocks.put(new BlockPos(xx,y+1,zz),"minecraft:oak_trapdoor");
                blocks.put(new BlockPos(xx+(axisX?0:1),y+1,zz+(axisX?1:0)),"minecraft:mycelium");
                blocks.put(new BlockPos(xx-(axisX?0:1),y+1,zz-(axisX?1:0)),"minecraft:white_stained_glass");
            }
        }
    }
    @Test void classifiesBothAxesAndTranslatedLayersWithoutAWorldCoordinateScript() {
        for(boolean axisX:new boolean[]{false,true})for(boolean trapdoor:new boolean[]{false,true}) {
            var w=new World();w.lane(-130,74,87,axisX,trapdoor);
            assertEquals(trapdoor?TRAPDOOR:NON_TRAPDOOR,classifier.classify(w,new Vec3(-129.5,74.5,87.5)));
        }
    }
    @Test void mixedGuideAtCornerFailsClosed() {
        var w=new World();w.lane(0,0,0,true,true);w.blocks.remove(new BlockPos(1,1,0));
        assertEquals(AMBIGUOUS,classifier.classify(w,new Vec3(.5,.5,.5)));
    }
    @Test void absentLaneSideBlockersOrIncompleteSlabPairAreAmbiguous() {
        var w=new World();w.lane(0,0,0,false,false);w.blocks.remove(new BlockPos(1,1,0));
        assertEquals(AMBIGUOUS,classifier.classify(w,new Vec3(.5,.5,.5)));
        w.lane(0,0,0,false,false);w.blocks.remove(new BlockPos(0,0,1));
        assertEquals(AMBIGUOUS,classifier.classify(w,new Vec3(.5,.5,.5)));
    }
    @Test void anyUnknownLocalFootprintFailsClosed() {
        var w=new World();w.lane(0,0,0,true,false);w.missing=new BlockPos(-1,2,1);
        assertEquals(UNKNOWN,classifier.classify(w,new Vec3(.5,.5,.5)));
    }
    @Test void liveBodyAndSupportAreRequiredEvenWhenBlockIdsLookCorrect() {
        var w=new World();w.lane(0,0,0,true,true);w.clear=false;
        assertEquals(AMBIGUOUS,classifier.classify(w,new Vec3(.5,.5,.5)));
        w.clear=true;w.support=false;assertEquals(AMBIGUOUS,classifier.classify(w,new Vec3(.5,.5,.5)));
    }
    @Test void stairsAndOtherFloorHeightsAreNotInferredAsStraightLanes() {
        var w=new World();w.lane(0,0,0,true,false);
        assertEquals(AMBIGUOUS,classifier.classify(w,new Vec3(.5,1,.5)));
        w.blocks.put(new BlockPos(0,0,0),"minecraft:quartz_stairs");
        assertEquals(AMBIGUOUS,classifier.classify(w,new Vec3(.5,.5,.5)));
    }
    @Test void fixedViewMustFacePersistentBedRatherThanGlassOrVoid() {
        var w=new World();w.lane(0,0,0,true,false);var feet=new Vec3(.5,.5,.5);
        assertEquals(MushroomRingLaneClassifier.CropFacing.READY,classifier.cropFacing(w,feet,63.43f));
        assertEquals(MushroomRingLaneClassifier.CropFacing.WRONG_SIDE,classifier.cropFacing(w,feet,-116.57f));
        // The bed has no mushrooms in this fixture: prior harvesting does not make it a void.
        w.missing=new BlockPos(0,1,1);
        assertEquals(MushroomRingLaneClassifier.CropFacing.UNKNOWN,classifier.cropFacing(w,feet,63.43f));
    }
}
