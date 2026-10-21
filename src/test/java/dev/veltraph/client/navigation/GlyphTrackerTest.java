package dev.veltraph.client.navigation;
import dev.veltraph.client.combat.slayer.GlyphTracker;
import dev.veltraph.client.world.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class GlyphTrackerTest {
    private final BlockPos beacon=new BlockPos(2,1,2);
    @Test void ignoresExistingOrUncorrelatedBlocks() {
        var tracker=new GlyphTracker();
        assertTrue(tracker.observe(Map.of(),Set.of(beacon),0).isEmpty());
        assertTrue(tracker.observe(Map.of(1,new Vec3(2.5,2,2.5)),Set.of(beacon),50).isEmpty());
        assertTrue(tracker.observe(Map.of(),Set.of(beacon,new BlockPos(8,1,8)),100).isEmpty());
    }
    @Test void tracksThrowLandingAndServerRemoval() {
        var tracker=new GlyphTracker();tracker.observe(Map.of(),Set.of(),0);
        tracker.observe(Map.of(1,new Vec3(2.5,2,2.5)),Set.of(),50);
        assertEquals(Set.of(beacon),tracker.observe(Map.of(),Set.of(beacon),100));
        assertEquals(Set.of(beacon),tracker.observe(Map.of(),Set.of(beacon),600));
        assertTrue(tracker.observe(Map.of(),Set.of(),700).isEmpty());
        assertTrue(tracker.observe(Map.of(),Set.of(beacon),800).isEmpty());
    }
    @Test void oneFlyingEntityCannotClaimMultipleBeaconBlocks() {
        var tracker=new GlyphTracker();var flight=Map.of(1,new Vec3(2.5,2,2.5));
        tracker.observe(flight,Set.of(),0);
        assertEquals(Set.of(beacon),tracker.observe(flight,Set.of(beacon),50));
        var second=new BlockPos(3,1,2);
        assertEquals(Set.of(beacon),tracker.observe(flight,Set.of(beacon,second),100));
        tracker.observe(flight,Set.of(),7000);
        assertTrue(tracker.observe(flight,Set.of(second),7100).isEmpty());
    }
    @Test void simultaneousBlocksMatchTheNearestFlightPosition() {
        var tracker=new GlyphTracker();var flight=Map.of(1,new Vec3(2.5,1.5,2.5));
        tracker.observe(flight,Set.of(),0);
        assertEquals(Set.of(beacon),tracker.observe(Map.of(),Set.of(beacon,new BlockPos(1,1,2)),50));
    }
    @Test void staleFlightAndWorldResetCannotCreateAFalseGlyph() {
        var tracker=new GlyphTracker();tracker.observe(Map.of(1,new Vec3(2.5,2,2.5)),Set.of(),0);
        assertTrue(tracker.observe(Map.of(),Set.of(beacon),2000).isEmpty());
        tracker.reset();assertTrue(tracker.observe(Map.of(),Set.of(beacon),2050).isEmpty());
    }
}
