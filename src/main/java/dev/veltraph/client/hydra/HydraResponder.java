package dev.veltraph.client.hydra;

import dev.veltraph.client.hydra.response.HydraMovementStep;
import dev.veltraph.client.hydra.response.HydraReaction;
import dev.veltraph.client.hydra.response.HydraResponseAction;

import java.util.List;

/**
 * The Minecraft execution seam for the LOCAL_INCLUDE staff-response layer: it carries out the local,
 * in-game actions the pure {@code HydraResponseHandler} decides on (a chat reply line, a re-route command,
 * or a fix-angle pitch set). Injected into {@link HydraAntiStaff} so the orchestrator and the decision
 * logic stay Minecraft-free and unit-testable; the MC adapter supplies the real implementation, tests
 * supply a recorder.
 *
 * <p><b>Local-only.</b> {@link #sendChat(String)} / {@link #sendCommand(String)} go over the same vanilla
 * in-game client path the recovered rewarp command uses — no {@code java.net}, no sockets, no backend/C2.
 * {@link #fixAngle(double, double)} adjusts the local camera only.
 */
public interface HydraResponder {

    /** Send a vanilla in-game chat message (a staff-reply template line). Best-effort; must not crash. */
    void sendChat(String message);

    /** Run a vanilla in-game command without a leading slash (the re-route). Best-effort; must not crash. */
    void sendCommand(String command);

    /**
     * Begin a <b>smooth</b> ease of the local player's look toward {@code targetYaw} / {@code targetPitch}
     * degrees (fix-angle). The motion is advanced over subsequent ticks via {@link #tick()} — not an instant
     * snap. Either may be {@code NaN} to leave that axis unchanged. Best-effort; must not crash.
     */
    void fixAngle(double targetYaw, double targetPitch);

    /**
     * Begin a local movement reaction ({@link HydraReaction}) by running its {@code steps} over the vanilla
     * movement inputs across the following ticks (advanced via {@link #tick()}). Local-only — drives the
     * client's own key inputs, never the network. Best-effort; must not crash.
     */
    default void move(HydraReaction reaction, List<HydraMovementStep> steps) {
    }

    /**
     * Advance any in-progress smooth fix-angle ease and/or movement reaction by one client tick. Called
     * every tick by {@code HydraAntiStaff} (even when no new action fired this tick). Best-effort.
     */
    default void tick() {
    }

    /**
     * Whether a local look or movement reaction is currently in flight (smooth fix-angle ease still easing,
     * or a movement step sequence still playing). Used by the Developer Mode dev-test tick path to advance an
     * explicitly dev-queued reaction <b>only while it is active</b> (so the path is bounded, never ticking
     * forever) without weakening the production macro gate. Default false.
     */
    default boolean hasActiveLocalReaction() {
        return false;
    }

    /**
     * Abort any in-progress local look/movement reaction and release owned inputs where applicable. Called
     * when Hydra is disabled or the world stops, so stale reactions cannot continue after the response state
     * machine has been reset.
     */
    default void reset() {
    }

    /**
     * Dispatch one decided action to the right method. {@link HydraResponseAction.Kind#STOP_MACRO} is NOT
     * handled here — {@code HydraAntiStaff} executes it through its stop/release runnables, so the responder
     * stays purely about chat/command/aim/movement.
     */
    default void execute(HydraResponseAction action) {
        if (action == null) {
            return;
        }
        switch (action.kind()) {
            case CHAT -> sendChat(action.text());
            case COMMAND -> sendCommand(action.text());
            case FIX_ANGLE -> fixAngle(action.yaw(), action.pitch());
            case MOVE -> move(action.reaction(), action.steps());
            case STOP_MACRO -> { /* handled by HydraAntiStaff */ }
        }
    }

    /** A responder that does nothing (tests / headless / when the response layer is unwired). */
    HydraResponder NONE = new HydraResponder() {
        @Override
        public void sendChat(String message) {
        }

        @Override
        public void sendCommand(String command) {
        }

        @Override
        public void fixAngle(double targetYaw, double targetPitch) {
        }
    };
}
