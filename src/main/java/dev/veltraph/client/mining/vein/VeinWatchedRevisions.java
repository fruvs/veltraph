package dev.veltraph.client.mining.vein;

import java.lang.ref.WeakReference;
import java.util.ArrayList;

/** Observation only. Weak identity keys, no native owners or mutation payloads are retained. */
public final class VeinWatchedRevisions {
    private VeinWatchedRevisions() {}
    private static final Registry registry = new Registry();
    static final class Registry {
        private static final int LIMIT = 128;
        private final ArrayList<Entry> entries = new ArrayList<>();
        synchronized Token watch(Object object) {
            if (object == null) return null;
            entries.removeIf(e -> e.object.get() == null);
            for (var entry : entries) if (entry.object.get() == object) return entry.revision < 0 ? null : new Token(this,entry);
            if (entries.size() >= LIMIT) return null;
            var entry=new Entry(object); entries.add(entry); return new Token(this,entry);
        }
        synchronized void mutation(Object object) {
            if (object == null) return;
            for (var entry : entries) if (entry.object.get() == object) {
                entry.revision=entry.revision<0 || entry.revision==Long.MAX_VALUE ? -1 : entry.revision+1;
                return;
            }
        }
    }
    private static final class Entry {
        final WeakReference<Object> object;
        long revision;
        Entry(Object object) { this.object = new WeakReference<>(object); }
    }
    public static final class Token {
        private final Registry registry;
        private final Entry entry;
        private final long revision;
        private Token(Registry registry,Entry entry) { this.registry=registry; this.entry=entry; revision=entry.revision; }
        public boolean current() {
            synchronized (registry) { return revision>=0 && entry.object.get()!=null && entry.revision==revision; }
        }
    }
    static Token watch(Object object) { return registry.watch(object); }
    /** Hooks call this before an attempted write, including same-value writes. Unwatched objects are ignored. */
    public static void mutation(Object object) { registry.mutation(object); dev.veltraph.client.rewarp.mc.IssuedReturnMutationJournal.mutation(object); }
    public static void position(Object object,double oldX,double oldY,double oldZ,double newX,double newY,double newZ) {
        if (positionChanged(oldX,oldY,oldZ,newX,newY,newZ)) mutation(object);
    }
    static boolean positionChanged(double oldX,double oldY,double oldZ,double newX,double newY,double newZ) {
        return Double.doubleToLongBits(oldX)!=Double.doubleToLongBits(newX)
                || Double.doubleToLongBits(oldY)!=Double.doubleToLongBits(newY)
                || Double.doubleToLongBits(oldZ)!=Double.doubleToLongBits(newZ);
    }
}
