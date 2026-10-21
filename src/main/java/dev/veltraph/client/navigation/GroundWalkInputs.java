package dev.veltraph.client.navigation;

import dev.veltraph.client.service.InputService;

/** Single absolute ground-input decision; active forward/jump are never release/repressed. */
public final class GroundWalkInputs {
    private GroundWalkInputs() {}
    public static void apply(InputService input,WalkController.Output out) {
        if(out.state()!=WalkController.State.WALKING){input.releaseAllMovement();return;}
        input.setForwardHeld(out.forward());input.setJumpHeld(out.jump());
        input.setBackHeld(false);input.setLeftHeld(false);input.setRightHeld(false);
        input.setSneakHeld(false);input.setSprintHeld(false);
    }
}
