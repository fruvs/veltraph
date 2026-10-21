package dev.veltraph.client.scheduling;

/** Counting discipline only. Native authority is supplied separately by the private Root caller. */
public final class IssuedFarmTimerCadence {
    public enum Update { FIRST, NEXT, DUPLICATE, REFUSED }
    private final Object originalTimer;
    private int expectedRemaining;
    private long lastTick=-1;
    private Object lastFrame;
    private boolean revoked,terminal,recorded;
    public IssuedFarmTimerCadence(Object originalTimer,int originalRemaining){
        this.originalTimer=java.util.Objects.requireNonNull(originalTimer);expectedRemaining=originalRemaining;
    }
    public static boolean firstFrameFits(long capturedTick,long currentTick){
        return capturedTick>=0&&currentTick>=capturedTick&&currentTick-capturedTick<=1;
    }
    public Update observe(Object timer,int remaining,Object actualFrame,long tick){
        if(revoked||terminal||timer!=originalTimer||actualFrame==null||tick<0||remaining!=expectedRemaining)return refuse();
        if(lastFrame==actualFrame&&lastTick==tick)return recorded?Update.DUPLICATE:refuse();
        if(lastFrame!=null&&!recorded)return refuse();
        if(lastTick>=0&&(lastTick==Long.MAX_VALUE||tick!=lastTick+1))return refuse();
        Update update=lastTick<0?Update.FIRST:Update.NEXT;lastFrame=actualFrame;lastTick=tick;recorded=false;return update;
    }
    /** Called only after this scheduler has updated its original timer in the selected real frame. */
    public boolean record(Object timer,Object frame,long tick,int resultingRemaining,boolean expired){
        if(revoked||terminal||recorded||timer!=originalTimer||frame!=lastFrame||tick!=lastTick
                ||resultingRemaining!=(expectedRemaining<=1?-1:expectedRemaining-1)
                ||expired!=(expectedRemaining<=1)){refuse();return false;}
        expectedRemaining=resultingRemaining;terminal=expired;recorded=true;return true;
    }
    public boolean recordWithoutTick(Object timer,Object frame,long tick,int unchangedRemaining){
        if(revoked||terminal||recorded||timer!=originalTimer||frame!=lastFrame||tick!=lastTick||unchangedRemaining!=expectedRemaining){refuse();return false;}
        recorded=true;return true;
    }
    public boolean finishWithoutTick(Object timer,Object frame,long tick,int unchangedRemaining){
        if(revoked||terminal||!recorded||timer!=originalTimer||frame!=lastFrame||tick!=lastTick||unchangedRemaining!=expectedRemaining){refuse();return false;}
        terminal=true;return true;
    }
    public void revoke(){revoked=true;lastFrame=null;}
    private Update refuse(){revoke();return Update.REFUSED;}
}
