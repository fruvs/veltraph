package dev.veltraph.client.service.mc;

/** A private-ledger compare-and-swap ordering kernel. It contains no source/native authority. */
final class RootPassiveCameraSlot<T> {
    private final Thread thread=Thread.currentThread();
    private final T original;
    private T successor;
    private boolean prepared,claimed,revoked;
    RootPassiveCameraSlot(T original){this.original=original;}
    boolean originalCurrent(T actual){return Thread.currentThread()==thread&&!claimed&&!revoked&&actual==original;}
    boolean prepare(T next,T actual){
        if(prepared||!originalCurrent(actual)){revoked=true;return false;}
        successor=next;prepared=true;return true;
    }
    boolean claim(T actual){
        if(!prepared||!originalCurrent(actual)){revoked=true;return false;}
        claimed=true;return true;
    }
    T successor(){if(!claimed||Thread.currentThread()!=thread)throw new IllegalStateException("Camera custody not claimed");return successor;}
}
