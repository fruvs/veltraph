package dev.veltraph.client.hydra;

import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** Stop-before-notify with fresh config/store and PWC after any external stop callback. */
public final class CropSpeciesReplantDelivery {
    private final Supplier<Optional<CropSpeciesObserverConfig.Stamp>> settings;
    private final Predicate<CropSpeciesReplantWatcher.Scope> nativeContextCurrent;
    private final Runnable stop;
    private final BiConsumer<CropSpeciesReplantWatcher.Detection, CropSpeciesObserverConfig.Stamp> notify;
    public CropSpeciesReplantDelivery(Supplier<Optional<CropSpeciesObserverConfig.Stamp>> settings,
            Predicate<CropSpeciesReplantWatcher.Scope> nativeContextCurrent, Runnable stop,
            BiConsumer<CropSpeciesReplantWatcher.Detection, CropSpeciesObserverConfig.Stamp> notify) {
        this.settings = Objects.requireNonNull(settings);
        this.nativeContextCurrent = Objects.requireNonNull(nativeContextCurrent);
        this.stop = Objects.requireNonNull(stop);
        this.notify = Objects.requireNonNull(notify);
    }
    private boolean current(CropSpeciesReplantWatcher.Detection finding, CropSpeciesObserverConfig.Stamp stamp) {
        return stamp != null && stamp.enabled() && finding.scope().configStamp() != null
                && finding.scope().configStamp().same(stamp) && nativeContextCurrent.test(finding.scope());
    }
    public void deliver(CropSpeciesReplantWatcher.Detection finding) {
        var before = settings.get().orElse(null);
        if (!current(finding, before)) return;
        if (before.autoStop()) stop.run();
        // Stopping deliberately disables the old owner; PWC/config freshness remains mandatory.
        // Notification is independently revocable inside beforeStopAll/onDisable callbacks.
        var after = settings.get().orElse(null);
        if (!current(finding, after) || !after.notifyUser()) return;
        notify.accept(finding, after);
    }
}
