package dev.veltraph.client.farming.domain;

/**
 * Master crop/run-target type. These are the 17 constants recovered from the original obfuscated
 * {@code CropType} enum (see {@code FarmingUiLayoutSelection.md} §2).
 *
 * <p>The original carried a single {@code String hypixelAPIName} field (a SkyBlock API id used for
 * HUD/state display, not a UI label). The exact string values were not recovered from the jar, so
 * they are intentionally not fabricated here (project hard rule: no invention). {@link #displayName}
 * is a Veltraph-assigned human label.
 *
 * <p>Note the deliberate absence of any "flower" value: the master enum has none, which is why
 * "Flowers" cannot be a farming run-mode and exists only as a FarmBuilder block type.
 */
public enum CropType {
    NETHER_WART("Nether Wart"),
    POTATO("Potato"),
    CARROT("Carrot"),
    WHEAT("Wheat"),
    COCOA_BEANS("Cocoa Beans"),
    SUGAR_CANE("Sugar Cane"),
    PUMPKIN("Pumpkin"),
    CACTUS("Cactus"),
    RED_MUSHROOM("Red Mushroom"),
    COBBLESTONE("Cobblestone"),
    ENDER_PEARL("Ender Pearl"),
    OAK_LOG("Oak Log"),
    SPRUCE_LOG("Spruce Log"),
    BIRCH_LOG("Birch Log"),
    JUNGLE_LOG("Jungle Log"),
    ACACIA_LOG("Acacia Log"),
    DARK_OAK_LOG("Dark Oak Log");

    private final String displayName;

    CropType(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
