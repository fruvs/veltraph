package dev.veltraph.client.navigation;

import dev.veltraph.client.world.Vec3;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class WaterTravelMaintenanceRadiusTest {
    private final Vec3 goal = new Vec3(.5,70,.5);
    @Test void retainedExplicitRadiusIsPartOfActualMaintenanceWitnessAcrossAirResume() {
        var controller = new WaterTravelController();
        controller.start(List.of(goal),20,10,.18);
        var checkpoint = controller.maintenanceState();
        assertEquals(.18,checkpoint.fields().get("terminalRadius"));
        controller.surface(List.of(new Vec3(.5,71,.5)),20);
        controller.resume(List.of(goal),30);
        assertEquals(.18,controller.maintenanceState().fields().get("terminalRadius"));
        assertEquals(.18,checkpoint.fields().get("terminalRadius"),"Captured witness must stay immutable");
        controller.start(List.of(goal),20,40);
        assertEquals(Double.NaN,controller.maintenanceState().fields().get("terminalRadius"),"Legacy start must reset explicit arrival contract");
    }
    @Test void radiusOnlyChangeCannotPassUnchangedEngineWitness() throws Exception {
        var controller = new WaterTravelController();
        controller.start(List.of(goal),20,10,.18);
        var checkpoint = controller.maintenanceState();
        var field = WaterTravelController.class.getDeclaredField("terminalRadius");
        field.setAccessible(true);
        field.setDouble(controller,.2);
        assertNotEquals(checkpoint,controller.maintenanceState(),"The same route and engine with a different arrival contract is not the original checkpoint");
        field.setDouble(controller,.18);
        assertEquals(checkpoint,controller.maintenanceState());
    }
}
