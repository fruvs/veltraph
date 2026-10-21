package dev.veltraph.client.rewarp;

import dev.veltraph.client.game.GameArea;
import dev.veltraph.client.world.BlockPos;
import dev.veltraph.client.world.Vec3;

import java.util.Locale;

/**
 * A persisted, manually saved return marker. This is distinct from the interrupted engine
 * live pose carried by {@link FarmReturnPoint}; automatic macro resumption must not substitute
 * a user marker for that captured pose. See {@code docs/REWARP_NOTES.md}.
 *
 * <p>Pure value type (no Minecraft dependency) so it can be built and asserted in plain JDK tests, and
 * so {@link RewarpStore} stays serialization-friendly for the later JSON-config target (every field is
 * a plain primitive / string / pure value type).
 *
 * <p>The {@code timestamp} is epoch millis <b>passed in</b> by the caller — pure logic never calls
 * {@code System.currentTimeMillis()} / {@code new Date()} so tests are deterministic.
 *
 * @param dimension namespaced world registry id (e.g. {@code "minecraft:overworld"}); never null
 * @param area      the resolved {@link GameArea} at capture time (e.g. {@link GameArea#GARDEN})
 * @param pos       integer block position of the player
 * @param yaw       player yaw in degrees
 * @param pitch     player pitch in degrees
 * @param timestamp capture time in epoch millis (deterministic input; not read from the clock here)
 * @param exactPosition fractional capture when present; null preserves legacy block-center behavior
 */
public record RewarpPoint(String dimension, GameArea area, BlockPos pos, float yaw, float pitch, long timestamp,
                          Vec3 exactPosition) {
    /** Legacy records retain their original block-center arrival behavior. */
    public RewarpPoint(String dimension,GameArea area,BlockPos pos,float yaw,float pitch,long timestamp) {
        this(dimension,area,pos,yaw,pitch,timestamp,null);
    }

    public RewarpPoint {
        if (dimension == null) {
            dimension = "";
        }
        if (area == null) {
            area = GameArea.UNKNOWN;
        }
        if (pos == null) {
            pos = new BlockPos(0, 0, 0);
        }
        if(exactPosition!=null&&(!Double.isFinite(exactPosition.x())||!Double.isFinite(exactPosition.y())
                ||!Double.isFinite(exactPosition.z())||!exactPosition.toBlockPos().equals(pos)))
            throw new IllegalArgumentException("Invalid fractional saved anchor");
    }
    public Vec3 arrivalPosition(){return exactPosition==null?new Vec3(pos.x()+.5,pos.y(),pos.z()+.5):exactPosition;}

    /**
     * One-line display for the {@code rewarp list} command, e.g.
     * {@code "minecraft:overworld | Garden | (12, 71, -8) yaw 90.0 pitch 0.0 | age 5s"}.
     *
     * @param nowMillis the current epoch-millis "now" used to compute age (passed in for determinism)
     */
    public String display(long nowMillis) {
        String display=String.format(Locale.ROOT,
                "%s | %s | (%d, %d, %d) yaw %.1f pitch %.1f | age %s",
                dimension, area.displayName(), pos.x(), pos.y(), pos.z(), yaw, pitch, formatAge(nowMillis));
        return exactPosition==null?display:display+String.format(Locale.ROOT," | exact (%.5f, %.5f, %.5f)",exactPosition.x(),exactPosition.y(),exactPosition.z());
    }

    /** Human-readable age string ({@code "now"} / {@code "12s"} / {@code "3m"} / {@code "2h"} / {@code "4d"}). */
    public String formatAge(long nowMillis) {
        long deltaMs = nowMillis - timestamp;
        if (deltaMs < 0) {
            deltaMs = 0;
        }
        long seconds = deltaMs / 1000L;
        if (seconds < 1) {
            return "now";
        }
        if (seconds < 60) {
            return seconds + "s";
        }
        long minutes = seconds / 60L;
        if (minutes < 60) {
            return minutes + "m";
        }
        long hours = minutes / 60L;
        if (hours < 24) {
            return hours + "h";
        }
        return (hours / 24L) + "d";
    }
}
