package dev.veltraph.client.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DirectionTest {

    @Test
    void oppositesAreCorrect() {
        assertEquals(Direction.SOUTH, Direction.NORTH.opposite());
        assertEquals(Direction.WEST, Direction.EAST.opposite());
        assertEquals(Direction.DOWN, Direction.UP.opposite());
    }

    @Test
    void clockwiseRotationCyclesHorizontals() {
        assertEquals(Direction.EAST, Direction.NORTH.rotateYClockwise());
        assertEquals(Direction.SOUTH, Direction.EAST.rotateYClockwise());
        assertEquals(Direction.WEST, Direction.SOUTH.rotateYClockwise());
        assertEquals(Direction.NORTH, Direction.WEST.rotateYClockwise());
    }

    @Test
    void counterClockwiseIsInverseOfClockwise() {
        for (Direction d : new Direction[]{Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST}) {
            assertEquals(d, d.rotateYClockwise().rotateYCounterclockwise());
        }
    }

    @Test
    void offsetMovesBlockPos() {
        BlockPos p = new BlockPos(0, 64, 0);
        assertEquals(new BlockPos(0, 64, -1), p.offset(Direction.NORTH));
        assertEquals(new BlockPos(0, 64, 3), p.offset(Direction.SOUTH, 3));
        assertEquals(new BlockPos(0, 65, 0), p.up());
    }

    @Test
    void yawRoundTripsToHorizontalDirection() {
        assertEquals(Direction.SOUTH, Direction.fromYaw(Direction.SOUTH.toYaw()));
        assertEquals(Direction.WEST, Direction.fromYaw(Direction.WEST.toYaw()));
        assertEquals(Direction.NORTH, Direction.fromYaw(Direction.NORTH.toYaw()));
        assertEquals(Direction.EAST, Direction.fromYaw(Direction.EAST.toYaw()));
    }
}
