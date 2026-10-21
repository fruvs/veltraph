package dev.veltraph.client.scheduling;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class SourceStopTimerTest {
    @Test void truncatesSecondsThenExpiresExactlyOnce(){var timer=new SourceStopTimer();timer.arm(1.99,ScheduleTimeUnit.SECONDS);for(int i=0;i<19;i++)assertFalse(timer.tick());assertTrue(timer.tick());assertFalse(timer.tick());assertEquals(SourceStopTimer.State.EXPIRED,timer.state());}
    @Test void convertsAllFourExplicitUnitsBeforeTruncation(){for(var unit:ScheduleTimeUnit.values()){var timer=new SourceStopTimer();timer.arm(1.5,unit);assertEquals((int)(1.5*unit.seconds())*20,timer.remainingTicks());}}
    @Test void zeroExpiresOnFirstUpdateCancellationNeverFires(){var timer=new SourceStopTimer();timer.arm(.99,ScheduleTimeUnit.SECONDS);assertTrue(timer.tick());timer.arm(1,ScheduleTimeUnit.MINUTES);timer.cancel();for(int i=0;i<1400;i++)assertFalse(timer.tick());}
    @Test void malformedOrOverflowDurationCannotArm(){for(double amount:new double[]{-1,Double.NaN,Double.POSITIVE_INFINITY,Double.MAX_VALUE})assertThrows(IllegalArgumentException.class,()->new SourceStopTimer().arm(amount,ScheduleTimeUnit.SECONDS));}
}
