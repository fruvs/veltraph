package dev.veltraph.client.combat.grind;

/** Readable Arena fail-stop gates. Dragon timestamp and staged exit effects remain unbound. */
public final class ZealotArenaFailsafes {
    public enum Decision { NONE, DRAGON_STOP, DISPLACEMENT_PENDING, DISPLACEMENT_STOP }
    private int dragonInvocations;
    private long displacementDue = -1;
    public Decision tick(long tick, boolean farming, boolean dragonEnabled, boolean dragonObserved,
                         boolean displacementEnabled, boolean hasAnchor, boolean sourceSuspended,
                         boolean onGround, double anchorDistance) {
        // A previously scheduled source callback only tests pending/enabled, not position again.
        if (displacementDue >= 0) return tick >= displacementDue ? Decision.DISPLACEMENT_STOP : Decision.DISPLACEMENT_PENDING;
        if (farming && dragonEnabled && ++dragonInvocations == 8) {
            dragonInvocations = 0;
            if (dragonObserved) return Decision.DRAGON_STOP;
        }
        if (farming && displacementEnabled && hasAnchor && !sourceSuspended && onGround && anchorDistance >= 2) {
            displacementDue = tick + 15;
            return Decision.DISPLACEMENT_PENDING;
        }
        return Decision.NONE;
    }
    public void reset() { dragonInvocations = 0; displacementDue = -1; }
    public boolean pending() { return displacementDue >= 0; }
    public int dragonInvocations() { return dragonInvocations; }
}
