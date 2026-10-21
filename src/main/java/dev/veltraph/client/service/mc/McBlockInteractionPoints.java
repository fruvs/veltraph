package dev.veltraph.client.service.mc;

import dev.veltraph.client.world.BlockPos;
import dev.veltraph.client.world.Vec3;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;

/** Read-only outline rays. A returned point is geometry evidence, not an attack authorization. */
public final class McBlockInteractionPoints {
    private McBlockInteractionPoints() {}

    public static Optional<Vec3> find(Minecraft client, BlockPos target, Vec3 eye, double requestedRange) {
        if (client == null || target == null || eye == null || !finite(eye)
                || !Double.isFinite(requestedRange) || requestedRange <= 0) return Optional.empty();
        var level = client.level;
        var player = client.player;
        if (level == null || player == null || player.level() != level) return Optional.empty();
        double nativeRange = player.blockInteractionRange();
        double range = Math.min(requestedRange, nativeRange);
        // Refuse abnormal inputs rather than allocating an unbounded ray/shape search.
        if (!Double.isFinite(range) || range <= 0 || range > 64
                || Math.abs(eye.x()) > 30_000_000 || Math.abs(eye.z()) > 30_000_000) return Optional.empty();
        var pos = McConversions.toMc(target);
        if (level.isOutsideBuildHeight(pos) || !loaded(level, pos.getX(), pos.getZ())) return Optional.empty();
        var origin = new net.minecraft.world.phys.Vec3(eye.x(), eye.y(), eye.z());
        var context = new ClipContext(origin, origin, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player);
        var shape = context.getBlockShape(level.getBlockState(pos), level, pos);
        if (shape.isEmpty()) return Optional.empty();
        var candidates = new ArrayList<net.minecraft.world.phys.Vec3>();
        for (var box : shape.toAabbs()) {
            // Include thin panels and narrow exposed fruit faces; use the actual outline boxes.
            for (int ix = 0; ix < 5; ix++) for (int iy = 0; iy <= 40; iy++) for (int iz = 0; iz < 5; iz++) {
                if (ix != 0 && ix != 4 && iy != 0 && iy != 40 && iz != 0 && iz != 4) continue;
                var point = new net.minecraft.world.phys.Vec3(
                        pos.getX() + coordinate(box.minX, box.maxX, ix / 4.0),
                        pos.getY() + coordinate(box.minY, box.maxY, iy / 40.0),
                        pos.getZ() + coordinate(box.minZ, box.maxZ, iz / 4.0));
                if (point.distanceToSqr(origin) <= range * range) candidates.add(point);
            }
        }
        var center = new net.minecraft.world.phys.Vec3(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5);
        candidates.sort(Comparator.comparingDouble(point -> point.distanceToSqr(center)));
        for (var point : candidates) {
            var delta = point.subtract(origin);
            if (delta.lengthSqr() < 1e-12) continue;
            var end = origin.add(delta.normalize().scale(range));
            if (!loadedRay(level, origin, end)) continue;
            var hit = level.clip(new ClipContext(origin, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
            if (hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(pos)
                    && hit.getLocation().distanceToSqr(origin) <= range * range + 1e-12
                    && client.level == level && client.player == player && player.level() == level) {
                // Aim into the proven shape rather than at its exact entering boundary. The
                // selected candidate itself remains inside the observed/requested range.
                return Optional.of(new Vec3(point.x, point.y, point.z));
            }
        }
        return Optional.empty();
    }

    private static double coordinate(double min, double max, double fraction) {
        double inset = Math.min(1e-4, (max - min) / 4);
        return min + inset + fraction * (max - min - 2 * inset);
    }

    private static boolean finite(Vec3 value) {
        return Double.isFinite(value.x()) && Double.isFinite(value.y()) && Double.isFinite(value.z());
    }

    private static boolean loaded(ClientLevel level, int x, int z) {
        return level.getChunkSource().hasChunk(x >> 4, z >> 4);
    }

    private static boolean loadedRay(ClientLevel level, net.minecraft.world.phys.Vec3 start,
            net.minecraft.world.phys.Vec3 end) {
        if (start.y < level.getMinY() || start.y >= level.getMaxY()
                || end.y < level.getMinY() || end.y >= level.getMaxY()) return false;
        // Conservative chunk envelope includes every voxel the vanilla ray can visit, including
        // exact corner crossings. Never let ClientLevel's unloaded-air fallback certify a ray.
        int minX = ((int) Math.floor(Math.min(start.x, end.x))) >> 4;
        int maxX = ((int) Math.floor(Math.max(start.x, end.x))) >> 4;
        int minZ = ((int) Math.floor(Math.min(start.z, end.z))) >> 4;
        int maxZ = ((int) Math.floor(Math.max(start.z, end.z))) >> 4;
        for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++)
            if (!level.getChunkSource().hasChunk(x, z)) return false;
        return true;
    }
}
