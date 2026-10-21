package dev.veltraph.client.combat.ghost;

import java.util.Objects;

/** COMMON_GUARD bytecode1458–1550: update on each third reached invocation unless FARMING+GARDEN. */
public final class GhostSourcePressureEventCadence {
    private final GhostSourceCrowdPressureCounter counter;private int reachedInvocations;
    public GhostSourcePressureEventCadence(GhostSourceCrowdPressureCounter actualProcessCounter){counter=Objects.requireNonNull(actualProcessCounter);}
    /** Call only when the mapped base event reaches its cadence block; earlier return/exception does not count. */
    public GhostSourceCrowdPressureCounter.Observation reachedBaseEvent(boolean actualFarmingGarden,int nativeRemotePlayersWithinFive,
            GhostSourceCrowdPressureCounter.SkipUpdate actualTransitionFlag){
        if(++reachedInvocations==3){reachedInvocations=0;
            if(!actualFarmingGarden)return counter.updateFromMappedHook(nativeRemotePlayersWithinFive,actualTransitionFlag);}
        return counter.snapshot();
    }
}
