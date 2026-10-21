package dev.veltraph.client.farming.domain;

/**
 * Movement/traversal mode for a farming profile. Extends the sketch in {@code REBUILD_PLAN.md} with
 * two source-accurate modes the deep-recovery reports identified ({@link #BACK_OPPOSITE} for
 * sugarcane and {@link #LANE_SWITCH} for cocoa), so no controller is mislabeled.
 */
public enum FarmMovementMode {
    /** Vertical wart: 40/20-tick two-counter still-detection machine (not the shared row engine). */
    VERTICAL,
    /** Boustrophedon row engine via the shared {@code MovementDirection} state machine. */
    S_SHAPE,
    /** Fixed user-side row traversal (e.g. Mushroom classical with {@code MUSHROOM_DIR}). */
    CLASSICAL,
    /** Mushroom staircase: FOUND/BLOCKED/NULL probe selector returning {@code MUSHROOM_DIR} vs FORWARD. */
    STAIRCASE,
    /** Cactus: single straight lane, inheriting the shared row engine. */
    STRAIGHT,
    /** Pumpkin/Melon: {@code PumpkinRowState{FARMING, ON_END_ROW}} end-row trigger. */
    ON_END_ROW,
    /** Sugarcane/Flowers: specialized {@code currentDir <-> BACK} toggle keyed on {@code SUGARCANE_DIR}. */
    BACK_OPPOSITE,
    /** Cocoa: lane-switch state machine ({@code FARMING <-> SWITCHING_ROW}) with same-tick sneak latch. */
    LANE_SWITCH,
    /** Creator-referenced stepped water lanes with fixed view and alternating lateral inputs. */
    WATER_LANES
}
