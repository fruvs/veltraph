package dev.veltraph.client.combat.ghost;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
/** Separates source buff-only availability from unchanged ordinary ingress/profile availability. */
final class GhostSourceBuffCookiePolicy {
    static boolean ready(Supplier<GhostSourceJumpBoostGate.Observed> recoveredSource,BooleanSupplier ordinaryProfile){
        return recoveredSource!=null?recoveredSource.get()==GhostSourceJumpBoostGate.Observed.YES:ordinaryProfile.getAsBoolean();
    }
}
