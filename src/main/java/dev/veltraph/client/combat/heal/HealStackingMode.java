package dev.veltraph.client.combat.heal;

/** The source names. Only selector cooldown handling is bound by this port. */
public enum HealStackingMode {
    OFF, LAZY, SPAM;

    /** Original companion chooses uniformly from OFF and LAZY for a fresh default. */
    public static HealStackingMode freshDefault(java.util.random.RandomGenerator random) {
        return random.nextInt(2) == 0 ? OFF : LAZY;
    }
}
