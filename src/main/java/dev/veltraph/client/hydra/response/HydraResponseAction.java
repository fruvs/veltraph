package dev.veltraph.client.hydra.response;

import java.util.List;

/**
 * One concrete, local reaction produced by the pure {@link HydraResponseHandler}. The handler returns a
 * timed sequence of these; the MC seam ({@code HydraResponder}) executes them. Keeping the action a plain
 * value type means the decision logic (what to say, when, what yaw/pitch, what movement) is fully
 * unit-testable without Minecraft.
 *
 * <p>Every kind is a <b>local, in-game</b> effect only:
 * <ul>
 *   <li>{@link Kind#CHAT} — send a vanilla in-game chat message (a staff-reply line).</li>
 *   <li>{@link Kind#COMMAND} — run a vanilla in-game command (the afterdisconnect / avoid-evacuation
 *       re-route, e.g. {@code lobby}).</li>
 *   <li>{@link Kind#FIX_ANGLE} — <b>smoothly</b> ease the look toward {@link #yaw()} / {@link #pitch()}
 *       over several ticks (fix-angle / auto-angle), not an instant snap.</li>
 *   <li>{@link Kind#MOVE} — run a local movement reaction ({@link #reaction()} expanded to {@link #steps()})
 *       over the vanilla movement inputs (CAGE/BARRIER/FLOOR_CHANGE/BACKUP).</li>
 *   <li>{@link Kind#STOP_MACRO} — stop all local macros + release input (the original {@code STOP_ALL}
 *       policy).</li>
 * </ul>
 * Nothing here is a network/transport addition; the chat/command go over the same vanilla client path the
 * recovered rewarp command already uses, and movement only drives the local key inputs.
 *
 * @param kind     what to do
 * @param text     the chat line ({@link Kind#CHAT}) or command without a leading slash ({@link Kind#COMMAND});
 *                 empty otherwise
 * @param yaw      the absolute target look yaw in degrees for {@link Kind#FIX_ANGLE}; {@code NaN} otherwise
 * @param pitch    the absolute target look pitch in degrees for {@link Kind#FIX_ANGLE}; {@code NaN} otherwise
 * @param reaction the movement situation for {@link Kind#MOVE}; {@code null} otherwise
 * @param steps    the expanded movement steps for {@link Kind#MOVE}; empty otherwise
 */
public record HydraResponseAction(Kind kind, String text, double yaw, double pitch,
                                  HydraReaction reaction, List<HydraMovementStep> steps) {

    public enum Kind {
        /** Send a vanilla in-game chat message (a staff-reply template line). */
        CHAT,
        /** Run a vanilla in-game command (afterdisconnect / avoid-evacuation re-route). */
        COMMAND,
        /** Smoothly ease the look to {@link #yaw()} / {@link #pitch()} degrees (fix-angle / auto-angle). */
        FIX_ANGLE,
        /** Run a local movement reaction ({@link #reaction()} / {@link #steps()}). */
        MOVE,
        /** Stop all local macros + release input (original STOP_ALL policy). */
        STOP_MACRO
    }

    public static HydraResponseAction chat(String message) {
        return new HydraResponseAction(Kind.CHAT, message, Double.NaN, Double.NaN, null, List.of());
    }

    public static HydraResponseAction command(String command) {
        return new HydraResponseAction(Kind.COMMAND, command, Double.NaN, Double.NaN, null, List.of());
    }

    public static HydraResponseAction fixAngle(double targetYaw, double targetPitch) {
        return new HydraResponseAction(Kind.FIX_ANGLE, "", targetYaw, targetPitch, null, List.of());
    }

    public static HydraResponseAction move(HydraReaction reaction, List<HydraMovementStep> steps) {
        return new HydraResponseAction(Kind.MOVE, "", Double.NaN, Double.NaN,
                reaction, steps == null ? List.of() : List.copyOf(steps));
    }

    public static HydraResponseAction stopMacro() {
        return new HydraResponseAction(Kind.STOP_MACRO, "", Double.NaN, Double.NaN, null, List.of());
    }
}
