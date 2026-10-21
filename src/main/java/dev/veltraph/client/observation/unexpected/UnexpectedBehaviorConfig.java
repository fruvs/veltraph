package dev.veltraph.client.observation.unexpected;

import dev.veltraph.client.config.ConfigKey;

/** Local observation policy. Attribution itself cannot grant immunity from other safety stops. */
public final class UnexpectedBehaviorConfig {
    private UnexpectedBehaviorConfig() {}
    public static final ConfigKey<Boolean> ENABLED=ConfigKey.ofBool("UNEXPECTED_OBSERVATION_ENABLED",true);
    public static final ConfigKey<Boolean> NOTIFY=ConfigKey.ofBool("UNEXPECTED_OBSERVATION_NOTIFY",true);
    public static final ConfigKey<Boolean> AUTO_STOP=ConfigKey.ofBool("UNEXPECTED_OBSERVATION_AUTO_STOP",false);
    public static final ConfigKey<Long> ALERT_COOLDOWN_MS=ConfigKey.ofLong("UNEXPECTED_OBSERVATION_ALERT_COOLDOWN_MS",5000L);
}
