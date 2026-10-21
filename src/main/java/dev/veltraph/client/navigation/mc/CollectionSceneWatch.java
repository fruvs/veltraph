package dev.veltraph.client.navigation.mc;

import java.util.BitSet;
import java.util.IdentityHashMap;

/** One-way observation. Revocation never becomes a permission or recovers after ABA. */
final class CollectionSceneWatch {
    private volatile boolean live = true;
    private final IdentityHashMap<Object, BitSet> selected = new IdentityHashMap<>();
    CollectionSceneWatch(IdentityHashMap<?, BitSet> indices) {
        indices.forEach((container,bits) -> selected.put(container,(BitSet)bits.clone()));
    }
    boolean current() { return live; }
    void revoke() { live = false; }
    void cellWrite(Object container, int index) {
        BitSet bits=selected.get(container);
        if (bits != null && (index < 0 || bits.get(index))) revoke();
    }
    void entityWrite(Object expectedWorld, Object actualWorld, Object player, Object ownedItem, Object entity,
                     double ax0, double ay0, double az0, double ax1, double ay1, double az1,
                     double bx0, double by0, double bz0, double bx1, double by1, double bz1) {
        if (expectedWorld == actualWorld && entity != player && entity != ownedItem
            && intersects(ax0,ay0,az0,ax1,ay1,az1,bx0,by0,bz0,bx1,by1,bz1)) revoke();
    }
    static boolean intersects(double ax0, double ay0, double az0, double ax1, double ay1, double az1,
                              double bx0, double by0, double bz0, double bx1, double by1, double bz1) {
        return ax0 < bx1 && ax1 > bx0 && ay0 < by1 && ay1 > by0 && az0 < bz1 && az1 > bz0;
    }
}
