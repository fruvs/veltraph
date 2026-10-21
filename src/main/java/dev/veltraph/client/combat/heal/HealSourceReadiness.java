package dev.veltraph.client.combat.heal;

/** Readable common-helper contract, using source raw integer units rather than health percentages. */
public final class HealSourceReadiness {
    private HealSourceReadiness() {}
    public record Input(boolean worldPlayerPresent, boolean enabled, boolean playerBlockerPresent,
                        HealItemType selectedType, int observedMana, int observedRawHealth,
                        boolean cooling, boolean ignoreHealth, boolean emergency,
                        int normalRawThreshold, int emergencyRawThreshold) {}

    public static boolean ready(Input in) {
        if (!in.worldPlayerPresent() || !in.enabled() || in.playerBlockerPresent()
                || in.selectedType() == null) return false;
        if (in.observedMana() < in.selectedType().manaCost() || in.cooling()) return false;
        if (in.ignoreHealth()) return true;
        return in.observedRawHealth() <= (in.emergency() ? in.emergencyRawThreshold() : in.normalRawThreshold());
    }
}
