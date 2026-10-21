package dev.veltraph.client.combat.heal;

import java.util.EnumMap;
import java.util.List;
import java.util.Optional;

/** Source hotbar scan and local emitted-use ledger; no server heal acknowledgement implied. */
public final class HealItemSelector {
    public record Item(String itemId, String displayName, boolean empty) {}
    public record Selection(HealItemType type, int slot) {}
    private final HealUseLedger ledger;

    /** Standalone pure selector; production explicitly injects its shared native ledger. */
    public HealItemSelector() { this(new HealUseLedger()); }
    public HealItemSelector(HealUseLedger ledger) { this.ledger = java.util.Objects.requireNonNull(ledger); }

    public Optional<Selection> select(List<Item> hotbar, HealStackingMode mode, long now) {
        if (hotbar == null || mode == null) return Optional.empty();
        var slots = new EnumMap<HealItemType, Integer>(HealItemType.class);
        for (int slot = 0; slot < Math.min(9, hotbar.size()); slot++) {
            var item = hotbar.get(slot);
            if (item == null) continue;
            var type = HealItemType.classify(item.itemId(), item.displayName(), item.empty());
            if (type == null) continue;
            if (cooling(type, now)) {
                if (mode == HealStackingMode.OFF) return Optional.empty();
                continue;
            }
            slots.put(type, slot);
        }
        for (var type : HealItemType.values()) {
            var slot = slots.get(type);
            if (slot != null) return Optional.of(new Selection(type, slot));
        }
        return Optional.empty();
    }

    public boolean cooling(HealItemType type, long now) {
        // getOrDefault(type, 0) and strict elapsed comparison are source behavior.
        return ledger.cooling(type, now);
    }

    /** Called immediately before the ordinary native use invocation, never on prediction/selection. */
    public void recordEmittedUse(HealItemType type, long now) {
        ledger.recordEmittedUse(type, now);
    }

    public long lastEmittedUse(HealItemType type) { return ledger.lastEmittedUse(type); }
}
