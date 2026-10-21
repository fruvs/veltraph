package dev.veltraph.client.fishing.worm;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class WormDreadlordControllerTest {
    private WormDreadlordController.Frame frame(long tick,boolean valid,boolean tool,boolean aimed,boolean back){return new WormDreadlordController.Frame(tick,valid,tool,aimed,back);}
    @Test void delayedAimIssuesOneUseThenReturnsOnlyAfterActualViewCompletion(){
        var c=new WormDreadlordController();c.start(0,20,3,100);
        assertEquals(WormDreadlordController.Action.SELECT_TOOL,c.tick(frame(0,true,false,false,false)));
        assertEquals(WormDreadlordController.Action.NONE,c.tick(frame(19,true,true,false,false)));
        assertEquals(WormDreadlordController.Action.AIM_BEHIND,c.tick(frame(20,true,true,false,false)));
        assertEquals(WormDreadlordController.Action.NONE,c.tick(frame(21,true,true,false,false)));
        c.tick(frame(22,true,true,true,false));assertEquals(WormDreadlordController.Action.NONE,c.tick(frame(24,true,true,true,false)));
        assertEquals(WormDreadlordController.Action.USE_ITEM,c.tick(frame(25,true,true,true,false)));
        assertEquals(WormDreadlordController.State.WAIT_FOR_DEADLORD,c.state());
        assertEquals(WormDreadlordController.Action.AIM_BACK,c.tick(frame(26,true,true,true,false)));
        assertEquals(WormDreadlordController.Action.NONE,c.tick(frame(27,true,true,true,false)));
        assertEquals(WormDreadlordController.Action.RETURN_PARENT,c.tick(frame(28,true,true,true,true)));
        assertEquals(WormDreadlordController.Action.NONE,c.tick(frame(29,true,true,true,true)));
    }
    @Test void nativeRevocationAndToolLossReleaseWithoutUse(){
        var c=new WormDreadlordController();c.start(0,2,2,100);c.tick(frame(0,true,false,false,false));
        assertEquals(WormDreadlordController.Action.RELEASE,c.tick(frame(1,true,false,false,false)));
        assertEquals(WormDreadlordController.State.FAILED,c.state());
        var other=new WormDreadlordController();other.start(0,2,2,100);
        assertEquals(WormDreadlordController.Action.RELEASE,other.tick(frame(0,false,true,true,true)));
    }
    @Test void cancellationInvalidatesAllFutureCallbacks(){
        var c=new WormDreadlordController();c.start(0,2,2,100);c.tick(frame(0,true,false,false,false));
        assertEquals(WormDreadlordController.Action.RELEASE,c.cancel());
        assertEquals(WormDreadlordController.Action.NONE,c.tick(frame(50,true,true,true,true)));
        assertEquals(WormDreadlordController.State.CANCELLED,c.state());
    }
    @Test void watchdogAndClockRollbackNeverResumeOrRepeatUse(){
        var c=new WormDreadlordController();c.start(10,2,2,100);
        assertEquals(WormDreadlordController.Action.RELEASE,c.tick(frame(9,true,true,true,true)));
        var other=new WormDreadlordController();other.start(0,2,2,10);
        assertEquals(WormDreadlordController.Action.RELEASE,other.tick(frame(11,true,true,true,true)));
    }
}
