package dev.veltraph.client.combat.ghost;

import java.util.Objects;

/** Source ordinary nearby-player pressure scalar. Its exact lifecycle call cadence must be bound separately. */
public final class GhostSourceCrowdPressureCounter {
    /** UNKNOWN denotes an unavailable migration observation; the original source flag itself is a primitive boolean. */
    public enum SkipUpdate { YES, NO, UNKNOWN }
    /** A current typed observation; stale or uncertain values cannot be used as scoring inputs. */
    public final class Observation {
        private final boolean capturedObserved;private final long capturedValue,capturedRevision;
        private Observation(){capturedObserved=observed;capturedValue=value;capturedRevision=revision;}
        public boolean observed(){return capturedObserved&&observed&&capturedRevision==revision;}
        public long value(){if(!observed())throw new IllegalStateException("Pressure counter is uncertain or observation stale");return capturedValue;}
        public long revision(){return capturedRevision;}
    }
    // Proven JVM static-long default. The source class initializer never assigns this field.
    private long value;private long revision;private boolean observed=true;
    public Observation snapshot(){return new Observation();}
    public void resetFromMappedLifecycle(){value=0;observed=true;revision++;}
    public void assignFromMappedLifecycle(long actualSourceValue){value=actualSourceValue;observed=true;revision++;}
    /** The caller must supply the actual source hook's <=25 squared-distance RemotePlayer count and skip flag.
     * UNKNOWN loses knowledge of the offset until a mapped reset/assignment supplies the actual value.
     * No invented per-query/per-tick updater is installed. Known arithmetic follows JVM source overflow. */
    public Observation updateFromMappedHook(int actualRemotePlayersWithinFive,SkipUpdate actualSkipFlag){
        Objects.requireNonNull(actualSkipFlag);
        if(actualSkipFlag==SkipUpdate.UNKNOWN){observed=false;revision++;return snapshot();}
        if(actualSkipFlag==SkipUpdate.YES)return snapshot();
        if(actualRemotePlayersWithinFive<0)throw new IllegalArgumentException("Observed native RemotePlayer count required");
        // Knowing a later delta cannot reconstruct the offset of an unobserved earlier update.
        if(!observed){revision++;return snapshot();}
        if(actualRemotePlayersWithinFive!=0)value+=(long)(actualRemotePlayersWithinFive*2);
        if(value>0)value+=-1L;
        revision++;return snapshot();
    }
}
