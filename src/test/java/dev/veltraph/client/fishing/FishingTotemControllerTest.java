package dev.veltraph.client.fishing;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class FishingTotemControllerTest {
    private FishingTotemController.Input in(boolean start,boolean hook,boolean prepared,boolean aligned,boolean ack,boolean restored,long now){return new FishingTotemController.Input(start,hook,prepared,aligned,ack,restored,now);}
    @Test void delaysRemovalPlacementAndRestoreAcknowledgments(){
        var c=new FishingTotemController(100,4000);
        assertEquals(FishingTotemController.Action.REEL,c.tick(in(true,true,false,false,false,false,0)));
        assertEquals(FishingTotemController.Action.NONE,c.tick(in(false,true,false,false,false,false,300)));
        c.tick(in(false,false,false,false,false,false,400));
        assertEquals(FishingTotemController.Action.PREPARE,c.tick(in(false,false,false,false,false,false,450)));
        c.tick(in(false,false,true,true,false,false,500));
        assertEquals(FishingTotemController.Action.NONE,c.tick(in(false,false,true,true,false,false,550)));
        assertEquals(FishingTotemController.Action.USE,c.tick(in(false,false,true,true,false,false,600)));
        for(int i=0;i<10;i++)assertEquals(FishingTotemController.Action.NONE,c.tick(in(false,false,true,true,false,false,700+i*50)));
        c.tick(in(false,false,true,true,true,false,1300));
        assertEquals(FishingTotemController.Action.RESTORE,c.tick(in(false,false,true,true,true,false,1350)));
        c.tick(in(false,false,true,true,true,true,1400));assertEquals(FishingTotemController.State.COMPLETE,c.state());
    }
    @Test void failedReelNeverPlaces(){
        var c=new FishingTotemController(100,4000);c.tick(in(true,true,false,false,false,false,0));
        assertEquals(FishingTotemController.Action.NONE,c.tick(in(false,true,false,false,false,false,4001)));
        assertEquals(FishingTotemController.State.FAILED,c.state());assertTrue(c.failure().contains("initial reel"));
    }
    @Test void alignmentLossResetsBufferAndAbsentAckDoesNotRetry(){
        var c=new FishingTotemController(100,4000);c.tick(in(true,false,false,false,false,false,0));c.tick(in(false,false,false,false,false,false,50));
        c.tick(in(false,false,true,true,false,false,100));c.tick(in(false,false,true,false,false,false,200));
        assertEquals(FishingTotemController.State.AIM,c.state());c.tick(in(false,false,true,true,false,false,300));
        assertEquals(FishingTotemController.Action.USE,c.tick(in(false,false,true,true,false,false,400)));
        assertEquals(FishingTotemController.Action.NONE,c.tick(in(false,false,true,true,false,false,4401)));assertEquals(FishingTotemController.State.FAILED,c.state());
    }
}
