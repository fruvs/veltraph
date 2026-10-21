package dev.veltraph.client.navigation;
import dev.veltraph.client.world.Vec3;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class TravelPathFlightTest {
    @Test void rejectsDisconnectedFallbackInsteadOfEmittingBlockedSegment() {
        var start=new Vec3(.9,0,.9);
        assertTrue(TravelPath.shapeFlight(start,List.of(new Vec3(5,0,.5)),(a,b)->false,(a,b)->false,1).isEmpty());
    }
    @Test void narrowRequiredClearanceRemainsUsableWithoutPreferredMargin() {
        var start=new Vec3(0,0,0);
        var path=TravelPath.shapeFlight(start,List.of(new Vec3(1,0,0),new Vec3(2,0,0)),
                (a,b)->a.z()==0&&b.z()==0,(a,b)->false,1);
        assertFalse(path.isEmpty());assertEquals(new Vec3(2,0,0),path.getLast());
        assertTrue(path.stream().allMatch(p->p.z()==0));
    }
}
