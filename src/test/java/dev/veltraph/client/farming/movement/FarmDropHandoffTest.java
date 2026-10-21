package dev.veltraph.client.farming.movement;

import dev.veltraph.client.world.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FarmDropHandoffTest {
    private static final class Driver implements FarmRouteDriver {
        State result=State.ACTIVE;Vec3 target;int bound,stops;
        public boolean startDrop(Vec3 target,int bound){this.target=target;this.bound=bound;return true;}
        public State tick(){return result;} public void stop(){stops++;}
        public String status(){return result.name();}
    }
    @Test void navigatorCompletionWithoutActualMovementCannotReportArrival(){
        var driver=new Driver();var handoff=new FarmDropHandoff(driver);var goal=new Vec3(1.5,61,0.5);
        handoff.start(goal,0);assertEquals(3,driver.bound);driver.result=FarmRouteDriver.State.ARRIVED;
        assertEquals(FarmRouteDriver.State.BLOCKED,handoff.tick(new Vec3(.5,64,.5),true,true,100));
        assertNull(handoff.target());
    }
    @Test void matchingFloatingPoseWaitsForActualGroundRatherThanTimedSuccess(){
        var driver=new Driver();var handoff=new FarmDropHandoff(driver);var goal=new Vec3(1.5,61,.5);
        handoff.start(goal,0);driver.result=FarmRouteDriver.State.ARRIVED;
        assertEquals(FarmRouteDriver.State.ACTIVE,handoff.tick(new Vec3(1.5,61.2,.5),false,true,100));
        assertEquals(FarmRouteDriver.State.ACTIVE,handoff.tick(new Vec3(1.5,61.2,.5),false,true,2000));
        assertEquals(FarmRouteDriver.State.ARRIVED,handoff.tick(goal,true,true,2100));
    }
    @Test void unloadingLandingAndMissingGroundExpireAndReleaseOwnership(){
        var driver=new Driver();var handoff=new FarmDropHandoff(driver);var goal=new Vec3(1.5,61,.5);
        handoff.start(goal,0);
        assertEquals(FarmRouteDriver.State.BLOCKED,handoff.tick(goal,false,false,100));
        handoff.start(goal,1000);driver.result=FarmRouteDriver.State.ARRIVED;
        handoff.tick(goal,false,true,1100);
        assertEquals(FarmRouteDriver.State.BLOCKED,handoff.tick(goal,false,true,6200));
        assertTrue(driver.stops>=3);
    }
}
