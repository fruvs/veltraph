package dev.veltraph.client.navigation.mc;
import dev.veltraph.client.navigation.*;
import dev.veltraph.client.world.Vec3;
import java.util.List;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class StationaryNeutralHorizonTest {
    private GroundStationaryNeutralProof.Descriptor descriptor(int traceFrames,int brakeFrames){
        var profile=GroundNativePhysicsProfile.classify(new GroundNativePhysicsProfile.State((double).36F,(double).36F,.36F,
                List.of(),(double).42F,.08D,1D,1D,-1,List.of(),0D,1F)).orElseThrow();
        var p=new Vec3(.5,70,.12);var v=new Vec3(0,0,0);
        var trace=CapturedProfileTrajectory.forecast(profile,p,v,.6F,0F,CapturedProfileTrajectory.Phase.SUPPORTED_GROUND,
                Collections.nCopies(traceFrames,CapturedProfileTrajectory.Input.NEUTRAL),0).orElseThrow();
        var brake=GroundProfileForecast.groundBrake(profile,p,v,.6F,brakeFrames).orElseThrow();
        return GroundStationaryNeutralProof.describe(profile,trace,brake).orElseThrow();
    }
    @Test void oneTraceFrameCannotClaimLongerBrakeOrUnprovedSteadyY(){
        var d=descriptor(1,32);assertEquals(List.of(new Vec3(0,0,0)),d.collisionMoves());assertFalse(StationaryNeutralHorizon.covered(d));
    }
    @Test void actualThirtyThreeAndThirtyTwoKeepsBothDistinctCollisionMoves(){
        var d=descriptor(33,32);assertTrue(StationaryNeutralHorizon.covered(d));assertEquals(33,d.maxHorizon());
        assertEquals(List.of(new Vec3(0,0,0),new Vec3(0,-.08D*(double).98F,0)),d.collisionMoves());
    }
    @Test void fullSixtyFourFrameHorizonIsRetainedAndShorterTraceRefuses(){
        assertTrue(StationaryNeutralHorizon.covered(descriptor(64,64)));assertFalse(StationaryNeutralHorizon.covered(descriptor(2,64)));
        assertTrue(StationaryNeutralHorizon.covered(descriptor(1,1)));assertFalse(StationaryNeutralHorizon.covered(null));
    }
}
