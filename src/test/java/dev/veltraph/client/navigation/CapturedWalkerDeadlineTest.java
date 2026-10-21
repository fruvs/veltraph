package dev.veltraph.client.navigation;
import dev.veltraph.client.world.BlockPos;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class CapturedWalkerDeadlineTest {
    @Test void passiveAirFreezePreservesExactOriginalFourSecondDeadline(){
        var walker=new WalkController(7);var path=List.of(new BlockPos(0,1,0),new BlockPos(0,2,1));walker.start(path,100);
        assertTrue(walker.locomotionProgressCurrent(4100,4000));assertFalse(walker.locomotionProgressCurrent(4101,4000));
        assertEquals(path,walker.admittedPath());assertEquals(WalkController.State.WALKING,walker.state());
    }
    @Test void timeoutFloorAndClockRollbackDoNotBecomePathProgress(){
        var walker=new WalkController(7);walker.start(List.of(new BlockPos(0,1,0)),100);
        assertTrue(walker.locomotionProgressCurrent(600,10));assertFalse(walker.locomotionProgressCurrent(601,10));
        assertFalse(walker.locomotionProgressCurrent(99,4000));assertEquals(WalkController.State.WALKING,walker.state());
    }
    @Test void originalClockFloorStillAppliesAfterWalkerHasArrived(){
        var walker=new WalkController(7);walker.start(List.of(),100);
        assertEquals(WalkController.State.ARRIVED,walker.state());
        assertTrue(walker.locomotionClockCurrent(100));assertFalse(walker.locomotionClockCurrent(99));
    }
}
