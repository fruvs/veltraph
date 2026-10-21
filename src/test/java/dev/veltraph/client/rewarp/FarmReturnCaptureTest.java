package dev.veltraph.client.rewarp;
import dev.veltraph.client.game.GameArea;
import dev.veltraph.client.world.Vec3;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
class FarmReturnCaptureTest {
    private FarmReturnPoint point(double x){return new FarmReturnPoint("test",GameArea.GARDEN,new Vec3(x,69.9375,.12),73,-12);}
    @Test void pinsLastHealthyExactPoseWithoutUsingLaterWarpPosition(){
        var c=new FarmReturnCapture<Object>();Object w=new Object(),farm=new Object();
        c.record(w,point(.79),List.of(farm));assertTrue(c.pin(w,List.of(farm)));
        c.record(w,point(8.5),List.of(farm));assertEquals(.79,c.point().position().x());
        assertTrue(c.matches(w,List.of(farm)));
    }
    @Test void worldAndFarmIdentityChangesCannotUseOldReturn(){
        var c=new FarmReturnCapture<Object>();Object w=new Object(),farm=new Object();
        c.record(w,point(.79),List.of(farm));assertFalse(c.pin(new Object(),List.of(farm)));
        assertFalse(c.pin(w,List.of(new Object())));assertFalse(c.pin(w,List.of()));assertNull(c.point());
        assertTrue(c.pin(w,List.of(farm)));assertFalse(c.matches(w,List.of()));assertFalse(c.matches(new Object(),List.of(farm)));
    }
    @Test void cancellationDropsPinnedPoseAndFullClearDropsHealthyWorld(){
        var c=new FarmReturnCapture<Object>();Object w=new Object(),farm=new Object();
        c.record(w,point(.79),List.of(farm));assertTrue(c.pin(w,List.of(farm)));
        c.clearPending();assertNull(c.point());assertTrue(c.owners().isEmpty());
        assertTrue(c.pin(w,List.of(farm)));c.clear();assertFalse(c.pin(w,List.of(farm)));
    }
}
