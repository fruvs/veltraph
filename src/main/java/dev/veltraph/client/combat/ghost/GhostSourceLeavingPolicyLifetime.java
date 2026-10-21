package dev.veltraph.client.combat.ghost;
/** Original configuration construction samples once,25%instant/75%ether; retries and queries never resample. */
public final class GhostSourceLeavingPolicyLifetime {
    private final GhostSourceLeavingLifecycle.Policy policy;
    public GhostSourceLeavingPolicyLifetime(java.util.function.DoubleSupplier constructionRandom){double draw=constructionRandom.getAsDouble();if(!Double.isFinite(draw)||draw<0||draw>=1)throw new IllegalArgumentException("Source nextDouble0 inclusive1 exclusive");policy=draw<.25?GhostSourceLeavingLifecycle.Policy.LEAVE_INSTANTLY:GhostSourceLeavingLifecycle.Policy.ETHERWARP_OUT;}
    public GhostSourceLeavingLifecycle.Policy policy(){return policy;}
}
