package dev.veltraph.client.farming.movement;

import dev.veltraph.client.config.ConfigKey;
import dev.veltraph.client.config.ConfigService;
import dev.veltraph.client.config.FarmingConfig;
import dev.veltraph.client.config.HoldForwardPolicy;
import dev.veltraph.client.service.FarmServices;

import java.util.Random;
import java.util.function.BooleanSupplier;

/**
 * Clean source-first row engine (boustrophedon / S-shape) used by Wart/Crops S-shape and Cactus.
 * Translated from the original {@code MovementDirection} controller
 * ({@code reports/_spec_parts/MovementDirectionDecoded.md} + {@code FarmingForwardExecutor.md}): a
 * {@code FARMING → GOING_FORWARD → TURNING} loop gated on {@code isOnGround}, a {@code 14900} ms turn
 * cooldown, {@code DROP_AND_TURN}, right-then-left side probing, and an S-shape lane switch + heading
 * flip ({@code executeTurnAndResume}/{@code flipForwardHeading}).
 *
 * <p>Key fixes over the previous pass:
 * <ul>
 *   <li><b>No snap aim.</b> Uses {@link FarmAimController} (smooth bounded motion) only — there is no
 *       DIRECT/snap mode in the farming runtime.</li>
 *   <li><b>Macro-owned heading.</b> The row heading is captured once at {@link #start()} (from the
 *       initial setup via {@link RowGeometry#laneYaw}) into a {@link FarmingAimProfile} and then owned
 *       by the engine; it is <b>not</b> re-read from the live camera each tick. A turn flips it 180°.</li>
 *   <li><b>Continuous correction (no stop-adjust-restart).</b> The aim is corrected <i>every</i> tick.
 *       Small drift is fixed while forward/break stay held; movement only pauses for a large unsafe
 *       error, leaving the ground, a missing tool, or an invalid lane.</li>
 *   <li><b>Break preserved through turns.</b> The lane-switch strafe keeps breaking on (the corner
 *       crops) rather than fully stopping, per the recovered engine.</li>
 * </ul>
 *
 * <p>Pure: depends only on {@link ConfigService}, {@link FarmServices}, a {@link RowGeometry}, and a
 * tool-ready predicate, so it is fully unit-testable with fakes (jitter/GCD/accel are config-gated so
 * tests run deterministically).
 */
public final class MovementDirectionEngine {

    /** Ticks the lane-switch strafe is held before re-aiming (the recovered timed adjacent-row move). */
    public static final int LANE_STEP_TICKS = 6;

    private final ConfigService config;
    private final FarmServices services;
    private final RowGeometry geometry;
    private final ConfigKey<HoldForwardPolicy> holdPolicyKey;
    private final BooleanSupplier toolReady;

    private MovementState state = MovementState.IDLE;
    private TurnDirection currentTurnDirection = TurnDirection.LEFT; // recovered default
    private long lastTurnTimestampMs;
    private int turnTicksLeft;

    private FarmAimController aim;
    private FarmingAimProfile profile;

    public MovementDirectionEngine(ConfigService config, FarmServices services, RowGeometry geometry) {
        this(config, services, geometry, FarmingConfig.SSHAPE_HOLD_FORWARD_POLICY, () -> true);
    }

    public MovementDirectionEngine(ConfigService config, FarmServices services, RowGeometry geometry,
                                   ConfigKey<HoldForwardPolicy> holdPolicyKey) {
        this(config, services, geometry, holdPolicyKey, () -> true);
    }

    public MovementDirectionEngine(ConfigService config, FarmServices services, RowGeometry geometry,
                                   ConfigKey<HoldForwardPolicy> holdPolicyKey, BooleanSupplier toolReady) {
        this.config = config;
        this.services = services;
        this.geometry = geometry;
        this.holdPolicyKey = holdPolicyKey;
        this.toolReady = toolReady;
    }

    /** Begin (or restart) farming: capture the macro-owned heading and prepare the tool first. */
    public void start() {
        this.aim = buildAim();
        // Capture the row heading once from the initial setup; the macro owns it from here on.
        float headingYaw = geometry.laneYaw(services.rotation().yaw());
        this.profile = new FarmingAimProfile("row", headingYaw, config.get(FarmingConfig.FARM_AIM_PITCH));
        this.aim.setTarget(profile);
        this.state = MovementState.PREPARE_TOOL;
        this.currentTurnDirection = TurnDirection.LEFT;
        this.turnTicksLeft = 0;
        this.lastTurnTimestampMs = services.clock().nowMillis();
    }

    /** Stop and release every input (movement + breaking). Idempotent. */
    public void stop() {
        this.state = MovementState.STOPPED;
        releaseAll();
    }

    public MovementState state() {
        return state;
    }

    public TurnDirection currentTurnDirection() {
        return currentTurnDirection;
    }

    public long lastTurnTimestampMs() {
        return lastTurnTimestampMs;
    }

    /** The macro-owned row heading (degrees), or 0 before {@link #start()}. */
    public float controlledYaw() {
        return profile == null ? 0f : profile.yaw();
    }

    public boolean isTurnOnCooldown(long now) {
        return (now - lastTurnTimestampMs) <= config.get(FarmingConfig.TURN_COOLDOWN_MS);
    }

    /** Debug string for the HUD: heading, target, aim error, and whether movement is paused. */
    public String aimDebug() {
        if (aim == null) {
            return state + " (no aim)";
        }
        float err = aim.aimError(services.rotation().yaw(), services.rotation().pitch());
        return String.format("%s head=%.0f tgt=%.0f/%.0f err=%.1f%s", state, controlledYaw(),
                aim.targetYaw(), aim.targetPitch(), err, aim.jittering() ? " jitter" : "");
    }

    /** One engine update. */
    public void tick() {
        if (state == MovementState.IDLE || state == MovementState.STOPPED) {
            return;
        }
        if (aim == null) {
            aim = buildAim();
        }
        long now = services.clock().nowMillis();
        switch (state) {
            case PREPARE_TOOL -> tickPrepareTool();
            case AIMING -> tickAiming();
            case FARMING -> tickFarming(now);
            case GOING_FORWARD -> tickGoingForward();
            case TURNING -> tickTurning();
            case REAIM_AFTER_TURN -> tickReaim();
            case RECOVERING -> tickRecovering();
            default -> { /* IDLE / STOPPED handled above */ }
        }
    }

    private void tickPrepareTool() {
        releaseAll();
        if (toolReady.getAsBoolean() && grounded()) {
            state = MovementState.AIMING;
        }
    }

    private void tickAiming() {
        pauseMovement();
        doAim();
        if (aimReady() && grounded() && toolReady.getAsBoolean()) {
            state = geometry.forwardLaneValid() ? MovementState.FARMING : MovementState.RECOVERING;
        }
    }

    private void tickFarming(long now) {
        if (!toolReady.getAsBoolean()) {
            state = MovementState.PREPARE_TOOL;
            releaseAll();
            return;
        }
        // Pause only for a large unsafe error or leaving the ground; keep correcting the aim while
        // paused. Small drift below the pause threshold is corrected with forward/break still held
        // (continuous correction — no stop-adjust-restart).
        if (!grounded() || aimLargeError()) {
            state = MovementState.AIMING;
            pauseMovement();
            doAim();
            return;
        }
        doAim();
        boolean dropAndTurn = config.get(FarmingConfig.DROP_AND_TURN);
        if (dropAndTurn && !isTurnOnCooldown(now) && geometry.atRowEnd()) {
            beginTurn(now);
            return;
        }
        releaseStrafe();
        services.breaking().setBreaking(true);
        boolean laneOk = geometry.forwardLaneValid();
        services.input().setForwardHeld(laneOk && shouldHoldForward());
    }

    private void tickGoingForward() {
        if (aimLargeError()) {
            state = MovementState.AIMING;
            pauseMovement();
            doAim();
            return;
        }
        doAim();
        releaseStrafe();
        services.input().setForwardHeld(true);
        services.breaking().setBreaking(true);
        if (geometry.forwardLaneValid() && !geometry.atRowEnd()) {
            state = MovementState.FARMING;
        }
    }

    private void tickTurning() {
        // Keep correcting toward the (flipped) heading while the lane-switch strafe runs.
        doAim();
        TurnDirection dir = currentTurnDirection;
        if (dir != TurnDirection.LEFT && dir != TurnDirection.RIGHT) {
            state = MovementState.RECOVERING;
            releaseAll();
            return;
        }
        // Lateral lane switch; keep breaking on through the corner (do not fully stop breaking).
        services.input().setForwardHeld(false);
        services.input().setLeftHeld(dir == TurnDirection.LEFT);
        services.input().setRightHeld(dir == TurnDirection.RIGHT);
        services.breaking().setBreaking(true);
        if (turnTicksLeft > 0) {
            turnTicksLeft--;
        }
        if (turnTicksLeft <= 0) {
            releaseStrafe();
            state = MovementState.REAIM_AFTER_TURN;
        }
    }

    private void tickReaim() {
        // Finish turning to the new heading; keep breaking at the corner, no forward/strafe yet.
        releaseStrafe();
        services.input().setForwardHeld(false);
        services.breaking().setBreaking(true);
        doAim();
        if (aimReady() && grounded() && toolReady.getAsBoolean()) {
            state = geometry.forwardLaneValid() ? MovementState.GOING_FORWARD : MovementState.RECOVERING;
        }
    }

    private void tickRecovering() {
        releaseAll();
        if (geometry.forwardLaneValid()) {
            state = MovementState.AIMING;
        }
    }

    private void beginTurn(long now) {
        // S-shape boustrophedon: flip the owned heading 180° (where configured) and switch lane.
        if (config.get(FarmingConfig.SSHAPE_FLIP_HEADING_ON_TURN)) {
            profile = profile.flipped();
            aim.setTarget(profile);
        }
        state = MovementState.TURNING;
        lastTurnTimestampMs = now;
        currentTurnDirection = chooseMovableDirection();
        turnTicksLeft = LANE_STEP_TICKS;
        services.input().setForwardHeld(false);
    }

    // ---- Aim ----

    private FarmAimController buildAim() {
        FarmAimController.Settings settings = new FarmAimController.Settings(
                config.get(FarmingConfig.ROTATION_MAX_YAW_DEG_PER_TICK).floatValue(),
                config.get(FarmingConfig.ROTATION_MAX_PITCH_DEG_PER_TICK).floatValue(),
                config.get(FarmingConfig.ROTATION_ACCELERATION),
                config.get(FarmingConfig.ROTATION_TOLERANCE_DEG).floatValue(),
                config.get(FarmingConfig.ROTATION_PAUSE_ERROR_DEG).floatValue(),
                config.get(FarmingConfig.ROTATION_USE_MOUSE_GCD),
                config.get(FarmingConfig.ROTATION_MOUSE_SENSITIVITY),
                config.get(FarmingConfig.ROTATION_JITTER),
                config.get(FarmingConfig.ROTATION_JITTER_MAX_DEG).floatValue(),
                config.get(FarmingConfig.ROTATION_JITTER_MIN_INTERVAL_TICKS),
                config.get(FarmingConfig.ROTATION_JITTER_MAX_INTERVAL_TICKS),
                config.get(FarmingConfig.ROTATION_JITTER_HOLD_TICKS));
        return new FarmAimController(settings, new Random());
    }

    private void doAim() {
        float[] next = aim.step(services.rotation().yaw(), services.rotation().pitch());
        services.rotation().aim(next[0], next[1]);
    }

    private boolean aimReady() {
        return aim.withinTolerance(services.rotation().yaw(), services.rotation().pitch());
    }

    private boolean aimLargeError() {
        return aim.isLargeError(services.rotation().yaw(), services.rotation().pitch());
    }

    // ---- Helpers ----

    private boolean grounded() {
        return services.player().onGround();
    }

    /** Probe RIGHT first, then LEFT (recovered order). Returns {@link TurnDirection#FORWARD} if neither. */
    private TurnDirection chooseMovableDirection() {
        if (geometry.sideMovable(TurnDirection.RIGHT)) {
            return TurnDirection.RIGHT;
        }
        if (geometry.sideMovable(TurnDirection.LEFT)) {
            return TurnDirection.LEFT;
        }
        return TurnDirection.FORWARD;
    }

    private boolean shouldHoldForward() {
        HoldForwardPolicy policy = config.get(holdPolicyKey);
        if (policy == HoldForwardPolicy.TAP) {
            return services.clock().currentTick() % 2L == 0L;
        }
        return true;
    }

    private void pauseMovement() {
        releaseStrafe();
        services.input().setForwardHeld(false);
        services.breaking().setBreaking(false);
    }

    private void releaseStrafe() {
        services.input().setLeftHeld(false);
        services.input().setRightHeld(false);
    }

    private void releaseAll() {
        services.input().releaseAllMovement();
        services.breaking().setBreaking(false);
    }

    /** Passive exact maintenance checkpoint. No services, geometry, clocks or planner callbacks run. */
    public dev.veltraph.client.farming.module.FarmMaintenanceState maintenanceState() {
        return dev.veltraph.client.farming.module.FarmMaintenanceState.of(this,
                "config", config,
                "services", services,
                "geometry", geometry instanceof dev.veltraph.client.farming.movement.WorldRowGeometry actual ? actual.maintenanceState() : dev.veltraph.client.farming.module.FarmMaintenanceState.identity(geometry),
                "holdPolicyKey", holdPolicyKey,
                "toolReady", toolReady,
                "state", state,
                "currentTurnDirection", currentTurnDirection,
                "lastTurnTimestampMs", lastTurnTimestampMs,
                "turnTicksLeft", turnTicksLeft,
                "aim", aim == null ? null : aim.maintenanceState(),
                "profile", profile);
    }
}
