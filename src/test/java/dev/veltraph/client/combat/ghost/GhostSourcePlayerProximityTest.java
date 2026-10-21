package dev.veltraph.client.combat.ghost;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class GhostSourcePlayerProximityTest {
    @Test void strictThreeDistanceAndStrictEightyThresholdRetainOriginalUpdater(){
        var tracker=new GhostSourcePlayerProximity();Object p=new Object();
        for(int i=0;i<40;i++)tracker.observe(List.of(new GhostSourcePlayerProximity.PlayerPoint(p,0)));
        assertEquals(80,tracker.score(p));assertFalse(tracker.randomized());
        tracker.observe(List.of(new GhostSourcePlayerProximity.PlayerPoint(p,0)));assertEquals(82,tracker.score(p));assertTrue(tracker.randomized());
        tracker.observe(List.of(new GhostSourcePlayerProximity.PlayerPoint(p,3)));assertEquals(81,tracker.score(p));
    }
    @Test void includingLocalIdentityDoesNotBecomeStaffOrRankClassification(){
        var tracker=new GhostSourcePlayerProximity();Object local=new Object();Object ordinary=new Object();
        for(int i=0;i<60;i++)tracker.observe(List.of(new GhostSourcePlayerProximity.PlayerPoint(local,0),new GhostSourcePlayerProximity.PlayerPoint(ordinary,3)));
        assertEquals(100,tracker.score(local));assertEquals(0,tracker.score(ordinary));assertTrue(tracker.randomized());
    }
    @Test void absentPlayersArePrunedAndFarScoresDecayToRemoval(){
        var tracker=new GhostSourcePlayerProximity();Object p=new Object();tracker.observe(List.of(new GhostSourcePlayerProximity.PlayerPoint(p,2.999)));
        tracker.observe(List.of(new GhostSourcePlayerProximity.PlayerPoint(p,10)));assertEquals(1,tracker.score(p));
        tracker.observe(List.of(new GhostSourcePlayerProximity.PlayerPoint(p,10)));assertEquals(0,tracker.score(p));
        tracker.observe(List.of(new GhostSourcePlayerProximity.PlayerPoint(p,0)));tracker.observe(List.of());assertEquals(0,tracker.score(p));assertFalse(tracker.randomized());
    }
}
