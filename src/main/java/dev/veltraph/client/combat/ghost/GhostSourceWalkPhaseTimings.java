package dev.veltraph.client.combat.ghost;
import java.util.EnumMap;
import java.util.Map;
/** Passive per-phase/read measurements; no external telemetry or runtime predicate evaluation in getters. */
public final class GhostSourceWalkPhaseTimings {
    public enum Phase { SCOPE, NATIVE_FRAME, CAPTURE_BATCH, NATIVE_READ, VALIDATION_BATCH, VALIDATION_READ, SEAL, ACTION_FRAME, DYNAMIC_READ, PROTECTED_RAY }
    public record Sample(long calls,long totalNanos,long maximumNanos) {}
    public record Evidence(Map<Phase,Sample> samples){public Evidence {samples=Map.copyOf(samples);}}
    private final long[] calls=new long[Phase.values().length],total=new long[calls.length],max=new long[calls.length];
    void add(Phase p,long nanos){int i=p.ordinal();calls[i]++;total[i]+=Math.max(0,nanos);max[i]=Math.max(max[i],nanos);}
    public Evidence cached(){var result=new EnumMap<Phase,Sample>(Phase.class);for(var p:Phase.values()){int i=p.ordinal();result.put(p,new Sample(calls[i],total[i],max[i]));}return new Evidence(result);}
}
