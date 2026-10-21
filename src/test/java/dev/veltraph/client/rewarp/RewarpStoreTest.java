package dev.veltraph.client.rewarp;

import dev.veltraph.client.game.GameArea;
import dev.veltraph.client.world.BlockPos;
import dev.veltraph.client.world.Vec3;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** add / clear / replace / replaceLast / get / list / size / nearest on the pure store. */
class RewarpStoreTest {

    private static RewarpPoint point(int x, int y, int z) {
        return new RewarpPoint("minecraft:overworld", GameArea.GARDEN, new BlockPos(x, y, z), 0f, 0f, 1000L);
    }

    @Test
    void addAndSizeAndList() {
        RewarpStore store = new RewarpStore();
        assertEquals(0, store.size());
        assertTrue(store.isEmpty());
        store.add(point(1, 64, 1));
        store.add(point(2, 64, 2));
        assertEquals(2, store.size());
        assertFalse(store.isEmpty());
        assertEquals(2, store.list().size());
        assertEquals(1, store.list().get(0).pos().x());
        assertEquals(2, store.list().get(1).pos().x());
    }

    @Test
    void listIsImmutableSnapshot() {
        RewarpStore store = new RewarpStore();
        store.add(point(1, 64, 1));
        assertThrows(UnsupportedOperationException.class, () -> store.list().add(point(9, 9, 9)));
    }

    @Test
    void clearRemovesAll() {
        RewarpStore store = new RewarpStore();
        store.add(point(1, 64, 1));
        store.add(point(2, 64, 2));
        store.clear();
        assertEquals(0, store.size());
        assertTrue(store.isEmpty());
        assertTrue(store.last().isEmpty());
    }

    @Test
    void replaceAtIndex() {
        RewarpStore store = new RewarpStore();
        store.add(point(1, 64, 1));
        store.add(point(2, 64, 2));
        store.replace(0, point(7, 70, 7));
        assertEquals(7, store.get(0).orElseThrow().pos().x());
        assertEquals(2, store.get(1).orElseThrow().pos().x());
        assertEquals(2, store.size());
    }

    @Test
    void replaceLastReplacesMostRecent() {
        RewarpStore store = new RewarpStore();
        store.add(point(1, 64, 1));
        store.add(point(2, 64, 2));
        store.replaceLast(point(9, 90, 9));
        assertEquals(1, store.get(0).orElseThrow().pos().x());
        assertEquals(9, store.get(1).orElseThrow().pos().x());
        assertEquals(9, store.last().orElseThrow().pos().x());
    }

    @Test
    void replaceOutOfRangeThrows() {
        RewarpStore store = new RewarpStore();
        store.add(point(1, 64, 1));
        assertThrows(IndexOutOfBoundsException.class, () -> store.replace(5, point(0, 0, 0)));
        assertThrows(IndexOutOfBoundsException.class, () -> store.replace(-1, point(0, 0, 0)));
    }

    @Test
    void replaceLastOnEmptyThrows() {
        RewarpStore store = new RewarpStore();
        assertThrows(IllegalStateException.class, () -> store.replaceLast(point(0, 0, 0)));
    }

    @Test
    void getOutOfRangeReturnsEmpty() {
        RewarpStore store = new RewarpStore();
        assertTrue(store.get(0).isEmpty());
        store.add(point(1, 64, 1));
        assertTrue(store.get(1).isEmpty());
        assertTrue(store.get(-1).isEmpty());
        assertTrue(store.get(0).isPresent());
    }

    @Test
    void addNullThrows() {
        RewarpStore store = new RewarpStore();
        assertThrows(IllegalArgumentException.class, () -> store.add(null));
    }

    @Test
    void nearestPicksClosestSameDimension() {
        RewarpStore store = new RewarpStore();
        store.add(point(0, 64, 0));
        store.add(point(100, 64, 100));
        store.add(point(10, 64, 10));
        Optional<RewarpPoint> near = store.nearest("minecraft:overworld", new Vec3(11.0, 64.0, 11.0));
        assertTrue(near.isPresent());
        assertEquals(10, near.orElseThrow().pos().x());
    }

    @Test
    void nearestIgnoresOtherDimensions() {
        RewarpStore store = new RewarpStore();
        store.add(new RewarpPoint("minecraft:the_nether", GameArea.GARDEN, new BlockPos(0, 64, 0), 0f, 0f, 1000L));
        Optional<RewarpPoint> near = store.nearest("minecraft:overworld", new Vec3(0, 64, 0));
        assertTrue(near.isEmpty());
    }
}
