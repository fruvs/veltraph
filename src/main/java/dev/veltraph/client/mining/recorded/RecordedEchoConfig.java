package dev.veltraph.client.mining.recorded;

import dev.veltraph.client.config.ConfigKey;

/** Source Echo design is a yaw/pitch pair and an ordered position/key-list recording. */
public final class RecordedEchoConfig {
    private RecordedEchoConfig() {}
    public static final ConfigKey<String> PROFILE=new ConfigKey<>("ECHO_RECORDED_PROFILE", "");
}
