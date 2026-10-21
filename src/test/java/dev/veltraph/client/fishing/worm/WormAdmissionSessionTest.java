package dev.veltraph.client.fishing.worm;
import org.junit.jupiter.api.Test;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;
class WormAdmissionSessionTest {
    static final class CountingRandom extends Random {int samples;@Override public double nextDouble(double min,double max){samples++;return min;}}
    @Test void disabledDefaultForTwoMinutesDoesNotParseSampleOrAdmit(){var s=new WormAdmissionSession();var r=new CountingRandom();for(long now=0;now<=120000;now+=1000){assertFalse(s.observe(false,true,"malformed","NaN",now,r));assertFalse(s.ready(100,now));}assertNull(s.sample());assertEquals(0,r.samples);}
    @Test void enabledLandAndBusyHelperRemainInertEvenAfterTimerDuration(){var s=new WormAdmissionSession();var r=new CountingRandom();assertFalse(s.observe(true,false,"malformed","NaN",120000,r));assertNull(s.sample());assertEquals(0,r.samples);}
    @Test void eligibleEnabledBoundarySamplesOnceAndLeavingWaterDoesNotReset(){var s=new WormAdmissionSession();var r=new CountingRandom();assertTrue(s.observe(true,true,"10:15","1:1.5",1000,r));var sample=s.sample();for(long n=1100;n<10000;n+=100)assertTrue(s.observe(true,true,"10:15","1:1.5",n,r));assertFalse(s.observe(true,false,"10:15","1:1.5",90000,r));assertSame(sample,s.sample());assertEquals(2,r.samples);assertTrue(s.ready(0,61000));}
    @Test void disabledThenReenabledStartsFreshAndNoStaleAdmission(){var s=new WormAdmissionSession();var r=new CountingRandom();s.observe(true,true,"10:15","1:1.5",0,r);assertTrue(s.ready(10,0));s.observe(false,true,"bad","bad",1000,r);assertNull(s.sample());s.observe(true,true,"10:15","1:1.5",2000,r);assertFalse(s.ready(0,2000));assertEquals(4,r.samples);}
    @Test void activeRangeMutationRejectsAndAcceptedChildResamplesExactlyOnce(){var s=new WormAdmissionSession();var r=new CountingRandom();s.observe(true,true,"10:15","1:1.5",0,r);assertThrows(IllegalArgumentException.class,()->s.observe(true,true,"11:15","1:1.5",100,r));assertEquals(2,r.samples);s.accepted(1000,r);assertEquals(4,r.samples);assertEquals(1000,s.sample().resetAt());}
    @Test void malformedEnabledEligibleRangeCannotCreateAdmission(){var s=new WormAdmissionSession();var r=new CountingRandom();assertThrows(IllegalArgumentException.class,()->s.observe(true,true,"malformed","1:1.5",0,r));assertNull(s.sample());assertFalse(s.ready(100,100000));assertEquals(0,r.samples);}
}
