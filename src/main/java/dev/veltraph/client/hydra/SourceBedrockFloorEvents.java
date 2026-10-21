package dev.veltraph.client.hydra;
import java.util.*;
/** Readable stable source event predicate; separate from authored collision-snapshot policy. */
public final class SourceBedrockFloorEvents {
    public static final long SOURCE_WINDOW_MS=450;
    public static final int SOURCE_THRESHOLD=10;
    public record Event(boolean enabled,boolean incomingBedrock,boolean priorBedrock,double playerX,double playerY,double playerZ,int x,int y,int z){}
    private final ArrayDeque<Long> timestamps=new ArrayDeque<>();
    private int pendingOneTick;
    /** Input is the pre-application native verified-block event; old state must be read at HEAD. */
    public boolean accept(Event e){
        if(e==null||!e.enabled()||!e.incomingBedrock()||e.priorBedrock())return false;
        double distance=Math.sqrt(Math.pow(e.playerX()-e.x(),2)+Math.pow(e.playerY()-e.y(),2)+Math.pow(e.playerZ()-e.z(),2));
        if(!(distance<18.0))return false;
        pendingOneTick++;return true;
    }
    /** Scheduler-equivalent next-tick callback appends the execution-time clock, not event time. */
    public void nextTick(long nowMillis){int count=pendingOneTick;pendingOneTick=0;for(int i=0;i<count;i++)timestamps.addLast(nowMillis);}
    /** Source evaluation prunes strictly age>450ms and detects strictly count>10. */
    public boolean detected(long nowMillis){timestamps.removeIf(time->nowMillis-time>SOURCE_WINDOW_MS);return timestamps.size()>SOURCE_THRESHOLD;}
    public int count(){return timestamps.size();}
    /** Source enable/reset clears appended list. Pending scheduler cancellation semantics stay separate. */
    public void clearCount(){timestamps.clear();}
}
