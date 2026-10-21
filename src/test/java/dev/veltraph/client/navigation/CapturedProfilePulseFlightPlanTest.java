package dev.veltraph.client.navigation;
import dev.veltraph.client.world.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static dev.veltraph.client.navigation.CapturedProfileTrajectory.*;
class CapturedProfilePulseFlightPlanTest {
    @Test void boostControlsHaveOneForwardOnsetThenBackAndNeutralToAvoidAirDoubleTapSprint(){
        for(float base:new float[]{.36F,.4F})for(double rise:new double[]{.600001D,1D})for(double distance:new double[]{.6D,1D,1.65D}) {
            double z=distance==1.65D?.1D:.5D;
            var p=CapturedProfilePulseFlightPlan.jump(CapturedProfileTrajectoryTest.profile(base,false),new Vec3(0,0,z),new Vec3(0,0,0),
                    new Vec3(0,rise,z+distance),.6F,0,100).orElseThrow();
            assertEquals(Input.NEUTRAL,p.firstInput());int starts=0;boolean previous=false;
            for(var c:p.expectedControls()){boolean forward=c.input()==Input.FORWARD;if(forward&&!previous)starts++;previous=forward;}
            assertEquals(1,starts);
            var end=p.trace().samples().getLast();assertTrue(end.position().horizontalDistanceTo(p.target())<=.075D);
            assertTrue(Math.hypot(end.intendedMove().x(),end.intendedMove().z())<=.05D);
            assertEquals(rise==1D?24:25,p.trace().samples().size());
        }
    }
    @Test void descentDefersNewForwardUntilActualNativeCounterCanExpireNaturally(){
        var p=CapturedProfilePulseFlightPlan.descent(CapturedProfileTrajectoryTest.profile(.4F,false),new Vec3(0,.9216,1.32),
                new Vec3(0,-.155232,.02),new Vec3(0,1,.5),new Vec3(0,0,1.5),.6F,0,Phase.OBSERVED_AIRBORNE,100,1).orElseThrow();
        assertEquals(Input.NEUTRAL,p.firstInput());
        int starts=0;boolean previous=false;
        for(var c:p.expectedControls()){boolean forward=c.input()==Input.FORWARD;if(forward&&!previous)starts++;previous=forward;}
        assertTrue(starts<=1);
    }
    @Test void unreasonableCounterDelayDoesNotCreateNativeTicksOrAirPermission(){
        assertTrue(CapturedProfilePulseFlightPlan.descent(CapturedProfileTrajectoryTest.profile(.4F,false),new Vec3(0,.9,1.32),
                new Vec3(0,-.15,.02),new Vec3(0,1,.5),new Vec3(0,0,1.5),.6F,0,Phase.OBSERVED_AIRBORNE,100,41).isEmpty());
    }
    @Test void descentCannotRebaseOriginalDropOrExtendHeightBeyondOneBlock(){
        var p=CapturedProfileTrajectoryTest.profile(.4F,false);var origin=new Vec3(0,1,.5);var actual=new Vec3(0,.9,1.32);var v=new Vec3(0,-.15,.02);
        assertTrue(CapturedProfilePulseFlightPlan.descent(p,actual,v,origin,new Vec3(0,-1,1.5),.6F,0,Phase.OBSERVED_AIRBORNE,100,0).isEmpty());
        assertTrue(CapturedProfilePulseFlightPlan.descent(p,actual,v,origin,new Vec3(1,0,1.5),.6F,0,Phase.OBSERVED_AIRBORNE,100,0).isEmpty());
    }
}
