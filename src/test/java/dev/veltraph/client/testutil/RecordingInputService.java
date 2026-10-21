package dev.veltraph.client.testutil;

import dev.veltraph.client.service.InputService;

/** Records the last requested key-hold state for assertions. */
public final class RecordingInputService implements InputService {

    public boolean forward;
    public boolean back;
    public boolean left;
    public boolean right;
    public boolean sneak;
    public boolean jump;
    public boolean sprint;
    public int releaseAllCount;

    @Override public void setSprintHeld(boolean held) { sprint = held; }

    @Override
    public void setForwardHeld(boolean held) {
        this.forward = held;
    }

    @Override
    public void setBackHeld(boolean held) {
        this.back = held;
    }

    @Override
    public void setLeftHeld(boolean held) {
        this.left = held;
    }

    @Override
    public void setRightHeld(boolean held) {
        this.right = held;
    }

    @Override
    public void setSneakHeld(boolean held) {
        this.sneak = held;
    }

    @Override
    public void setJumpHeld(boolean held) {
        this.jump = held;
    }

    @Override
    public void releaseAllMovement() {
        forward = back = left = right = sneak = jump = sprint = false;
        releaseAllCount++;
    }
}
