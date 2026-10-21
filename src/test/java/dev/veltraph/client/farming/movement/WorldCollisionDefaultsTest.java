package dev.veltraph.client.farming.movement;

import dev.veltraph.client.service.WorldService;
import dev.veltraph.client.world.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Pure adapters fail closed; fractional-shape permissiveness belongs to the live shape adapter. */
class WorldCollisionDefaultsTest {
    @Test void defaultClearanceRejectsUnknownAndOccupiedFootprints() {
        var world=new World();var p=new Vec3(.5,64,.5);assertTrue(world.bodyClear(p,.3,1.8));
        world.solid.add(new BlockPos(0,65,0));assertFalse(world.bodyClear(p,.3,1.8));
        world.solid.clear();world.loaded=false;assertFalse(world.bodyClear(p,.3,1.8));
    }
    @Test void supportRequiresLoadedFootprintAndActualBelowCell() {
        var world=new World();var p=new Vec3(.5,64,.5);assertFalse(world.hasGroundSupport(p,.3));
        world.solid.add(new BlockPos(0,63,0));assertTrue(world.hasGroundSupport(p,.3));
        assertFalse(world.hasGroundSupport(new Vec3(.5,64.5,.5),.3),"unknown fractional shapes cannot manufacture contact");
        world.loaded=false;assertFalse(world.hasGroundSupport(p,.3));
    }
    @Test void malformedVolumesAreRejected() {
        var world=new World();var p=new Vec3(.5,64,.5);
        assertFalse(world.bodyClear(null,.3,1.8));assertFalse(world.bodyClear(p,0,1.8));assertFalse(world.bodyClear(p,3,1.8));
        assertFalse(world.bodyClear(p,.3,5));assertFalse(world.bodyClear(new Vec3(Double.NaN,64,0),.3,1.8));
        assertFalse(world.hasGroundSupport(p,Double.POSITIVE_INFINITY));
    }
    private static final class World implements WorldService {
        boolean loaded=true;final Set<BlockPos> solid=new HashSet<>();
        public boolean isLoaded(BlockPos p){return loaded;}
        public boolean isPassable(BlockPos p){return !solid.contains(p);}
        public boolean isStandable(BlockPos p){return solid.contains(p);}
        public String blockId(BlockPos p){return "";}
    }
}
