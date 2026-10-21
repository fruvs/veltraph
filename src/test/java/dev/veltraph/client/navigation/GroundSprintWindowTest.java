package dev.veltraph.client.navigation;
import dev.veltraph.client.world.Vec3;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class GroundSprintWindowTest {
    @Test void nearerAdmittedBrakingPointCanMatchHeadingWhenFurthestHintDoesNot(){
        var actual=new Vec3(0,70,0);
        var first=new Vec3(-Math.sin(Math.toRadians(7)),70,Math.cos(Math.toRadians(7)));
        var near=new Vec3(-3.1*Math.sin(Math.toRadians(4.3)),70,3.1*Math.cos(Math.toRadians(4.3)));
        var far=new Vec3(-5.9*Math.sin(Math.toRadians(8.4)),70,5.9*Math.cos(Math.toRadians(8.4)));
        var walker=new WalkController(1);walker.startCourse(List.of(first,near,far),actual,100,.14);
        var candidates=walker.locomotionSprintCandidates(actual);assertEquals(List.of(first,near,far),candidates);
        assertTrue(Math.abs(Math.toDegrees(Math.atan2(-far.x(),far.z())))>8);
        assertEquals(far,walker.locomotionSprintTarget(actual));
        assertEquals(near,GroundSprintWindow.select(actual,0,candidates).orElseThrow());
    }
    @Test void originalDistanceHeadingHeightAndFiniteBoundsRemainRequired(){var actual=new Vec3(0,70,0);assertTrue(GroundSprintWindow.select(actual,0,List.of(new Vec3(0,70,2.999),new Vec3(-1,70,3),new Vec3(0,70.1,3.5))).isEmpty());assertTrue(GroundSprintWindow.select(actual,Float.NaN,List.of(new Vec3(0,70,4))).isEmpty());}
    @Test void passiveCandidateEnumerationRetainsOriginalStraightnessAndCannotAdvanceWalker(){var w=new WalkController(1);var origin=new Vec3(.5,70,.5);w.startCourse(List.of(new Vec3(.5,70,1.5),new Vec3(.5,70,3.6),new Vec3(.6,70,5.1),new Vec3(1.2,70,6)),origin,100,.14);var before=w.maintenanceState();assertEquals(3,w.locomotionSprintCandidates(origin).size());assertEquals(new Vec3(.6,70,5.1),w.locomotionSprintTarget(origin));assertEquals(before,w.maintenanceState());}
}
