package dev.veltraph.client.mining.commission;

import java.util.ArrayList;
import java.util.List;
import static dev.veltraph.client.mining.commission.CommissionObservation.*;

/** Locality first, with compatible global ore commissions attached without mutating input. */
public final class CommissionSelector {
    private CommissionSelector() {}
    public static List<Task> select(List<Task> observations, String currentRegion) {
        List<Task> active = observations.stream().filter(Task::active).toList();
        if (active.isEmpty()) return List.of();
        Task chosen = active.stream().filter(t -> !t.region().isEmpty() && t.region().equals(currentRegion))
                .findFirst().orElseGet(() -> active.stream().filter(t -> !t.region().isEmpty()).findFirst().orElse(active.getFirst()));
        if (chosen.work() == Work.MOB) return List.of(chosen);
        List<Task> batch = new ArrayList<>(); batch.add(chosen);
        for (Task task : active) {
            if (task == chosen || task.work() == Work.MOB) continue;
            boolean dwarvenOre = ore(chosen.work()) && ore(task.work());
            boolean sameRegion = !chosen.region().isEmpty() && task.region().equals(chosen.region());
            boolean globalOre = dwarvenOre && task.region().isEmpty();
            if (sameRegion || globalOre) batch.add(task);
        }
        return List.copyOf(batch);
    }
    private static boolean ore(Work work) { return work == Work.MITHRIL || work == Work.TITANIUM; }
}
