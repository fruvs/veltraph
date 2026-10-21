package dev.veltraph.client.garden.jacob;

/** Immutable original local deadline. A durable write or native prediction cannot extend it. */
public record JacobRewardClaimWindow(long startedAt,long deadline) {
    public static JacobRewardClaimWindow start(long now,long timeout){if(now<0||timeout<1000||timeout>JacobRewardClaim.MAX_TIMEOUT_MS)throw new IllegalArgumentException("Original bounded claim window required");return new JacobRewardClaimWindow(now,Math.addExact(now,timeout));}
    public JacobRewardClaimWindow {if(startedAt<0||deadline<=startedAt||deadline-startedAt>JacobRewardClaim.MAX_TIMEOUT_MS)throw new IllegalArgumentException("Invalid claim window");}
    public boolean current(long now){return now>=startedAt&&now<deadline;}
}
