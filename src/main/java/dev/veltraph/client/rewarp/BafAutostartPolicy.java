package dev.veltraph.client.rewarp;

import java.util.*;

/** Stable BAF2211–2425 readable dispatch ordering. Read-only; each candidate needs concrete readiness. */
public final class BafAutostartPolicy {
    private BafAutostartPolicy() {}
    public enum Trigger { HYDRA, TIMER, AUTOSELL, SPRAYONATOR, PEST_GATE, VISITOR_GATE,
        PEST_THRESHOLD, CONTEST_TRAPS, CONTEST_PEST, AGATHA, DYNAMICREST }
    public record Observation(boolean emergency,boolean terminalTimer,boolean farmingAvoidance,
                              Set<Trigger> ready) {
        public Observation {ready=Set.copyOf(ready);}
    }
    public static Optional<Trigger> select(Observation o) {
        if(o.emergency())return Optional.of(Trigger.HYDRA);
        // TIMER is the separate terminal callback and takes no interruption/resume lease.
        if(o.terminalTimer())return Optional.of(Trigger.TIMER);
        if(o.ready().contains(Trigger.AUTOSELL))return Optional.of(Trigger.AUTOSELL);
        if(o.farmingAvoidance())return Optional.empty();
        for(var candidate:List.of(Trigger.SPRAYONATOR,Trigger.PEST_GATE,Trigger.VISITOR_GATE,
                Trigger.PEST_THRESHOLD,Trigger.CONTEST_TRAPS,Trigger.CONTEST_PEST,Trigger.AGATHA,Trigger.DYNAMICREST))
            if(o.ready().contains(candidate))return Optional.of(candidate);
        return Optional.empty();
    }
}
