package dev.veltraph.client.fishing;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class FishingWormAdmissionPriorityTest {
    static final class Actions extends FishingWormControllerTest.Actions {
        boolean admitted;@Override public boolean killingAdmitted(){return admitted;}
    }
    @Test void aNearbyLegacyTargetDoesNotBypassSourceAdmission(){
        var c=new FishingWormController();var a=new Actions();a.hook=false;
        assertFalse(c.tick(true,true,0,4000,a));assertEquals(0,a.kills);assertEquals(0,a.reels);
        a.admitted=true;assertTrue(c.tick(true,true,1,4000,a));assertEquals(1,a.kills);
    }
    @Test void lowAirSurfacesAndReturnsWithoutUnadmittedWeaponUse(){
        var c=new FishingWormController();var a=new Actions();a.hook=false;a.air=1;
        c.tick(true,true,0,4000,a);assertEquals(FishingWormController.State.SURFACING,c.state());assertEquals(1,a.ascents);
        a.air=10;a.submerged=false;c.tick(true,false,1,4000,a);c.tick(true,false,2,4000,a);
        a.at=true;c.tick(true,false,3,4000,a);a.aim=true;c.tick(true,false,4,4000,a);
        assertEquals(FishingWormController.State.COMPLETE,c.state());assertEquals(0,a.kills);assertEquals(0,a.reels);
    }
}
