package dev.veltraph.client.rotation;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
class RotationRequestTest {
    final SourceRotationStepper.Settings settings=SourceRotationStepper.Settings.standard(.5,1);
    @Test void replacedRequestNeverRunsItsOldCallback() {
        var request=new RotationRequest(new SourceRotationStepper());var old=new AtomicInteger();var current=new AtomicInteger();
        request.replace(90,0,old::incrementAndGet);request.replace(0,0,current::incrementAndGet);
        var step=request.advance(0,0,settings,true,false);assertEquals(0,current.get());request.applied(step);request.applied(step);
        assertEquals(0,old.get());assertEquals(1,current.get());assertFalse(request.pending());
    }
    @Test void callbackCanRequestAnotherRotationWithoutBeingCleared() {
        var request=new RotationRequest(new SourceRotationStepper());
        request.replace(0,0,()->request.replace(90,0,null));request.applied(request.advance(0,0,settings,true,false));
        assertTrue(request.pending());assertTrue(request.advance(0,0,settings,true,false).yaw()>0);
    }
    @Test void pauseCancellationAndStaleAppliedResultCannotCompleteANewTarget() {
        var request=new RotationRequest(new SourceRotationStepper());var count=new AtomicInteger();
        request.replace(0,0,count::incrementAndGet);var stale=request.advance(0,0,settings,true,false);
        request.replace(90,0,count::incrementAndGet);request.applied(stale);assertEquals(0,count.get());
        assertEquals(0,request.advance(0,0,settings,true,true).yaw());assertTrue(request.pending());
        assertEquals(0,request.advance(0,0,settings,false,false).yaw());request.cancel();assertFalse(request.pending());
    }
}
