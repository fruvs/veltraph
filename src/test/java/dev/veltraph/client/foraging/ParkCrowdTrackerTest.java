package dev.veltraph.client.foraging;
import dev.veltraph.client.world.Vec3;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ParkCrowdTrackerTest {
    @Test void sourcePressureNeedsFortyOneNearUpdatesThenDecaysAndRemovesDepartedPlayers() {
        var tracker=new ParkCrowdTracker();var id=UUID.randomUUID();var local=new Vec3(0,70,0);
        for(int i=0;i<40;i++)assertFalse(tracker.observe(local,Map.of(id,new Vec3(2.99,70,0))));
        assertTrue(tracker.observe(local,Map.of(id,new Vec3(2.99,70,0))));
        assertTrue(tracker.observe(local,Map.of(id,new Vec3(3,70,0))));
        assertFalse(tracker.observe(local,Map.of(id,new Vec3(3,70,0))));
        for(int i=0;i<80;i++)tracker.observe(local,Map.of(id,local));
        assertFalse(tracker.observe(local,Map.of()));
        assertFalse(tracker.crowded());tracker.clear();
    }
    @Test void newIdentityDoesNotInheritThePreviousPlayersHistory() {
        var tracker=new ParkCrowdTracker();var local=new Vec3(0,0,0);var id=UUID.randomUUID();
        for(int i=0;i<60;i++)tracker.observe(local,Map.of(id,local));
        assertTrue(tracker.crowded());
        assertFalse(tracker.observe(local,Map.of(UUID.randomUUID(),local)));
    }
}
