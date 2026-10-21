package dev.veltraph.client.combat.quiver;

import dev.veltraph.client.config.ConfigKey;

/** Explicit ingress contract. The recovered reference does not establish an NPC name/type or warp. */
public final class QuiverIngressConfig {
    private QuiverIngressConfig() {}
    public enum Opener { HOTBAR_ITEM, NAMED_NPC }
    public static final ConfigKey<Opener> QUIVER_OPENER_MODE=ConfigKey.ofEnum("QUIVER_OPENER_MODE",Opener.HOTBAR_ITEM);
    public static final ConfigKey<String> QUIVER_NPC_NAME=new ConfigKey<>("QUIVER_NPC_NAME","");
    public static final ConfigKey<String> QUIVER_NPC_TYPE=new ConfigKey<>("QUIVER_NPC_TYPE","");
    public static final ConfigKey<Integer> QUIVER_NPC_MAX_DISTANCE=ConfigKey.ofInt("QUIVER_NPC_MAX_DISTANCE",16);
}
