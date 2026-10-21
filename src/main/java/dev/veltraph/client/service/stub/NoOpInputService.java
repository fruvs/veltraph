package dev.veltraph.client.service.stub;

import dev.veltraph.client.service.InputService;

/**
 * Safe placeholder {@link InputService}: accepts every call but drives no real key. Used until the
 * Minecraft-backed adapter is implemented, so the framework runs without moving the player.
 */
public final class NoOpInputService implements InputService {

    @Override
    public void setForwardHeld(boolean held) {
    }

    @Override
    public void setBackHeld(boolean held) {
    }

    @Override
    public void setLeftHeld(boolean held) {
    }

    @Override
    public void setRightHeld(boolean held) {
    }

    @Override
    public void setSneakHeld(boolean held) {
    }

    @Override
    public void setJumpHeld(boolean held) {
    }

    @Override
    public void releaseAllMovement() {
    }
}
