package dev.veltraph.client.fishing.offense;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static dev.veltraph.client.fishing.offense.FishingOffenseController.Action.*;

/** Native BN entered RESTORE_VIEW inside the bound, then water flow moved it outside before view converged. */
final class FishingReturnDriftTest {
    private FishingOffenseController.Frame f(long now,boolean held,boolean position,boolean view,boolean supported){return new FishingOffenseController.Frame(now,true,true,true,1,1,false,true,held,true,now<350?500:400,now<350?1:2,now<350?0:1,now<350?8:5,82,true,!held,position,view,supported);}
    private FishingOffenseController restoring(){var c=new FishingOffenseController();var p=new FishingOffenseProfile("minecraft:diamond_sword","Private",List.of("Private"),"minecraft:silverfish","Private {target}",100,100L,100L,8000L,1,true);
        assertTrue(c.start(f(0,true,false,false,true),p,100));c.tick(f(1,true,false,false,true),100);c.tick(f(2,true,false,false,true),100);assertEquals(USE,c.tick(f(100,true,false,false,true),100));c.tick(f(350,true,false,false,true),100);assertEquals(RETURN,c.tick(f(400,false,false,false,true),100));assertEquals(RESTORE_VIEW,c.tick(f(450,false,true,false,true),100));return c;}
    @Test void supportedDriftWhileViewConvergesReturnsBeforeJointCompletion(){var c=restoring();assertEquals(RESTORE_VIEW,c.tick(f(500,false,true,false,true),100));assertEquals(RETURN,c.tick(f(550,false,false,false,true),100));assertEquals(FishingOffenseController.State.RETURN,c.state());assertEquals(RETURN,c.tick(f(600,false,false,true,true),100));assertEquals(RESTORE_VIEW,c.tick(f(650,false,true,true,true),100));assertEquals(RELEASE,c.tick(f(700,false,true,true,true),100));assertEquals(FishingOffenseController.State.COMPLETE,c.state());assertEquals(1,c.uses());}
    @Test void driftOnTheExactViewReceiptCannotCompleteOutsideAnchor(){var c=restoring();assertEquals(RETURN,c.tick(f(550,false,false,true,true),100));assertTrue(c.active());assertEquals(1,c.uses());}
    @Test void lostSupportWaitsWithRodAndOriginalDeadlineThenReturns(){var c=restoring();assertEquals(WAIT_FOOTING,c.tick(f(550,false,true,true,false),100));assertEquals(FishingOffenseController.State.WAIT_FOOTING,c.state());assertEquals(WAIT_FOOTING,c.tick(f(8000,false,false,true,false),100));assertEquals(RETURN,c.tick(f(8349,false,false,true,true),100));assertEquals(RELEASE,c.tick(f(8351,false,true,true,true),100));assertEquals(FishingOffenseController.State.FAILED,c.state());assertTrue(c.failure().contains("deadline"));assertEquals(1,c.uses());}
    @Test void repeatedDriftDoesNotExtendReturnExpiryOrRequestAnotherUse(){var c=restoring();long original=c.deadlineMillis();assertEquals(8350,original);assertEquals(RETURN,c.tick(f(550,false,false,false,true),100));assertEquals(RESTORE_VIEW,c.tick(f(700,false,true,false,true),100));assertEquals(original,c.deadlineMillis());assertEquals(RETURN,c.tick(f(800,false,false,true,true),100));assertEquals(original,c.deadlineMillis());assertEquals(RELEASE,c.tick(f(8351,false,true,true,true),100));assertEquals(FishingOffenseController.State.FAILED,c.state());assertEquals(1,c.uses());}
}
