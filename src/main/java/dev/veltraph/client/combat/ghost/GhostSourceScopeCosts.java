package dev.veltraph.client.combat.ghost;
import java.util.EnumMap;
import java.util.Map;
/** Passive local full-supplier breakdown. Thread context exists only inside an actual fallback scope call. */
public final class GhostSourceScopeCosts {
    public enum Part { FULL_SUPPLIER, REGISTRATION, FINAL_REGISTRATION, FINAL_PARENT_STAMP, FINAL_FACTS, PARENT_STAMP_FIRST, PARENT_STAMP_ALL, ROOT_AUTHORITY, SETTINGS_VALUES, JUMP_OBSERVATION, STAMP_ALLOCATION, CHILD_GETTER, CHILD_HANDOFF, NAMED_CHILD_CURRENT, AREA_RECEIPT, INPUT_OWNER, ENABLED_CONTROLLERS_SIZE, ENABLED_CONTROLLERS_FIRST, CAMERA_OWNER, CAMERA_EPOCH, AREA_CURRENT, DEVELOPER_REVISION, DEVELOPER_SELECTION, NATIVE_JUMP_FLAG, WALK_SETTING, ROUGH_SETTING, TAIL_SETTING, FORBIDDEN_SETTING }
    public record Sample(long calls,long totalNanos,long maximumNanos) {}
    /** samples contain actual measured calls only; entry counters cover attachSampled attempts, never extrapolation. */
    public record Evidence(Map<Part,Sample> samples,long fullInvocations,long measuredFullInvocations,int samplingStride){
        public Evidence {samples=Map.copyOf(samples);}
        public Evidence(Map<Part,Sample> samples){this(samples,0,0,1);}
    }
    private static final ThreadLocal<GhostSourceScopeCosts> ACTIVE=new ThreadLocal<>();
    private final long[] calls=new long[Part.values().length],total=new long[calls.length],maximum=new long[calls.length];
    private final int samplingStride;private final java.util.function.LongSupplier nanoClock;
    private long fullInvocations,measuredFullInvocations;
    public GhostSourceScopeCosts(){this(1,System::nanoTime);}
    public GhostSourceScopeCosts(int samplingStride){this(samplingStride,System::nanoTime);}
    GhostSourceScopeCosts(int samplingStride,java.util.function.LongSupplier nanoClock){
        if(samplingStride!=1&&samplingStride!=64)throw new IllegalArgumentException("Detailed stride1 or64 required");
        this.samplingStride=samplingStride;this.nanoClock=java.util.Objects.requireNonNull(nanoClock);
    }
    static GhostSourceScopeCosts attach(GhostSourceScopeCosts actual){var prior=ACTIVE.get();ACTIVE.set(actual);return prior;}
    /** Selects diagnostic detail only. The caller still invokes its complete real guard body. */
    static GhostSourceScopeCosts attachSampled(GhostSourceScopeCosts actual){
        java.util.Objects.requireNonNull(actual);var prior=ACTIVE.get();long index=actual.fullInvocations++;
        boolean measured=index%actual.samplingStride==0;
        if(measured)actual.measuredFullInvocations++;
        ACTIVE.set(measured?actual:null);return prior;
    }
    // A null value means no collector, exactly as after remove(). Retaining this bounded
    // thread-local slot avoids remove/get recreating an Entry on every unsampled native guard.
    // No collector, parent, world or native permission is retained when prior is null.
    static void restore(GhostSourceScopeCosts prior){ACTIVE.set(prior);}
    static long begin(Part part){var current=ACTIVE.get();return current==null?0:current.nanoClock.getAsLong();}
    static void end(Part part,long start){var current=ACTIVE.get();if(current==null||start==0)return;int i=part.ordinal();long elapsed=Math.max(0,current.nanoClock.getAsLong()-start);current.calls[i]++;current.total[i]+=elapsed;current.maximum[i]=Math.max(current.maximum[i],elapsed);}
    static <T> T value(Part part,java.util.function.Supplier<T> call){
        var current=ACTIVE.get();if(current==null)return call.get();
        long start=current.nanoClock.getAsLong();try{return call.get();}finally{end(part,start);}
    }
    static boolean flag(Part part,java.util.function.BooleanSupplier call){
        var current=ACTIVE.get();if(current==null)return call.getAsBoolean();
        long start=current.nanoClock.getAsLong();try{return call.getAsBoolean();}finally{end(part,start);}
    }
    /** Cached only: no timers, callbacks, scope checks or world reads. Nested spans must not be summed. */
    public Evidence cached(){var result=new EnumMap<Part,Sample>(Part.class);for(var part:Part.values()){int i=part.ordinal();result.put(part,new Sample(calls[i],total[i],maximum[i]));}return new Evidence(result,fullInvocations,measuredFullInvocations,samplingStride);}
}
