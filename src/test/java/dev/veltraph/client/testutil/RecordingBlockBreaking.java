package dev.veltraph.client.testutil;

import dev.veltraph.client.service.BlockBreakingService;

/** Records the current breaking state for assertions. */
public final class RecordingBlockBreaking implements BlockBreakingService {

    public boolean breaking;

    @Override
    public void setBreaking(boolean breaking) {
        this.breaking = breaking;
    }

    @Override
    public boolean isBreaking() {
        return breaking;
    }
}
