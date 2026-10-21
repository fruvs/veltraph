package dev.veltraph.client.inventory.bottle;

import dev.veltraph.client.game.GameArea;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static dev.veltraph.client.inventory.bottle.BottleAcquisitionRuntime.*;

/** These regressions compile unchanged against H: native support/UNKNOWN must settle before adoption. */
class BottleAcquisitionLandingSupportTest {
    static Observation copy(Observation o,boolean supported,GameArea area){return new Observation(o.player(),o.world(),o.connection(),o.uuid(),o.dimension(),area,o.position(),o.yaw(),o.pitch(),o.valid(),supported,o.menu(),o.title(),o.menuId(),o.revision(),o.cursor(),o.selected(),o.inventory(),o.rows(),o.teleportSerial(),o.teleportPosition(),o.nowMillis());}
    @Test void freshCorrectAirborneNativeLandingWaitsThenCommitsSupport(){var r=new BottleAcquisitionRuntimeTest.Run();r.reach(State.WAIT_TRAVEL);var ticket=r.runtime.pendingTravel().orElseThrow();var o=r.port.read();assertEquals(Progress.WAITING,r.runtime.pollTravel(copy(o,false,GameArea.HUB),r.tick+1));assertEquals(State.WAIT_TRAVEL,r.runtime.state());assertSame(ticket,r.runtime.pendingTravel().orElseThrow());assertEquals(0,r.port.interactions);assertEquals(Progress.COMPLETE,r.runtime.pollTravel(copy(o,true,GameArea.HUB),r.tick+2));assertEquals(State.PATH,r.runtime.state());assertEquals(1,r.port.commands);}
    @Test void freshCorrectNativeLandingWaitsForUnknownAreaToResolve(){var r=new BottleAcquisitionRuntimeTest.Run();r.reach(State.WAIT_TRAVEL);var ticket=r.runtime.pendingTravel().orElseThrow();var o=r.port.read();assertEquals(Progress.WAITING,r.runtime.pollTravel(copy(o,true,GameArea.UNKNOWN),r.tick+1));assertSame(ticket,r.runtime.pendingTravel().orElseThrow());assertEquals(0,r.port.walks);assertEquals(Progress.COMPLETE,r.runtime.pollTravel(copy(o,true,GameArea.HUB),r.tick+2));assertEquals(State.PATH,r.runtime.state());}
}
