package dev.veltraph.client.farming.movement;

import dev.veltraph.client.world.Direction;
import dev.veltraph.client.world.Vec3;

/**
 * Crop/farm-aware geometry queries the {@link SShapeRowEngine} needs, expressed against a macro-owned
 * heading. Implemented by {@link FarmGeometry} (world-backed) and faked in tests. All queries use the
 * engine-set heading, never the live camera (goal Target 3 / root cause #4).
 */
public interface SShapeGeometry {

    /** Set the macro-owned heading (the cardinal the player faces toward the crop wall). */
    void setHeading(Direction heading);

    Direction heading();

    /** Player is near the centre of its block (within the recovered near-middle radius). */
    boolean nearMiddle();

    /** Is there a (non-air) block directly in front along the heading — a crop/wall to break? */
    boolean blockInFront();

    /** Can strafe one block to {@code side} (lateral neighbour enterable + has a floor). */
    boolean canStrafe(TurnDirection side);

    /** Can step forward one block along the heading into the next row. */
    boolean canStepForward();

    /** Probe RIGHT then LEFT (recovered order); {@link TurnDirection#FORWARD} if neither is movable. */
    TurnDirection movableLateral();

    /** Centre of the block in front to aim/break at (the crop along the heading). */
    Vec3 targetVec();

    /** Fresh-start crop-outline view data, never a native action or a per-crop tracking target. */
    default FixedRowView initialFixedRowView() { return null; }
    /** A normal production crop run refuses startup when its reference profile is unsupported. */
    default boolean requiresNormalFixedRowView() { return false; }
    /** Cached startup evidence only; implementations must not query a new scene here. */
    default String normalFixedRowViewRefusal(){return "unsupported observed crop outline";}
    default boolean fixedViewHarvestTargetAvailable(){return harvestTargetAvailable();}
    default SupplementalWaterCoverage.Plan supplementalCoveragePlan(TurnDirection side) { return null; }
    default boolean supplementalCoverageCurrent(SupplementalWaterCoverage.Plan plan) { return false; }
    default boolean supplementalWaterAt(Vec3 feet) { return false; }
    default boolean supplementalCoverageBodyClear(Vec3 feet,boolean allowWater) { return false; }


    /** Actual adjacent lane centre, reached through a loaded, supported end corridor. */
    default Vec3 rowTransitionTarget(TurnDirection sweep) { return null; }

    /** World-backed ground-crop geometry must never substitute elapsed ticks for an absent lane. */
    default boolean requiresObservedRowDestination() { return false; }

    /** Current end-block centre for active key-driven alignment before a lane crossing. */
    default Vec3 rowEndCenter() { return null; }

    /** Whether the sightline currently enters a harvestable crop (avoid attacking bare end paths). */
    default boolean harvestTargetAvailable() { return true; }

    /** Actual loaded supported lane end, never inferred from unknown collision. */
    default boolean initialCoverageEndProved(TurnDirection side){return false;}
    default boolean initialCoverageEndProved(TurnDirection side,boolean requiresForwardExit){return requiresForwardExit&&initialCoverageEndProved(side);}

    /** Explicit loaded lower farm standing position beside an intentional ledge. */
    default Vec3 safeDropTarget(TurnDirection side) { return null; }
    default boolean dropLandingValid(Vec3 feetTarget) { return false; }
    default float eyeHeight(){return 1.62f;}
}
