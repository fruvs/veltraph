package dev.veltraph.client.combat.grind;

import java.util.List;
import java.util.Optional;

/** Readable finishing-swap computation only. Every result is advisory, never an effect receipt. */
public final class ReadableDaedalusPolicy {
    private ReadableDaedalusPolicy() {}
    public enum Known { YES, NO, UNKNOWN }
    public record Settings(boolean enabled, int percentage) {
        public static Settings defaults() { return new Settings(false, 5); }
        public boolean valid() { return percentage >= 0 && percentage <= 100; }
    }
    /** Reference health is the source wrapper's first successful HP observation, not getMaxHealth(). */
    public record Health(Object target, float current, float reference, Known sourceAssociation) {}
    /** Match is the recovered source predicate result, not a guessed tool name or a configurable slot. */
    public record Tool(int slot, Object stackIdentity, String componentIdentity, int count,
                       Known sourceMatch, Known usable) {
        boolean complete() { return slot >= 0 && slot < 9 && stackIdentity != null
                && componentIdentity != null && count >= 0
                && (count != 0 || sourceMatch == Known.NO && usable == Known.NO)
                && sourceMatch != null && sourceMatch != Known.UNKNOWN && usable != null; }
    }
    /** Native producer must capture these callback-free, with a non-reusable configuration revision. */
    public record Scope(Object parent, Object player, Object world, Object connection, Object target,
                        String tierIdentity, long session, long configurationRevision, long ownerRevision,
                        int selectedSlot, Known enabledParent, Known eligibleSourceParent,
                        Known sourceEligibilityFlag, Known inputCustody, Known inReach, Known clearHitRay) {
        boolean admissible() { return parent != null && player != null && world != null && connection != null
                && target != null && tierIdentity != null && !tierIdentity.isEmpty()
                && session >= 0 && configurationRevision >= 0 && ownerRevision >= 0
                && selectedSlot >= 0 && selectedSlot < 9 && enabledParent == Known.YES
                && eligibleSourceParent == Known.YES && sourceEligibilityFlag == Known.YES
                && inputCustody == Known.YES && inReach == Known.YES && clearHitRay == Known.YES; }
        boolean sameAuthority(Scope other) { return other != null && parent == other.parent
                && player == other.player && world == other.world && connection == other.connection
                && target == other.target && tierIdentity.equals(other.tierIdentity)
                && session == other.session && configurationRevision == other.configurationRevision
                && ownerRevision == other.ownerRevision && selectedSlot == other.selectedSlot; }
    }
    public record Observation(Settings settings, Scope scope, Health health, List<Tool> hotbar,
                              int primarySlot) {
        public Observation { hotbar = hotbar == null ? null : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(hotbar)); }
    }
    /** Even a fresh advisory does not authorize selection, attack, restoration or a loot claim. */
    public record Advisory(int slot, boolean primaryFallback, int integerThreshold,
                           Observation observed) {}

    public static Optional<Advisory> propose(Observation observed) {
        if (observed == null || observed.settings == null || !observed.settings.enabled
                || !observed.settings.valid() || observed.scope == null || !observed.scope.admissible()
                || observed.health == null || observed.health.target != observed.scope.target
                || observed.health.sourceAssociation != Known.YES
                || !Float.isFinite(observed.health.current) || !Float.isFinite(observed.health.reference)
                || observed.health.current <= 0 || observed.health.reference <= 0
                || observed.hotbar == null || observed.hotbar.size() != 9) return Optional.empty();
        // Preserve source float multiplication followed by Java int truncation, including saturation.
        int threshold = (int)(observed.health.reference * (observed.settings.percentage / 100.0f));
        if (observed.settings.percentage != 100 && observed.health.current > threshold) return Optional.empty();
        Tool[] slots = new Tool[9];
        for (Tool tool : observed.hotbar) {
            if (tool == null || !tool.complete() || slots[tool.slot] != null) return Optional.empty();
            slots[tool.slot] = tool;
        }
        int selected = -1;
        for (int slot = 0; slot < 9; slot++) {
            if (slots[slot] == null) return Optional.empty();
            if (slots[slot].sourceMatch == Known.YES) { selected = slot; break; }
        }
        boolean fallback = selected == -1;
        if (fallback) selected = observed.primarySlot;
        if (selected < 0 || selected >= 9 || slots[selected].usable != Known.YES) return Optional.empty();
        return Optional.of(new Advisory(selected, fallback, threshold, observed));
    }

    /** A producer can compare a fresh observation, but must separately obtain actual effect custody. */
    public static boolean stillAdvisoryCurrent(Advisory prior, Observation fresh) {
        if (prior == null || fresh == null || prior.observed == null) return false;
        var original = propose(prior.observed);
        if (original.isEmpty() || original.get().slot != prior.slot
                || original.get().primaryFallback != prior.primaryFallback
                || original.get().integerThreshold != prior.integerThreshold) return false;
        if (!prior.observed.scope.sameAuthority(fresh.scope)
                || !prior.observed.settings.equals(fresh.settings)
                || fresh.health == null
                || Float.floatToIntBits(prior.observed.health.reference) != Float.floatToIntBits(fresh.health.reference)
                || prior.observed.primarySlot != fresh.primarySlot) return false;
        var next = propose(fresh);
        if (next.isEmpty() || next.get().slot != prior.slot || next.get().primaryFallback != prior.primaryFallback
                || next.get().integerThreshold != prior.integerThreshold) return false;
        for (int slot = 0; slot < 9; slot++) {
            Tool before = at(prior.observed.hotbar, slot), after = at(fresh.hotbar, slot);
            if (before.stackIdentity != after.stackIdentity || !before.componentIdentity.equals(after.componentIdentity)
                    || before.count != after.count || before.sourceMatch != after.sourceMatch
                    || before.usable != after.usable) return false;
        }
        return true;
    }
    private static Tool at(List<Tool> tools, int slot) {
        return tools.stream().filter(tool -> tool.slot == slot).findFirst().orElseThrow();
    }
}
