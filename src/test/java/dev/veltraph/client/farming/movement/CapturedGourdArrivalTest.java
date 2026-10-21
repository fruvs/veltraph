package dev.veltraph.client.farming.movement;

import dev.veltraph.client.world.Direction;
import dev.veltraph.client.world.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class CapturedGourdArrivalTest {
    @Test void withinRadiusButMovingCannotCommitCameraHandoff() {
        var arrival = new CapturedGourdArrival();
        var goal = new Vec3(.5,71,-142.5);
        assertEquals(CapturedGourdArrival.Action.RELEASE,
                arrival.observe(new Vec3(.5,71,-142.55),goal,Direction.SOUTH,.12));
        assertEquals(CapturedGourdArrival.Action.RELEASE,
                arrival.observe(new Vec3(.5,71,-142.45),goal,Direction.SOUTH,.08));
        assertEquals(CapturedGourdArrival.Action.RELEASE,
                arrival.observe(new Vec3(.5,71,-142.45),goal,Direction.SOUTH,0));
        assertEquals(CapturedGourdArrival.Action.ARRIVED,
                arrival.observe(new Vec3(.5,71,-142.45),goal,Direction.SOUTH,0));
    }

    @Test void stopsBeforeCoastAndCorrectsPassedGoalWithBackKey() {
        var arrival = new CapturedGourdArrival();
        var goal = new Vec3(.5,71,-142.5);
        arrival.observe(new Vec3(.5,71,-142.9),goal,Direction.SOUTH,.2);
        assertEquals(CapturedGourdArrival.Action.RELEASE,
                arrival.observe(new Vec3(.5,71,-142.7),goal,Direction.SOUTH,.2));
        assertEquals(CapturedGourdArrival.Action.BACK,
                arrival.observe(new Vec3(.5,71,-142.281),goal,Direction.SOUTH,0));
        assertEquals(CapturedGourdArrival.Action.RELEASE,
                arrival.observe(new Vec3(.5,71,-142.281),goal,Direction.SOUTH,0));
    }

    @Test void boundedOrdinaryKeyInertiaModelSettlesWithoutSneakOrPoseWrites() {
        var arrival = new CapturedGourdArrival();
        double position = -1, velocity = .2;
        boolean arrived = false;
        for (int tick=0; tick<240; tick++) {
            var action = arrival.observe(new Vec3(position,71,.5),new Vec3(0,71,.5),
                    Direction.EAST,Math.abs(velocity));
            if (action == CapturedGourdArrival.Action.ARRIVED) { arrived=true; break; }
            double input = action == CapturedGourdArrival.Action.FORWARD ? .10
                    : action == CapturedGourdArrival.Action.BACK ? -.10 : 0;
            velocity = velocity*.55 + input;
            position += velocity;
        }
        assertTrue(arrived);
        assertTrue(Math.abs(position)<=.10);
        assertTrue(Math.abs(velocity)<=.015);
    }
    @Test void invalidObservationGapCannotReuseOldStillSamples() {
        var arrival = new CapturedGourdArrival();
        var goal = new Vec3(.5,71,-142.5);
        arrival.observe(goal,goal,Direction.SOUTH,0);
        assertEquals(CapturedGourdArrival.Action.RELEASE,arrival.observe(goal,goal,Direction.SOUTH,0));
        arrival.reset();
        assertEquals(CapturedGourdArrival.Action.RELEASE,arrival.observe(goal,goal,Direction.SOUTH,0));
        assertEquals(CapturedGourdArrival.Action.RELEASE,arrival.observe(goal,goal,Direction.SOUTH,0));
        assertEquals(CapturedGourdArrival.Action.ARRIVED,arrival.observe(goal,goal,Direction.SOUTH,0));
    }

}
