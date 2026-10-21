package dev.veltraph.client.galatea;

import java.util.IdentityHashMap;

/** Source parent feedback cooldown keys descriptor identity, independently of per-log failures.
 * Caller must preserve the admitted descriptor token through its observed lifecycle.
 */
public final class FigSourceDescriptorCooldown {
    private final IdentityHashMap<Object,Long> timestamps=new IdentityHashMap<>();
    public void cannotDamage(Object descriptor,long now) {
        timestamps.put(java.util.Objects.requireNonNull(descriptor,"Admitted descriptor identity required"),now);
    }
    public boolean contains(Object descriptor){return timestamps.containsKey(descriptor);}
    public void expire(long now){timestamps.entrySet().removeIf(e->now-e.getValue()>5000);}
    public void reset(){timestamps.clear();}
    public int size(){return timestamps.size();}
}
