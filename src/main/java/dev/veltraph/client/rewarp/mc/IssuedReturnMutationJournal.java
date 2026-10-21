package dev.veltraph.client.rewarp.mc;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.IdentityHashMap;

/** Scoped native mutation observations; neither a gameplay grant nor account authentication. */
public final class IssuedReturnMutationJournal {
    private static final int LIVE_LIMIT=32, OBJECT_LIMIT=16384;
    private static final ArrayList<WeakReference<Scope>> scopes=new ArrayList<>();
    private IssuedReturnMutationJournal() {}
    static final class Scope {
        private final IdentityHashMap<Object,Boolean> watched=new IdentityHashMap<>();
        private boolean changed,closed;
        private NativeSetterWindow nativeSetter;
        private Scope() {}
        /** Package-private invocation custody, armed only around the genuine direct native setter. */
        NativeSetterWindow beginNativeSelectedSetter(Object inventory,int next) {
            synchronized(scopes) {
                if(closed||changed||nativeSetter!=null||inventory==null||next<0||next>8||!watched.containsKey(inventory))return null;
                nativeSetter=new NativeSetterWindow(this,inventory,next);return nativeSetter;
            }
        }
        boolean finishNativeSelectedSetter(NativeSetterWindow exact,boolean returnedNormally) {
            synchronized(scopes) {
                if(exact==null||nativeSetter!=exact||exact.owner!=this||exact.thread!=Thread.currentThread()) {
                    changed=true;return false;
                }
                nativeSetter=null;exact.closed=true;
                if(!returnedNormally||exact.observations!=1)changed=true;
                return !closed&&!changed;
            }
        }
        boolean watch(Object object) {
            synchronized(scopes) {
                if(closed||changed||object==null)return false;
                if(watched.containsKey(object))return true;
                if(watched.size()>=OBJECT_LIMIT){changed=true;return false;}
                watched.put(object,Boolean.TRUE);return true;
            }
        }
        boolean current() {synchronized(scopes){return !changed&&!closed&&nativeSetter==null;}}
        void close() {synchronized(scopes){closed=true;watched.clear();}}
    }
    static Scope open() {
        synchronized(scopes) {
            scopes.removeIf(ref->{var scope=ref.get();return scope==null||scope.closed;});
            if(scopes.size()>=LIVE_LIMIT)return null;
            var scope=new Scope();scopes.add(new WeakReference<>(scope));return scope;
        }
    }
    /** This token is never a gameplay grant, callable fence, or rearm of a revoked snapshot. */
    static final class NativeSetterWindow {
        private final Scope owner;
        private final Object inventory;
        private final int next;
        private final Thread thread=Thread.currentThread();
        private int observations;
        private boolean closed;
        private NativeSetterWindow(Scope owner,Object inventory,int next){this.owner=owner;this.inventory=inventory;this.next=next;}
    }
    /** Every other snapshot, wrong slot, extra setter and foreign thread still permanently revokes. */
    public static void selectedSlotMutation(Object inventory,int next) {
        synchronized(scopes) {
            for(var ref:scopes) {
                var scope=ref.get();
                if(scope==null||scope.closed||!scope.watched.containsKey(inventory))continue;
                var exact=scope.nativeSetter;
                if(exact!=null&&!exact.closed&&exact.owner==scope&&exact.inventory==inventory
                    &&exact.next==next&&exact.thread==Thread.currentThread()&&exact.observations==0)exact.observations++;
                else scope.changed=true;
            }
        }
    }
    /** Called only by installed native mutator HEAD observations, before same-value or attempted writes. */
    public static void mutation(Object object) {
        synchronized(scopes) {
            for(var ref:scopes){var scope=ref.get();if(scope!=null&&!scope.closed&&scope.watched.containsKey(object))scope.changed=true;}
        }
    }
}
