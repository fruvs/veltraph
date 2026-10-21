package dev.veltraph.client.service.mc;

import java.util.concurrent.atomic.AtomicReference;

/** All genuine guard writes share one exact monotonic history; a no-op write also retires prior custody. */
final class GuardCustodyStore<T> {
    private final AtomicReference<T> current;private long generation;
    GuardCustodyStore(AtomicReference<T> current){this.current=current;}
    record Snapshot<T>(T value,long generation){}
    synchronized Snapshot<T> snapshot(){return new Snapshot<>(current.get(),generation);}
    T get(){return current.get();}
    synchronized long generation(){return generation;}
    synchronized void set(T replacement){current.set(replacement);advance();}
    synchronized boolean compareAndSet(T expected,T replacement){
        if(!current.compareAndSet(expected,replacement))return false;advance();return true;
    }
    private void advance(){if(generation<Long.MAX_VALUE)generation++;}
    synchronized long replaceExact(T expected,T replacement,long expectedGeneration){
        if(expectedGeneration<0||generation==Long.MAX_VALUE||generation!=expectedGeneration
            ||!current.compareAndSet(expected,replacement))return -1;
        advance();return generation;
    }
}
