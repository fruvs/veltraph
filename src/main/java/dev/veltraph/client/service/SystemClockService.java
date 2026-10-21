package dev.veltraph.client.service;

/**
 * Real runtime {@link ClockService}: wall-clock milliseconds plus a tick counter advanced once per
 * client tick by the bootstrap. Pure (JDK-only).
 */
public final class SystemClockService implements ClockService {

    private long tick;

    @Override
    public long nowMillis() {
        return System.currentTimeMillis();
    }

    @Override
    public long currentTick() {
        return tick;
    }

    /** Advance the client-tick counter; call once per client tick. */
    public void advanceTick() {
        tick++;
    }
}
