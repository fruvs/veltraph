package dev.veltraph.client.scheduling;

import java.util.Objects;

/** Source TIMER: truncate total seconds, count 20 client updates per second, terminal expiry once. */
public final class SourceStopTimer {
    public enum State { IDLE,ARMED,EXPIRED,CANCELLED }
    private State state=State.IDLE;private int remaining;
    public void arm(double amount,ScheduleTimeUnit unit){
        Objects.requireNonNull(unit);double seconds=amount*unit.seconds();
        if(!Double.isFinite(amount)||amount<0||!Double.isFinite(seconds)||seconds>Integer.MAX_VALUE/20)
            throw new IllegalArgumentException("Timer duration is outside the safe source tick range");
        remaining=((int)seconds)*20;state=State.ARMED;
    }
    /** One actual client callback, including the source zero-duration first-update expiry. */
    public boolean tick(){if(state!=State.ARMED)return false;if(--remaining<=0){remaining=-1;state=State.EXPIRED;return true;}return false;}
    public void cancel(){remaining=-1;state=State.CANCELLED;}
    public State state(){return state;}public int remainingTicks(){return remaining;}
    public double remainingHours(){return Math.max(0,remaining)/20.0/3600.0;}
}
