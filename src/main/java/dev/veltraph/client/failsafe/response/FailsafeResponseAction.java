package dev.veltraph.client.failsafe.response;

/**
 * One local step of a {@link FailsafeResponsePlan}. Pure value type — no Minecraft. The
 * {@link FailsafeResponseRunner} executes a sequence of these over client ticks via the
 * {@link FailsafeActionSink} seam. Every kind is a <b>local</b> effect (camera / vanilla key inputs); the
 * only non-camera/key kind is {@link Kind#COMMAND}, which runs a vanilla in-game command and is never
 * produced by the built-in profiles (reserved for explicit configured re-routes).
 *
 * @param kind          what to do
 * @param durationTicks how many client ticks this action lasts (clamped to &ge; 1 on use; {@code LOOK}
 *                      uses it as a cap — it ends early when the eased look reaches the target)
 * @param dYaw          {@code LOOK}: relative yaw offset (degrees) from the look at action start
 * @param dPitch        {@code LOOK}: relative pitch offset (degrees) from the look at action start
 * @param forward       {@code MOVE}: hold forward
 * @param back          {@code MOVE}: hold back
 * @param left          {@code MOVE}: hold strafe-left
 * @param right         {@code MOVE}: hold strafe-right
 * @param command       {@code COMMAND}: the vanilla command (no leading slash); empty otherwise
 */
public record FailsafeResponseAction(Kind kind, int durationTicks, float dYaw, float dPitch,
                                     boolean forward, boolean back, boolean left, boolean right,
                                     String command) {

    public enum Kind {
        /** Smoothly ease the look by a relative ({@link #dYaw()}, {@link #dPitch()}) offset. */
        LOOK,
        /** Hold the given movement keys for the duration. */
        MOVE,
        /** Hold jump for the (short) duration — a hop. */
        JUMP,
        /** Hold sneak for the duration. */
        SNEAK,
        /** Hold the attack/left key for the (short) duration — a click. */
        ATTACK_CLICK,
        /** Hold the use/right key for the (short) duration — a click. */
        USE_CLICK,
        /** Hold nothing for the duration — a pause. */
        WAIT,
        /** Run a vanilla in-game command once (reserved; not used by the built-in profiles). */
        COMMAND
    }

    public static FailsafeResponseAction look(float dYaw, float dPitch, int ticks) {
        return new FailsafeResponseAction(Kind.LOOK, ticks, dYaw, dPitch, false, false, false, false, "");
    }

    public static FailsafeResponseAction move(boolean fwd, boolean back, boolean left, boolean right, int ticks) {
        return new FailsafeResponseAction(Kind.MOVE, ticks, 0f, 0f, fwd, back, left, right, "");
    }

    public static FailsafeResponseAction jump(int ticks) {
        return new FailsafeResponseAction(Kind.JUMP, ticks, 0f, 0f, false, false, false, false, "");
    }

    public static FailsafeResponseAction sneak(int ticks) {
        return new FailsafeResponseAction(Kind.SNEAK, ticks, 0f, 0f, false, false, false, false, "");
    }

    public static FailsafeResponseAction attackClick(int ticks) {
        return new FailsafeResponseAction(Kind.ATTACK_CLICK, ticks, 0f, 0f, false, false, false, false, "");
    }

    public static FailsafeResponseAction useClick(int ticks) {
        return new FailsafeResponseAction(Kind.USE_CLICK, ticks, 0f, 0f, false, false, false, false, "");
    }

    public static FailsafeResponseAction wait(int ticks) {
        return new FailsafeResponseAction(Kind.WAIT, ticks, 0f, 0f, false, false, false, false, "");
    }

    public static FailsafeResponseAction command(String command) {
        return new FailsafeResponseAction(Kind.COMMAND, 1, 0f, 0f, false, false, false, false,
                command == null ? "" : command);
    }
}
