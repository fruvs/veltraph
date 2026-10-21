package dev.veltraph.client.combat.ghost;

import java.lang.management.ManagementFactory;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;

/** Local cached diagnostics only. GC/JIT totals are batch correlations, not a measured stop-the-world cause. */
public final class GhostSourceWalkReadProfile {
    public enum Part { CURRENT, STATE_LOOKUP, STATE_RECHECK, PASSABILITY, COLLISION_SHAPE, SUPPORT, CLASSIFY, VALUE_PROPERTIES, MANAGEMENT_SAMPLE }
    public record Sample(long calls,long totalNanos,long maximumNanos) {}
    public record RuntimeCounters(long gcCollections,long gcMillis,long compilationMillis,long usedHeapBytes) {}
    public record Batch(long elapsedNanos,long slowestReadNanos,long gcCollectionsDelta,long gcMillisDelta,long compilationMillisDelta,long netHeapBytesDelta) {}
    public record Read(long elapsedNanos,Map<Part,Long> componentNanos){public Read {componentNanos=Map.copyOf(componentNanos);}}
    public record Evidence(Map<Part,Sample> samples,long batches,Batch longestBatch,Read longestRead) { public Evidence {samples=Map.copyOf(samples);} }
    private final long[] calls=new long[Part.values().length],total=new long[calls.length],maximum=new long[calls.length];
    private final Supplier<RuntimeCounters> counters;
    private long batches,batchSlowestRead;private Batch longestBatch;private Read longestRead;
    private final long[] readTotals=new long[calls.length];
    private static final class LocalManagement {
        static final java.util.List<java.lang.management.GarbageCollectorMXBean> COLLECTORS=ManagementFactory.getGarbageCollectorMXBeans();
        static final java.lang.management.CompilationMXBean COMPILER=ManagementFactory.getCompilationMXBean();
        static RuntimeCounters sample(){long count=0,time=0;boolean countKnown=true,timeKnown=true;
            for(var collector:COLLECTORS){long c=collector.getCollectionCount(),t=collector.getCollectionTime();if(c<0)countKnown=false;else count+=c;if(t<0)timeKnown=false;else time+=t;}
            var runtime=Runtime.getRuntime();return new RuntimeCounters(countKnown?count:-1,timeKnown?time:-1,
                COMPILER!=null&&COMPILER.isCompilationTimeMonitoringSupported()?COMPILER.getTotalCompilationTime():-1,runtime.totalMemory()-runtime.freeMemory());}
    }
    public GhostSourceWalkReadProfile(){counters=LocalManagement::sample;}
    GhostSourceWalkReadProfile(Supplier<RuntimeCounters> counters){this.counters=java.util.Objects.requireNonNull(counters);}
    void add(Part part,long nanos){int i=part.ordinal();long elapsed=Math.max(0,nanos);calls[i]++;total[i]+=elapsed;maximum[i]=Math.max(maximum[i],elapsed);}
    RuntimeCounters beginBatch(){batchSlowestRead=0;return sampleCounters();}
    void beginRead(){System.arraycopy(total,0,readTotals,0,total.length);}
    void recordRead(long nanos){long elapsed=Math.max(0,nanos);batchSlowestRead=Math.max(batchSlowestRead,elapsed);
        if(longestRead==null||elapsed>longestRead.elapsedNanos()){var components=new EnumMap<Part,Long>(Part.class);for(var part:Part.values())components.put(part,total[part.ordinal()]-readTotals[part.ordinal()]);longestRead=new Read(elapsed,components);}}
    void endBatch(RuntimeCounters before,long elapsedNanos){var after=sampleCounters();var batch=new Batch(Math.max(0,elapsedNanos),batchSlowestRead,
        delta(before.gcCollections(),after.gcCollections()),delta(before.gcMillis(),after.gcMillis()),delta(before.compilationMillis(),after.compilationMillis()),after.usedHeapBytes()-before.usedHeapBytes());
        batches++;if(longestBatch==null||batch.elapsedNanos()>longestBatch.elapsedNanos())longestBatch=batch;
    }
    private RuntimeCounters sampleCounters(){long start=System.nanoTime();try{return counters.get();}finally{add(Part.MANAGEMENT_SAMPLE,System.nanoTime()-start);}}
    private static long delta(long before,long after){return before<0||after<before?-1:after-before;}
    /** Passive cached getter: does not query beans, world, scope or native state. */
    public Evidence cached(){var samples=new EnumMap<Part,Sample>(Part.class);for(var part:Part.values()){int i=part.ordinal();samples.put(part,new Sample(calls[i],total[i],maximum[i]));}return new Evidence(samples,batches,longestBatch,longestRead);}
}
