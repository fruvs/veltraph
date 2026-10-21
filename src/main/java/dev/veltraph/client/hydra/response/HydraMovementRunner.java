package dev.veltraph.client.hydra.response;

import java.util.List;

/**
 * Pure sequencer for a local movement reaction: given an ordered list of {@link HydraMovementStep}s, it
 * yields the step to hold on each client tick, holding each step for its {@code durationTicks} before
 * advancing, and going inactive when the sequence finishes. No Minecraft — the MC seam
 * ({@code McHydraResponder}) calls {@link #advance()} each tick and applies the returned step's held inputs
 * to the {@code InputService}, releasing everything once {@link #active()} goes false. Unit-testable.
 */
public final class HydraMovementRunner {

    private List<HydraMovementStep> steps = List.of();
    private int index;
    private int ticksLeftInStep;
    private boolean active;

    /** Load a fresh reaction sequence. An empty/null list leaves the runner inactive. */
    public void start(List<HydraMovementStep> sequence) {
        this.steps = sequence == null ? List.of() : sequence;
        this.index = 0;
        this.active = !this.steps.isEmpty();
        primeStep();
    }

    private void primeStep() {
        ticksLeftInStep = index < steps.size() ? Math.max(1, steps.get(index).durationTicks()) : 0;
    }

    public boolean active() {
        return active;
    }

    /** Number of steps still to play, including the current one (read API / status). */
    public int remainingSteps() {
        return active ? Math.max(0, steps.size() - index) : 0;
    }

    /**
     * Advance one client tick: return the {@link HydraMovementStep} whose inputs should be held this tick,
     * or {@code null} when the sequence is finished/inactive (the caller then releases all inputs).
     */
    public HydraMovementStep advance() {
        if (!active || index >= steps.size()) {
            active = false;
            return null;
        }
        HydraMovementStep current = steps.get(index);
        ticksLeftInStep--;
        if (ticksLeftInStep <= 0) {
            index++;
            primeStep();
            if (index >= steps.size()) {
                active = false;
            }
        }
        return current;
    }

    /** Abort the sequence (e.g. on world stop / disable). */
    public void stop() {
        steps = List.of();
        index = 0;
        ticksLeftInStep = 0;
        active = false;
    }
}
