package dev.veltraph.client.navigation;

/** Exact native-call accounting, never gameplay permission or a server ACK. */
public final class NativeCarryPublicationWitness {
    private final Object mode,client,listener,transport;private final int slot;
    private boolean entered,cacheAllowed,cacheStaged,commitAllowed,cacheSet,offered,transportEntered,transportReturned,submitted,returned,bodyNormal,failed;
    private Object packet;
    public NativeCarryPublicationWitness(Object mode,Object client,Object listener,Object transport,int slot) {
        this.mode=mode;this.client=client;this.listener=listener;this.transport=transport;this.slot=slot;
    }
    public boolean enter(Object actualMode,Object actualClient,Object actualListener,boolean current) {
        if(entered||actualMode!=mode||actualClient!=client||actualListener!=listener||!current){failed=true;return false;}
        entered=true;return true;
    }
    public boolean beforeCache(Object actualMode,int actualSlot,boolean current) {
        if(!entered||cacheAllowed||cacheSet||failed||actualMode!=mode||actualSlot!=slot||!current){failed=true;return false;}
        cacheAllowed=true;return true;
    }
    public void assignmentStaged(){if(!cacheAllowed||cacheStaged)failed=true;else cacheStaged=true;}
    public boolean beforeCommit(boolean exactNativeBookkeeping){
        if(!entered||!cacheStaged||cacheSet||commitAllowed||!submitted||failed||!exactNativeBookkeeping){failed=true;return false;}
        commitAllowed=true;return true;
    }
    public void cacheReturned(){if(!commitAllowed||cacheSet||failed)failed=true;else cacheSet=true;}
    public boolean offer(Object actualListener,Object actualPacket,int actualSlot,boolean current) {
        if(!entered||!cacheStaged||offered||failed||actualListener!=listener||actualPacket==null||actualSlot!=slot||!current){failed=true;return false;}
        offered=true;packet=actualPacket;return true;
    }
    public boolean beforeTransport(Object actualListener,Object actualTransport,Object actualPacket,boolean current) {
        if(!offered||transportEntered||failed||actualListener!=listener||actualTransport!=transport||actualPacket!=packet||!current){failed=true;return false;}
        transportEntered=true;return true;
    }
    public void transportReturned(boolean normal){
        if(!transportEntered||transportReturned){failed=true;return;}
        transportReturned=true;if(normal)submitted=true;else failed=true;
    }
    public void bodyReturned(boolean normal){if(returned){failed=true;return;}returned=true;bodyNormal=normal;if(!normal)failed=true;}
    public boolean submitted(){return submitted;}
    public boolean cacheCommitted(){return cacheSet;}
    /** Pure entry ordering only; private adapter state and config supply actual gameplay authority. */
    public enum Entry { REVOKED, PRIMARY, CONTINUE }
    public static Entry entry(boolean pending,boolean restoring,boolean retired,boolean noLease,boolean untouched,java.util.function.BooleanSupplier enabled){
        if(pending||restoring||retired)return Entry.REVOKED;
        if(noLease&&untouched&&!enabled.getAsBoolean())return Entry.PRIMARY;
        return Entry.CONTINUE;
    }
    /** Pure original-window arithmetic; only the adapter supplies its private original times and real clock. */
    public static boolean withinOriginalWindow(long now,long born,long last,long deadline){
        return born<=last&&last<=now&&now<=deadline;
    }
    /** A real normal Connection.send return survives later refusal; authority never does. */
    public boolean confirmed(boolean current){return entered&&cacheSet&&offered&&transportEntered&&transportReturned&&submitted&&returned&&!failed&&current;}
    /** Read-only snapshots of actual native cache-write history; every returned original write advances once. */
    public static final class CacheHistory {
        private long version;private boolean known=true;private Thread writer;
        private void thread(){var actual=Thread.currentThread();if(writer==null)writer=actual;else if(writer!=actual)known=false;}
        public synchronized Snapshot snapshot(){thread();return new Snapshot(this,version,known);}
        public synchronized void originalWriteReturned(){thread();if(version==Long.MAX_VALUE)known=false;else version++;}
        public synchronized void originalWriteFailed(){known=false;}
        private synchronized boolean current(Snapshot captured){thread();return captured.known&&known&&version==captured.version;}
        public static final class Snapshot {
            private final CacheHistory history;private final long version;private final boolean known;
            private Snapshot(CacheHistory history,long version,boolean known){this.history=history;this.version=version;this.known=known;}
            public boolean current(){return history.current(this);}
            public boolean followsExactlyOne(Snapshot before){return before!=null&&known&&before.known&&history==before.history
                    &&before.version<Long.MAX_VALUE&&version==before.version+1&&current();}
        }
    }

    /** Passive actual native send/queue-return order, never permission or a server ACK. */
    public static final class PacketHistory {
        private long version;private boolean known=true;private Object lastPacket;private int lastSlot;private Thread writer;
        private void thread(){var actual=Thread.currentThread();if(writer==null)writer=actual;else if(writer!=actual)known=false;}
        public synchronized Snapshot snapshot(){thread();return new Snapshot(this,version,known,lastPacket,lastSlot);}
        public synchronized void nativeSubmissionReturned(Object packet,int slot){
            thread();
            if(packet==null||slot<0||slot>8||version==Long.MAX_VALUE){known=false;return;}
            version++;lastPacket=packet;lastSlot=slot;
        }
        public synchronized void unknown(){known=false;}
        private synchronized boolean current(Snapshot captured){thread();return captured.known&&known&&version==captured.version;}
        public static final class Snapshot {
            private final PacketHistory history;private final long version;private final boolean known;private final Object packet;private final int slot;
            private Snapshot(PacketHistory history,long version,boolean known,Object packet,int slot){this.history=history;this.version=version;this.known=known;this.packet=packet;this.slot=slot;}
            public boolean current(){return history.current(this);}
            public boolean latestExact(Object actualPacket,int actualSlot){return current()&&packet!=null&&packet==actualPacket&&slot==actualSlot;}
        }
    }

}
