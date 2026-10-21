package dev.veltraph.client.dev;

import dev.veltraph.client.hydra.HydraEvent;

/**
 * The Minecraft execution seam for {@link DevTestAction}s. The pure action callbacks call these methods;
 * the MC adapter ({@code dev/mc/McDevTestContext}) backs them with the live Veltraph services, while tests
 * supply a recording fake. Keeping the seam an interface keeps the action logic Minecraft-free and
 * unit-testable.
 *
 * <p>Every method is <b>local</b> except the two explicitly named {@code server*} methods, which are only
 * ever reached by SERVER_BOUND actions after the registry's explicit-allow gate.
 */
public interface DevTestContext {

    // ---- context read (for logging) ----

    /** The current area name (e.g. {@code GARDEN}), or {@code "UNKNOWN"}. */
    String areaName();

    /** A one-line context summary (on Hypixel / in SkyBlock / area). */
    String contextSummary();

    /** A one-line summary of running modules (or "no macro running"). */
    String moduleStateSummary();

    // ---- local macro / input lifecycle ----

    /** Stop all running macros. Returns the number that were running. */
    int stopAllMacros();

    /** Release all script-owned input (WASD/jump/sneak/attack/use). */
    void releaseInput();

    // ---- failsafe / gate ----

    /** Evaluate the wrong-location activation gate for a representative module against the live context. */
    String gateDecision();

    // ---- hydra / anti-staff (local) ----

    /** Run the real local anti-staff alert path for a synthetic event (no auto-stop). Returns a summary. */
    String simulateHydraAlert(HydraEvent.Type type);

    /** Classify a synthetic event's rare/parity predicates + response eligibility (local; booleans only). */
    String classifyHydra(HydraEvent.Type type);

    /** Set the local camera angle (fix-angle / auto-angle test). Local only. */
    void fixAngle(double yaw, double pitch);

    /** The local player's current look pitch (degrees), or {@code NaN} if unavailable. */
    double currentPitch();

    /** The local player's current look yaw (degrees), or {@code NaN} if unavailable. */
    double currentYaw();

    /** The configured fix-angle pitch window {@code [min,max]} (for the fix-angle test target). */
    double fixAngleMinPitch();

    double fixAngleMaxPitch();

    /**
     * Begin a local movement reaction ({@code CAGE/BARRIER/FLOOR_CHANGE/BACKUP}) using the configured
     * durations — local key inputs only, advanced over the following ticks. Returns a summary.
     */
    String runReaction(dev.veltraph.client.hydra.response.HydraReaction reaction);

    /**
     * Queue a local failsafe automated-response plan for a trigger type (LOOK/MOVE/JUMP/SNEAK/clicks),
     * bypassing the master/per-trigger gate so the dev harness works without staff. Local only; advanced by
     * the dev-test tick path. Returns a summary.
     */
    String runFailsafeResponse(dev.veltraph.client.failsafe.response.FailsafeResponseType type);

    /** Queue a focused failsafe routine (look sweep / jump+sneak / clicks / movement / full plan). Local only. */
    String runFailsafeRoutine(dev.veltraph.client.failsafe.response.FailsafeRoutine routine);

    /** Whether Hydra parity mode is on (for the parity/rare classification test). */
    boolean parityMode();

    /** A representative first-reply chat template line (for the chat-response dry-run / execute). */
    String firstReplyTemplate();

    /** The configured after-disconnect re-route command (without a leading slash). */
    String afterDisconnectCommand();

    /** The configured avoid-evacuation re-route command (without a leading slash). */
    String avoidEvacuationCommand();

    /** The configured rewarp command (without a leading slash). */
    String rewarpCommand();

    // ---- chat / command output ----

    /** Append a line to the local client chat HUD (never sent to the server). */
    void localChat(String message);

    /** Send a real chat message to the server. Only reached by SERVER_BOUND actions. Returns sent?. */
    boolean serverChat(String message);

    /** Run a real in-game command (no leading slash) on the server. SERVER_BOUND only. Returns sent?. */
    boolean serverCommand(String command);
}
