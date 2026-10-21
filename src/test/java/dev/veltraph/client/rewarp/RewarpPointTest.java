package dev.veltraph.client.rewarp;

import dev.veltraph.client.game.GameArea;
import dev.veltraph.client.world.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Field capture + the formatted display string (dim/area/coords/yaw/pitch/age). */
class RewarpPointTest {

    @Test
    void capturesAllFields() {
        RewarpPoint p = new RewarpPoint("minecraft:overworld", GameArea.GARDEN,
                new BlockPos(12, 71, -8), 90.5f, -10.25f, 5_000L);
        assertEquals("minecraft:overworld", p.dimension());
        assertEquals(GameArea.GARDEN, p.area());
        assertEquals(new BlockPos(12, 71, -8), p.pos());
        assertEquals(90.5f, p.yaw());
        assertEquals(-10.25f, p.pitch());
        assertEquals(5_000L, p.timestamp());
    }

    @Test
    void nullsAreNormalized() {
        RewarpPoint p = new RewarpPoint(null, null, null, 0f, 0f, 0L);
        assertEquals("", p.dimension());
        assertEquals(GameArea.UNKNOWN, p.area());
        assertEquals(new BlockPos(0, 0, 0), p.pos());
    }

    @Test
    void displayContainsDimAreaCoordsYawPitch() {
        RewarpPoint p = new RewarpPoint("minecraft:overworld", GameArea.GARDEN,
                new BlockPos(12, 71, -8), 90.0f, 0.0f, 1_000L);
        String s = p.display(6_000L); // 5s later
        assertTrue(s.contains("minecraft:overworld"), s);
        assertTrue(s.contains("Garden"), s);
        assertTrue(s.contains("(12, 71, -8)"), s);
        assertTrue(s.contains("yaw 90.0"), s);
        assertTrue(s.contains("pitch 0.0"), s);
        assertTrue(s.contains("age 5s"), s);
    }

    @Test
    void ageFormatting() {
        RewarpPoint p = new RewarpPoint("d", GameArea.GARDEN, new BlockPos(0, 0, 0), 0f, 0f, 0L);
        assertEquals("now", p.formatAge(500L));
        assertEquals("5s", p.formatAge(5_000L));
        assertEquals("3m", p.formatAge(3L * 60 * 1000));
        assertEquals("2h", p.formatAge(2L * 60 * 60 * 1000));
        assertEquals("4d", p.formatAge(4L * 24 * 60 * 60 * 1000));
        // Clock skew (now before timestamp) clamps to "now".
        assertEquals("now", p.formatAge(-1000L));
    }
    @Test
    void fractionalAndLegacyArrivalCoordinatesHaveSeparateSemantics() {
        var exact=new dev.veltraph.client.world.Vec3(-234.31,69.9375,.12);
        var captured=new RewarpPoint("minecraft:overworld",GameArea.GARDEN,exact.toBlockPos(),73,-12,1,exact);
        assertEquals(exact,captured.arrivalPosition());assertEquals(exact,FarmReturnPoint.fromSavedMarker(captured).position());
        var legacy=new RewarpPoint("minecraft:overworld",GameArea.GARDEN,exact.toBlockPos(),73,-12,1);
        assertEquals(new dev.veltraph.client.world.Vec3(-234.5,69,.5),legacy.arrivalPosition());
        assertTrue(captured.display(1).contains("69.93750"));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,()->new RewarpPoint("minecraft:overworld",GameArea.GARDEN,new BlockPos(0,0,0),0,0,0,exact));
    }
}
