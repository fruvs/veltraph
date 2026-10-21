package dev.veltraph.client.hydra.response;

import dev.veltraph.client.config.ConfigService;
import dev.veltraph.client.config.HydraConfig;

/**
 * Immutable per-tick snapshot of the LOCAL_INCLUDE staff-response configuration, so the pure
 * {@link HydraResponseHandler} never reads {@link ConfigService} (or Minecraft) directly. The adapter
 * builds one via {@link #fromConfig(ConfigService)}.
 *
 * <p>All fields trace to {@link HydraConfig} (spec: {@code HydraAntiStaffMechanism.md} §4-§6 + the original
 * response handler). The {@code parityMode}/{@code originalPolicy}/{@code alwaysReactRare}/
 * {@code parityTemplates} fields drive the closer parity rebuild (default-off so the safe Veltraph
 * defaults stay in effect until the user opts into original-compatible behavior).
 *
 * @param uiPolicy           the Veltraph UI policy (OFF/REPLY/REPLY_AND_FIX_ANGLE/FULL)
 * @param parityMode         use the original policy + exact templates instead of the UI policy
 * @param originalPolicy     the original-policy concept used when {@code parityMode} (REACT/STOP_ALL/IGNORE)
 * @param alwaysReactRare    force REACT for rare/high-severity checks (original HYDRA_ALWAYS_REACT_FOR_RARE_CHECKS)
 * @param parityTemplates    use the exact original {@code antistaff-messages.yml} lists for defaults
 * @param extreme            use the aggressive non-parity first list / escalate the chain
 * @param firstMessages      custom {@code |}-joined first list (blank → default)
 * @param secondMessages     custom {@code |}-joined second list (blank → default)
 * @param thirdMessages      custom {@code |}-joined third list (blank → default)
 * @param secondChance       probability [0,1] of an occasional second reply
 * @param thirdChance        probability [0,1] of an occasional third reply
 * @param minDelayMs         min humanized delay (ms) between chained messages (original Random.nextInt(50,2000))
 * @param maxDelayMs         max humanized delay (ms) between chained messages
 * @param cooldownMs         per-encounter cooldown (local-safety addition; original re-arms via AFTER_RESPONDING)
 * @param afterRespondingMs  settle time (ms) in AFTER_RESPONDING before re-arming to LISTENING
 * @param fixAngle           whether fix-angle is allowed (policy + toggle)
 * @param minPitch           lower bound of the in-band pitch check (degrees, recovered -38)
 * @param maxPitch           upper bound of the in-band pitch check (degrees, recovered 38)
 * @param fixAngleReentryFraction how far into the [min,max] band (from the violated edge, fraction of the
 *                                band width) the fix-angle target lands — so min/max shape the result
 * @param fixAngleYawJitter  +/- yaw randomization (degrees) applied to the fix-angle target
 * @param movementReactionsEnabled master toggle for the local CAGE/BARRIER/FLOOR_CHANGE/BACKUP movement reactions
 * @param reactionBackupTicks  BACKUP primary duration (client ticks)
 * @param reactionBarrierTicks BARRIER primary duration (client ticks)
 * @param reactionCageTicks    CAGE primary duration (client ticks)
 * @param reactionFloorChangeTicks FLOOR_CHANGE primary duration (client ticks)
 * @param avoidEvacuation    whether avoid-evacuation re-route is allowed (also escalates the message count)
 * @param avoidEvacuationCommand the avoid-evacuation command (no leading slash)
 * @param afterDisconnect    whether afterdisconnect re-route is allowed
 * @param afterDisconnectCommand the afterdisconnect command (no leading slash)
 * @param parityBypassCooldown in parity mode, re-arm via the state machine instead of the wall-clock cooldown
 * @param realignmentMessages optional local {@code |}-joined REALIGNMENT reply lines (blank → none)
 * @param lookMessages       optional local {@code |}-joined LOOK_UP/LOOK_DOWN chat lines (blank → seed from first list)
 * @param barrierMessages    optional local {@code |}-joined BARRIER reply lines (blank → none)
 * @param floorChangeMessages optional local {@code |}-joined FLOOR_CHANGE reply lines (blank → none)
 */
public record HydraResponseSettings(
        HydraResponsePolicy uiPolicy,
        boolean parityMode,
        HydraOriginalPolicy originalPolicy,
        boolean alwaysReactRare,
        boolean parityTemplates,
        boolean extreme,
        String firstMessages,
        String secondMessages,
        String thirdMessages,
        double secondChance,
        double thirdChance,
        long minDelayMs,
        long maxDelayMs,
        long cooldownMs,
        long afterRespondingMs,
        boolean fixAngle,
        double minPitch,
        double maxPitch,
        double fixAngleReentryFraction,
        double fixAngleYawJitter,
        boolean movementReactionsEnabled,
        int reactionBackupTicks,
        int reactionBarrierTicks,
        int reactionCageTicks,
        int reactionFloorChangeTicks,
        boolean avoidEvacuation,
        String avoidEvacuationCommand,
        boolean afterDisconnect,
        String afterDisconnectCommand,
        boolean parityBypassCooldown,
        String realignmentMessages,
        String lookMessages,
        String barrierMessages,
        String floorChangeMessages) {

    public static HydraResponseSettings fromConfig(ConfigService config) {
        return new HydraResponseSettings(
                config.get(HydraConfig.STAFF_RESPONSE_POLICY),
                config.get(HydraConfig.HYDRA_PARITY_MODE),
                config.get(HydraConfig.HYDRA_ORIGINAL_POLICY),
                config.get(HydraConfig.HYDRA_ALWAYS_REACT_FOR_RARE_CHECKS),
                config.get(HydraConfig.PARITY_TEMPLATES_ENABLED),
                config.get(HydraConfig.AUTO_REPLY_EXTREME),
                config.get(HydraConfig.AUTO_REPLY_FIRST_MESSAGES),
                config.get(HydraConfig.AUTO_REPLY_SECOND_MESSAGES),
                config.get(HydraConfig.AUTO_REPLY_THIRD_MESSAGES),
                config.get(HydraConfig.AUTO_REPLY_SECOND_CHANCE),
                config.get(HydraConfig.AUTO_REPLY_THIRD_CHANCE),
                config.get(HydraConfig.AUTO_REPLY_MIN_DELAY_MS),
                config.get(HydraConfig.AUTO_REPLY_MAX_DELAY_MS),
                config.get(HydraConfig.RESPONSE_COOLDOWN_MS),
                config.get(HydraConfig.AFTER_RESPONDING_MS),
                config.get(HydraConfig.FIX_ANGLE_ENABLED),
                config.get(HydraConfig.FIX_ANGLE_MIN_PITCH),
                config.get(HydraConfig.FIX_ANGLE_MAX_PITCH),
                config.get(HydraConfig.FIX_ANGLE_REENTRY_FRACTION),
                config.get(HydraConfig.FIX_ANGLE_YAW_JITTER),
                config.get(HydraConfig.MOVEMENT_REACTIONS_ENABLED),
                config.get(HydraConfig.REACTION_BACKUP_TICKS).intValue(),
                config.get(HydraConfig.REACTION_BARRIER_TICKS).intValue(),
                config.get(HydraConfig.REACTION_CAGE_TICKS).intValue(),
                config.get(HydraConfig.REACTION_FLOOR_CHANGE_TICKS).intValue(),
                config.get(HydraConfig.AVOID_EVACUATION_ENABLED),
                config.get(HydraConfig.AVOID_EVACUATION_COMMAND),
                config.get(HydraConfig.AFTER_DISCONNECT_ENABLED),
                config.get(HydraConfig.AFTER_DISCONNECT_COMMAND),
                config.get(HydraConfig.PARITY_BYPASS_COOLDOWN),
                config.get(HydraConfig.REALIGNMENT_MESSAGES),
                config.get(HydraConfig.LOOK_MESSAGES),
                config.get(HydraConfig.BARRIER_MESSAGES),
                config.get(HydraConfig.FLOOR_CHANGE_MESSAGES));
    }

    /**
     * The effective original-policy concept for this tick: the configured {@link #originalPolicy} when
     * parity mode is on, otherwise the UI policy mapped via {@link HydraOriginalPolicy#fromUiPolicy}.
     */
    public HydraOriginalPolicy effectivePolicy() {
        return parityMode ? originalPolicy : HydraOriginalPolicy.fromUiPolicy(uiPolicy);
    }

    /**
     * Whether the response layer is opted in at all: parity mode on, or a non-OFF UI policy. When false
     * the layer is fully silent — even the rare-check always-react override is suppressed so the Veltraph
     * default (UI policy OFF, parity off) never sends anything.
     */
    public boolean active() {
        return parityMode || (uiPolicy != null && uiPolicy != HydraResponsePolicy.OFF);
    }

    /** A live restriction invalidates the captured response, including commands already queued. */
    public boolean revokes(HydraResponseSettings captured) {
        if (captured == null) return false;
        return (captured.active() && !active()) || effectivePolicy() != captured.effectivePolicy()
                || parityMode != captured.parityMode || (captured.alwaysReactRare && !alwaysReactRare)
                || (captured.fixAngleAllowed() && !fixAngleAllowed())
                || (captured.reactionsAllowed() && !reactionsAllowed())
                || (captured.rerouteAllowed() && !rerouteAllowed())
                || (captured.avoidEvacuation && !avoidEvacuation)
                || (captured.afterDisconnect && !afterDisconnect)
                || !java.util.Objects.equals(avoidEvacuationCommand, captured.avoidEvacuationCommand)
                || !java.util.Objects.equals(afterDisconnectCommand, captured.afterDisconnectCommand);
    }

    /** Whether fix-angle may run this tick (parity: always allowed under REACT; non-parity: gated by the UI policy). */
    public boolean fixAngleAllowed() {
        if (!fixAngle) {
            return false;
        }
        return parityMode || (uiPolicy != null && uiPolicy.allowsFixAngle());
    }

    /** Whether a re-route command may run this tick (parity: allowed under REACT; non-parity: gated by FULL). */
    public boolean rerouteAllowed() {
        return parityMode || (uiPolicy != null && uiPolicy.allowsReroute());
    }

    /**
     * Whether the local-safety wall-clock cooldown applies this tick. In parity mode with
     * {@code parityBypassCooldown} the cooldown is bypassed (the state machine re-arms instead).
     */
    public boolean cooldownActive() {
        return !(parityMode && parityBypassCooldown);
    }

    /** Per-reaction durations for {@link HydraReactionPlanner}. */
    public HydraReactionPlanner.Settings reactionPlannerSettings() {
        return new HydraReactionPlanner.Settings(reactionBackupTicks, reactionBarrierTicks,
                reactionCageTicks, reactionFloorChangeTicks);
    }

    /**
     * Whether a local movement reaction may run this tick: the master toggle is on AND the response layer
     * is opted in via a non-OFF UI policy or parity mode (so the safe default stays fully passive).
     */
    public boolean reactionsAllowed() {
        return movementReactionsEnabled && active();
    }

    /** Build the template set for this tick (parity lists when parity mode / parity templates is on). */
    public ResponseTemplates templates() {
        return ResponseTemplates.build(firstMessages, secondMessages, thirdMessages, extreme,
                parityMode || parityTemplates);
    }
}
