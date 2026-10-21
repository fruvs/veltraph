package dev.veltraph.client.navigation;
import dev.veltraph.client.combat.slayer.VampireFinisher;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static dev.veltraph.client.combat.slayer.VampireFinisher.Action.*;
class VampireFinisherTest {
    private final List<String> items=List.of("Sword","Steak Stake");
    @Test void requiresVampireReadinessMarkerAndReach() {
        assertTrue(VampireFinisher.ready("§c҉ Riftstalker Bloodfiend 600❤"));
        assertFalse(VampireFinisher.ready("Riftstalker Bloodfiend 1❤"));
        assertFalse(VampireFinisher.ready("҉ Revenant Horror"));
        var c=new VampireFinisher();assertEquals(MELEE,c.tick(1,false,items,true,0).action());
        assertEquals(WAIT,c.tick(1,true,items,false,0).action());
        assertEquals(ATTACK,c.tick(1,true,items,true,50).action());
    }
    @Test void waitsForOutcomeAndBoundsUnresolvedAttempts() {
        var c=new VampireFinisher();assertEquals(ATTACK,c.tick(1,true,items,true,0).action());
        assertEquals(WAIT,c.tick(1,true,items,true,500).action());
        assertEquals(ATTACK,c.tick(1,true,items,true,1000).action());
        assertEquals(ATTACK,c.tick(1,true,items,true,2000).action());
        assertEquals(MELEE,c.tick(1,true,items,true,3000).action());
        assertEquals(ATTACK,c.tick(2,true,items,true,3050).action());
    }
    @Test void missingItemDoesNotFreezeCombatAndUpgradedItemIsRecognized() {
        var c=new VampireFinisher();assertEquals(MELEE,c.tick(1,true,List.of("Steak"),true,0).action());
        assertEquals(1,VampireFinisher.findSlot(List.of("Sword","Super Sharp 'n Stabby Steak Stake")));
        c.tick(1,true,items,true,0);c.tick(1,false,items,true,10);
        assertEquals(WAIT,c.tick(1,true,items,true,20).action());
    }
}
