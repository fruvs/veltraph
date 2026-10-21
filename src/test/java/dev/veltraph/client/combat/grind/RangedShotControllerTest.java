package dev.veltraph.client.combat.grind;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class RangedShotControllerTest {
    @Test void shortbowClicksRespectCadenceAndProtection(){var c=new RangedShotController();assertEquals(RangedShotController.Action.USE,c.tick(1,true,true,1000,350));assertEquals(RangedShotController.Action.NONE,c.tick(1,true,true,1100,350));assertEquals(RangedShotController.Action.NONE,c.tick(1,false,true,1350,350));assertEquals(RangedShotController.Action.USE,c.tick(1,true,true,1351,350));}
    @Test void vanillaDrawReleasesOnlyAfterCharge(){var c=new RangedShotController();assertEquals(RangedShotController.Action.USE,c.tick(1,true,false,1000,350));assertEquals(RangedShotController.Action.NONE,c.tick(1,true,false,2050,350));assertEquals(RangedShotController.Action.RELEASE,c.tick(1,true,false,2100,350));assertFalse(c.drawing());assertEquals(RangedShotController.Action.NONE,c.tick(1,true,false,2200,350));}
    @Test void targetChangeAndObstructionCancelDraw(){var c=new RangedShotController();c.tick(1,true,false,1000,350);assertEquals(RangedShotController.Action.CANCEL,c.tick(2,true,false,1500,350));c.tick(2,true,false,1900,350);assertEquals(RangedShotController.Action.CANCEL,c.tick(2,false,false,2200,350));}
}
