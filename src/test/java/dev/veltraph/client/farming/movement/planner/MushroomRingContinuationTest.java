package dev.veltraph.client.farming.movement.planner;

import dev.veltraph.client.service.WorldService;
import dev.veltraph.client.world.*;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static dev.veltraph.client.farming.movement.planner.MushroomRingLaneClassifier.Lane.*;

class MushroomRingContinuationTest {
    private static final BlockPos BASE=new BlockPos(100,70,100);
    private static final Vec3 FEET=new Vec3(100.5,70.5,100.5);
    private final MushroomRingContinuation continuation=new MushroomRingContinuation();
    private static final class World implements WorldService {
        final Map<BlockPos,String> cells=new HashMap<>();boolean loaded=true,clear=true;
        public boolean isLoaded(BlockPos p){return loaded;}
        public String blockId(BlockPos p){return loaded?cells.getOrDefault(p,"minecraft:air"):"";}
        public boolean isPassable(BlockPos p){return blockId(p).equals("minecraft:air");}
        public boolean isStandable(BlockPos p){return blockId(p).equals("minecraft:quartz_slab");}
        public boolean bodyClear(Vec3 p,double w,double h){return loaded&&clear;}
        public boolean hasGroundSupport(Vec3 p,double w){return loaded&&isStandable(p.toBlockPos());}
        void slab(BlockPos p,boolean trap){cells.put(p,"minecraft:quartz_slab");if(trap)cells.put(p.up(),"minecraft:oak_trapdoor");}
    }
    private World corner() {
        var w=new World();w.slab(BASE,false);w.slab(BASE.offset(Direction.WEST),false);w.slab(BASE.offset(Direction.NORTH),true);
        w.cells.put(BASE.offset(Direction.EAST).up(),"minecraft:mycelium");return w;
    }
    @Test void turnUsesActualOutgoingGuideAndIncomingArmRatherThanStillness() {
        var d=continuation.find(corner(),FEET,-26.57f,Direction.EAST);
        assertNotNull(d);assertEquals(TRAPDOOR,d.lane());assertEquals(Direction.NORTH,d.travel());
        assertEquals(MushroomRingContinuation.Phase.CORNER,d.phase());
    }
    @Test void wrongCropQuadrantAndUnknownContextCannotBecomeCornerMovement() {
        var w=corner();assertNull(continuation.find(w,FEET,63.43f,Direction.EAST));
        w.loaded=false;assertNull(continuation.find(w,FEET,-26.57f,Direction.EAST));
        w.loaded=true;w.clear=false;assertNull(continuation.find(w,FEET,-26.57f,Direction.EAST));
    }
    @Test void anIncomingArmAloneDoesNotAuthorizeBacktracking() {
        var w=corner();w.cells.remove(BASE.offset(Direction.NORTH));
        assertNull(continuation.find(w,FEET,-26.57f,Direction.EAST));
    }
    @Test void oneCellGuideGapRequiresForwardSupportAndCropBed() {
        var w=new World();w.slab(BASE,false);w.slab(BASE.offset(Direction.NORTH),true);w.slab(BASE.offset(Direction.SOUTH),true);
        w.cells.put(BASE.offset(Direction.EAST).up(),"minecraft:mycelium");
        var d=continuation.find(w,FEET,-26.57f,Direction.NORTH);assertNotNull(d);
        assertEquals(TRAPDOOR,d.lane());assertEquals(MushroomRingContinuation.Phase.GUIDE_GAP,d.phase());
    }
    @Test void anIntersectionWithNoIncomingRoleRemainsAmbiguous() {
        var w=corner();w.slab(BASE.offset(Direction.SOUTH),false);
        assertNull(continuation.find(w,FEET,-26.57f,null));
    }
    @Test void landingSelectsLoadedSingleContinuationWithAuthorInput() {
        var w=new World();w.slab(BASE,false);w.slab(BASE.offset(Direction.EAST),false);
        w.cells.put(BASE.offset(Direction.SOUTH).up(),"minecraft:mycelium");
        var d=continuation.find(w,FEET,-26.57f,Direction.SOUTH);assertNotNull(d);
        assertEquals(NON_TRAPDOOR,d.lane());assertEquals(Direction.EAST,d.travel());
        assertEquals(MushroomRingContinuation.Phase.LANDING,d.phase());
    }
}
