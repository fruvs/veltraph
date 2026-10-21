package dev.veltraph.client.service;

/**
 * Movement / key-hold surface. The farming planners drive the player by holding and releasing these
 * inputs (forward walk, lateral strafe, sneak-on-lane-switch, etc.).
 *
 * <p>Safety contract: {@link #releaseAllMovement()} must fully release every input and is called
 * whenever a module is disabled or stopped.
 */
public interface InputService {

    void setForwardHeld(boolean held);

    void setBackHeld(boolean held);

    void setLeftHeld(boolean held);

    void setRightHeld(boolean held);

    void setSneakHeld(boolean held);

    void setJumpHeld(boolean held);

    /** Ordinary sprint-key input; adapters without sprint support leave it unavailable. */
    default void setSprintHeld(boolean held) {}

    /** Release every movement, sneak, jump and sprint input. Idempotent. */
    void releaseAllMovement();
}
