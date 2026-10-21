package dev.veltraph.client.farming.movement;

import dev.veltraph.client.world.BlockPos;
import dev.veltraph.client.world.Direction;
import dev.veltraph.client.world.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Crop-sightline aim-point + pitch maths (Target 6 tuning). */
class CropSightlineTest {

    @Test
    void targetLeadsPastTheAdjacentCropAtTheConfiguredHeight() {
        // Player at (0,64,0) facing NORTH (dz=-1); adjacent crop block at z=-1, centre -0.5.
        Vec3 t = CropSightline.target(new BlockPos(0, 64, 0), Direction.NORTH, 2.5, 1.35);
        assertEquals(0.5, t.x(), 1e-6);
        assertEquals(65.35, t.y(), 1e-6);
        assertEquals(-3.0, t.z(), 1e-6, "0.5 + (-1)*1 block + (-1)*2.5 lead");
    }

    @Test
    void biggerLeadFlattensThePitch() {
        Vec3 eye = new Vec3(0.5, 65.62, 0.5);
        float nearPitch = CropSightline.pitchTo(eye,
                CropSightline.target(new BlockPos(0, 64, 0), Direction.NORTH, 0.5, 1.0));
        float farPitch = CropSightline.pitchTo(eye,
                CropSightline.target(new BlockPos(0, 64, 0), Direction.NORTH, 3.0, 1.0));
        assertTrue(farPitch < nearPitch, "a farther sightline gives a flatter (smaller) downward pitch: "
                + farPitch + " < " + nearPitch);
    }

    @Test
    void higherTargetRaisesTheAim() {
        Vec3 eye = new Vec3(0.5, 65.62, 0.5);
        float low = CropSightline.pitchTo(eye,
                CropSightline.target(new BlockPos(0, 64, 0), Direction.NORTH, 2.5, 0.5));
        float high = CropSightline.pitchTo(eye,
                CropSightline.target(new BlockPos(0, 64, 0), Direction.NORTH, 2.5, 1.6));
        assertTrue(high < low, "aiming at a higher point reduces the downward pitch");
    }

    @Test
    void pitchAimsDownwardAtAGroundCropAndStaysInRange() {
        Vec3 eye = new Vec3(0.5, 65.62, 0.5);
        Vec3 crop = CropSightline.target(new BlockPos(0, 64, 0), Direction.NORTH, 2.5, 1.35);
        float pitch = CropSightline.pitchTo(eye, crop);
        assertTrue(pitch > 0f, "looking down at the crop is a positive pitch");
        assertTrue(pitch <= 90f && pitch >= -90f, "clamped to the valid range");
    }

    @Test
    void degenerateVerticalTargetReturnsZero() {
        assertEquals(0f, CropSightline.pitchTo(new Vec3(0, 64, 0), new Vec3(0, 60, 0)), 1e-6);
    }
}
