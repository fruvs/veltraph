package dev.veltraph.client.fishing;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FishingWormControllerTest {
    static class Actions implements FishingWormController.Actions {
        boolean target=true,hook=true,submerged=true,surface=true,clear=true,at,aim;
        double air=10;int reels,kills,ascents,refills,returns,releases;
        public boolean targetPresent(){return target;}public boolean hookPresent(){return hook;}
        public boolean submerged(){return submerged;}public double bubbles(){return air;}
        public boolean surfaceReachable(){return surface;}public boolean returnClear(){return clear;}
        public boolean atAnchor(){return at;}public boolean restoreAim(){return aim;}
        public void reel(){reels++;}public void kill(){kills++;}public void ascend(){ascents++;}
        public void refill(){refills++;}public void returnToAnchor(){returns++;}public void release(){releases++;}
    }
    @Test void completesAcknowledgedReelKillFullRefillReturnAndAim() {
        var c=new FishingWormController();var a=new Actions();
        c.tick(true,true,0,4000,a);assertEquals(1,a.reels);assertEquals(0,a.kills);
        c.tick(true,false,100,4000,a);assertEquals(1,a.reels);
        a.hook=false;c.tick(true,false,200,4000,a);c.tick(true,false,250,4000,a);assertEquals(1,a.kills);
        a.target=false;c.tick(true,false,300,4000,a);c.tick(true,false,350,4000,a);assertEquals(1,a.ascents);
        a.submerged=false;a.air=2;c.tick(true,false,400,4000,a);c.tick(true,false,450,4000,a);
        assertEquals(FishingWormController.State.REFILLING,c.state());assertEquals(0,a.returns);
        a.air=10;c.tick(true,false,500,4000,a);c.tick(true,false,550,4000,a);assertEquals(1,a.returns);
        a.at=true;c.tick(true,false,600,4000,a);a.aim=true;c.tick(true,false,650,4000,a);
        assertEquals(FishingWormController.State.COMPLETE,c.state());assertFalse(c.tick(true,false,700,4000,a));
    }
    @Test void lowAirOverridesPendingReelAndDivingWithoutAnotherRodClick() {
        var c=new FishingWormController();var a=new Actions();c.tick(true,true,0,4000,a);
        a.air=1.5;c.tick(true,false,100,4000,a);
        assertEquals(FishingWormController.State.SURFACING,c.state());assertEquals(1,a.ascents);assertEquals(1,a.reels);
        a.hook=false;a.submerged=false;a.air=10;c.tick(true,false,200,4000,a);c.tick(true,false,250,4000,a);
        a.submerged=true;a.air=1;c.tick(true,false,300,4000,a);
        assertEquals(FishingWormController.State.SURFACING,c.state());assertEquals(0,a.returns);assertEquals(0,a.kills);
    }
    @Test void unreadablePathAndReelTimeoutStopRatherThanRepeatingActions() {
        var c=new FishingWormController();var a=new Actions();c.tick(true,true,0,4000,a);c.tick(true,false,4001,4000,a);
        assertTrue(c.failure().contains("acknowledge"));assertEquals(1,a.reels);assertEquals(1,a.releases);
        c.stop(a);a.hook=false;c.tick(true,true,5000,4000,a);a.target=false;c.tick(true,false,5100,4000,a);
        a.submerged=false;c.tick(true,false,5200,4000,a);c.tick(true,false,5300,4000,a);
        a.clear=false;c.tick(true,false,5400,4000,a);assertTrue(c.failure().contains("corridor blocked"));
    }
    @Test void idleOffAndNoTargetsNeverAcquireInputsButSurfaceLossAndDisableReleaseActiveFlow() {
        var c=new FishingWormController();var a=new Actions();assertFalse(c.tick(false,true,0,4000,a));
        a.target=false;assertFalse(c.tick(true,true,10,4000,a));assertEquals(0,a.releases);
        a.air=1;a.surface=false;c.tick(true,true,20,4000,a);assertTrue(c.failure().contains("No clear"));
        c.stop(a);a.surface=true;c.tick(true,true,30,4000,a);c.tick(false,false,40,4000,a);
        assertTrue(c.failure().contains("disabled"));assertEquals(0,a.kills);assertEquals(0,a.reels);
    }
    @Test void targetStillPresentAfterFullRefillReturnsToKillingAndOverallWatchdogBoundsFlow() {
        var c=new FishingWormController();var a=new Actions();a.hook=false;a.air=1;
        c.tick(true,true,0,4000,a);a.submerged=false;a.air=10;c.tick(true,false,100,4000,a);c.tick(true,false,150,4000,a);
        a.at=true;a.submerged=true;c.tick(true,false,200,4000,a);a.aim=true;c.tick(true,false,250,4000,a);
        assertEquals(FishingWormController.State.KILLING,c.state());c.tick(true,false,300,4000,a);assertEquals(1,a.kills);
        c.tick(true,false,60_001,4000,a);assertTrue(c.failure().contains("timed out"));
    }
}
