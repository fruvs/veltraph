package dev.veltraph.client.farming.movement;

import dev.veltraph.client.config.MapConfigService;
import dev.veltraph.client.service.WorldService;
import dev.veltraph.client.testutil.FakePlayerService;
import dev.veltraph.client.world.BlockPos;
import dev.veltraph.client.world.Direction;
import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class FarmGeometryHarvestTest {
    private static final class Field implements WorldService {
        final Map<BlockPos,String> blocks = new HashMap<>();
        final Set<BlockPos> unloaded = new HashSet<>();
        public boolean isLoaded(BlockPos p) { return !unloaded.contains(p); }
        public boolean isPassable(BlockPos p) { return p.y() >= 64 && !blockId(p).equals("minecraft:stone"); }
        public boolean isStandable(BlockPos p) { return false; }
        public String blockId(BlockPos p) { return blocks.getOrDefault(p, p.y()<64 ? "minecraft:farmland" : "minecraft:air"); }
    }

    private FarmGeometry geometry(Field field) {
        var geometry = new FarmGeometry(new MapConfigService(), new FakePlayerService(), field);
        geometry.setHeading(Direction.NORTH);
        return geometry;
    }

    @Test void nearestCropIsTargetedBeforeTheFartherCropAndHarvestedGapCanBeTraversed() {
        Field field = new Field();
        BlockPos near = new BlockPos(0,64,-1), far = new BlockPos(0,64,-2);
        field.blocks.put(near,"minecraft:wheat"); field.blocks.put(far,"minecraft:carrots");
        var g = geometry(field);
        assertEquals(64.45,g.targetVec().y(),1e-6);
        assertEquals(-0.5,g.targetVec().z(),1e-6);
        field.blocks.remove(near);
        assertEquals(64.35,g.targetVec().y(),1e-6);
        assertEquals(-1.5,g.targetVec().z(),1e-6);
        assertTrue(g.canStepForward(),"crop removal does not strand traversal");
    }

    @Test void unloadedSpaceAndPhysicalWallsCannotSelectAHiddenCrop() {
        Field field = new Field();
        BlockPos near = new BlockPos(0,64,-1);
        field.blocks.put(new BlockPos(0,64,-2),"minecraft:wheat");
        field.unloaded.add(near);
        assertEquals(65.35,geometry(field).targetVec().y(),1e-6,"no hidden crop target through unloaded space");
        field.unloaded.clear(); field.blocks.put(near,"minecraft:stone");
        assertEquals(65.35,geometry(field).targetVec().y(),1e-6,"no hidden crop target through a wall");
    }

    @Test void unknownHeadAndSupportBlocksCannotBeUsedForMovement() {
        Field field = new Field();
        field.unloaded.add(new BlockPos(0,65,-1));
        assertFalse(geometry(field).canStepForward());
        field.unloaded.clear(); field.unloaded.add(new BlockPos(0,63,-1));
        assertFalse(geometry(field).canStepForward());
    }
}
