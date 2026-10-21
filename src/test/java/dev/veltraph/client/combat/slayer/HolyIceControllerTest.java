package dev.veltraph.client.combat.slayer;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HolyIceControllerTest {
    private static final HolyIceController.Item ICE=new HolyIceController.Item(3,"§bHoly Ice",List.of(
            "Ability: Chilling","Reduces incoming damage by 80% for 1 second.","Cooldown: 5s"),true);
    private static TwinclawsTiming.Hazard hazard(long serial,long remaining) {return new TwinclawsTiming.Hazard(true,serial,remaining,true,"Twinclaws");}
    @Test void waitsForLoreDurationWindowAndRequestsOncePerCast() {
        var c=new HolyIceController();assertFalse(c.tick(2,hazard(1,1200),List.of(ICE),0).use());
        var use=c.tick(2,hazard(1,500),List.of(ICE),700);assertTrue(use.use());assertEquals(3,use.slot());
        assertFalse(c.tick(2,hazard(1,0),List.of(ICE),1200).use());
        assertFalse(c.tick(2,hazard(2,100),List.of(ICE),3000).use());assertTrue(c.tick(2,hazard(2,100),List.of(ICE),5700).use());
    }
    @Test void newBossDoesNotEraseSharedItemCooldownAndMissingOrAmbiguousLoreCannotAct() {
        var c=new HolyIceController();assertTrue(c.tick(2,hazard(1,100),List.of(ICE),0).use());
        assertFalse(c.tick(3,hazard(1,100),List.of(ICE),500).use());assertTrue(c.tick(3,hazard(1,100),List.of(ICE),5000).use());
        var wrong=new HolyIceController.Item(4,"Holy Ice",List.of("Ability: Chilling","Reduces damage for 1 second."),true);
        c.reset();assertFalse(c.tick(2,hazard(1,100),List.of(wrong),6000).use());
        assertFalse(c.tick(2,hazard(1,100),List.of(ICE,ICE),6000).use());
        assertFalse(c.tick(2,new TwinclawsTiming.Hazard(true,1,0,false,"quiet"),List.of(ICE),6000).use());
    }

    @Test void renamedOrdinaryItemsAndNonIceItemsCannotInvokeAbility() {
        var c=new HolyIceController();
        var decoy=new HolyIceController.Item(1,"Holy Ice",List.of(),true);
        var wrongType=new HolyIceController.Item(2,ICE.name(),ICE.lore(),false);
        assertFalse(c.tick(2,hazard(1,100),List.of(decoy,wrongType),0).use());
    }

    @Test void stalledServerCounterCannotAuthorizeAnEarlyProtectionRequest() {
        var c=new HolyIceController();var timing=new TwinclawsTiming();
        var label=List.of(new TwinclawsTiming.Signal(4,"TWINCLAWS 1.5s"));
        timing.observe(2,label,0);
        assertFalse(c.tick(2,timing.observe(2,label,1200),List.of(ICE),1200).use());
        assertTrue(c.tick(2,timing.observe(2,List.of(new TwinclawsTiming.Signal(4,"TWINCLAWS 0.4s")),1250),List.of(ICE),1250).use());
    }
}
