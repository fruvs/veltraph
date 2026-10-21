package dev.veltraph.client.navigation.mc;

/** Private retained data ordering only. Neither a slot nor a continuation grants native authority. */
public final class PassiveTravelLifecycleSlot<T> {
    private final Thread thread=Thread.currentThread();
    private T value;private long revision;
    public PassiveTravelLifecycleSlot(T value){this.value=value;}
    public T value(){return value;}
    public Continuation capture(){return Thread.currentThread()==thread?new Continuation(value,revision):null;}
    public final class Continuation {
        private final T before;private final long beforeRevision;private T after;private boolean prepared,committed,revoked;
        private Continuation(T before,long revision){this.before=before;beforeRevision=revision;}
        public T before(){return before;}
        public boolean rawCurrent(){return Thread.currentThread()==thread&&!committed&&!revoked&&value==before&&revision==beforeRevision&&revision<Long.MAX_VALUE;}
        public boolean prepare(T next){if(prepared||next==null||!rawCurrent()){revoked=true;return false;}after=next;prepared=true;return true;}
        public boolean ready(){return prepared&&rawCurrent();}
        public boolean commit(){if(!ready()){revoked=true;return false;}committed=true;value=after;revision++;return true;}
    }
}
