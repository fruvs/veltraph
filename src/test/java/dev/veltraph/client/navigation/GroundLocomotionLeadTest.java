package dev.veltraph.client.navigation;
import dev.veltraph.client.world.Vec3;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class GroundLocomotionLeadTest {
    @Test void straightContinuationHintIsBoundedAndCannotAdvanceTheController(){
        var walker=new WalkController(1);var origin=new Vec3(.5,1,.5);
        walker.startCourse(List.of(new Vec3(.5,1,1.5),new Vec3(.5,1,4.5),new Vec3(.5,1,8.5)),origin,100,.35);
        var before=walker.maintenanceState();
        assertEquals(new Vec3(.5,1,4.5),walker.locomotionSprintTarget(origin));assertEquals(new Vec3(.5,1,1.5),walker.locomotionTarget());
        assertEquals(before,walker.maintenanceState());
    }
    @Test void turnRiseAndDeviationDoNotBecomeStraightSprintPermission(){
        var origin=new Vec3(.5,1,.5);var first=new Vec3(.5,1,1.5);
        for(Vec3 bend:List.of(new Vec3(1.5,1,3.5),new Vec3(.5,2,3.5),new Vec3(.7,1,3.5))){
            var walker=new WalkController(1);walker.startCourse(List.of(first,bend),origin,100,.35);
            assertEquals(first,walker.locomotionSprintTarget(origin));
        }
    }
}
