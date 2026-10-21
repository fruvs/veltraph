package dev.veltraph.client.garden.visitor;

import java.util.Locale;

/**
 * Garden-visitor rarity tier (the colored name prefix on the visitor in the Garden visitor menu). Used by
 * the {@link VisitorAcceptFilter#VALUABLE} policy. Source: {@code reports/_spec_parts/VisitorGui.md} +
 * {@code reports/MACRO_INVENTORY.md} §1 (visitor serving). The original "VALUABLE" predicate is hardened
 * (behavior-only); this enum gives the deterministic, source-aligned equivalent (high tiers = valuable).
 */
public enum VisitorRarity {
    COMMON(false),
    UNCOMMON(false),
    RARE(true),
    EPIC(true),
    LEGENDARY(true),
    MYTHIC(true),
    /** Named special visitors (e.g. Beth / Seymour) — always treated as valuable. */
    SPECIAL(true),
    UNKNOWN(false);

    private final boolean valuable;

    VisitorRarity(boolean valuable) {
        this.valuable = valuable;
    }

    /** Whether this tier counts as "valuable" for the {@link VisitorAcceptFilter#VALUABLE} filter. */
    public boolean isValuable() {
        return valuable;
    }
    /** Standard SkyBlock item/NPC rarity colors; unrelated or absent colors remain unknown. */
    public static VisitorRarity fromRgb(int rgb){return switch(rgb&0xffffff){
        case 0xffffff -> COMMON;case 0x55ff55 -> UNCOMMON;case 0x5555ff -> RARE;
        case 0xaa00aa -> EPIC;case 0xffaa00 -> LEGENDARY;case 0xff55ff -> MYTHIC;
        case 0xff5555 -> SPECIAL;default -> UNKNOWN;
    };}

    /** Lenient parse of a rarity word (e.g. from a stripped visitor name color), default {@link #UNKNOWN}. */
    public static VisitorRarity parse(String raw) {
        if (raw == null) {
            return UNKNOWN;
        }
        String s = raw.trim().toUpperCase(Locale.ROOT);
        for (VisitorRarity r : values()) {
            if (r.name().equals(s)) {
                return r;
            }
        }
        return UNKNOWN;
    }
}
