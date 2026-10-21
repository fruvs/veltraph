package dev.veltraph.client.fishing.worm;
import org.junit.jupiter.api.Test;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;
class WormAdmissionTest {
    @Test void eitherActualCountOrMinutesAdmitsAtInclusiveBoundary(){
        var s=new WormAdmission.Sample(10,1.5,1000);
        assertFalse(s.ready(9,90999));assertTrue(s.ready(10,1000));assertTrue(s.ready(0,91000));
        assertEquals(1,s.progress(50,1000));assertEquals(.9,s.progress(9,1000));
    }
    @Test void positiveKotlinRoundingAndFractionalMinutesArePreserved(){
        var g=new WormAdmission();var s=g.reset(new WormAdmission.Range(10.5,10.5),new WormAdmission.Range(1.25,1.25),500,new Random(1));
        assertEquals(11,s.entityThreshold());assertFalse(g.ready(0,75499));assertTrue(g.ready(0,75500));
    }
    @Test void samplesStayFixedUntilAnExplicitReset(){
        var g=new WormAdmission();var random=new Random(4);var first=g.reset(new WormAdmission.Range(10,15),new WormAdmission.Range(1,1.5),100,random);
        for(int i=0;i<100;i++){g.ready(i%5,100+i);assertSame(first,g.sample());}
        var second=g.reset(new WormAdmission.Range(10,15),new WormAdmission.Range(1,1.5),10000,random);
        assertNotSame(first,second);assertEquals(10000,second.resetAt());assertFalse(g.ready(0,10000));
    }
    @Test void fullThreeDimensionalNativePositionMetricAndStrictRadius(){
        assertTrue(WormAdmission.nearby(0,14.999,0));assertFalse(WormAdmission.nearby(0,15,0));
        assertFalse(WormAdmission.nearby(10,10,10));assertTrue(WormAdmission.nearby(10,0,10));
        assertFalse(WormAdmission.nearby(Double.NaN,0,0));assertFalse(WormAdmission.nearby(0,Double.POSITIVE_INFINITY,0));
    }
    @Test void freshResetAndClockRollbackDoNotInventElapsedAdmission(){
        var g=new WormAdmission();assertFalse(g.ready(100,0));g.reset(new WormAdmission.Range(10,10),new WormAdmission.Range(1,1),10000,new Random());
        assertFalse(g.ready(0,9999));assertFalse(g.ready(9,10000));assertTrue(g.ready(10,9999));
    }
    @Test void invalidThresholdsCannotAuthorizeActions(){
        assertThrows(IllegalArgumentException.class,()->WormAdmission.Range.parse("0:1"));
        assertThrows(IllegalArgumentException.class,()->WormAdmission.Range.parse("NaN:1"));
        assertThrows(IllegalArgumentException.class,()->WormAdmission.Range.parse("2:1"));
        assertThrows(IllegalArgumentException.class,()->new WormAdmission.Sample(0,1,0));
        assertThrows(IllegalArgumentException.class,()->new WormAdmission.Sample(1,0,0));
    }
}
