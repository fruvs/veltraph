package dev.veltraph.client.rewarp;

import dev.veltraph.client.navigation.TravelHeading;
import dev.veltraph.client.rotation.OwnedRotationTrajectory;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class SavedAnchorViewToleranceTest {
    @Test void measuredGeorgeTerminalPosePassesLegacyButCannotMeetExactCallerReceipt() {
        assertTrue(SavedAnchorViewTolerance.aligned(-270.89655f,.1731f,90,0,1.5));
        assertFalse(SavedAnchorViewTolerance.aligned(-270.89655f,.1731f,90,0,.01));
        assertFalse(SavedAnchorViewTolerance.aligned(90,.011f,90,0,.01));
    }
    @Test void equivalentCircularYawRetainsOriginalPointZeroOneBound() {
        assertTrue(SavedAnchorViewTolerance.aligned(-270.005f,.005f,90,0,.01));
        assertFalse(SavedAnchorViewTolerance.aligned(-270.02f,0,90,0,.01));
    }
    @Test void invalidCallerToleranceCannotBroadenLegacyReceipt() {
        for(double value:new double[]{0,-1,Double.NaN,Double.POSITIVE_INFINITY,1.50001})
            assertFalse(SavedAnchorViewTolerance.valid(value));
        assertTrue(SavedAnchorViewTolerance.valid(.01));
        assertFalse(SavedAnchorViewTolerance.aligned(Float.NaN,0,90,0,.01));
    }
    @Test void actualTrackingHeadingsAndFrameEndpointsConvergeWithoutQuantizedRequestOrPoseSnap() {
        for(double speed:List.of(.25,1.0,2.0)) {
            var serviceYaw=new TravelHeading();var servicePitch=new TravelHeading();
            var trajectory=new OwnedRotationTrajectory();
            var scope=new OwnedRotationTrajectory.Scope(new Object(),new Object(),new Object(),new Object());
            var pose=new OwnedRotationTrajectory.Pose(-270.89655f,.1731f);int tick=0;
            // McSavedAnchorRecovery.align supplies the complete target. The service alone
            // integrates its bounded headings, then the canonical frame path applies the pose.
            while(!SavedAnchorViewTolerance.aligned(pose.yaw(),pose.pitch(),90,0,.01)&&tick<300) {
                var requested=new OwnedRotationTrajectory.Pose(serviceYaw.step(pose.yaw(),90,speed),servicePitch.stepLinear(pose.pitch(),0,speed));
                long ticket=trajectory.plan(scope,tick,pose,requested);
                for(float partial:new float[]{.18f,.52f,.86f})pose=trajectory.frame(scope,tick,partial,pose).orElseThrow().pose();
                pose=trajectory.beforeTick(scope,tick,pose).orElseThrow().pose();
                assertTrue(trajectory.completed(scope,ticket));
                assertTrue(trajectory.frame(scope,tick,1,pose).isEmpty(),"Idle frame must not replay endpoint");
                tick++;
            }
            assertTrue(tick>0&&tick<300,"Actual tracking/Frame path must reach strict receipt at declared speed "+speed);
            assertTrue(SavedAnchorViewTolerance.aligned(pose.yaw(),pose.pitch(),90,0,.01));
        }
    }
}
