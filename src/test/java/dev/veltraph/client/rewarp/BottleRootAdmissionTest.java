package dev.veltraph.client.rewarp;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import dev.veltraph.client.inventory.bottle.BottleAcquisitionRuntime.Admission;
final class BottleRootAdmissionTest {
 @Test void freshReceivedAirborneFrameWaitsThenExactGroundedFrameCommits(){assertEquals(Admission.WAIT,BottleRootAdmission.withParentFooting(Admission.READY,false,0));assertEquals(Admission.READY,BottleRootAdmission.withParentFooting(Admission.READY,true,.02));}
 @Test void movingOrUnknownFootingNeverCommits(){for(double motion:new double[]{.02001,Double.NaN,Double.POSITIVE_INFINITY})assertEquals(Admission.WAIT,BottleRootAdmission.withParentFooting(Admission.READY,true,motion));}
 @Test void wrongReceiptCannotBecomeWaitOrReadyThroughFooting(){assertEquals(Admission.STOP,BottleRootAdmission.withParentFooting(Admission.STOP,false,0));assertEquals(Admission.STOP,BottleRootAdmission.withParentFooting(Admission.STOP,true,0));assertEquals(Admission.WAIT,BottleRootAdmission.withParentFooting(Admission.WAIT,true,0));}
}
