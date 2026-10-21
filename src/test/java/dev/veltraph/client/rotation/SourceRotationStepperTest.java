package dev.veltraph.client.rotation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class SourceRotationStepperTest {
    final SourceRotationStepper.Settings settings=SourceRotationStepper.Settings.standard(.5,1);
    @Test void recoveredFormulaUsesRemainingAngleAndAxisRange() {
        assertEquals(1.2,SourceRotationStepper.curve(180,true,3,1),1e-9);
        assertEquals(2.8666666667,SourceRotationStepper.curve(90,true,3,1),1e-8);
        assertEquals(.2,SourceRotationStepper.curve(0,true,3,1),1e-9);
        assertEquals(1.2,SourceRotationStepper.curve(90,false,3,1),1e-9);
    }
    @Test void sourceGoldenStepsDistinguishL1NormalizationFromHypotenuse() {
        var one=new SourceRotationStepper().step(0,0,90,0,settings);
        assertEquals(24.45,one.yawSpeed(),1e-4);
        var both=new SourceRotationStepper().step(0,0,90,90,settings);
        assertEquals(12.3,both.yawSpeed(),1e-4);assertEquals(5.1,both.pitchSpeed(),1e-4);
    }
    @Test void takesShortestYawPathAndCompletesOnMouseGrid() {
        var stepper=new SourceRotationStepper();float yaw=179,pitch=0;
        var out=stepper.step(yaw,pitch,-179,20,settings);
        assertTrue(out.yaw()>yaw);
        for(int i=0;i<100&&!out.complete();i++) out=stepper.step(out.yaw(),out.pitch(),-179,20,settings);
        assertTrue(out.complete());
        assertTrue(Math.abs(SourceRotationStepper.wrap(out.yaw()+179))<=SourceRotationStepper.mouseStep(.5));
        assertEquals(20,out.pitch(),SourceRotationStepper.mouseStep(.5));
    }
    @Test void rejectsWholeStepAboveRecoveredSpeedGuard() {
        var out=new SourceRotationStepper().step(0,0,90,30,new SourceRotationStepper.Settings(.5,1,1,100,3,1));
        assertTrue(out.rejected());assertFalse(out.complete());assertEquals(0,out.yaw());assertEquals(0,out.pitch());
    }
    @Test void mouseSensitivityChangesQuantizationWithoutInvalidSteps() {
        for(double sensitivity:new double[]{0,.5,1}) {
            var out=new SourceRotationStepper().step(0,0,90,30,SourceRotationStepper.Settings.standard(sensitivity,1));
            double gcd=SourceRotationStepper.mouseStep(sensitivity);
            assertEquals(Math.rint(out.yawSpeed()/gcd),out.yawSpeed()/gcd,1e-4);
            assertFalse(out.rejected());assertTrue(out.yaw()>0&&out.pitch()>0);
        }
    }
    @Test void validatesTargetsRatherThanApplyingInvalidCameraValues() {
        assertThrows(IllegalArgumentException.class,()->new SourceRotationStepper().step(0,0,Float.NaN,0,settings));
        assertThrows(IllegalArgumentException.class,()->new SourceRotationStepper().step(0,0,0,91,settings));
    }
}
