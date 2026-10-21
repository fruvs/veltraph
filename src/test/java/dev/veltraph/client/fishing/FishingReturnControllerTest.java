package dev.veltraph.client.fishing;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FishingReturnControllerTest {
    static final class Actions implements FishingReturnController.Actions {
        boolean path=true,aim;
        int starts,releases,walk;
        @Override public boolean startWalking(){starts++;return path;}
        @Override public int walk(){return walk;}
        @Override public boolean restoreAim(){return aim;}
        @Override public void release(){releases++;}
    }
    @Test void returnWaitsForArrivalAndSavedAimBeforeReleasing() {
        var c=new FishingReturnController();var a=new Actions();
        assertFalse(c.tick(false,0,a));assertEquals(0,a.starts);
        assertTrue(c.tick(true,0,a));assertEquals(1,a.starts);
        assertTrue(c.tick(false,100,a));assertEquals(FishingReturnController.State.WALKING,c.state());
        a.walk=1;c.tick(false,200,a);c.tick(false,300,a);
        assertEquals(FishingReturnController.State.AIMING,c.state());assertEquals(0,a.releases);
        a.aim=true;assertTrue(c.tick(false,400,a));assertEquals(1,a.releases);
        assertFalse(c.tick(false,450,a));assertEquals(FishingReturnController.State.IDLE,c.state());
    }
    @Test void failedPathAndOverallTimeoutReleaseOnlyOwnedInput() {
        var c=new FishingReturnController();var a=new Actions();a.path=false;
        c.tick(true,0,a);assertTrue(c.failure().contains("Cannot return"));assertEquals(1,a.releases);
        c.stop(a);assertEquals(1,a.releases);
        a.path=true;c.tick(true,0,a);c.tick(false,15_001,a);
        assertTrue(c.failure().contains("timed out"));assertEquals(2,a.releases);
    }
    @Test void blockedRouteAndLowAirCancellationCannotLeakMovement() {
        var c=new FishingReturnController();var a=new Actions();c.stop(a);assertEquals(0,a.releases);
        c.tick(true,0,a);a.walk=-1;c.tick(false,50,a);
        assertTrue(c.failure().contains("blocked"));assertEquals(1,a.releases);
        c.stop(a);a.walk=0;c.tick(true,100,a);c.stop(a);
        assertEquals(2,a.releases);assertFalse(c.active());assertEquals("",c.failure());
    }
}
