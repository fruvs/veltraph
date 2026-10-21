package dev.veltraph.client.fishing.offense;
import dev.veltraph.client.navigation.WaterTravelController;
import dev.veltraph.client.world.Vec3;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
/** Actual BK settled native vector lies outside strict receipt radius but below both projected key thresholds. */
final class FishingInstalledSteeringDeadzoneTest {
    @Test void settledOutsideStrictRadiusMustRequestOrdinaryMovement(){var goal=new Vec3(.5,70,.5);var actual=new Vec3(.30000001192092896,70,.42344568759115647);var c=new WaterTravelController();c.start(List.of(goal),20,0,.18);var g=new WaterTravelController.Geometry(){public boolean connection(Vec3 a,Vec3 b){return true;}public boolean ground(Vec3 p){return true;}public boolean water(Vec3 p){return true;}};assertTrue(actual.distanceTo(goal)>.2);var out=c.tick(new WaterTravelController.Input(actual,new Vec3(0,-.005,-6.146227893049284E-13),-22.403198f,true,true,false,300,300,20,true,false,true,50),g,6000,1);assertEquals(WaterTravelController.State.TRAVELLING,out.state());assertTrue(out.forward()||out.back()||out.left()||out.right(),"Actual BK supported settled actor is outside strict .18 receipt but both .025 projected key thresholds create a deadzone");}
}
