package dev.veltraph.client.navigation;
import dev.veltraph.client.service.InputService;
/** Publishes only normal absolute key state through the caller's existing scoped input port. */
public final class GroundLocomotionInputs {
    private GroundLocomotionInputs(){}
    public static void apply(InputService input,GroundLocomotionPolicy.Decision out){
        if(out.blocked()){input.releaseAllMovement();return;}
        input.setForwardHeld(out.forward());input.setJumpHeld(out.jump());input.setSprintHeld(out.sprint());
        input.setBackHeld(false);input.setLeftHeld(false);input.setRightHeld(false);input.setSneakHeld(false);
    }
}
