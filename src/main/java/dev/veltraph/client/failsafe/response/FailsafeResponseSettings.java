package dev.veltraph.client.failsafe.response;

import dev.veltraph.client.config.ConfigService;
import dev.veltraph.client.config.FailsafeConfig;

import java.util.EnumSet;
import java.util.Set;

/**
 * Immutable per-use snapshot of the local failsafe automated-response configuration, so the pure
 * {@link FailsafeResponsePlanner} / {@link FailsafeResponseRunner} never read {@link ConfigService} (or
 * Minecraft) directly. All fields trace to {@link FailsafeConfig}.
 *
 * @param enabled         master switch ({@code FAILSAFE_AUTOMATED_RESPONSE_ENABLED}, default false)
 * @param profile         how elaborate the routine is
 * @param lookSweeps      how many alternating look sweeps the routine performs
 * @param lookRangeDeg    yaw amplitude (degrees) of each look sweep
 * @param lookTicks       client ticks budget per look sweep (its eased speed)
 * @param jump            allow a JUMP step
 * @param sneak           allow a SNEAK step
 * @param attack          allow an ATTACK_CLICK step
 * @param use             allow a USE_CLICK step
 * @param move            allow a MOVE shuffle step
 * @param moveTicks       client ticks per MOVE step
 * @param cooldownMs      minimum spacing between scheduled responses
 * @param maxTicks        hard cap on a running plan's total ticks
 * @param enabledTriggers the per-trigger toggles (a response only schedules for an enabled trigger)
 */
public record FailsafeResponseSettings(
        boolean enabled,
        FailsafeResponseProfile profile,
        int lookSweeps,
        double lookRangeDeg,
        int lookTicks,
        boolean jump,
        boolean sneak,
        boolean attack,
        boolean use,
        boolean move,
        int moveTicks,
        long cooldownMs,
        int maxTicks,
        Set<FailsafeResponseType> enabledTriggers) {

    public FailsafeResponseSettings {
        profile = profile == null ? FailsafeResponseProfile.LOCAL_SAFE : profile;
        enabledTriggers = enabledTriggers == null ? EnumSet.noneOf(FailsafeResponseType.class)
                : EnumSet.copyOf(enabledTriggers);
    }

    /** Whether the automated response may schedule for {@code type} (master on AND its per-trigger toggle on). */
    public boolean triggerEnabled(FailsafeResponseType type) {
        return enabled && type != null && enabledTriggers.contains(type);
    }

    /** Per-tick yaw/pitch cap for the smooth look ease, derived from the sweep range / tick budget. */
    public float lookSpeedPerTick() {
        int ticks = Math.max(1, lookTicks);
        double range = Math.max(1.0, lookRangeDeg);
        return (float) Math.max(1.0, range / ticks);
    }

    public static FailsafeResponseSettings fromConfig(ConfigService c) {
        EnumSet<FailsafeResponseType> triggers = EnumSet.noneOf(FailsafeResponseType.class);
        for (FailsafeResponseType type : FailsafeResponseType.values()) {
            if (c.get(FailsafeConfig.responseTriggerKey(type))) {
                triggers.add(type);
            }
        }
        return new FailsafeResponseSettings(
                c.get(FailsafeConfig.FAILSAFE_AUTOMATED_RESPONSE_ENABLED),
                c.get(FailsafeConfig.FAILSAFE_RESPONSE_PROFILE),
                c.get(FailsafeConfig.FAILSAFE_RESPONSE_LOOK_SWEEPS),
                c.get(FailsafeConfig.FAILSAFE_RESPONSE_LOOK_RANGE),
                c.get(FailsafeConfig.FAILSAFE_RESPONSE_LOOK_TICKS),
                c.get(FailsafeConfig.FAILSAFE_RESPONSE_JUMP),
                c.get(FailsafeConfig.FAILSAFE_RESPONSE_SNEAK),
                c.get(FailsafeConfig.FAILSAFE_RESPONSE_ATTACK),
                c.get(FailsafeConfig.FAILSAFE_RESPONSE_USE),
                c.get(FailsafeConfig.FAILSAFE_RESPONSE_MOVE),
                c.get(FailsafeConfig.FAILSAFE_RESPONSE_MOVE_TICKS),
                c.get(FailsafeConfig.FAILSAFE_RESPONSE_COOLDOWN_MS),
                c.get(FailsafeConfig.FAILSAFE_RESPONSE_MAX_TICKS),
                triggers);
    }
}
