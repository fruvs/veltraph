package dev.veltraph.client.hydra;

import java.util.*;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;

/** Modern bounded, native-scope wrapper around the unchanged readable source algorithm. */
public final class CropSpeciesReplantWatcher {
    public static final Set<String> SPECIES = SourceCropSpeciesReplants.SPECIES;
    public static final int MAX_PENDING = 256;
    public record Scope(Object player, Object world, Object connection, Object owner,
                        long generation, boolean enabled, CropSpeciesObserverConfig.Stamp configStamp) {
        /** Legacy pure-custody seam; actual native admission always supplies an attested config stamp. */
        public Scope(Object player, Object world, Object connection, Object owner, long generation, boolean enabled) {
            this(player, world, connection, owner, generation, enabled, null);
        }
        public boolean valid() {
            return enabled && player != null && world != null && connection != null && owner != null;
        }
        public boolean same(Scope other) {
            return other != null && player == other.player && world == other.world
                    && connection == other.connection && owner == other.owner
                    && generation == other.generation && enabled == other.enabled
                    && (configStamp == null ? other.configStamp == null : configStamp.same(other.configStamp));
        }
    }
    public record Detection(Scope scope, long timeMillis, int count, int threshold,
                            List<SourceCropSpeciesReplants.Replacement> examples) {}
    private final SourceCropSpeciesReplants source;
    private Scope scope;
    private long lastTime = -1;
    public CropSpeciesReplantWatcher(IntSupplier lifetimeThreshold) {
        source = new SourceCropSpeciesReplants(lifetimeThreshold);
    }
    public int threshold() { return source.threshold(); }
    public int pendingCount() { return source.pendingCount(); }
    public int timestampCount() { return source.timestampCount(); }
    public record Observation(int threshold, int pending, int count, Scope scope, Set<Long> timestamps) {}
    /** Cached state only; does not bind a context, read a block or advance source timing. */
    public Observation observation() { return new Observation(source.threshold(), source.pendingCount(), source.timestampCount(), scope, source.timestampSnapshot()); }
    public boolean contains(SourceCropSpeciesReplants.Cell cell) { return source.contains(cell); }
    public void reset() { source.clearAll(); scope = null; lastTime = -1; }
    private boolean bind(Scope observed, long now) {
        if (observed == null || !observed.valid() || now < 0) { reset(); return false; }
        if (scope == null || !scope.same(observed) || now < lastTime) { reset(); scope = observed; }
        lastTime = now; return true;
    }
    public void attack(Scope observed, SourceCropSpeciesReplants.Cell cell, String species, long now) {
        if (!bind(observed, now) || cell == null || species == null || !SPECIES.contains(species)) return;
        if (!source.contains(cell) && source.pendingCount() >= MAX_PENDING) { source.clearAll(); return; }
        source.attack(cell, species, now);
    }
    public Optional<Detection> poll(Scope observed, long now, SourceCropSpeciesReplants.SpeciesReader reader) {
        return poll(observed, now, reader, () -> now);
    }
    public Optional<Detection> poll(Scope observed, long now, SourceCropSpeciesReplants.SpeciesReader reader,
                                    LongSupplier mismatchClock) {
        if (!bind(observed, now)) return Optional.empty();
        return source.poll(now, reader, mismatchClock)
                .map(finding -> new Detection(scope, finding.timeMillis(), finding.count(), finding.threshold(), finding.examples()));
    }
}
