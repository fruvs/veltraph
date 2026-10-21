package dev.veltraph.client.navigation;
import dev.veltraph.client.world.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ReturnApproachGeometryTest {
 @Test void raisedOperationalOriginKeepsLevelDeviceApproachAndCanonicalEntry(){var origin=new Vec3(20.5,72,-41.5);var goal=new Vec3(50.5,70,-41.5);var landing=ReturnApproachGeometry.landing(origin,goal,8).orElseThrow();assertEquals(new Vec3(28.5,72,-41.5),landing);assertEquals(new Vec3(50.5,70,-41.5),goal);assertEquals(8,origin.distanceTo(landing),1e-9);assertTrue(landing.distanceTo(goal)<origin.distanceTo(goal));assertTrue(ReturnApproachGeometry.exact(origin,goal,landing,8));}
 @Test void finalStraightEntryAndTooShortApproachDoNotSelectDevice(){var origin=new Vec3(0,70,0);assertTrue(ReturnApproachGeometry.landing(origin,new Vec3(10,70,0),8).isEmpty());assertTrue(ReturnApproachGeometry.landing(origin,new Vec3(0,85,0),8).isEmpty());}
 @Test void nonfiniteZeroNegativeOrUnboundedCapabilityRangeRefuses(){var origin=new Vec3(0,70,0);var goal=new Vec3(30,70,30);for(double range:new double[]{Double.NaN,Double.POSITIVE_INFINITY,0,-1,65})assertTrue(ReturnApproachGeometry.landing(origin,goal,range).isEmpty());assertTrue(ReturnApproachGeometry.landing(new Vec3(Double.NaN,70,0),goal,8).isEmpty());assertTrue(ReturnApproachGeometry.landing(origin,new Vec3(0,Double.NaN,0),8).isEmpty());}
 @Test void copiedAlternativeSideOrHeightIsNotTheQualifiedEndpoint(){var origin=new Vec3(0,72,0);var goal=new Vec3(30,70,0);assertFalse(ReturnApproachGeometry.exact(origin,goal,new Vec3(8,71.5,0),8));assertFalse(ReturnApproachGeometry.exact(origin,goal,new Vec3(8,72,.1),8));assertFalse(ReturnApproachGeometry.exact(origin,goal,new Vec3(-8,72,0),8));}
}
