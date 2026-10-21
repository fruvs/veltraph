package dev.veltraph.client.rewarp;

import dev.veltraph.client.game.GameArea;
import dev.veltraph.client.world.Vec3;
import java.util.Objects;

/**
 * Validated foot/camera pose shared by explicitly separate callers. Interrupted recovery captures
 * it transiently, analogous to BafCoreEngine -> ReturnToLocationController setCompanion2. Normal
 * cycling persists its explicit START in FarmCycleStore; manual RETURN data lives in RewarpPoint.
 * This value does not infer a role or substitute one caller's pose for another. Capturing
 * owner/world/farm identities and the last healthy Garden observation belongs to the caller.
 */
public record FarmReturnPoint(String dimension, GameArea area, Vec3 position, float yaw, float pitch) {
    public FarmReturnPoint {
        Objects.requireNonNull(dimension,"dimension"); Objects.requireNonNull(area,"area");
        Objects.requireNonNull(position,"position");
        if(!Double.isFinite(position.x())||!Double.isFinite(position.y())||!Double.isFinite(position.z())
                ||!Float.isFinite(yaw)||!Float.isFinite(pitch)||pitch < -90 || pitch > 90)
            throw new IllegalArgumentException("Invalid farm return pose");
    }
    public static FarmReturnPoint fromSavedMarker(RewarpPoint marker) {
        return new FarmReturnPoint(marker.dimension(),marker.area(),
                marker.arrivalPosition(),marker.yaw(),marker.pitch());
    }
}
