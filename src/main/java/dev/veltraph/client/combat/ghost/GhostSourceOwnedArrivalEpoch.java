package dev.veltraph.client.combat.ghost;
/** Exact synchronous own-release transition, never an epoch renewal. */
public final class GhostSourceOwnedArrivalEpoch {
    private GhostSourceOwnedArrivalEpoch(){}
    public static boolean matches(long expectedBefore,long actualBefore,long actualAfter,long current){return expectedBefore>=0&&expectedBefore==actualBefore&&actualBefore<Long.MAX_VALUE&&actualAfter==actualBefore+1&&current==actualAfter;}
}
