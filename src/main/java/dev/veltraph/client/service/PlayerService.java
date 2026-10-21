package dev.veltraph.client.service;

import dev.veltraph.client.world.BlockPos;
import dev.veltraph.client.world.Direction;
import dev.veltraph.client.world.Vec3;

/** Read-only view of the local player's spatial state. */
public interface PlayerService {

    /** Whether a player and world are currently present (false on menus / between worlds). */
    boolean isAvailable();

    Vec3 position();

    /** Current eye position; pure adapters default to the standing eye height. */
    default Vec3 eyePosition() {
        Vec3 position = position();
        return new Vec3(position.x(), position.y() + 1.62, position.z());
    }

    /** Observed native block-interaction reach; unsupported adapters provide no usable witness. */
    default double blockInteractionRange() { return Double.NaN; }

    BlockPos blockPos();

    float yaw();

    float pitch();

    boolean onGround();

    /**
     * Horizontal movement speed this tick in blocks/tick. The recovered vertical-wart and sugarcane
     * planners gate behavior on whether the player is effectively still (delta below a threshold).
     */
    double horizontalSpeed();
    /** Fresh native horizontal momentum; unknown adapters must fail closed for blended turns. */
    default java.util.Optional<Vec3> horizontalVelocity(){return java.util.Optional.empty();}

    /** Horizontal direction the player is currently facing. */
    Direction facing();
}
