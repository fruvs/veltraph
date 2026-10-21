package dev.veltraph.client.combat.heal;

import java.util.EnumMap;

/** Shared helper-level local use history. Production keeps one ledger across native heal handlers. */
public final class HealUseLedger {
    private final EnumMap<HealItemType, Long> emittedAt = new EnumMap<>(HealItemType.class);

    public boolean cooling(HealItemType type, long now) {
        return (double) (now - lastEmittedUse(type)) < type.cooldownMillis();
    }

    public void recordEmittedUse(HealItemType type, long now) {
        emittedAt.put(java.util.Objects.requireNonNull(type), now);
    }

    public long lastEmittedUse(HealItemType type) { return emittedAt.getOrDefault(type, 0L); }
}
