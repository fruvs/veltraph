package dev.veltraph.client.foraging.scheduling;

/** Exact ticket gate. A fresh native-ref rebind is mandatory before resume; manual stop clears it. */
public final class ForagingPauseGate {
    private Object ticket;
    private boolean rebound;
    private long pausedAt;
    public boolean paused(){return ticket!=null;}
    public boolean owns(Object value){return value!=null&&value==ticket;}
    public boolean pause(Object value,boolean ready,long now,Runnable release){
        if(value==null||paused()||!ready)return false;
        ticket=value;rebound=false;pausedAt=now;
        try{release.run();return true;}catch(RuntimeException error){clear();throw error;}
    }
    public boolean rebound(Object value){if(!owns(value))return false;rebound=true;return true;}
    /** Returns paused duration, or -1 for a foreign/unbound ticket or backwards clock. */
    public long resume(Object value,long now){
        if(!owns(value)||!rebound||now<pausedAt)return -1;
        long elapsed=now-pausedAt;clear();return elapsed;
    }
    public void clear(){ticket=null;rebound=false;pausedAt=0;}
}
