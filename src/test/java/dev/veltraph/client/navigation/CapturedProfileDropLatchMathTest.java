package dev.veltraph.client.navigation;
import dev.veltraph.client.world.Vec3;
import dev.veltraph.client.navigation.mc.GroundProfileMotionCarrier.NativePose;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class CapturedProfileDropLatchMathTest {
    private static final Vec3 ORIGINAL=new Vec3(0,1,.5),LANDING=new Vec3(0,0,1.5);
    private static NativePose pose(double y,boolean ground,boolean sprint,int tick,float speed){return new NativePose(new Vec3(0,y,1.32),new Vec3(0,-.0784,.02),0,ground,sprint,tick,speed);}
    @Test void supportedHorizontalStepCanRetainGroundFlagForAnObservedExitReceipt(){
        assertTrue(CapturedProfileDropLatchMath.mayRetain(pose(1,true,false,10,.4F),pose(1,true,false,10,.4F),ORIGINAL,LANDING,0));
    }
    @Test void jumpAirLandingOrDifferentSpeedCannotBecomeOldSourceSupportReceipt(){
        assertFalse(CapturedProfileDropLatchMath.mayRetain(pose(1,true,false,10,.4F),pose(1,true,false,10,.4F),ORIGINAL,LANDING,1));
        assertFalse(CapturedProfileDropLatchMath.mayRetain(pose(1,true,false,10,.4F),pose(.9,false,false,10,.4F),ORIGINAL,LANDING,0));
        assertFalse(CapturedProfileDropLatchMath.mayRetain(pose(1,true,false,10,.4F),pose(0,true,false,10,.4F),ORIGINAL,LANDING,0));
        assertFalse(CapturedProfileDropLatchMath.mayRetain(pose(1,true,false,10,.4F),pose(1,true,false,10,.52F),ORIGINAL,LANDING,0));
    }
    @Test void foreignTickSprintOrWiderDescriptorCannotAuthorizeLatchedExit(){
        assertFalse(CapturedProfileDropLatchMath.mayRetain(pose(1,true,false,10,.4F),pose(1,true,false,11,.4F),ORIGINAL,LANDING,0));
        assertFalse(CapturedProfileDropLatchMath.mayRetain(pose(1,true,false,10,.4F),pose(1,true,true,10,.4F),ORIGINAL,LANDING,0));
        assertFalse(CapturedProfileDropLatchMath.mayRetain(pose(1,true,false,10,.4F),pose(1,true,false,10,.4F),ORIGINAL,new Vec3(0,-1,1.5),0));
    }
}
