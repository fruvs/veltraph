package dev.veltraph.client.testutil;

import dev.veltraph.client.service.ClockService;

/** Deterministic clock for tests. */
public final class FakeClock implements ClockService {

    private long millis;
    private long tick;

    @Override
    public long nowMillis() {
        return millis;
    }

    @Override
    public long currentTick() {
        return tick;
    }

    public void setMillis(long millis) {
        this.millis = millis;
    }

    public void setTick(long tick) {
        this.tick = tick;
    }

    public void advanceMillis(long delta) {
        this.millis += delta;
    }

    public void advanceTick() {
        this.tick++;
    }
}
