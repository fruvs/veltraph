package dev.veltraph.client.navigation;
import dev.veltraph.client.world.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static dev.veltraph.client.navigation.CapturedProfileTrajectory.*;
class CapturedProfileGroundPlanTest {
    @Test void endpointPulsePlanSettlesBelowOriginalStrictToleranceForBothCapturedSpeeds(){
        for(float base:new float[]{.36F,.4F})for(double distance:new double[]{.1D,.5D,1D}) {
            var target=new Vec3(0,0,distance);
            var plan=CapturedProfileGroundPlan.plan(CapturedProfileTrajectoryTest.profile(base,false),new Vec3(0,0,0),new Vec3(0,0,0),target,.6F,0,.04D,100).orElseThrow();
            var end=plan.trace().samples().getLast();
            assertTrue(end.position().horizontalDistanceTo(target)<=.04D);
            assertTrue(Math.hypot(end.velocity().x(),end.velocity().z())<.003D);
            assertTrue(plan.trace().samples().size()<=28);
            assertNotEquals(Input.NEUTRAL,plan.firstInput());
        }
    }
    @Test void pulseSearchRefusesWiderTerminalToleranceOrSprintAssumptions(){
        var zero=new Vec3(0,0,0);var target=new Vec3(0,0,.1);
        assertTrue(CapturedProfileGroundPlan.plan(CapturedProfileTrajectoryTest.profile(.4F,false),zero,zero,target,.6F,0,.36,100).isEmpty());
        assertTrue(CapturedProfileGroundPlan.plan(CapturedProfileTrajectoryTest.profile(.4F,true),zero,zero,target,.6F,0,.04,100).isEmpty());
    }
    @Test void unknownHeightAndDistantTargetAreNotNativeGroundPulsePermission(){
        var zero=new Vec3(0,0,0);var p=CapturedProfileTrajectoryTest.profile(.4F,false);
        assertTrue(CapturedProfileGroundPlan.plan(p,zero,zero,new Vec3(0,.6,.1),.6F,0,.04,100).isEmpty());
        assertTrue(CapturedProfileGroundPlan.plan(p,zero,zero,new Vec3(0,0,5),.6F,0,.04,100).isEmpty());
    }
    @Test void finiteEffectsMustOutlastPlannedPulses(){
        assertTrue(CapturedProfileGroundPlan.plan(CapturedProfileTrajectoryTest.profile(.4F,false),new Vec3(0,0,0),new Vec3(0,0,0),new Vec3(0,0,.1),.6F,0,.04,1).isEmpty());
    }
    @Test void repeatedlyIssuedFirstControlReplansFromEachActualArithmeticResult(){
        for(float base:new float[]{.36F,.4F})for(double distance:new double[]{.1D,.5D,1D}) {
            var profile=CapturedProfileTrajectoryTest.profile(base,false);var target=new Vec3(0,0,distance);
            Vec3 p=new Vec3(0,0,0),v=new Vec3(0,0,0);
            for(int i=0;i<64&&(p.horizontalDistanceTo(target)>.04D||Math.hypot(v.x(),v.z())>=.003D);i++) {
                var plan=CapturedProfileGroundPlan.plan(profile,p,v,target,.6F,0,.04D,100).orElseThrow();
                var result=frame(profile,p,v,.6F,0,plan.firstInput(),Phase.SUPPORTED_GROUND).orElseThrow();
                p=result.position();v=result.velocity();
            }
            assertTrue(p.horizontalDistanceTo(target)<=.04D);assertTrue(Math.hypot(v.x(),v.z())<.003D);
        }
    }
}
