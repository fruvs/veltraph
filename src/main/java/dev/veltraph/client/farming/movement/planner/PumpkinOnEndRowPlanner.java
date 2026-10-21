package dev.veltraph.client.farming.movement.planner;

import dev.veltraph.client.farming.movement.TurnDirection;
import dev.veltraph.client.log.VeltraphLog;
import dev.veltraph.client.service.FarmServices;

import java.util.function.BooleanSupplier;

/**
 * Local forward-corridor adaptation for the explicitly selected captured gourd layout. The decoded
 * literal-side/original-yaw turn sequence belongs to {@link PumpkinSourceRowPlanner}; this adapter's
 * 180-degree view change is not source parity. Runtime supplies actual adjacent-row destinations.
 *
 * <p>Adaptation with recovered state names {@code PumpkinRowState{FARMING, ON_END_ROW}}:
 * <ul>
 *   <li><b>FARMING</b>: walk + break until the block-ahead row-end predicate fails, then release left
 *       click and forward and transition to {@code ON_END_ROW}.</li>
 *   <li><b>ON_END_ROW</b>: when the adjacent-row stand position is valid, <b>signal</b> the open lane
 *       side + a 180° heading-flip <i>intent</i>, then <b>step laterally</b> into the open lane while
 *       the camera pans smoothly to the flipped heading, and finally return to {@code FARMING}.</li>
 * </ul>
 *
 * <p><b>Smooth-aim parity change (this revision):</b> the planner no longer performs a one-shot camera
 * snap ({@code services.rotation().aim(yaw+180, pitch)}). Instead it raises a <b>heading-flip signal</b>
 * (see {@link #consumeHeadingFlip()} / {@link #stepSide()}) that the owning module turns into a
 * macro-owned heading change driven by {@link dev.veltraph.client.farming.movement.FarmAimController}
 * across multiple ticks — exactly how {@code SShapeRowEngine} handles its boustrophedon row turn. The
 * lateral lane step here is gated on the module reporting the smooth aim is <b>aligned</b> (the injected
 * {@code aimAligned} supplier) and the player is on the ground, so the strafe only commits once the
 * camera has actually panned to the new heading.
 *
 * <p>This class stays pure (config-free; only services + the injected {@link World} + the aim gate), so
 * it is unit-testable with fakes.
 */
public final class PumpkinOnEndRowPlanner {

    public enum PumpkinRowState {
        FARMING,
        ON_END_ROW
    }

    /** Geometry queries; faked in tests, world-backed at runtime. */
    public interface World {
        boolean atRowEnd();

        /** The adjacent-row stand position is valid (can step into the next lane). */
        boolean nextLaneStandable();

        /** Which side the next lane is on: {@link TurnDirection#LEFT}/{@code RIGHT}, or FORWARD if none. */
        TurnDirection openLaneSide();

        /** Current player yaw (degrees) — surfaced so callers can derive the flipped heading. */
        float currentYaw();

        /** Actual loaded adjacent-row centre. Empty preserves the legacy inferred timing adapter. */
        default java.util.Optional<dev.veltraph.client.world.Vec3> nextLaneCenter(TurnDirection side) {
            return java.util.Optional.empty();
        }
    }

    /** Ticks spent strafing into the next lane (once aim-aligned) before resuming FARMING. */
    public static final int LANE_STEP_TICKS = 6;

    private final FarmServices services;
    private final World world;
    /** Reports whether the module's smooth aim has reached the (possibly flipped) macro heading. */
    private final BooleanSupplier aimAligned;

    private PumpkinRowState state = PumpkinRowState.FARMING;
    private int stepTicksLeft;
    private TurnDirection stepSide = TurnDirection.FORWARD;

    /**
     * Raised once on entering the lane phase of {@code ON_END_ROW}; the module consumes it to flip its
     * macro-owned heading 180° and let the smooth aim pan there. Never drives the camera directly.
     */
    private boolean headingFlipPending;
    /** True between raising the flip and the smooth aim reporting aligned (waiting to commit the step). */
    private boolean awaitingAim;
    private dev.veltraph.client.world.Vec3 targetLane;
    private dev.veltraph.client.world.Direction stepWorldDirection;
    private int acknowledgedStepTicks;

    /**
     * Backward-compatible constructor: no external aim gate, so the lateral step commits as soon as the
     * next lane is standable (legacy snap-style cadence). Prefer the gated constructor at runtime.
     */
    public PumpkinOnEndRowPlanner(FarmServices services, World world) {
        this(services, world, () -> true);
    }

    public PumpkinOnEndRowPlanner(FarmServices services, World world, BooleanSupplier aimAligned) {
        this.services = services;
        this.world = world;
        this.aimAligned = aimAligned == null ? () -> true : aimAligned;
    }

    public PumpkinRowState state() {
        return state;
    }

    /** The lane side selected for the current/last row-end step (FORWARD = none chosen). */
    public TurnDirection stepSide() {
        return stepSide;
    }

    /** Whether a lateral strafe step is currently in progress. */
    /** Physical transfer heading captured before the final return-heading change. */
    public java.util.Optional<dev.veltraph.client.world.Direction> stepHeading() {
        return targetLane==null?java.util.Optional.empty():java.util.Optional.ofNullable(stepWorldDirection);
    }

    public boolean stepping() {
        return stepTicksLeft > 0;
    }

    public void start() {
        state = PumpkinRowState.FARMING;
        stepTicksLeft = 0;
        stepSide = TurnDirection.FORWARD;
        headingFlipPending = false;
        awaitingAim = false;
        targetLane=null;stepWorldDirection=null;acknowledgedStepTicks=0;
    }

    public void stop() {
        state = PumpkinRowState.FARMING;
        stepTicksLeft = 0;
        stepSide = TurnDirection.FORWARD;
        headingFlipPending = false;
        awaitingAim = false;
        targetLane=null;stepWorldDirection=null;acknowledgedStepTicks=0;
        services.input().releaseAllMovement();
        services.breaking().setBreaking(false);
    }

    /**
     * Whether the planner is requesting a 180° macro-heading flip this tick. The module calls this to
     * pick up the intent; it is auto-cleared on read so the flip is applied exactly once.
     */
    public boolean consumeHeadingFlip() {
        boolean pending = headingFlipPending;
        headingFlipPending = false;
        return pending;
    }

    public void tick() {
        switch (state) {
            case FARMING -> tickFarming();
            case ON_END_ROW -> tickOnEndRow();
        }
    }

    private void tickFarming() {
        // inferred: forward+break held continuously while farming the row (deterministic, mirrors the
        // Wart/Crops + Cactus world-adapter cadence). PUMPKIN_ANTI_GHOST_BLOCK cadence is gated by the
        // module before tick(); the planner asserts the desired break state each tick.
        services.breaking().setBreaking(true);
        services.input().setForwardHeld(true);
        if (world.atRowEnd()) {
            services.breaking().setBreaking(false);
            services.input().setForwardHeld(false);
            state = PumpkinRowState.ON_END_ROW;
            stepTicksLeft = 0;
            awaitingAim = false;
            VeltraphLog.details("PumpkinOnEndRowPlanner",
                    "FARMING -> ON_END_ROW (row end: forward lane invalid)");
        }
    }

    private void tickOnEndRow() {
        // Phase 1: arm the step. The first tick the next lane is reachable, choose the lane side and
        // raise the heading-flip signal for the module's smooth aim. Do NOT strafe yet — wait for the
        // module to report the camera has panned to the flipped heading (awaitingAim).
        if (stepTicksLeft == 0 && !awaitingAim) {
            if (!world.nextLaneStandable()) {
                VeltraphLog.verbose("PumpkinOnEndRowPlanner.tickOnEndRow",
                        () -> "waiting: next lane not standable");
                return;
            }
            TurnDirection side = world.openLaneSide();
            if (side != TurnDirection.LEFT && side != TurnDirection.RIGHT) {
                // No lateral lane: still request the heading flip, then resume forward immediately
                // (the smooth aim pans while FARMING re-asserts forward).
                stepSide = TurnDirection.FORWARD;
                headingFlipPending = true;
                state = PumpkinRowState.FARMING;
                VeltraphLog.details("PumpkinOnEndRowPlanner",
                        "ON_END_ROW -> FARMING (no lateral lane; flip-only)");
                return;
            }
            stepSide = side;
            targetLane=world.nextLaneCenter(side).orElse(null);
            stepWorldDirection=side.resolve(dev.veltraph.client.world.Direction.fromYaw(world.currentYaw()));
            acknowledgedStepTicks=0;
            headingFlipPending = true;
            awaitingAim = true;
            VeltraphLog.details("PumpkinOnEndRowPlanner",
                    "ON_END_ROW arm lane step side=" + side + " (heading flip signalled)");
            return;
        }

        // Phase 2: wait for the smooth aim to reach the flipped heading + be on the ground before the
        // lateral step commits (no snap; mirrors SShapeRowEngine's grounded()+aimReady() gate).
        if (awaitingAim) {
            if (!aimAligned.getAsBoolean() || !services.player().onGround()) {
                VeltraphLog.verbose("PumpkinOnEndRowPlanner.tickOnEndRow",
                        () -> "waiting for smooth aim/ground before lane step");
                return;
            }
            awaitingAim = false;
            stepTicksLeft = LANE_STEP_TICKS;
        }

        if(targetLane!=null&&stepTicksLeft>0) {
            // Capture the physical destination before turning. Literal LEFT/RIGHT invert after a
            // 180-degree view change, so derive the key from the measured post-turn facing.
            services.input().releaseAllMovement();services.breaking().setBreaking(false);
            if(services.player().position().horizontalDistanceTo(targetLane)<=.10) {
                targetLane=null;stepTicksLeft=0;state=PumpkinRowState.FARMING;return;
            }
            if(++acknowledgedStepTicks>160)return; // no invented arrival; leave inputs released
            var facing=dev.veltraph.client.world.Direction.fromYaw(world.currentYaw());
            services.input().setForwardHeld(stepWorldDirection==facing);
            services.input().setBackHeld(stepWorldDirection==facing.opposite());
            services.input().setLeftHeld(stepWorldDirection==facing.rotateYCounterclockwise());
            services.input().setRightHeld(stepWorldDirection==facing.rotateYClockwise());
            // Latch ordinary slow walking throughout the one-block dry transfer. Releasing W at
            // full speed leaves enough vanilla inertia to overlap the next stem's slab guard.
            services.input().setSneakHeld(true);
            services.breaking().setBreaking(true); // owning module/native guard allows only actual crop targets
            return;
        }

        // Legacy inferred adapter only: drive the lateral step (strafe + forward + break) for LANE_STEP_TICKS, then resume.
        if (stepTicksLeft > 0) {
            stepTicksLeft--;
            applyStrafe(stepSide);
            services.input().setForwardHeld(true);
            services.breaking().setBreaking(true);
            if (stepTicksLeft == 0) {
                releaseStrafe();
                state = PumpkinRowState.FARMING;
                VeltraphLog.details("PumpkinOnEndRowPlanner",
                        "ON_END_ROW -> FARMING (lane step complete, side=" + stepSide + ")");
            }
        }
    }

    private void applyStrafe(TurnDirection side) {
        services.input().setLeftHeld(side == TurnDirection.LEFT);
        services.input().setRightHeld(side == TurnDirection.RIGHT);
    }

    private void releaseStrafe() {
        services.input().setLeftHeld(false);
        services.input().setRightHeld(false);
    }

    /** Passive exact maintenance checkpoint. No services, geometry, clocks or planner callbacks run. */
    public dev.veltraph.client.farming.module.FarmMaintenanceState maintenanceState() {
        return dev.veltraph.client.farming.module.FarmMaintenanceState.of(this,
                "services", services,
                "world", world,
                "aimAligned", aimAligned,
                "state", state,
                "stepTicksLeft", stepTicksLeft,
                "stepSide", stepSide,
                "headingFlipPending", headingFlipPending,
                "awaitingAim", awaitingAim,
                "targetLane", targetLane,
                "stepWorldDirection", stepWorldDirection,
                "acknowledgedStepTicks", acknowledgedStepTicks);
    }
}
