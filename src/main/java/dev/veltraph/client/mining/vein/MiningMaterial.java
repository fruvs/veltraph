package dev.veltraph.client.mining.vein;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Explicit archive representation migration. A matching block does not prove a server resource. */
public enum MiningMaterial {
    QUARTZ("quartz_block", "nether_quartz_ore"), DIAMOND("diamond_block"),
    EMERALD("emerald_block"), REDSTONE("redstone_block"), LAPIS("lapis_block"),
    GOLD("gold_block"), IRON("iron_block"), COAL("coal_block"), SULPHUR("sponge"),
    HARDSTONE("stone"), TITANIUM("polished_diorite"),
    GRAY_MITHRIL("gray_wool", "cyan_terracotta"),
    GREEN_MITHRIL("prismarine", "prismarine_bricks", "dark_prismarine"),
    BLUE_MITHRIL("light_blue_wool"), OPAL("white_stained_glass", "white_stained_glass_pane"),
    JASPER("magenta_stained_glass", "magenta_stained_glass_pane"),
    TOPAZ("yellow_stained_glass", "yellow_stained_glass_pane"),
    AMBER("orange_stained_glass", "orange_stained_glass_pane"),
    SAPPHIRE("light_blue_stained_glass", "light_blue_stained_glass_pane"),
    JADE("lime_stained_glass", "lime_stained_glass_pane"),
    AMETHYST("purple_stained_glass", "purple_stained_glass_pane"),
    // The archive's full Ruby entry decodes to cake. Correct representation requires red glass.
    RUBY("red_stained_glass", "red_stained_glass_pane"),
    AQUAMARINE("blue_stained_glass", "blue_stained_glass_pane"),
    PERIDOT("green_stained_glass", "green_stained_glass_pane"),
    ONYX("black_stained_glass", "black_stained_glass_pane"),
    CITRINE("brown_stained_glass", "brown_stained_glass_pane"),
    GLACITE("packed_ice"), UMBER("terracotta", "smooth_red_sandstone", "brown_terracotta"),
    TUNGSTEN("infested_cobblestone", "clay");

    private final Set<String> ids;
    MiningMaterial(String... paths) {
        ids = Set.of(java.util.Arrays.stream(paths).map(path -> "minecraft:" + path).toArray(String[]::new));
    }
    public Set<String> registryIds() { return ids; }
    public boolean matches(String registryId) { return ids.contains(registryId); }
    public static Optional<MiningMaterial> classify(String registryId) {
        if (registryId == null) return Optional.empty();
        for (var material : values()) if (material.matches(registryId)) return Optional.of(material);
        return Optional.empty();
    }
    public static Map<MiningMaterial,Integer> mithril(boolean gray, boolean green, boolean blue, boolean titanium) {
        return Map.of(GRAY_MITHRIL, gray ? 1 : 0, GREEN_MITHRIL, green ? 1 : 0,
                BLUE_MITHRIL, blue ? 1 : 0, TITANIUM, titanium ? 10 : 0);
    }
}
