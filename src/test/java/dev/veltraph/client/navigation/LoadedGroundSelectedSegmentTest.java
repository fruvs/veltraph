package dev.veltraph.client.navigation;
import dev.veltraph.client.world.Vec3;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Actual controller selection/lifetime regressions; no native publication or device receipt is simulated. */
final class LoadedGroundSelectedSegmentTest {
    private static final Object P=new Object(),W=new Object(),C=new Object();
    private static LoadedGroundReturnController.Observation observe(Vec3 p,long now){return new LoadedGroundReturnController.Observation(P,W,C,p,true,now);}
    static final class Driver implements LoadedGroundReturnController.Driver {
        final List<LoadedGroundReturnController.SelectedSegment> starts=new ArrayList<>();
        LoadedGroundReturnController.SelectedSegment selected;Vec3 position=new Vec3(0,70,0);boolean rejectFirst,arrived,cleanupPositive,cleanupProvenance;
        public boolean ready(Vec3 goal){return true;}
        public List<Vec3> loadedProgressCandidates(Vec3 from,Vec3 target){return List.of(new Vec3(from.x()+16,70,0),new Vec3(from.x()+15,70,0));}
        public boolean start(Vec3 g,int n,int r,double radius){fail("real selected callback must carry its private token");return false;}
        public boolean startSelected(LoadedGroundReturnController.SelectedSegment actual,int nodes,int radius,double terminalRadius){
            selected=actual;starts.add(actual);assertTrue(actual.selectingOriginalStart());assertEquals(2500,nodes);assertEquals(32,radius);
            assertEquals(terminalRadius,actual.terminalRadius());assertEquals(45000,actual.originalDeadline());
            return !rejectFirst||starts.size()>1;
        }
        public LoadedGroundReturnController.Progress tick(){return arrived?LoadedGroundReturnController.Progress.ARRIVED:LoadedGroundReturnController.Progress.ACTIVE;}
        public void stop(){if(selected!=null){cleanupPositive=selected.selectionCurrent();cleanupProvenance=selected.cleanupProvenanceCurrent();}}
    }
    @Test void realRankedSelectionRetainsCanonicalGoalAndSupportedHopBounds(){var d=new Driver();var c=new LoadedGroundReturnController(d);var canonical=new Vec3(90,70,0);
        assertTrue(c.start(canonical,.35,observe(d.position,0)));var token=d.selected;assertSame(c,token.controllerIdentity());assertSame(canonical,token.canonicalTarget());
        assertEquals(new Vec3(16,70,0),token.segmentPosition());assertFalse(token.canonicalFinalSegment());assertTrue(token.normallyReturnedStart());assertEquals(0,token.rankedAttempt());}
    @Test void failedFirstCandidateCannotBeReplayedIntoSecondActualAttempt(){var d=new Driver();d.rejectFirst=true;var c=new LoadedGroundReturnController(d);
        assertTrue(c.start(new Vec3(90,70,0),.35,observe(d.position,0)));assertEquals(2,d.starts.size());assertFalse(d.starts.getFirst().selectionCurrent());
        assertEquals(1,d.selected.rankedAttempt());assertEquals(new Vec3(15,70,0),d.selected.segmentPosition());}
    @Test void deadlineAndContextRevocationRetirePositiveBeforeRealCleanup(){var d=new Driver();var c=new LoadedGroundReturnController(d);assertTrue(c.start(new Vec3(90,70,0),.35,observe(d.position,0)));
        var token=d.selected;assertEquals(LoadedGroundReturnController.State.FAILED,c.tick(observe(d.position,45000)));assertFalse(token.selectionCurrent());assertFalse(d.cleanupPositive);assertTrue(d.cleanupProvenance);assertEquals(1,d.starts.size());}
    @Test void genuineHopArrivalRetiresOldSelectionBeforeNextLoadedCandidate(){var d=new Driver();var c=new LoadedGroundReturnController(d);assertTrue(c.start(new Vec3(90,70,0),.35,observe(d.position,0)));
        var first=d.selected;d.position=first.segmentPosition();d.arrived=true;c.tick(observe(d.position,1));assertFalse(first.selectionCurrent());assertFalse(first.cleanupProvenanceCurrent());assertNotSame(first,d.selected);assertTrue(d.selected.normallyReturnedStart());}
    @Test void explicitStopKeepsOnlyCleanupProvenanceDuringCallbackThenRevokesIt(){var d=new Driver();var c=new LoadedGroundReturnController(d);assertTrue(c.start(new Vec3(90,70,0),.35,observe(d.position,0)));
        var token=d.selected;c.stop();assertFalse(d.cleanupPositive);assertTrue(d.cleanupProvenance);assertFalse(token.cleanupProvenanceCurrent());assertFalse(token.selectionCurrent());}
    @Test void nearCanonicalSelectionPreservesExactFinalTarget(){var d=new Driver();var c=new LoadedGroundReturnController(d);var target=new Vec3(10,70,0);assertTrue(c.start(target,.25,observe(d.position,0)));
        assertSame(target,d.selected.segmentPosition());assertTrue(d.selected.canonicalFinalSegment());assertEquals(.25,d.selected.terminalRadius());}
}
