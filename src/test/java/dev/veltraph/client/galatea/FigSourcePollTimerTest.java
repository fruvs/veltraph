package dev.veltraph.client.galatea;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class FigSourcePollTimerTest {
    static class Draws implements FigSourcePollTimer.RandomSource {
        long interval=180;double chanceDraw=1,multiplierDraw=1.9;int draws,doubleDraws;
        public long nextLong(long low,long high){draws++;assertTrue(interval>=low&&interval<high);return interval;}
        public double nextDouble(){doubleDraws++;return chanceDraw;}
        public double nextDouble(double low,double high){doubleDraws++;assertTrue(multiplierDraw>=low&&multiplierDraw<high);return multiplierDraw;}
    }
    private FigSourcePollTimer timer(Draws r,boolean d){return new FigSourcePollTimer(180,350,d,r,new FigSourcePollTimer.Distraction(.01,2.3));}
    @Test void constructorZeroMeansFirstPollReady(){var r=new Draws();var t=timer(r,false);assertTrue(t.poll(0));assertEquals(180,t.currentInterval());}
    @Test void explicitResetMakesAdmissionWaitAndExactDeadlineReady(){var r=new Draws();var t=timer(r,false);t.reset(100);assertFalse(t.poll(279));assertTrue(t.poll(280));assertEquals(280,t.timestamp());}
    @Test void successfulPollResetsBeforeOptionalDistraction(){var r=new Draws();r.chanceDraw=0;r.multiplierDraw=2.1;var t=timer(r,true);assertTrue(t.poll(100));assertEquals(360,t.currentInterval());assertEquals(100,t.timestamp());}
    @Test void multiplierIsCastToLongBeforeMultiplying(){var r=new Draws();r.chanceDraw=0;r.multiplierDraw=1.99;var t=timer(r,true);assertTrue(t.poll(100));assertEquals(180,t.currentInterval());assertTrue(t.distracted());}
    @Test void chanceBoundaryIsStrictAndFalseDistractionDoesNotDraw(){var r=new Draws();r.chanceDraw=.01;var t=timer(r,true);t.poll(0);assertFalse(t.distracted());var n=timer(r,false);r.chanceDraw=0;int before=r.doubleDraws;n.poll(0);assertFalse(n.distracted());assertEquals(before,r.doubleDraws);}
    @Test void impossibleNonfiniteDistractionValuesRefuse(){for(double invalid:new double[]{Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY}){
        assertThrows(IllegalArgumentException.class,()->new FigSourcePollTimer.Distraction(invalid,2.3));
        assertThrows(IllegalArgumentException.class,()->new FigSourcePollTimer.Distraction(.01,invalid));}}
    @Test void prestartAndPeriodicUseRecoveredRanges(){var r=new Draws();r.interval=1100;var p=new FigSourcePollTimer(1100,1300,false,r,new FigSourcePollTimer.Distraction(.01,2.3));assertTrue(p.poll(0));assertFalse(p.poll(1099));r.interval=200;var a=new FigSourcePollTimer(200,400,true,r,new FigSourcePollTimer.Distraction(.01,2.3));assertTrue(a.poll(0));assertFalse(a.poll(199));}
}
