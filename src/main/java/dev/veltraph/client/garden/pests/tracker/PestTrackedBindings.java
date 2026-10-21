package dev.veltraph.client.garden.pests.tracker;

import dev.veltraph.client.garden.pests.*;
import java.util.*;

/** Exact native tab/counter/tracker contracts where current strings and settle timers are opaque. */
public record PestTrackedBindings(Signals signals,PestBindings.Cleaner cleaner,PestTrackerTrace.Settings trace,
                                  String trackerLoreLine,float activationPitch,int maxTotalPests,int requestTimeoutTicks,int maxRequestsPerPlot,int raySteps) {
    public PestTrackedBindings {Objects.requireNonNull(signals);Objects.requireNonNull(cleaner);Objects.requireNonNull(trace);
        if(!cleaner.pest().registryId().equals("minecraft:armor_stand")||trackerLoreLine==null||trackerLoreLine.isBlank()||!Float.isFinite(activationPitch)||activationPitch>-30||activationPitch<-89
            ||maxTotalPests<1||maxTotalPests>32||requestTimeoutTicks<20||requestTimeoutTicks>1200||maxRequestsPerPlot<1||maxRequestsPerPlot>8||raySteps<40||raySteps>=70)
            throw new IllegalArgumentException("Exact source armor-stand tracker, air-view contract, source ray range and bounded request budgets required");}
    public enum CounterSource { TAB, SIDEBAR }
    public record Signals(String plotsPrefix,String separator,CounterSource counterSource,String totalPrefix,List<PestBoxGeometry.Plot> plots){public Signals {
        Objects.requireNonNull(counterSource);plots=List.copyOf(plots);if(plotsPrefix==null||plotsPrefix.isBlank()||separator==null||separator.isEmpty()||totalPrefix==null||totalPrefix.isBlank()||plots.isEmpty()||plots.size()>24)
            throw new IllegalArgumentException("Observed exact native tab plot-list/counter prefixes and complete permitted spawns required");
        var ids=new HashSet<Integer>();for(var plot:plots)if(!ids.add(plot.plotId()))throw new IllegalArgumentException("Duplicate permitted plot mapping");}}
}
