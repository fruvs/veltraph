package dev.veltraph.client.combat.slayer;

import dev.veltraph.client.world.Vec3;
import java.lang.ref.WeakReference;
import java.util.*;

/** Exact ENCHANT observations only, bounded and scoped to the client world. */
public final class SlayerParticleSignal {
    public record Receipt(long serial,Vec3 position,long observed) {}
    private static WeakReference<Object> world=new WeakReference<>(null);
    private static final ArrayDeque<Receipt> receipts=new ArrayDeque<>();
    private static long serial;
    private SlayerParticleSignal() {}
    public static void receive(Object owner,Vec3 point,long now) {
        if(owner==null||!Double.isFinite(point.x())||!Double.isFinite(point.y())||!Double.isFinite(point.z()))return;
        if(world.get()!=owner) {world=new WeakReference<>(owner);receipts.clear();}
        receipts.addLast(new Receipt(++serial,point,now));while(receipts.size()>64)receipts.removeFirst();
    }
    public static List<Receipt> read(Object owner,long now) {
        if(owner==null||world.get()!=owner) {world=new WeakReference<>(owner);receipts.clear();return List.of();}
        receipts.removeIf(r->now-r.observed()>500||now<r.observed());return List.copyOf(receipts);
    }
}
