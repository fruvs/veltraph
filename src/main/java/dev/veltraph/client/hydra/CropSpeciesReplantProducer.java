package dev.veltraph.client.hydra;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.LongSupplier;

/** Fresh custody before sampling and again before delivering a local defensive finding. */
public final class CropSpeciesReplantProducer {
    private final CropSpeciesReplantWatcher watcher;
    private final Supplier<CropSpeciesReplantWatcher.Scope> scopes;
    private final Consumer<CropSpeciesReplantWatcher.Detection> findings;
    public CropSpeciesReplantProducer(CropSpeciesReplantWatcher watcher,
            Supplier<CropSpeciesReplantWatcher.Scope> scopes,
            Consumer<CropSpeciesReplantWatcher.Detection> findings) {
        this.watcher = Objects.requireNonNull(watcher);
        this.scopes = Objects.requireNonNull(scopes);
        this.findings = Objects.requireNonNull(findings);
    }
    public void attack(SourceCropSpeciesReplants.Cell cell, String species, long now) {
        watcher.attack(scopes.get(), cell, species, now);
    }
    public void tick(long now, SourceCropSpeciesReplants.SpeciesReader reader) {
        tick(now, reader, () -> now);
    }
    public void tick(long now, SourceCropSpeciesReplants.SpeciesReader reader, LongSupplier findingClock) {
        var observed = scopes.get();
        var finding = watcher.poll(observed, now, reader, findingClock);
        var fresh = scopes.get();
        if (observed == null || !observed.same(fresh) || !fresh.valid()) {
            watcher.reset(); return;
        }
        finding.ifPresent(findings);
    }
    public CropSpeciesReplantWatcher.Observation observation() { return watcher.observation(); }
    public boolean contains(SourceCropSpeciesReplants.Cell cell) { return watcher.contains(cell); }
    public void reset() { watcher.reset(); }
}
