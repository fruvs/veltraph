package dev.veltraph.client.galatea;
import java.lang.ref.WeakReference;
import java.util.*;
/** Thread-safe identity-scoped timestamp journal. No packet or payload is retained. */
public final class FigPacketGapRegistry {
    private record Bucket(WeakReference<Object> connection,FigSourcePacketGap clock){}
    private final List<Bucket> buckets=new ArrayList<>();
    private FigSourcePacketGap clock(Object connection){
        Objects.requireNonNull(connection);buckets.removeIf(b->b.connection().get()==null);
        for(var b:buckets)if(b.connection().get()==connection)return b.clock();
        var clock=new FigSourcePacketGap();buckets.add(new Bucket(new WeakReference<>(connection),clock));return clock;
    }
    public synchronized void received(Object connection,long networkingressMillis){clock(connection).received(networkingressMillis);}
    public synchronized boolean observed(Object connection,long now){return clock(connection).observed(now);}
    public synchronized Snapshot snapshot(Object connection){var clock=clock(connection);return new Snapshot(clock.lastPacketAt(),clock.lastDetectedGapAt());}
    public record Snapshot(long lastPacketAt,long lastDetectedGapAt){}
}
