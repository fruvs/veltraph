package dev.veltraph.client.foraging.hub;
import dev.veltraph.client.config.ConfigKey;
/** Explicit local server warp contract. Salted labels/destination metadata are not guessed. */
public final class HubForagingConfig {
    private HubForagingConfig(){}
    public static final ConfigKey<String> HUB_WARP_COMMAND=new ConfigKey<>("HUB_WARP_COMMAND","");
    /** Optional exact additional receipt. Blank uses the recovered native area/body completion. */
    public static final ConfigKey<String> HUB_WARP_ACCEPTED_MESSAGE=new ConfigKey<>("HUB_WARP_ACCEPTED_MESSAGE","");
    /** Exact rejection messages separated by newlines. */
    public static final ConfigKey<String> HUB_WARP_REJECTED_MESSAGES=new ConfigKey<>("HUB_WARP_REJECTED_MESSAGES","");
    /** Exact native registry identity and x,y,z destination receipt. */
    public static final ConfigKey<String> HUB_WARP_DIMENSION=new ConfigKey<>("HUB_WARP_DIMENSION","");
    public static final ConfigKey<String> HUB_WARP_DESTINATION=new ConfigKey<>("HUB_WARP_DESTINATION","");
    public static final ConfigKey<Double> HUB_WARP_DESTINATION_RADIUS=ConfigKey.ofDouble("HUB_WARP_DESTINATION_RADIUS",.75);
    public static final ConfigKey<Long> HUB_WARP_TIMEOUT_MS=ConfigKey.ofLong("HUB_WARP_TIMEOUT_MS",15000L);
}
