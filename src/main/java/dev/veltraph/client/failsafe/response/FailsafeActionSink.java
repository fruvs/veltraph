package dev.veltraph.client.failsafe.response;

/**
 * The Minecraft execution seam for the local failsafe response routine. The pure {@link FailsafeResponseRunner}
 * calls these; the MC adapter ({@code failsafe/response/mc/McFailsafeActionSink}) backs them with the live
 * rotation/input services, and tests supply a recorder. Keeping it an interface keeps the runner Minecraft-free
 * and unit-testable.
 *
 * <p>Every method is <b>local</b> (camera + vanilla key inputs) except {@link #sendCommand(String)}, which runs
 * a vanilla in-game command and is only reached by a {@code COMMAND} action (never produced by the built-in
 * profiles). No network/transport beyond that one named method.
 */
public interface FailsafeActionSink {

    /** The local player's current look yaw (degrees), or {@code NaN} if unavailable. */
    float currentYaw();

    /** The local player's current look pitch (degrees), or {@code NaN} if unavailable. */
    float currentPitch();

    /** Apply an absolute look (degrees) — one smooth-eased step toward a LOOK target. */
    void applyLook(float yaw, float pitch);

    /** Hold/release the movement keys for this tick (all false = released). */
    void setMovement(boolean forward, boolean back, boolean left, boolean right, boolean jump, boolean sneak);

    /** Hold/release the attack/left key (for ATTACK_CLICK). */
    void setAttack(boolean held);

    /** Hold/release the use/right key (for USE_CLICK). */
    void setUse(boolean held);

    /** Run a vanilla in-game command (no leading slash). Only reached by a COMMAND action. */
    void sendCommand(String command);

    /** Actual sink-issued controls remain, independent of a runner's logical active flag. */
    default boolean hasOwnedControls(){return false;}
    /** Capture old cleanup before callbacks can issue newer controls; native adapters return opaque receipts. */
    default Object captureOwnedControls(){return this;}
    default void releaseOwnedControls(Object exact){if(exact==this)releaseAll();}

    /** Release this response's owned movement/sneak/jump/attack/use input. Idempotent. */
    void releaseAll();

    /** A no-op sink (tests / headless / unwired). */
    FailsafeActionSink NONE = new FailsafeActionSink() {
        @Override public float currentYaw() { return Float.NaN; }
        @Override public float currentPitch() { return Float.NaN; }
        @Override public void applyLook(float yaw, float pitch) { }
        @Override public void setMovement(boolean f, boolean b, boolean l, boolean r, boolean j, boolean s) { }
        @Override public void setAttack(boolean held) { }
        @Override public void setUse(boolean held) { }
        @Override public void sendCommand(String command) { }
        @Override public void releaseAll() { }
    };
}
