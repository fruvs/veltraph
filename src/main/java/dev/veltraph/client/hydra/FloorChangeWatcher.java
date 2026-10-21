package dev.veltraph.client.hydra;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure rolling-window detector for the live FLOOR_CHANGE signal. The Minecraft adapter supplies a stable
 * block anchor plus a client-world collision snapshot; this class handles only thresholding and resets.
 */
public final class FloorChangeWatcher {

    private final long windowMs;
    private final int changeThreshold;

    public record Diagnostic(Object priorAnchor,Object currentAnchor,Map<Object,Boolean> before,Map<Object,Boolean> after,List<Object> changedCells,int windowCount,boolean fire,String reason){}
    private Diagnostic diagnostic;
    private java.util.function.Consumer<Diagnostic> diagnosticProbe=d->{};
    public Diagnostic diagnostic(){return diagnostic;}
    /** Optional passive test/report sink; never owns decision state. */
    public void diagnosticProbe(java.util.function.Consumer<Diagnostic> probe){diagnosticProbe=java.util.Objects.requireNonNull(probe);}
    private Object lastAnchor;
    private Map<Object, Boolean> lastSnapshot = Map.of();
    private final List<Long> changeTimes = new ArrayList<>();

    public FloorChangeWatcher() {
        this(10_000L, 10);
    }

    public FloorChangeWatcher(long windowMs, int changeThreshold) {
        this.windowMs = windowMs;
        this.changeThreshold = changeThreshold;
    }

    public boolean observe(Object anchor, Map<?, Boolean> snapshot, long nowMillis) {
        Map<Object,Boolean> priorSnapshot=lastSnapshot;Object priorAnchor=lastAnchor;
        Map<Object, Boolean> normalized = normalize(snapshot);
        if (anchor == null || normalized.isEmpty()) {
            reset();
            report(priorAnchor,anchor,priorSnapshot,normalized,0,false,false,"invalid or empty sample");
            return false;
        }
        if (lastAnchor == null || !lastAnchor.equals(anchor)) {
            lastAnchor = anchor;
            lastSnapshot = normalized;
            changeTimes.clear();
            report(priorAnchor,anchor,priorSnapshot,normalized,0,false,false,"new anchor baseline");
            return false;
        }

        int changed = countChanged(lastSnapshot, normalized);
        lastSnapshot = normalized;
        if (changed > 0) {
            for (int i = 0; i < changed; i++) {
                changeTimes.add(nowMillis);
            }
        }
        changeTimes.removeIf(t -> nowMillis - t > windowMs);
        boolean fired=changeTimes.size()>changeThreshold;
        report(priorAnchor,anchor,priorSnapshot,normalized,changeTimes.size(),fired,true,"compared same anchor");
        return fired;
    }

    private void report(Object priorAnchor,Object anchor,Map<Object,Boolean> before,Map<Object,Boolean> after,int count,boolean fired,boolean compared,String reason){
        diagnostic=null; // A failed report must not present an older sample as this decision's evidence.
        try{var changed=new ArrayList<Object>();if(compared)for(var entry:after.entrySet()){var old=before.get(entry.getKey());if(old!=null&&old.booleanValue()!=entry.getValue().booleanValue())changed.add(entry.getKey());}
            var next=new Diagnostic(priorAnchor,anchor,Map.copyOf(before),Map.copyOf(after),List.copyOf(changed),count,fired,reason);
            diagnosticProbe.accept(next);diagnostic=next;
        }catch(Throwable ignored){}
    }

    public void reset() {
        lastAnchor = null;
        lastSnapshot = Map.of();
        changeTimes.clear();
    }

    private static Map<Object, Boolean> normalize(Map<?, Boolean> snapshot) {
        if (snapshot == null || snapshot.isEmpty()) {
            return Map.of();
        }
        Map<Object, Boolean> out = new HashMap<>();
        for (Map.Entry<?, Boolean> e : snapshot.entrySet()) {
            if (e.getKey() != null && e.getValue() != null) {
                out.put(e.getKey(), e.getValue());
            }
        }
        return out;
    }

    private static int countChanged(Map<Object, Boolean> before, Map<Object, Boolean> after) {
        int changed = 0;
        for (Map.Entry<Object, Boolean> e : after.entrySet()) {
            Boolean old = before.get(e.getKey());
            if (old != null && old.booleanValue() != e.getValue().booleanValue()) {
                changed++;
            }
        }
        return changed;
    }
}
