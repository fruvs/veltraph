package dev.veltraph.client.farming.movement;

/**
 * Geometric queries the shared row engine needs, expressed in lane-relative terms. A concrete
 * implementation ({@link WorldRowGeometry}) derives these from the world/player services using
 * deterministic block-ahead / can-stand checks; tests provide a fake.
 */
public interface RowGeometry extends RowEndPredicate {

    /** Whether the player can keep walking straight ahead (lane ahead is standable and clear). */
    boolean forwardLaneValid();

    /**
     * Whether the player can move to the given lateral side. Only {@link TurnDirection#LEFT} and
     * {@link TurnDirection#RIGHT} are meaningful.
     */
    boolean sideMovable(TurnDirection side);

    /**
     * The lane heading the farming aim should capture at start (degrees, Minecraft yaw). Default: snap
     * the player's current facing to the nearest cardinal so the row is straight. The engine captures
     * this once and then <b>owns</b> the heading (it is not re-read from the camera each tick).
     */
    default float laneYaw(float currentYaw) {
        return Angles.snapToCardinal(currentYaw);
    }
}
