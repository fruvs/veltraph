package dev.veltraph.client.rewarp;

import dev.veltraph.client.game.GameArea;
import dev.veltraph.client.world.BlockPos;
import dev.veltraph.client.world.Vec3;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Selection policy (nearest-then-last), triggerReturn pending state, and the HUD read API. */
class RewarpServiceTest {

    private static RewarpPoint at(int x, int z) {
        return new RewarpPoint("minecraft:overworld", GameArea.GARDEN, new BlockPos(x, 64, z), 0f, 0f, 1000L);
    }

    @Test
    void selectTargetEmptyWhenNoPoints() {
        RewarpService s = new RewarpService();
        assertTrue(s.selectTarget("minecraft:overworld", new Vec3(0, 64, 0)).isEmpty());
    }

    @Test
    void selectTargetPrefersNearestSameDimension() {
        RewarpService s = new RewarpService();
        s.store().add(at(0, 0));
        s.store().add(at(50, 50));
        Optional<RewarpPoint> t = s.selectTarget("minecraft:overworld", new Vec3(48, 64, 48));
        assertEquals(50, t.orElseThrow().pos().x());
    }

    @Test
    void selectTargetFallsBackToLastWhenNoPositionContext() {
        RewarpService s = new RewarpService();
        s.store().add(at(0, 0));
        s.store().add(at(99, 99));
        Optional<RewarpPoint> t = s.selectTarget(null, null);
        assertEquals(99, t.orElseThrow().pos().x());
    }

    @Test
    void selectTargetFallsBackToLastWhenNoSameDimensionPoint() {
        RewarpService s = new RewarpService();
        s.store().add(new RewarpPoint("minecraft:the_nether", GameArea.GARDEN, new BlockPos(5, 64, 5), 0f, 0f, 1L));
        Optional<RewarpPoint> t = s.selectTarget("minecraft:overworld", new Vec3(0, 64, 0));
        assertTrue(t.isPresent());
        assertEquals(5, t.orElseThrow().pos().x());
    }

    @Test
    void triggerReturnSetsPendingState() {
        RewarpService s = new RewarpService();
        s.store().add(at(10, 10));
        assertFalse(s.isReturnPending());
        Optional<RewarpPoint> target = s.triggerReturn("minecraft:overworld", new Vec3(0, 64, 0));
        assertTrue(target.isPresent());
        assertTrue(s.isReturnPending());
        assertEquals(10, s.pendingTarget().orElseThrow().pos().x());
        s.clearPending();
        assertFalse(s.isReturnPending());
        assertTrue(s.pendingTarget().isEmpty());
    }

    @Test
    void triggerReturnWithNoPointsDoesNotSetPending() {
        RewarpService s = new RewarpService();
        Optional<RewarpPoint> target = s.triggerReturn("minecraft:overworld", new Vec3(0, 64, 0));
        assertTrue(target.isEmpty());
        assertFalse(s.isReturnPending());
    }

    @Test
    void readApiReflectsStore() {
        RewarpService s = new RewarpService();
        assertEquals(0, s.savedCount());
        assertFalse(s.hasPoints());
        assertTrue(s.lastPoint().isEmpty());
        s.store().add(at(1, 1));
        s.store().add(at(2, 2));
        assertEquals(2, s.savedCount());
        assertTrue(s.hasPoints());
        assertEquals(2, s.lastPoint().orElseThrow().pos().x());
    }
}
