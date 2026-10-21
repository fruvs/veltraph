package dev.veltraph.client.chocolate;
import java.util.ArrayDeque;
import java.util.IdentityHashMap;
import java.util.Objects;
/** Bounded original acquisitions. Eviction removes oldest identity; it never creates or rebases a binding. */
final class ChocolateObservationBindingRetention {
    private ChocolateObservationBindingRetention(){}
    static <K,V>void put(IdentityHashMap<K,V> bindings,ArrayDeque<K> order,K key,V value,int capacity){
        Objects.requireNonNull(key);Objects.requireNonNull(value);if(capacity<1)throw new IllegalArgumentException("Positive original binding capacity required");
        if(bindings.containsKey(key)){bindings.put(key,value);return;}
        while(bindings.size()>=capacity){if(order.isEmpty())throw new IllegalStateException("Original binding acquisition order unavailable");bindings.remove(order.removeFirst());}
        bindings.put(key,value);order.addLast(key);
    }
    /** Identity-only removal; no observation equality, native codec or caller callback. */
    static <K>void forget(ArrayDeque<K> order,K key){for(var it=order.iterator();it.hasNext();)if(it.next()==key){it.remove();return;}}
}
