package dev.veltraph.client.service.stub;

import dev.veltraph.client.service.BlockBreakingService;

/** Safe placeholder {@link BlockBreakingService} used until the Minecraft-backed adapter is implemented. */
public final class NoOpBlockBreakingService implements BlockBreakingService {

    @Override
    public void setBreaking(boolean breaking) {
    }

    @Override
    public boolean isBreaking() {
        return false;
    }
}
