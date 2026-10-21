package dev.veltraph.client.fishing.support;

import java.util.List;

/** Readable stable availability suppression only; never acknowledges a requested ability use. */
public final class SourceBlackHolePresence {
    private SourceBlackHolePresence() {}
    public record Observation(boolean armorStand, boolean withinSourceBounds, String rawName) {}
    /** Only an explicit configured source vocabulary opts into the local username binding. */
    public static boolean applies(BlackHoleProfile profile) {
        return profile != null && profile.itemName().equals("Pocket Black Hole")
                && profile.effectMarkerName().equals("Black Hole")
                && profile.ownerMarkerTemplate().equals("Spawned by: {player}")
                && profile.renewalIntervalMillis() == 160000L;
    }
    public static boolean present(List<Observation> observations, String playerIdentity) {
        if (observations == null || playerIdentity == null || playerIdentity.isEmpty()) return false;
        boolean effect = false, owner = false;
        for (var observation : observations) {
            if (observation == null || !observation.armorStand() || !observation.withinSourceBounds()
                    || observation.rawName() == null) continue;
            var name = observation.rawName();
            if (name.contains("Black Hole")) effect = true;
            if (name.contains("Spawned by: ") && name.contains(playerIdentity)) owner = true;
        }
        return effect && owner;
    }
}
