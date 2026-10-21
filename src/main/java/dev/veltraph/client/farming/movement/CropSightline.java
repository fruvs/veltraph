package dev.veltraph.client.farming.movement;

import dev.veltraph.client.world.BlockPos;
import dev.veltraph.client.world.Direction;
import dev.veltraph.client.world.Vec3;

/**
 * Pure crop-sightline geometry: computes the world point the farming aim should target so the
 * crosshair stays on the crop being harvested. Translated from the recovered executor, which aimed at
 * {@code next = playerBlockPos.offset(facing, 1)} and broke the crop in front
 * ({@code reports/_spec_parts/FarmingForwardExecutor.md}); Veltraph aims a configurable distance
 * <i>past</i> the adjacent crop and at a configurable height (the upper crop hitbox) so the pitch is
 * flatter and the crosshair holds mature wheat/wart while strafing.
 *
 * <p>No Minecraft dependency, so it is unit-testable on a plain JVM.
 */
public final class CropSightline {

    private CropSightline() {
    }

    /**
     * The aim point for a crop one block ahead of {@code playerBlock} along {@code heading}.
     *
     * @param playerBlock   the player's block position
     * @param heading       the macro-owned facing
     * @param leadDistance  how far past the adjacent crop to look, in blocks ({@code CROP_SIGHTLINE_DISTANCE})
     * @param heightOffset  height above the crop block base to target ({@code CROP_AIM_HEIGHT_OFFSET})
     */
    public static Vec3 target(BlockPos playerBlock, Direction heading, double leadDistance, double heightOffset) {
        BlockPos front = playerBlock.offset(heading);
        return new Vec3(
                front.x() + 0.5 + heading.dx() * leadDistance,
                front.y() + heightOffset,
                front.z() + 0.5 + heading.dz() * leadDistance);
    }

    /**
     * Pitch (degrees, Minecraft convention; positive = looking down) from an eye position to a target
     * point. Clamped to [-90, 90]. Returns 0 when the target is directly above/below (degenerate).
     */
    public static float pitchTo(Vec3 eye, Vec3 target) {
        double dx = target.x() - eye.x();
        double dy = target.y() - eye.y();
        double dz = target.z() - eye.z();
        double horiz = Math.sqrt(dx * dx + dz * dz);
        if (horiz < 1.0e-4) {
            return 0f;
        }
        float pitch = (float) (-Math.toDegrees(Math.atan2(dy, horiz)));
        return Math.max(-90f, Math.min(90f, pitch));
    }
}
