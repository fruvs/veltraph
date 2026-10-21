package dev.veltraph.client.combat.ghost;

import java.util.IdentityHashMap;
import java.util.Objects;
import java.util.function.Predicate;

/** Source entity-identity ignore map; separate from failed-path UUID and protected-chat suppression. */
public final class GhostSourceIdentityIgnore {
    public static final long SOURCE_EXPIRY_MS=10000;
    private static final GhostSourceIdentityIgnore RUNTIME=new GhostSourceIdentityIgnore();
    private final IdentityHashMap<Object,Long> entries=new IdentityHashMap<>();private long revision;
    public static GhostSourceIdentityIgnore runtime(){return RUNTIME;}
    public void observeIgnored(Object actualEntity,long now){if(actualEntity!=null){entries.put(actualEntity,now);revision++;}}
    public boolean contains(Object actualEntity){return entries.containsKey(actualEntity);}
    /** Actual source cleanup hook: invalid body OR elapsed>10000; query itself does not expire. */
    public void cleanup(long now,Predicate<Object> sourceInvalidBody){Objects.requireNonNull(sourceInvalidBody);
        boolean changed=entries.entrySet().removeIf(e->sourceInvalidBody.test(e.getKey())||now-e.getValue()>SOURCE_EXPIRY_MS);if(changed)revision++;}
    public void clear(){entries.clear();revision++;}
    public long revision(){return revision;}
}
