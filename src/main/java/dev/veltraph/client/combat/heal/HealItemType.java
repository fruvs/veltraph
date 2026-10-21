package dev.veltraph.client.combat.heal;

import java.util.List;

/** Readable source enum order, exact base items, captions and per-type values. */
public enum HealItemType {
    HYPERION("minecraft:iron_sword", 5500, 300, "Astraea", "Hyperion", "Scylla", "Valkyrie"),
    FLORID("minecraft:golden_sword", 0, 70, "Florid", "Ornate Zombie Sword"),
    WAND_OF_ATONEMENT("minecraft:stick", 6000, 240, "Wand of Atonement"),
    WAND_OF_RESTORATION("minecraft:stick", 6000, 200, "Wand of Restoration"),
    WAND_OF_MENDING("minecraft:stick", 6000, 100, "Wand of Mending");

    private final String itemId;
    private final long cooldownMillis;
    private final int manaCost;
    private final List<String> captions;

    HealItemType(String itemId, long cooldownMillis, int manaCost, String... captions) {
        this.itemId = itemId;
        this.cooldownMillis = cooldownMillis;
        this.manaCost = manaCost;
        this.captions = List.of(captions);
    }

    public long cooldownMillis() { return cooldownMillis; }
    /** Source readiness cost; exposing this value does not prove a native mana binding. */
    public int manaCost() { return manaCost; }

    public static HealItemType classify(String itemId, String displayName, boolean empty) {
        if (empty || itemId == null || displayName == null) return null;
        for (var type : values()) {
            if (type.itemId.equals(itemId) && type.captions.stream().anyMatch(displayName::contains)) return type;
        }
        return null;
    }
}
