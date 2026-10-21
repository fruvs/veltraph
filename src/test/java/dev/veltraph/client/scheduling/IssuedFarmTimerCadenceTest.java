package dev.veltraph.client.scheduling;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IssuedFarmTimerCadenceTest {
    @Test void originalCountdownIncludesEveryOwnedOfflineFrameAndExpiresOnce(){
        var timer=new SourceStopTimer();timer.arm(1,ScheduleTimeUnit.SECONDS);
        var cadence=new IssuedFarmTimerCadence(timer,timer.remainingTicks());
        for(int tick=100;tick<120;tick++){
            Object frame=new Object();assertEquals(tick==100?IssuedFarmTimerCadence.Update.FIRST:IssuedFarmTimerCadence.Update.NEXT,
                cadence.observe(timer,timer.remainingTicks(),frame,tick));
            boolean expired=timer.tick();assertEquals(tick==119,expired);
            assertTrue(cadence.record(timer,frame,tick,timer.remainingTicks(),expired));
            if(!expired)assertEquals(IssuedFarmTimerCadence.Update.DUPLICATE,cadence.observe(timer,timer.remainingTicks(),frame,tick));
        }
        assertEquals(SourceStopTimer.State.EXPIRED,timer.state());assertFalse(timer.tick());
        assertEquals(IssuedFarmTimerCadence.Update.REFUSED,cadence.observe(timer,timer.remainingTicks(),new Object(),120));
    }
    @Test void duplicateActualFrameDoesNotAuthorizeAnotherTick(){
        var timer=new SourceStopTimer();timer.arm(1,ScheduleTimeUnit.MINUTES);Object frame=new Object();
        var c=new IssuedFarmTimerCadence(timer,timer.remainingTicks());assertEquals(IssuedFarmTimerCadence.Update.FIRST,c.observe(timer,1200,frame,4));
        assertFalse(timer.tick());assertTrue(c.record(timer,frame,4,1199,false));
        assertEquals(IssuedFarmTimerCadence.Update.DUPLICATE,c.observe(timer,1199,frame,4));assertEquals(1199,timer.remainingTicks());
    }
    @Test void sameClockWithDifferentFrameCannotMintAnotherUpdate(){
        Object timer=new Object(),frame=new Object();var c=new IssuedFarmTimerCadence(timer,20);
        assertEquals(IssuedFarmTimerCadence.Update.FIRST,c.observe(timer,20,frame,10));assertTrue(c.record(timer,frame,10,19,false));
        assertEquals(IssuedFarmTimerCadence.Update.REFUSED,c.observe(timer,19,new Object(),10));
    }
    @Test void skippedOrRewoundRootClockRevokesInsteadOfAdopting(){
        for(long next:new long[]{8,9,11,100}){Object timer=new Object(),frame=new Object();var c=new IssuedFarmTimerCadence(timer,20);
            assertEquals(IssuedFarmTimerCadence.Update.FIRST,c.observe(timer,20,frame,9));assertTrue(c.record(timer,frame,9,19,false));
            assertEquals(IssuedFarmTimerCadence.Update.REFUSED,c.observe(timer,19,new Object(),next));
            assertEquals(IssuedFarmTimerCadence.Update.REFUSED,c.observe(timer,19,new Object(),10));}
    }
    @Test void replacementTimerOrChangedCountRevokesOriginal(){
        Object timer=new Object();var c=new IssuedFarmTimerCadence(timer,20);
        assertEquals(IssuedFarmTimerCadence.Update.REFUSED,c.observe(new Object(),20,new Object(),1));
        assertEquals(IssuedFarmTimerCadence.Update.REFUSED,new IssuedFarmTimerCadence(timer,20).observe(timer,21,new Object(),1));
    }
    @Test void rearmToSameCountAfterPriorUpdateCannotRestoreOldContinuation(){
        var timer=new SourceStopTimer();timer.arm(1,ScheduleTimeUnit.SECONDS);var c=new IssuedFarmTimerCadence(timer,20);Object frame=new Object();
        assertEquals(IssuedFarmTimerCadence.Update.FIRST,c.observe(timer,20,frame,1));timer.tick();assertTrue(c.record(timer,frame,1,19,false));
        timer.arm(1,ScheduleTimeUnit.SECONDS);assertEquals(IssuedFarmTimerCadence.Update.REFUSED,c.observe(timer,20,new Object(),2));
    }
    @Test void absentTimerStateDoesNotCreateACountdownAndFinishesOnce(){
        var timer=new SourceStopTimer();var c=new IssuedFarmTimerCadence(timer,0);
        for(int tick=0;tick<4;tick++){Object frame=new Object();assertEquals(tick==0?IssuedFarmTimerCadence.Update.FIRST:IssuedFarmTimerCadence.Update.NEXT,c.observe(timer,0,frame,tick));
            assertTrue(c.recordWithoutTick(timer,frame,tick,0));assertEquals(SourceStopTimer.State.IDLE,timer.state());
            if(tick==3){assertTrue(c.finishWithoutTick(timer,frame,tick,0));assertFalse(c.finishWithoutTick(timer,frame,tick,0));}}
        assertEquals(0,timer.remainingTicks());
    }
    @Test void zeroDurationExpiresOnFirstActualFrame(){
        var timer=new SourceStopTimer();timer.arm(.99,ScheduleTimeUnit.SECONDS);var c=new IssuedFarmTimerCadence(timer,0);Object frame=new Object();
        assertEquals(IssuedFarmTimerCadence.Update.FIRST,c.observe(timer,0,frame,0));assertTrue(timer.tick());assertTrue(c.record(timer,frame,0,-1,true));
    }
    @Test void recordRequiresTheSelectedFrameAndExactDecrement(){
        Object timer=new Object(),frame=new Object();var c=new IssuedFarmTimerCadence(timer,3);c.observe(timer,3,frame,0);
        assertFalse(c.record(timer,new Object(),0,2,false));assertEquals(IssuedFarmTimerCadence.Update.REFUSED,c.observe(timer,3,frame,0));
        var other=new IssuedFarmTimerCadence(timer,3);other.observe(timer,3,frame,0);assertFalse(other.record(timer,frame,0,1,false));
    }
    @Test void unrecordedFrameAndDuplicateRecordRefuse(){
        Object timer=new Object(),frame=new Object();var c=new IssuedFarmTimerCadence(timer,3);
        assertEquals(IssuedFarmTimerCadence.Update.FIRST,c.observe(timer,3,frame,0));
        assertEquals(IssuedFarmTimerCadence.Update.REFUSED,c.observe(timer,3,new Object(),1));
        var duplicate=new IssuedFarmTimerCadence(timer,3);duplicate.observe(timer,3,frame,0);assertTrue(duplicate.record(timer,frame,0,2,false));
        assertFalse(duplicate.record(timer,frame,0,1,false));
    }
    @Test void directGenuineCaptureFitsOnlySameOrImmediatelyFollowingRootFrame(){
        assertTrue(IssuedFarmTimerCadence.firstFrameFits(10,10));assertTrue(IssuedFarmTimerCadence.firstFrameFits(10,11));
        assertFalse(IssuedFarmTimerCadence.firstFrameFits(10,9));assertFalse(IssuedFarmTimerCadence.firstFrameFits(10,12));
        assertFalse(IssuedFarmTimerCadence.firstFrameFits(-1,0));assertTrue(IssuedFarmTimerCadence.firstFrameFits(Long.MAX_VALUE,Long.MAX_VALUE));
        assertFalse(IssuedFarmTimerCadence.firstFrameFits(Long.MAX_VALUE,Long.MIN_VALUE));
    }
    @Test void explicitRevocationAndClockOverflowAreTerminal(){
        Object timer=new Object();var c=new IssuedFarmTimerCadence(timer,3);c.revoke();assertEquals(IssuedFarmTimerCadence.Update.REFUSED,c.observe(timer,3,new Object(),0));
        var o=new IssuedFarmTimerCadence(timer,3);Object f=new Object();assertEquals(IssuedFarmTimerCadence.Update.FIRST,o.observe(timer,3,f,Long.MAX_VALUE));
        assertTrue(o.record(timer,f,Long.MAX_VALUE,2,false));assertEquals(IssuedFarmTimerCadence.Update.REFUSED,o.observe(timer,2,new Object(),0));
    }
}
