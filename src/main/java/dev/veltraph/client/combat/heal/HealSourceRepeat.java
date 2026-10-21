package dev.veltraph.client.combat.heal;

/** Post-use source decision only. Timed controller and subsequent readiness are separate contracts. */
public final class HealSourceRepeat {
    public enum Decision { HEAL_AGAIN, FINISH }
    private int decisions;
    public void beginAttempt() { decisions = 0; }
    public int repeatDecisionCount() { return decisions; }

    public Decision afterUse(HealStackingMode mode, boolean normalReadiness) {
        // Source SPAM short-circuits this readiness call only; it does not waive later use ownership.
        if (mode != HealStackingMode.SPAM && (!normalReadiness || mode != HealStackingMode.LAZY))
            return Decision.FINISH;
        // Kotlin nextInt(2,3) has the single possible value 2, not an inclusive 2–3 range.
        return ++decisions < 2 ? Decision.HEAL_AGAIN : Decision.FINISH;
    }
}
