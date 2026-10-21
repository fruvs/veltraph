package dev.veltraph.client.navigation;
import dev.veltraph.client.combat.slayer.DaggerAttunement;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static dev.veltraph.client.combat.slayer.DaggerAttunement.Action.*;
class DaggerAttunementTest {
    private DaggerAttunement.Item item(String aspect) {return new DaggerAttunement.Item(3,"Firedust Dagger",List.of("§7Attuned: §8"+aspect));}
    @Test void switchOnceWaitForAcknowledgmentThenAttack() {
        var c=new DaggerAttunement();
        assertEquals(SWITCH,c.tick("ASHEN",List.of(item("Auric")),0).action());
        assertEquals(WAIT,c.tick("ASHEN",List.of(item("Auric")),100).action());
        assertEquals(WAIT,c.tick("ASHEN",List.of(item("Auric")),5000).action());
        assertEquals(READY,c.tick("ASHEN",List.of(item("Ashen")),5100).action());
    }
    @Test void changedShieldWaitsForPendingSwitchBeforeSwitchingAgain() {
        var c=new DaggerAttunement();c.tick("ASHEN",List.of(item("Auric")),0);
        assertEquals(WAIT,c.tick("AURIC",List.of(item("Auric")),50).action());
        assertEquals(SWITCH,c.tick("AURIC",List.of(item("Ashen")),100).action());
        assertEquals(READY,c.tick("AURIC",List.of(item("Auric")),150).action());
    }
    @Test void rejectsWrongItemsEmptyLoreAndAmbiguousShield() {
        var c=new DaggerAttunement();
        assertEquals(WAIT,c.tick("ASHEN",List.of(new DaggerAttunement.Item(0,"Paper",List.of("Attuned: Ashen"))),0).action());
        assertEquals(WAIT,c.tick("ASHEN",List.of(item("")),0).action());
        assertEquals(WAIT,c.tick("ASHEN",List.of(item("Ashen bonus")),0).action());
        assertEquals(WAIT,c.tick("ASHEN AURIC",List.of(item("Ashen")),0).action());
        assertEquals(WAIT,c.tick("ASHEN",List.of(new DaggerAttunement.Item(0,"Twilight Dagger",List.of("Attuned: Spirit"))),0).action());
    }
    @Test void newerExactTitleAcknowledgesWithoutRequiringLoreRefresh() {
        var c=new DaggerAttunement();var stale=new DaggerAttunement.Receipt(4,DaggerAttunement.Aspect.ASHEN);
        assertEquals(SWITCH,c.tick("ASHEN",List.of(item("Auric")),0,stale).action());
        assertEquals(WAIT,c.tick("ASHEN",List.of(item("Auric")),50,stale).action());
        var fresh=new DaggerAttunement.Receipt(5,DaggerAttunement.Aspect.ASHEN);
        assertEquals(READY,c.tick("ASHEN",List.of(item("Auric")),100,fresh).action());
        assertEquals(READY,c.tick("ASHEN",List.of(item("Auric")),150,fresh).action());
        assertEquals(SWITCH,c.tick("AURIC",List.of(item("Auric")),200,fresh).action());
        assertEquals(WAIT,c.tick("AURIC",List.of(item("Auric")),250,fresh).action());
        assertEquals(READY,c.tick("AURIC",List.of(item("Auric")),300,new DaggerAttunement.Receipt(6,DaggerAttunement.Aspect.AURIC)).action());
    }
    @Test void titleCacheDoesNotSurviveResetOrReplacement() {
        var c=new DaggerAttunement();c.tick("ASHEN",List.of(item("Auric")),0);
        c.tick("ASHEN",List.of(item("Auric")),50,new DaggerAttunement.Receipt(1,DaggerAttunement.Aspect.ASHEN));
        assertEquals(SWITCH,c.tick("ASHEN",List.of(new DaggerAttunement.Item(3,"Kindlebane Dagger",List.of("Attuned: Auric"))),75,new DaggerAttunement.Receipt(1,DaggerAttunement.Aspect.ASHEN)).action());
        c.reset();assertEquals(SWITCH,c.tick("ASHEN",List.of(item("Auric")),100,new DaggerAttunement.Receipt(1,DaggerAttunement.Aspect.ASHEN)).action());
    }
    @Test void prefersAlreadyMatchingDaggerAndDoesNotToggleIt() {
        var c=new DaggerAttunement();
        assertEquals(READY,c.tick("ASHEN 20M",List.of(item("Auric"),new DaggerAttunement.Item(4,"Pyrochaos Dagger",List.of("Attuned: Ashen"))),0).action());
    }
}
