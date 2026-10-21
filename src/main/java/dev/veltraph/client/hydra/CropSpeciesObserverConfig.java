package dev.veltraph.client.hydra;

import dev.veltraph.client.config.ConfigKey;
import dev.veltraph.client.config.ConfigService;
import dev.veltraph.client.observation.unexpected.UnexpectedBehaviorConfig;
import java.util.List;
import java.util.Optional;
import java.util.Objects;
import java.util.function.Supplier;

/** Typed actual-store/per-key epochs prevent unobserved configuration change-and-revert. */
public final class CropSpeciesObserverConfig {
    private CropSpeciesObserverConfig() {}
    public static final List<ConfigKey<?>> KEYS = List.of(UnexpectedBehaviorConfig.ENABLED,
            UnexpectedBehaviorConfig.AUTO_STOP, UnexpectedBehaviorConfig.NOTIFY,
            UnexpectedBehaviorConfig.ALERT_COOLDOWN_MS);
    public record Stamp(ConfigService store, long enabledRevision, long stopRevision,
                        long notifyRevision, long cooldownRevision, boolean enabled,
                        boolean autoStop, boolean notifyUser, long cooldownMillis) {
        public boolean same(Stamp other) {
            return other != null && store == other.store && enabledRevision == other.enabledRevision
                    && stopRevision == other.stopRevision && notifyRevision == other.notifyRevision
                    && cooldownRevision == other.cooldownRevision && enabled == other.enabled
                    && autoStop == other.autoStop && notifyUser == other.notifyUser
                    && cooldownMillis == other.cooldownMillis;
        }
    }
    /** Shared native binding follows the current store identity on every scope capture. */
    public static final class CurrentStore {
        private final Supplier<ConfigService> current;
        public CurrentStore(Supplier<ConfigService> current) { this.current = Objects.requireNonNull(current); }
        public Optional<Stamp> capture() { return CropSpeciesObserverConfig.capture(current.get()); }
    }
    public static Optional<Stamp> capture(ConfigService store) {
        if (store == null) return Optional.empty();
        var revisions = new long[KEYS.size()];
        for (int i = 0; i < KEYS.size(); i++) {
            var revision = store.revision(KEYS.get(i));
            if (revision.isEmpty() || revision.getAsLong() < 0) return Optional.empty();
            revisions[i] = revision.getAsLong();
        }
        boolean enabled = store.get(UnexpectedBehaviorConfig.ENABLED);
        boolean stop = store.get(UnexpectedBehaviorConfig.AUTO_STOP);
        boolean notify = store.get(UnexpectedBehaviorConfig.NOTIFY);
        long cooldown = store.get(UnexpectedBehaviorConfig.ALERT_COOLDOWN_MS);
        for (int i = 0; i < KEYS.size(); i++) {
            var revision = store.revision(KEYS.get(i));
            if (revision.isEmpty() || revision.getAsLong() != revisions[i]) return Optional.empty();
        }
        return Optional.of(new Stamp(store, revisions[0], revisions[1], revisions[2], revisions[3],
                enabled, stop, notify, cooldown));
    }
}
