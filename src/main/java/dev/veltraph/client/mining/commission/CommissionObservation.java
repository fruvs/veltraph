package dev.veltraph.client.mining.commission;

import java.util.List;
import java.util.Objects;

/** Observed task data is distinct from native permission to mine or claim it. */
public record CommissionObservation(String context, long serial, long observedMillis,
                                    List<Task> tasks, boolean completeSection) {
    public CommissionObservation {
        context = Objects.requireNonNull(context);
        tasks = List.copyOf(tasks);
        if (context.isBlank() || serial < 0 || observedMillis < 0 || tasks.size() > 16)
            throw new IllegalArgumentException("Invalid commission observation");
    }
    public enum Work { MITHRIL, TITANIUM, MOB, GLACITE, UMBER, TUNGSTEN, GEMSTONE, UNSUPPORTED }
    public record Task(String name, Work work, String region, String resource,
                       double percent, boolean progressKnown, boolean claimable, int slot) {
        public Task {
            Objects.requireNonNull(name); Objects.requireNonNull(work);
            Objects.requireNonNull(region); Objects.requireNonNull(resource);
            if (name.isBlank() || !Double.isFinite(percent) || percent < 0 || percent > 100 || slot < -1)
                throw new IllegalArgumentException("Invalid commission task");
        }
        public boolean done() { return progressKnown && percent == 100; }
        public boolean active() { return progressKnown && percent < 100 && work != Work.UNSUPPORTED; }
    }
}
