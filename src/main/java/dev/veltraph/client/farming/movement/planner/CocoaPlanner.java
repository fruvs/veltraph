package dev.veltraph.client.farming.movement.planner;

import dev.veltraph.client.config.ConfigService;
import dev.veltraph.client.config.FarmingConfig;
import dev.veltraph.client.config.LaneSide;
import dev.veltraph.client.farming.movement.TurnDirection;
import dev.veltraph.client.log.VeltraphLog;
import dev.veltraph.client.service.FarmServices;

/**
 * Cocoa row traversal recovered from the shared base and cocoa override.
 * The base enters SWITCHING_ROW at a row obstruction and holds the configured lateral input.
 * The cocoa override's changed-axis/centred-fraction/pod predicate ENDS that switch; older
 * recovery reports incorrectly called it the row-end trigger. Longitudinal direction is
 * captured once and reverses only on completed switches, not on transient side-probe loss.
 * See docs/FARMING_COCOA_SOURCE_AUDIT.md.
 */
public final class CocoaPlanner {

    public enum CocoaState {
        FARMING,
        SWITCHING_ROW
    }

    /**
     * Lane geometry queries; faked in tests, world-backed at runtime ({@code CocoaConstants}).
     */
    public interface World {

        /**
         * The detected cocoa lane side from the {@code player.up(2)} probe ({@code CocoaConstants
         * .getType40}): {@link LaneSide#LEFT}/{@link LaneSide#RIGHT}, or {@code null} when no side can
         * be detected (the recovered fallback path → {@code FORWARD} heading).
         */
        LaneSide detectedLaneSide();

        /** Physical obstruction at the end of the current longitudinal row. */
        boolean atRowEnd();

        /** New lateral block, centred fraction, and cocoa above the return lane. */
        boolean laneAligned();

        /** Capture the lane being left, once, before lateral movement begins. */
        default void beginSwitch() {}

        /** Loaded, supported body clearance in the requested direction. */
        default boolean canMove(TurnDirection direction) { return true; }

        /** Decoded height observer samples bounding-box bottom minus 0.001, and that block's up(). */
        default boolean heightProbeLoaded() { return true; }
        default boolean heightProbeClear() { return true; }
        default void clearSwitch() {}
    }

    private final ConfigService config;
    private final FarmServices services;
    private final World world;

    private CocoaState state = CocoaState.FARMING;
    private boolean sneakLatched;
    private LaneSide lastLaneSide;
    private TurnDirection heading = TurnDirection.FORWARD;
    private String blocker = "idle";
    private boolean headingInitialized;
    private TurnDirection switchDirection = TurnDirection.RIGHT;
    private int recordedLevel;
    private boolean changedLevel;

    public CocoaPlanner(ConfigService config, FarmServices services, World world) {
        this.config = config;
        this.services = services;
        this.world = world;
    }

    public CocoaState state() {
        return state;
    }

    /** The lane side detected on the most recent tick ({@code null} if none). */
    public LaneSide laneSide() {
        return lastLaneSide;
    }

    /** The lane heading resolved from the detected side + {@code COCOA_LANE_SWITCH_DIRECTION}. */
    public TurnDirection heading() {
        return heading;
    }

    public boolean sneakLatched() {
        return sneakLatched;
    }

    public String blocker() {
        return blocker;
    }

    public void start() {
        state = CocoaState.FARMING;
        sneakLatched = false;
        lastLaneSide = null;
        heading = TurnDirection.FORWARD;
        headingInitialized = false;
        switchDirection = TurnDirection.fromLaneSide(config.get(FarmingConfig.COCOA_LANE_SWITCH_DIRECTION));
        recordedLevel = (int) Math.floor(services.player().position().y());
        changedLevel = false;
        blocker = "farming";
        VeltraphLog.details("CocoaPlanner.start", "state=FARMING");
    }

    public void stop() {
        state = CocoaState.FARMING;
        sneakLatched = false;
        blocker = "stopped";
        services.input().releaseAllMovement();
        services.breaking().setBreaking(false);
    }

    /**
     * The recovered {@code laneDirFor}: combine the detected lane side with the configured switch
     * direction to pick a FORWARD/BACK lane heading. Pure — exercised directly by the planner tests.
     *
     * <p>side LEFT &amp; switch RIGHT → FORWARD; LEFT &amp; else → BACK; RIGHT &amp; RIGHT → BACK;
     * RIGHT &amp; else → FORWARD; side {@code null} → FORWARD (§5b / FarmingCocoaLayout.md:20-26).
     */
    public static TurnDirection laneDirFor(LaneSide side, LaneSide switchDirection) {
        if (side == null) {
            return TurnDirection.FORWARD;
        }
        boolean switchRight = switchDirection == LaneSide.RIGHT;
        return switch (side) {
            case LEFT -> switchRight ? TurnDirection.FORWARD : TurnDirection.BACK;
            case RIGHT -> switchRight ? TurnDirection.BACK : TurnDirection.FORWARD;
        };
    }

    /** Advance the recovered state machine and keep movement asserted between transitions. */
    public void tick() {
        observeHeight();
        lastLaneSide = world.detectedLaneSide();
        if (!headingInitialized) {
            heading = laneDirFor(lastLaneSide, config.get(FarmingConfig.COCOA_LANE_SWITCH_DIRECTION));
            headingInitialized = true;
        }
        // Shared base :250-281: a change is latched in free space, then consumed at support.
        // Do not toggle for each airborne block crossed or for an unknown chunk.
        if (changedLevel && world.heightProbeLoaded() && !world.heightProbeClear()) {
            recordedLevel = (int) Math.floor(services.player().position().y());
            changedLevel = false;
            state = CocoaState.FARMING;
            heading = TurnDirection.FORWARD;
            switchDirection = switchDirection.opposite();
            sneakLatched = false;
            world.clearSwitch();
            driveFarming();
            blocker = "resumed new level; switch " + switchDirection;
            return;
        }
        CocoaState prev = state;
        VeltraphLog.verbose("CocoaPlanner.tick", () -> "state=" + prev + " side=" + lastLaneSide
                + " heading=" + heading);
        switch (state) {
            case FARMING -> tickFarming();
            case SWITCHING_ROW -> tickSwitchingRow();
        }
        if (state != prev) {
            VeltraphLog.details("CocoaPlanner",
                    prev + " -> " + state + " (side=" + lastLaneSide + " heading=" + heading
                            + " sneak=" + sneakLatched + ")");
        }
    }

    /** Called while airborne too: module input gating must not discard the original level latch. */
    public void observeHeight() {
        if (world.heightProbeLoaded() && world.heightProbeClear()
                && (int) Math.floor(services.player().position().y()) != recordedLevel) {
            changedLevel = true;
        }
    }

    private void tickFarming() {
        if (world.atRowEnd()) {
            world.beginSwitch();
            state = CocoaState.SWITCHING_ROW;
            sneakLatched = config.get(FarmingConfig.COCOA_SNEAK_WHEN_SWITCHING);
            driveSwitch();
            return;
        }
        driveFarming();
    }

    private void driveFarming() {
        services.input().releaseAllMovement();
        boolean move = config.get(FarmingConfig.COCOA_PLACER_AUTO_WALK) && world.canMove(heading);
        services.input().setForwardHeld(move && heading == TurnDirection.FORWARD);
        services.input().setBackHeld(move && heading == TurnDirection.BACK);
        services.breaking().setBreaking(true);
        blocker = move ? "farming" : "farming (movement held)";
    }

    private void tickSwitchingRow() {
        if (world.laneAligned()) {
            heading = heading.opposite();
            state = CocoaState.FARMING;
            sneakLatched = false;
            // Resume continuously along the new lane, without a stop tick or a camera flip.
            services.input().releaseAllMovement();
            tickFarming();
        } else {
            driveSwitch();
        }
    }

    private void driveSwitch() {
        services.input().releaseAllMovement();
        services.breaking().setBreaking(false);
        services.input().setSneakHeld(sneakLatched);
        boolean clear = world.canMove(switchDirection);
        services.input().setLeftHeld(clear && switchDirection == TurnDirection.LEFT);
        services.input().setRightHeld(clear && switchDirection == TurnDirection.RIGHT);
        blocker = clear ? "aligning to next cocoa lane" : "lane switch blocked or unloaded";
    }

    /** Passive exact maintenance checkpoint. No services, geometry, clocks or planner callbacks run. */
    public dev.veltraph.client.farming.module.FarmMaintenanceState maintenanceState() {
        return dev.veltraph.client.farming.module.FarmMaintenanceState.of(this,
                "config", config,
                "services", services,
                "world", world,
                "state", state,
                "sneakLatched", sneakLatched,
                "lastLaneSide", lastLaneSide,
                "heading", heading,
                "blocker", blocker,
                "headingInitialized", headingInitialized,
                "switchDirection", switchDirection,
                "recordedLevel", recordedLevel,
                "changedLevel", changedLevel);
    }
}
