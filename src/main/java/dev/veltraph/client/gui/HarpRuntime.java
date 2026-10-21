package dev.veltraph.client.gui;
import java.util.*;

/** Item identity snapshot 0..34, first wool at 28..38, click note+9. No stack count/lore sampling. */
public final class HarpRuntime {
    private record Pending(int slot,long tick) {}
    private List<String> previous=List.of();
    private final Deque<Pending> queue=new ArrayDeque<>();
    private int sync=-1;
    private long lastClick=-100;

    /** Exact stable4.0.30 numeric constants recovered by inert static arithmetic/DES decoding. */
    public static final long SOURCE_SAMPLE_RETENTION_MILLIS=1000;
    public static final long SOURCE_CONTAINER_INPUT_COOLDOWN_MILLIS=100;
    /** Empty limits still cannot authorize timing; useful for unsupported/missing-contract callers. */
    public record TimingLimits(OptionalLong sampleRetentionMillis, OptionalLong clickCooldownMillis) {
        public TimingLimits {
            Objects.requireNonNull(sampleRetentionMillis);Objects.requireNonNull(clickCooldownMillis);
            if(sampleRetentionMillis.isPresent()&&sampleRetentionMillis.getAsLong()<=0)
                throw new IllegalArgumentException("Sample retention must be positive");
            if(clickCooldownMillis.isPresent()&&clickCooldownMillis.getAsLong()<0)
                throw new IllegalArgumentException("Click cooldown must be nonnegative");
        }
        public static TimingLimits unresolvedSource() { return new TimingLimits(OptionalLong.empty(),OptionalLong.empty()); }
        public static TimingLimits recoveredSource() {
            return new TimingLimits(OptionalLong.of(SOURCE_SAMPLE_RETENTION_MILLIS),OptionalLong.of(SOURCE_CONTAINER_INPUT_COOLDOWN_MILLIS));
        }
        public boolean complete() { return sampleRetentionMillis.isPresent()&&clickCooldownMillis.isPresent(); }
    }
    public enum TimingState { WAITING_FOR_SNAPSHOT, FIRST_CHANGE, SAMPLING, UNSUPPORTED_SOURCE_LIMITS, MISSING_NATIVE_LATENCY }
    private record Sample(long observedMillis,double ticks) {}
    private static final class SourceNote { final int slot;int age;SourceNote(int slot) { this.slot=slot; } }
    private final Deque<Sample> samples=new ArrayDeque<>();
    private final Deque<SourceNote> sourceQueue=new ArrayDeque<>();
    private List<String> sourcePrevious=List.of();
    private int sourceSync=-1,warmup;
    private boolean firstChange=true;
    private long lastChangeMillis,lastSourceClickMillis=Long.MIN_VALUE;
    private long nativeInputReceipts;
    private TimingState timingState=TimingState.WAITING_FOR_SNAPSHOT;
    /** Diagnostic receipt only; eviction still controls all scheduling/statistics via samples. */
    public record SampleObservation(int containerId,long serial,long observedMillis,long elapsedMillis,
                                    long nativeLatencyMillis,double ticks) {}
    private SampleObservation latestSampleObservation;
    private long sampleSerial;

    /**
     * Called once at actual END_CLIENT_TICK, after reading the current native menu. The original
     * timestamp is currentTimeMillis and latency is the local native PlayerInfo.getLatency().
     * Injected numeric limits are explicit contracts, never inferred RTT/window defaults.
     */
    public OptionalInt nativeTick(int syncId,List<String> containerSlots,long nowMillis,
                                  OptionalLong latencyMillis,boolean automatic,int manualDelay,
                                  TimingLimits limits) {
        Objects.requireNonNull(latencyMillis);Objects.requireNonNull(limits);
        if(syncId!=sourceSync) { resetSource();sourceSync=syncId; }
        if(containerSlots.size()<48) { resetSource();return OptionalInt.empty(); }
        if(!limits.complete()) { resetSource();timingState=TimingState.UNSUPPORTED_SOURCE_LIMITS;return OptionalInt.empty(); }
        if(latencyMillis.isEmpty()) { resetSource();timingState=TimingState.MISSING_NATIVE_LATENCY;return OptionalInt.empty(); }
        var snapshot=List.copyOf(containerSlots.subList(0,35));
        if(sourcePrevious.isEmpty()) {
            if(++warmup!=10) return OptionalInt.empty();
            warmup=0;sourcePrevious=snapshot;
        }
        if(!sourcePrevious.equals(snapshot)) {
            if(firstChange) {
                // Source deliberately retains the old snapshot here: the next tick samples it.
                firstChange=false;lastChangeMillis=nowMillis;timingState=TimingState.FIRST_CHANGE;
            } else {
                long elapsedMillis=nowMillis-lastChangeMillis;
                double sampledTicks=(elapsedMillis-latencyMillis.getAsLong())/1000.0*20.0;
                samples.addLast(new Sample(nowMillis,sampledTicks));
                latestSampleObservation=new SampleObservation(syncId,++sampleSerial,nowMillis,elapsedMillis,latencyMillis.getAsLong(),sampledTicks);
                lastChangeMillis=nowMillis;sourcePrevious=snapshot;timingState=TimingState.SAMPLING;
                for(int slot=28;slot<39;slot++) {
                    var id=containerSlots.get(slot);
                    if(id.startsWith("minecraft:")&&id.endsWith("_wool")) { sourceQueue.addLast(new SourceNote(slot+9));break; }
                }
            }
        }
        if(sourceQueue.isEmpty()) return OptionalInt.empty();
        var head=sourceQueue.getFirst();head.age++;
        int delay=automatic?(int)Math.round(averageTicks(nowMillis,limits.sampleRetentionMillis().getAsLong())):manualDelay;
        // Source ages only the head, uses the current average, and removes at most one per tick.
        if(head.age<delay) return OptionalInt.empty();
        if(nativeInputCooldownRemainingMillis(nowMillis,limits.clickCooldownMillis().getAsLong())>0) return OptionalInt.empty();
        lastSourceClickMillis=nowMillis;
        return OptionalInt.of(sourceQueue.removeFirst().slot);
    }
    public double averageTicks(long nowMillis,long retentionMillis) {
        evictSamples(nowMillis,retentionMillis);
        return samples.stream().mapToDouble(Sample::ticks).average().orElse(0.0);
    }
    public double sumTicks(long nowMillis,long retentionMillis) {
        evictSamples(nowMillis,retentionMillis);return samples.stream().mapToDouble(Sample::ticks).sum();
    }
    /** Source getMost casts each double to int before taking max; truncation is toward zero. */
    public int maxWholeTicks(long nowMillis,long retentionMillis) {
        evictSamples(nowMillis,retentionMillis);return samples.stream().mapToInt(sample->(int)sample.ticks()).max().orElse(0);
    }
    private void evictSamples(long nowMillis,long retentionMillis) {
        if(retentionMillis<=0) throw new IllegalArgumentException("Sample retention must be positive");
        // Strictly older samples expire; samples exactly at the lower bound remain included.
        samples.removeIf(sample->sample.observedMillis()<nowMillis-retentionMillis);
    }
    public TimingState timingState() { return timingState; }
    public int sampleCount() { return samples.size(); }
    public int queuedSourceNotes() { return sourceQueue.size(); }
    public Optional<SampleObservation> latestSampleObservation() { return Optional.ofNullable(latestSampleObservation); }
    /** Forward every actual native container input; the source helper's cooldown spans menus. */
    public void onNativeContainerInput(long wallClockMillis) { lastSourceClickMillis=wallClockMillis;nativeInputReceipts++; }
    /** Read-only receipt, including inputs received while Harp is disabled or another menu is open. */
    public record NativeInputObservation(OptionalLong wallClockMillis,long receipts) {}
    public NativeInputObservation nativeInputObservation() {
        return new NativeInputObservation(lastSourceClickMillis==Long.MIN_VALUE?OptionalLong.empty():OptionalLong.of(lastSourceClickMillis),nativeInputReceipts);
    }
    /** The scheduler's shared timestamp gate, queryable without supplying or authorizing source limits. */
    public long nativeInputCooldownRemainingMillis(long nowMillis,long cooldownMillis) {
        if(cooldownMillis<0) throw new IllegalArgumentException("Click cooldown must be nonnegative");
        return lastSourceClickMillis==Long.MIN_VALUE?0:Math.max(0,cooldownMillis-(nowMillis-lastSourceClickMillis));
    }
    private void resetSource() {
        sourcePrevious=List.of();sourceQueue.clear();samples.clear();sourceSync=-1;warmup=0;
        latestSampleObservation=null;sampleSerial=0;
        // The source shared helper clock is not part of Harp's startup/reset state.
        firstChange=true;lastChangeMillis=0;
        timingState=TimingState.WAITING_FOR_SNAPSHOT;
    }
    public OptionalInt tick(int syncId,List<String> containerSlots,long tick,int delayTicks) {
        if(syncId!=sync) { reset();sync=syncId; }
        if(containerSlots.size()<48) { reset();return OptionalInt.empty(); }
        List<String> snapshot=List.copyOf(containerSlots.subList(0,35));
        if(!previous.isEmpty()&&!previous.equals(snapshot)) {
            for(int slot=28;slot<39;slot++) {
                String id=containerSlots.get(slot);
                if(id.startsWith("minecraft:")&&id.endsWith("_wool")) {
                    if(queue.size()<16) queue.add(new Pending(slot+9,tick+Math.max(0,Math.min(20,delayTicks))));
                    break;
                }
            }
        }
        previous=snapshot;
        if(!queue.isEmpty()&&queue.peek().tick()<=tick&&tick-lastClick>=1) {
            lastClick=tick;return OptionalInt.of(queue.remove().slot());
        }
        return OptionalInt.empty();
    }
    public void reset() { previous=List.of();queue.clear();sync=-1;lastClick=-100;resetSource(); }
}
