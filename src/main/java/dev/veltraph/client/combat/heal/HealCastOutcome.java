package dev.veltraph.client.combat.heal;

/** Actual local use invocation outcome, independent of a server healing/mana acknowledgement. */
public enum HealCastOutcome { EMITTED, REFUSED_COOLDOWN, REFUSED_CUSTODY }
