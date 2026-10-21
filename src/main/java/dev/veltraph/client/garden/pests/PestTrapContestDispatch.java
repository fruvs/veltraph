package dev.veltraph.client.garden.pests;

import dev.veltraph.client.garden.jacob.JacobContestTracker;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/** Read-only preview and source-ordered consumed-window dispatch into the guarded farm lease. */
public final class PestTrapContestDispatch {
    private PestTrapContestDispatch() {}
    public static boolean ready(JacobContestTracker tracker,JacobContestTracker.Scope scope,long tick,
                                boolean collect,boolean contestOnly,List<String> tabRows,String prefix,long cutoff){
        Objects.requireNonNull(tracker);
        var contest=tracker.observation(scope,tick);
        return collect&&contestOnly&&cutoff>=0&&!tracker.trapConsumed()&&contest.clockKnown()
            &&contest.remainingSeconds()<cutoff&&PestTrapReadiness.readyTraps(tabRows,prefix)>0;
    }
    public static boolean start(JacobContestTracker tracker,JacobContestTracker.Scope scope,long tick,
                                boolean collect,boolean contestOnly,List<String> tabRows,String prefix,long cutoff,
                                BooleanSupplier nativeReady,BooleanSupplier guardedActivation){
        Objects.requireNonNull(nativeReady);Objects.requireNonNull(guardedActivation);
        if(!ready(tracker,scope,tick,collect,contestOnly,tabRows,prefix,cutoff)||!nativeReady.getAsBoolean())return false;
        // Stable caller consumes before dispatcher acceptance; denial cannot retry the same record.
        if(!tracker.consumeTrapWindow(scope,tick,collect,contestOnly,PestTrapReadiness.readyTraps(tabRows,prefix),cutoff))return false;
        return guardedActivation.getAsBoolean();
    }
}
