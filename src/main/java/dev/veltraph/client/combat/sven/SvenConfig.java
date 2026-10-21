package dev.veltraph.client.combat.sven;

import dev.veltraph.client.config.ConfigKey;

/** Blank identities are intentional: historical Superboom/Bazaar labels are not stable bindings. */
public final class SvenConfig {
    private SvenConfig(){}
    /** Validated JSON contract for the normal native constructor. Empty never grants readiness. */
    public static final ConfigKey<String> SVEN_SOURCE_BINDING_PROFILE=new ConfigKey<>("SVEN_SOURCE_BINDING_PROFILE","");
    public static final ConfigKey<SvenCaveProfile> SVEN_CAVE_TYPE=ConfigKey.ofEnum("SVEN_CAVE_TYPE",SvenCaveProfile.DARK_OAK);
    public static final ConfigKey<String> SVEN_TARGET_NAMES=new ConfigKey<>("SVEN_TARGET_NAMES","");
    public static final ConfigKey<String> SVEN_RESOURCE_NAME=new ConfigKey<>("SVEN_RESOURCE_NAME","");
    public static final ConfigKey<String> SVEN_RESOURCE_ALTERNATIVE_NAME=new ConfigKey<>("SVEN_RESOURCE_ALTERNATIVE_NAME","");
    public static final ConfigKey<String> SVEN_PURCHASE_RECEIPT_PREFIX=new ConfigKey<>("SVEN_PURCHASE_RECEIPT_PREFIX","");
    public static final ConfigKey<String> SVEN_PURCHASE_RECEIPT_ITEM=new ConfigKey<>("SVEN_PURCHASE_RECEIPT_ITEM","");
    public static final ConfigKey<String> SVEN_TRAVEL_DENIED_MESSAGE=new ConfigKey<>("SVEN_TRAVEL_DENIED_MESSAGE","");
    public static final ConfigKey<String> SVEN_BAZAAR_RETRY_MESSAGES=new ConfigKey<>("SVEN_BAZAAR_RETRY_MESSAGES","");
    public static final ConfigKey<Boolean> SVEN_AUTO_TOOL_SWAP=ConfigKey.ofBool("SVEN_AUTO_TOOL_SWAP",false);
    public static final ConfigKey<Boolean> SVEN_AUTO_SLAYER=ConfigKey.ofBool("SVEN_AUTO_SLAYER",false);
}
