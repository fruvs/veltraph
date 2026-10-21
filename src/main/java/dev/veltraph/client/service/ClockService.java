package dev.veltraph.client.service;

/**
 * Time source for cooldowns and tick-based scheduling. Abstracted so planners can be driven by a
 * deterministic fake clock in tests instead of the real game loop.
 */
public interface ClockService {

    /** Wall-clock milliseconds, used for millisecond cooldowns (e.g. {@code turnCooldownMs}). */
    long nowMillis();

    /** Monotonically increasing client tick counter, used for tick-period planners. */
    long currentTick();
}
