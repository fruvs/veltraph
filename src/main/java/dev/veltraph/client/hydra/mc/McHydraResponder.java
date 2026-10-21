package dev.veltraph.client.hydra.mc;

import dev.veltraph.client.hydra.HydraResponder;
import dev.veltraph.client.hydra.response.HydraLook;
import dev.veltraph.client.hydra.response.HydraMovementRunner;
import dev.veltraph.client.hydra.response.HydraMovementStep;
import dev.veltraph.client.hydra.response.HydraReaction;
import dev.veltraph.client.log.VeltraphLog;
import dev.veltraph.client.service.InputService;
import dev.veltraph.client.service.RotationService;
import dev.veltraph.client.service.ServerChatService;
import dev.veltraph.client.service.ServerCommandService;

import java.util.List;

/**
 * Minecraft execution seam for the LOCAL_INCLUDE staff-response layer. Carries out the local, in-game
 * actions the pure {@code HydraResponseHandler} decides on:
 * <ul>
 *   <li>chat reply → {@link ServerChatService} (vanilla in-game chat),</li>
 *   <li>re-route command → {@link ServerCommandService} (vanilla in-game command),</li>
 *   <li>fix-angle → a <b>smooth</b> eased turn via {@link HydraLook}, stepping {@link RotationService#aim}
 *       a bounded amount per client tick (no instant snap),</li>
 *   <li>movement reaction → a {@link HydraMovementRunner} sequence over the {@link InputService} inputs
 *       (CAGE/BARRIER/FLOOR_CHANGE/BACKUP).</li>
 * </ul>
 * The smooth look and movement both progress on {@link #tick()}, which {@code HydraAntiStaff} calls every
 * client tick. Every method is try/catch-degraded so a malformed action or signature drift can never break
 * the tick loop. <b>No network/transport is added</b>: chat/command go over the same vanilla client path the
 * recovered rewarp command uses; movement only drives the local key inputs.
 */
public final class McHydraResponder implements HydraResponder {

    private final ServerChatService serverChat;
    private final ServerCommandService serverCommand;
    private final RotationService rotation;
    private final InputService input;
    private final Runnable emergencyRelease;
    private final Runnable beforeControl;

    private final HydraLook look = new HydraLook(HydraLook.Settings.defaults());
    private final HydraMovementRunner movement = new HydraMovementRunner();
    private boolean movementHoldingInput;
    private long movementGeneration,lookGeneration;
    private final dev.veltraph.client.failsafe.response.mc.McFailsafeActionSink nativeMovement;

    public McHydraResponder(ServerChatService serverChat,
                            ServerCommandService serverCommand,
                            RotationService rotation,
                            InputService input) {
        this(serverChat, serverCommand, rotation, input, () -> {});
    }

    public McHydraResponder(ServerChatService serverChat, ServerCommandService serverCommand,
                            RotationService rotation, InputService input, Runnable emergencyRelease) {
        this(serverChat,serverCommand,rotation,input,emergencyRelease,()->{});
    }

    /** Root quiesces its exact previous owner before local look/movement authority is acquired. */
    public McHydraResponder(ServerChatService serverChat,ServerCommandService serverCommand,
                            RotationService rotation,InputService input,Runnable emergencyRelease,Runnable beforeControl) {
        this.beforeControl=java.util.Objects.requireNonNull(beforeControl);
        this.serverChat = serverChat;
        this.serverCommand = serverCommand;
        this.rotation = rotation;
        this.input = input;
        this.emergencyRelease = emergencyRelease == null ? () -> {} : emergencyRelease;
        nativeMovement=input instanceof dev.veltraph.client.service.mc.McInputService
                ?new dev.veltraph.client.failsafe.response.mc.McFailsafeActionSink(net.minecraft.client.Minecraft.getInstance(),rotation,input,null):null;
    }

    @Override
    public void sendChat(String message) {
        try {
            if (serverChat != null && message != null && !message.isBlank()) {
                serverChat.sendChat(message);
            }
        } catch (Throwable t) {
            VeltraphLog.details("McHydraResponder.sendChat", "failed: " + t.getClass().getSimpleName());
        }
    }

    @Override
    public void sendCommand(String command) {
        try {
            if (serverCommand != null && command != null && !command.isBlank()) {
                serverCommand.sendCommand(command);
            }
        } catch (Throwable t) {
            VeltraphLog.details("McHydraResponder.sendCommand", "failed: " + t.getClass().getSimpleName());
        }
    }

    @Override
    public void fixAngle(double targetYaw, double targetPitch) {
        try {
            if (rotation == null) {
                return;
            }
            float curYaw = rotation.yaw();
            float curPitch = rotation.pitch();
            // Either axis may be NaN → keep the current value for that axis as the target.
            float yaw = Double.isNaN(targetYaw) ? curYaw : (float) targetYaw;
            float pitch = Double.isNaN(targetPitch) ? curPitch : (float) targetPitch;
            long entered=++lookGeneration;beforeControl.run();if(entered!=lookGeneration)return;
            look.begin(yaw, pitch); // smooth — advanced over the following ticks in tick()
            VeltraphLog.basic("McHydraResponder.fixAngle", String.format(java.util.Locale.ROOT,
                    "queued smooth look: cur=(%.1f, %.1f) -> target=(%.1f, %.1f)", curYaw, curPitch, yaw, pitch));
        } catch (Throwable t) {
            VeltraphLog.details("McHydraResponder.fixAngle", "failed: " + t.getClass().getSimpleName());
        }
    }

    @Override
    public void move(HydraReaction reaction, List<HydraMovementStep> steps) {
        try {
            if (input == null || steps == null || steps.isEmpty()) {
                return;
            }
            long entered=++movementGeneration;beforeControl.run();if(entered!=movementGeneration)return;
            movement.start(steps);
            VeltraphLog.basic("McHydraResponder.move",
                    "queued movement reaction " + reaction + " steps=" + steps.size());
        } catch (Throwable t) {
            VeltraphLog.details("McHydraResponder.move", "failed: " + t.getClass().getSimpleName());
        }
    }

    @Override
    public void tick() {
        advanceLook();
        advanceMovement();
    }

    /** Actual final native responder fields only; never invokes services, callbacks or response execution. */
    public boolean nativeIdleCurrent(){return !look.active()&&!movement.active()&&!movementHoldingInput&&(nativeMovement==null||!nativeMovement.hasOwnedControls());}

    @Override
    public boolean hasActiveLocalReaction() {
        return look.active()||movement.active()||movementHoldingInput||(nativeMovement!=null&&nativeMovement.hasOwnedControls());
    }

    @Override public void reset(){
        long oldLook=lookGeneration,oldMovement=movementGeneration;
        boolean held=movementHoldingInput||(nativeMovement!=null&&nativeMovement.hasOwnedControls());
        // Queued/idle/look-only work never owns another source's keyboard state.
        try{if(held)releaseMovement();}
        finally{if(lookGeneration==oldLook){look.stop();lookGeneration++;}
            if(movementGeneration==oldMovement){movement.stop();movementHoldingInput=false;movementGeneration++;}}
    }
    private void releaseMovement(){
        if(nativeMovement!=null){nativeMovement.releaseAll();return;}
        try{if(input!=null)input.releaseAllMovement();}
        catch(Throwable t){VeltraphLog.details("McHydraResponder.reset","failed: "+t.getClass().getSimpleName());releaseIndependently();}
        finally{try{emergencyRelease.run();}catch(RuntimeException ignored){}}
    }

    private void releaseIndependently() {
        if (input == null) return;
        for (java.util.function.Consumer<Boolean> setter : java.util.List.<java.util.function.Consumer<Boolean>>of(
                input::setForwardHeld, input::setBackHeld, input::setLeftHeld, input::setRightHeld,
                input::setJumpHeld, input::setSneakHeld, input::setSprintHeld)) {
            try { setter.accept(false); } catch (RuntimeException ignored) { }
        }
    }

    private void advanceLook() {
        long advancing=lookGeneration,oldMovement=movementGeneration;
        try {
            if (rotation == null || !look.active()) {
                return;
            }
            float yaw=rotation.yaw();if(lookGeneration!=advancing)return;
            float pitch=rotation.pitch();if(lookGeneration!=advancing)return;
            float[] next=look.step(yaw,pitch);if(lookGeneration!=advancing)return;
            rotation.aim(next[0],next[1]);if(lookGeneration!=advancing)return;
            VeltraphLog.details("McHydraResponder.advanceLook", String.format(java.util.Locale.ROOT,
                    "step -> (%.1f, %.1f) target=(%.1f, %.1f)%s",
                    next[0], next[1], look.targetYaw(), look.targetPitch(), look.active() ? "" : " [done]"));
        } catch (Throwable t) {
            VeltraphLog.details("McHydraResponder.advanceLook", "failed: " + t.getClass().getSimpleName());
            if(lookGeneration==advancing&&movementGeneration==oldMovement)reset();
            else if(lookGeneration==advancing){look.stop();lookGeneration++;}
        }
    }

    private void advanceMovement() {
        long advancing=movementGeneration,oldLook=lookGeneration;
        if (input == null) {
            return;
        }
        try {
            if (!movement.active()) {
                if (movementHoldingInput) {
                    releaseMovement();
                    if(movementGeneration!=advancing)return;movementHoldingInput=false;
                    VeltraphLog.details("McHydraResponder.advanceMovement", "reaction finished — input released");
                }
                return;
            }
            HydraMovementStep step = movement.advance();
            if (step == null) {
                releaseMovement();
                if(movementGeneration!=advancing)return;movementHoldingInput=false;
                VeltraphLog.details("McHydraResponder.advanceMovement", "no step — input released");
                return;
            }
            movementHoldingInput=true; // Partial attempted publication still requires exact own cleanup.
            if(nativeMovement!=null)nativeMovement.setMovement(step.forward(),step.back(),step.left(),step.right(),step.jump(),step.sneak());
            else{
                input.setForwardHeld(step.forward());if(movementGeneration!=advancing)return;input.setBackHeld(step.back());if(movementGeneration!=advancing)return;
                input.setLeftHeld(step.left());if(movementGeneration!=advancing)return;input.setRightHeld(step.right());if(movementGeneration!=advancing)return;
                input.setJumpHeld(step.jump());if(movementGeneration!=advancing)return;input.setSneakHeld(step.sneak());
            }
            if(movementGeneration!=advancing)return;
            VeltraphLog.details("McHydraResponder.advanceMovement", "step held "
                    + (step.forward() ? "F" : "") + (step.back() ? "B" : "") + (step.left() ? "L" : "")
                    + (step.right() ? "R" : "") + (step.jump() ? "J" : "") + (step.sneak() ? "S" : "")
                    + " remaining=" + movement.remainingSteps());
            // Note: the final step is held for its full duration this tick; the inputs are released next
            // tick when {@code movement.active()} reads false (the top branch above).
        } catch (Throwable t) {
            VeltraphLog.details("McHydraResponder.advanceMovement", "failed: " + t.getClass().getSimpleName());
            if(movementGeneration==advancing&&lookGeneration==oldLook)reset();
            else if(movementGeneration==advancing){
                try{if(movementHoldingInput||(nativeMovement!=null&&nativeMovement.hasOwnedControls()))releaseMovement();}
                finally{if(movementGeneration==advancing){movement.stop();movementHoldingInput=false;movementGeneration++;}}
            }
        }
    }
}
