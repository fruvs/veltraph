package dev.veltraph.client.combat.sven;
import dev.veltraph.client.world.Vec3;
/** Observational transport intent is separate from the source operation/arrival contract. */
public final class SvenTeleportIntent {
 private SvenTeleportIntent(){}
 public record Spec(Vec3 target,double radius,long ttlMillis){}
 public static Spec forOperation(long operationTimeoutMs){if(operationTimeoutMs<1)throw new IllegalArgumentException("Positive original operation lifetime required");return new Spec(SvenCaveProfile.PARK_SPAWN,3.0,Math.min(5000L,operationTimeoutMs));}
}
