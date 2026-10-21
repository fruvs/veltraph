package dev.veltraph.client.foraging.supply;
import dev.veltraph.client.config.ConfigKey;
/** Standalone selected chest-fill utility, independent of replant loops. */
public final class ForagingSupplyConfig {
    private ForagingSupplyConfig() {}
    public enum Type { BONEMEAL, SAPLING }
    public static final ConfigKey<Type> FORAGING_AUTO_FILL_TYPE=ConfigKey.ofEnum("FORAGING_AUTO_FILL_TYPE",Type.SAPLING);
    public static final ConfigKey<Boolean> FORAGING_CHEST_FILL_ENABLED=ConfigKey.ofBool("FORAGING_CHEST_FILL_ENABLED",false);
    public static final ConfigKey<String> FORAGING_CHEST_FILL_BINDINGS=new ConfigKey<>("FORAGING_CHEST_FILL_BINDINGS","");
}
