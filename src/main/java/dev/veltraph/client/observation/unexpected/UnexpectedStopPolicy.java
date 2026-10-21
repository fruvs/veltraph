package dev.veltraph.client.observation.unexpected;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import dev.veltraph.client.observation.unexpected.UnexpectedBehaviorMonitor.*;

/** Per-observer delivery policy. Only a fresh event can request an immediate local stop. */
public final class UnexpectedStopPolicy implements Consumer<Finding> {
    private final BooleanSupplier autoStop;
    private final Runnable stop;
    private final Consumer<Finding> notify;
    private long delivered=Long.MIN_VALUE;
    public UnexpectedStopPolicy(BooleanSupplier autoStop,Runnable stop,Consumer<Finding> notify) {
        this.autoStop=Objects.requireNonNull(autoStop);this.stop=Objects.requireNonNull(stop);
        this.notify=Objects.requireNonNull(notify);
    }
    @Override public void accept(Finding finding) {
        Objects.requireNonNull(finding);
        if(finding.serial()<=delivered)return;
        delivered=finding.serial();
        if(finding.classification()!=Classification.UNEXPECTED)return;
        // Explicit stop permission is independent of alert cooldown/general reaction choreography.
        // Stop first so a notification failure cannot leave an active actuator running.
        if(autoStop.getAsBoolean())stop.run();
        notify.accept(finding);
    }
}
