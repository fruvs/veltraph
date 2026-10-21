package dev.veltraph.client.farming.movement;

import dev.veltraph.client.config.MapConfigService;
import dev.veltraph.client.service.WorldService;
import dev.veltraph.client.testutil.FakePlayerService;
import dev.veltraph.client.world.BlockPos;
import dev.veltraph.client.world.Direction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Crop-aware geometry must validate a normal farmland/crop row — the live no-move bug was caused by the
 * generic {@code isStandable} (full-solid-square) test rejecting farmland.
 */
class FarmGeometryTest {

    /**
     * A flat crop field at feet level 64: floor (y &lt; 64) is farmland — has collision (not passable)
     * but is NOT a full solid square (so {@code isStandable} returns false, like real farmland). The
     * block in front is wheat (non-air, passable). Everything at/above feet level is air.
     */
    private static final class CropFieldWorld implements WorldService {
        @Override public boolean isLoaded(BlockPos pos) { return true; }
        @Override public boolean isPassable(BlockPos pos) { return pos.y() >= 64; }
        @Override public boolean isStandable(BlockPos pos) { return false; } // farmland is not full-square
        @Override public String blockId(BlockPos pos) {
            if (pos.y() < 64) return "minecraft:farmland";
            if (pos.equals(new BlockPos(0, 64, -1))) return "minecraft:wheat"; // crop in front (NORTH)
            return "minecraft:air";
        }
    }

    private FarmGeometry geometry() {
        FakePlayerService player = new FakePlayerService();
        player.blockPos = new BlockPos(0, 64, 0);
        player.position = new dev.veltraph.client.world.Vec3(0.5, 64.0, 0.5);
        FarmGeometry g = new FarmGeometry(new MapConfigService(), player, new CropFieldWorld());
        g.setHeading(Direction.NORTH);
        return g;
    }

    @Test
    void farmAwareFloorAcceptsFarmlandThatIsStandableRejects() {
        WorldService w = new CropFieldWorld();
        BlockPos floor = new BlockPos(0, 63, 0);
        assertFalse(w.isStandable(floor), "farmland is not a full solid square");
        assertTrue(w.isSolidFloor(floor), "but it IS a farm-aware solid floor (the fix)");
    }

    @Test
    void cropRowLanesAreValid() {
        FarmGeometry g = geometry();
        assertTrue(g.canStepForward(), "can step forward along a normal crop row");
        assertTrue(g.canStrafe(TurnDirection.RIGHT), "can strafe across a normal crop row");
        assertTrue(g.canStrafe(TurnDirection.LEFT));
        assertTrue(g.blockInFront(), "the wheat in front is detected as a block to break");
        assertTrue(g.nearMiddle(), "centred in the block");
    }

    @Test
    void movableLateralProbesRightThenLeft() {
        FarmGeometry g = geometry();
        assertTrue(g.movableLateral() == TurnDirection.RIGHT, "right is probed first when both are open");
    }

    @Test
    void targetVecEntersTheAdjacentWheatSelectionShape() {
        FarmGeometry g = geometry();
        // The previous y=65.35 target was above the entire wheat hitbox.
        assertEquals(0.5, g.targetVec().x(), 1e-6);
        assertEquals(64.45, g.targetVec().y(), 1e-6);
        assertEquals(-0.5, g.targetVec().z(), 1e-6);
    }

    @Test
    void decodedRowCoordinatePlacesTheBodyAboveRealFarmlandAndSoulSand() {
        FakePlayerService player = new FakePlayerService();
        FarmGeometry g = new FarmGeometry(new MapConfigService(), player, new CropFieldWorld());
        g.setHeading(Direction.NORTH);
        for (double standingY : new double[]{63.9375, 63.875, 64.0}) {
            player.position = new dev.veltraph.client.world.Vec3(0.5,standingY,0.5);
            player.blockPos = player.position.toBlockPos();
            assertEquals(new BlockPos(0,64,0), g.rowBodyPos());
            assertTrue(g.canStrafe(TurnDirection.RIGHT));
            assertTrue(g.canStepForward());
            assertEquals(64.45, g.targetVec().y(), 1e-6);
        }
    }

    @Test
    void partialSlabHeightIsNotSilentlyPromotedToAFullBlock() {
        FakePlayerService player = new FakePlayerService();
        player.position = new dev.veltraph.client.world.Vec3(0.5,63.5,0.5);
        FarmGeometry g = new FarmGeometry(new MapConfigService(), player, new CropFieldWorld());
        assertEquals(new BlockPos(0,63,0), g.rowBodyPos());
        assertFalse(g.canStepForward(), "the recovered 0.15 offset is not general slab navigation");
    }
}
