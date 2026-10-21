package dev.veltraph.client.combat.ghost;

import java.util.IdentityHashMap;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/** Original first-seen entity-identity map and one immutable per-session [120,220)ms draw. */
public final class GhostSourceEntityGraceWindow {
    public static final long SOURCE_EXPIRY_MS=60000;
    private static final GhostSourceEntityGraceWindow RUNTIME=new GhostSourceEntityGraceWindow(ThreadLocalRandom.current().nextInt(120,220));
    private record Entry(long firstSeen) {}
    public final class Stamp {
        private final Object entity;private final Entry entry;
        private Stamp(Object entity,Entry entry){this.entity=entity;this.entry=entry;}
        public boolean current(){return firstSeen.get(entity)==entry;}
        public boolean readyAt(long now){if(!current())throw new IllegalStateException("Observed first-seen entry changed");return now-entry.firstSeen()>=graceMs;}
    }
    private final int graceMs;private final IdentityHashMap<Object,Entry> firstSeen=new IdentityHashMap<>();
    public GhostSourceEntityGraceWindow(int observedSessionGraceMs){
        if(observedSessionGraceMs<120||observedSessionGraceMs>=220)throw new IllegalArgumentException("Source session grace [120,220)ms required");
        graceMs=observedSessionGraceMs;
    }
    public static GhostSourceEntityGraceWindow runtime(){return RUNTIME;}
    public int graceMs(){return graceMs;}
    public boolean ready(Object actualEntity,long now){
        Objects.requireNonNull(actualEntity);Entry first=firstSeen.get(actualEntity);
        if(first==null){firstSeen.put(actualEntity,new Entry(now));return false;}
        return now-first.firstSeen()>=graceMs;
    }
    public Stamp capture(Object actualEntity){Entry entry=firstSeen.get(Objects.requireNonNull(actualEntity));
        if(entry==null)throw new IllegalStateException("First-seen observation required");return new Stamp(actualEntity,entry);}
    /** Explicit source cleanup hook; no inferred every-query cadence or module/world reset. */
    public void cleanupExpired(long now){firstSeen.entrySet().removeIf(e->now-e.getValue().firstSeen()>SOURCE_EXPIRY_MS);}
}
