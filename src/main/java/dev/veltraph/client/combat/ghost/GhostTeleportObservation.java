package dev.veltraph.client.combat.ghost;

/** One pre-dispatch attribution window; never the transport/activation expiry or a waiting refresh. */
final class GhostTeleportObservation {
    private GhostTeleportObservation() {}
    static long ttlMillis(GhostTransportLoop.Attempt attempt,long now){
        return Math.max(1,Math.min(5000,attempt.deadline()-now));
    }
}
