package dev.veltraph.client.farming.module;
import dev.veltraph.client.farming.movement.FarmRouteDriver;
import dev.veltraph.client.world.Vec3;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
/** Same source runs against frozen B and the route witness overlay. */
class FarmRoutePassiveCoverageRegressionTest {
    static final class Route implements FarmRouteDriver {
        int cursor=4;public boolean startDrop(Vec3 p,int b){return true;}public State tick(){cursor++;return State.ACTIVE;}
        public void stop(){}public String status(){return "Active retained drop";}
        public Optional<FarmMaintenanceState> maintenanceState(){return Optional.of(FarmMaintenanceState.of(this,"cursor",cursor));}
    }
    @Test void internalDropCursorMutationMustChangeFarmCheckpoint(){
        var r=new FarmMaintenanceWitnessTest.Rig();var farm=(WartCropsFarmModule)r.start("wart");var route=new Route();farm.setRouteDriver(route);
        var checkpoint=farm.maintenanceState();route.tick();
        assertNotEquals(checkpoint,farm.maintenanceState(),"identity-only native route observation hides cursor progress");
    }
}
