package dev.veltraph.client.navigation;

import dev.veltraph.client.world.Vec3;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Production controller failure lifetime only; does not fabricate native cleanup/slot permission. */
final class LoadedGroundFailedCleanupCustodyTest {
    private static final Object P=new Object(),W=new Object(),C=new Object();
    private static LoadedGroundReturnController.Observation observe(long now){return new LoadedGroundReturnController.Observation(P,W,C,new Vec3(0,70,0),true,now);}
    private static final class Driver implements LoadedGroundReturnController.Driver {
        LoadedGroundReturnController controller;
        LoadedGroundReturnController.SelectedSegment selected;
        Throwable failure;boolean arrived,reenter,swallowReentry;int starts,ticks,stops;
        public boolean ready(Vec3 goal){return true;}
        public List<Vec3> loadedProgressCandidates(Vec3 from,Vec3 goal){return List.of(new Vec3(16,70,0));}
        public boolean start(Vec3 goal,int nodes,int radius,double terminal){fail("genuine selected callback required");return false;}
        public boolean startSelected(LoadedGroundReturnController.SelectedSegment actual,int nodes,int radius,double terminal){selected=actual;starts++;return true;}
        public LoadedGroundReturnController.Progress tick(){ticks++;return arrived?LoadedGroundReturnController.Progress.ARRIVED:LoadedGroundReturnController.Progress.ACTIVE;}
        public void stop(){
            if(selected==null)return;stops++;
            if(reenter){try{controller.stop();}catch(IllegalStateException original){if(!swallowReentry)throw original;}}
            if(failure instanceof RuntimeException original)throw original;
            if(failure instanceof Error original)throw original;
        }
    }
    private static Driver start(){var d=new Driver();d.controller=new LoadedGroundReturnController(d);assertTrue(d.controller.start(new Vec3(90,70,0),.35,observe(0)));return d;}
    @Test void originalCleanupFailureRetainsRealTokenAndBlocksTickAndNewStart(){
        for(Throwable original:List.of(new IllegalStateException("native cleanup"),new AssertionError("native cleanup"))){
            var d=start();var token=d.selected;d.failure=original;
            assertSame(original,assertThrows(original.getClass(),()->d.controller.stop()));
            assertEquals(LoadedGroundReturnController.State.FAILED,d.controller.state());
            assertFalse(token.selectionCurrent());assertTrue(token.cleanupProvenanceCurrent());assertEquals(45000,token.originalDeadline());
            assertEquals(LoadedGroundReturnController.State.FAILED,d.controller.tick(observe(1)));assertEquals(0,d.ticks);
            assertSame(original,assertThrows(original.getClass(),()->d.controller.start(new Vec3(10,70,0),.35,observe(2))));
            assertEquals(1,d.starts);assertSame(token,d.selected);assertTrue(token.cleanupProvenanceCurrent());
        }
    }
    @Test void callbackCannotHideReentrantStopAndMakeOuterCleanupAppearSuccessful(){
        var d=start();var token=d.selected;d.reenter=true;d.swallowReentry=true;
        assertThrows(IllegalStateException.class,()->d.controller.stop());
        assertEquals(1,d.stops);assertEquals(LoadedGroundReturnController.State.FAILED,d.controller.state());
        assertFalse(token.selectionCurrent());assertTrue(token.cleanupProvenanceCurrent());assertEquals(1,d.starts);
    }
    @Test void OnlySameActualDriverNormalReturnRetiresRetainedControllerMetadata(){
        var d=start();var token=d.selected;d.failure=new IllegalStateException("cleanup");
        assertThrows(IllegalStateException.class,()->d.controller.stop());assertTrue(token.cleanupProvenanceCurrent());
        // This pure interface return is not a native receipt. The actual Shared adapter still enforces nativeAttempted.
        d.failure=null;d.controller.stop();assertEquals(LoadedGroundReturnController.State.IDLE,d.controller.state());
        assertFalse(token.cleanupProvenanceCurrent());assertFalse(token.selectionCurrent());assertEquals(1,d.starts);
    }
    @Test void arrivalCleanupSwallowedReentryRetainsOriginalSelection(){
        var d=start();var token=d.selected;d.reenter=true;d.swallowReentry=true;d.arrived=true;
        var reached=new LoadedGroundReturnController.Observation(P,W,C,token.segmentPosition(),true,1);
        assertThrows(IllegalStateException.class,()->d.controller.tick(reached));
        assertEquals(1,d.stops);assertEquals(LoadedGroundReturnController.State.FAILED,d.controller.state());
        assertFalse(token.selectionCurrent());assertTrue(token.cleanupProvenanceCurrent());assertEquals(1,d.starts);
    }
    @Test void invalidContextCleanupSwallowedReentryRetainsOriginalSelection(){
        var d=start();var token=d.selected;d.reenter=true;d.swallowReentry=true;
        var invalid=new LoadedGroundReturnController.Observation(new Object(),W,C,new Vec3(0,70,0),true,1);
        assertThrows(IllegalStateException.class,()->d.controller.tick(invalid));
        assertEquals(1,d.stops);assertEquals(LoadedGroundReturnController.State.FAILED,d.controller.state());
        assertFalse(token.selectionCurrent());assertTrue(token.cleanupProvenanceCurrent());assertEquals(1,d.starts);
    }
}
