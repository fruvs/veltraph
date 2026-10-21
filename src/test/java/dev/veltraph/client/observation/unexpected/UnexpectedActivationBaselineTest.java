package dev.veltraph.client.observation.unexpected;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static dev.veltraph.client.observation.unexpected.UnexpectedBehaviorMonitor.*;
final class UnexpectedActivationBaselineTest {
    private record Frame(Context context,Value view){}
    @Test void freshSuccessfulActivationDetectsFirstSameFrameOutsideRotation(){
        var current=new Context(new Activation("farm",7,true),1,1,1);var delivered=new AtomicInteger();var monitor=new UnexpectedBehaviorMonitor(f->delivered.incrementAndGet());monitor.context(current,100);
        var baseline=new Frame(current,Value.rotation(0,0));assertEquals(0,delivered.get());
        var saved=baseline;assertTrue(UnexpectedSampleBoundary.comparable(saved.context(),current));
        assertEquals(Classification.UNEXPECTED,monitor.observe(new Observation(current,Kind.ROTATION,saved.view(),Value.rotation(90,0),101)).classification());assertEquals(1,delivered.get());
    }
    @Test void ownedSetupIsBaselineNotAnOutsideFinding(){
        var current=new Context(new Activation("farm",8,true),1,1,1);var delivered=new AtomicInteger();var monitor=new UnexpectedBehaviorMonitor(f->delivered.incrementAndGet());monitor.context(current,100);
        monitor.intent(current,Kind.ROTATION,Value.rotation(60,0),0,100,250);var baseline=new Frame(current,Value.rotation(60,0));
        assertEquals(0,delivered.get());assertEquals(1,monitor.pendingIntents());assertFalse(UnexpectedSampleBoundary.changed(baseline.view(),Value.rotation(60,0),Kind.ROTATION,.5));
    }
    @Test void preparedHandoffCapturesNewGenerationAndKeepsNativeEpochs(){
        var old=new Context(new Activation("farm",23,true),1,1,1);var prepared=new Context(new Activation("farm",27,true),1,1,1);var oldBaseline=new Frame(old,Value.rotation(0,0));var baseline=new Frame(prepared,Value.rotation(0,0));
        assertEquals(prepared,baseline.context());assertFalse(UnexpectedSampleBoundary.comparable(old,prepared));assertTrue(UnexpectedSampleBoundary.comparable(baseline.context(),prepared));
    }
    @Test void newActivationDoesNotBorrowPreviousIntent() {
        var prior=new Context(new Activation("farm",1,true),1,1,1);var fresh=new Context(new Activation("farm",2,true),1,1,1);var monitor=new UnexpectedBehaviorMonitor(f->{});
        monitor.intent(prior,Kind.ROTATION,Value.rotation(90,0),0,100,250);monitor.context(fresh,101);assertEquals(0,monitor.pendingIntents());
        assertEquals(Classification.UNEXPECTED,monitor.observe(new Observation(fresh,Kind.ROTATION,Value.rotation(0,0),Value.rotation(90,0),102)).classification());
    }
}
