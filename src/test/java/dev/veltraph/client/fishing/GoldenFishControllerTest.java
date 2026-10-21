package dev.veltraph.client.fishing;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class GoldenFishControllerTest {
    private static final String SURFACE="You spot a Golden Fish surface from beneath the lava!";
    private static GoldenFishController.Input in(boolean bobber,boolean near,long now){return new GoldenFishController.Input(true,true,bobber,false,near,false,true,now);}
    @Test void initialReelWaitsForRemovalThenCastsAndWeakRequiresContact() {
        var c=new GoldenFishController();c.message(SURFACE,0);
        assertEquals(FishingAction.REEL,c.tick(in(true,false,300)));
        assertEquals(FishingAction.NONE,c.tick(in(true,false,900)));
        c.tick(in(false,false,1000));assertEquals(FishingAction.CAST,c.tick(in(false,false,1050)));
        c.tick(in(true,false,1100));c.message("The Golden Fish is weak!",1200);
        assertEquals(FishingAction.NONE,c.tick(in(true,false,1300)));
        for(int i=0;i<5;i++)assertEquals(FishingAction.NONE,c.tick(in(true,true,1400+i*50)));
        assertEquals(FishingAction.REEL,c.tick(in(true,true,1650)));
        assertEquals(FishingAction.NONE,c.tick(in(true,true,2000)));
        c.tick(in(false,false,2050));assertEquals(GoldenFishController.State.COMPLETE,c.state());
    }
    @Test void closeContactWithoutWeakNeverFinalReels() {
        var c=new GoldenFishController();c.message(SURFACE,0);
        for(int i=0;i<30;i++)assertEquals(FishingAction.NONE,c.tick(in(true,true,300+i*50)));
        c.message("Player: The Golden Fish is weak!",2000);
        assertEquals(FishingAction.NONE,c.tick(in(true,true,2100)));
        c.message("§6The Golden Fish is weak!",2200);
        assertEquals(FishingAction.REEL,c.tick(in(true,true,2300)));
    }
    @Test void missedVisibilityAndAbsentEntityReturnToFishing() {
        var c=new GoldenFishController();c.message(SURFACE,0);c.tick(in(true,true,300));
        for(int i=0;i<11;i++)c.tick(new GoldenFishController.Input(true,false,true,false,false,false,true,400+i*50));
        assertEquals(GoldenFishController.State.COMPLETE,c.state());
        c.message(SURFACE,2000);c.tick(in(true,true,2300));c.tick(new GoldenFishController.Input(false,false,true,false,false,false,true,2400));
        assertEquals(GoldenFishController.State.COMPLETE,c.state());
    }
    @Test void requestTimeoutStopsInsteadOfRepeatingAndSearchExpires() {
        var c=new GoldenFishController();c.message(SURFACE,0);c.tick(in(true,false,300));c.tick(in(true,false,4400));
        assertEquals(GoldenFishController.State.FAILED,c.state());assertFalse(c.failure().isEmpty());
        assertEquals(FishingAction.NONE,c.tick(in(true,false,4500)));
        c.reset();c.message("The Golden Fish is weak!",0);assertFalse(c.active());
        c.message(SURFACE,0);c.tick(new GoldenFishController.Input(false,false,false,false,false,false,false,15001));
        assertEquals(GoldenFishController.State.COMPLETE,c.state());
    }
    @Test void unalignedFishCannotCauseRodUse() {
        var c=new GoldenFishController();c.message(SURFACE,0);
        assertEquals(FishingAction.NONE,c.tick(new GoldenFishController.Input(true,true,false,false,false,false,false,300)));
        assertEquals(FishingAction.CAST,c.tick(in(false,false,400)));
    }
}
