package dev.veltraph.client.combat.grind;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ZealotLootTrackerTest {
    @Test void newWorldFlushesStaleReceiptAndDropIdentityWithoutResettingCollectedTotal(){var l=new ZealotLootTracker();l.begin(0);assertTrue(l.observe(1));l.onChat("A special Zealot has spawned nearby!",100);l.abandon(42,100);l.begin(1);l.clearNativeContext();assertFalse(l.special(101));assertFalse(l.observe(2));assertTrue(l.eligible(42,101));assertEquals(1,l.confirmedEyes());}
    @Test void spawnNoticeHasBoundedPriority(){var l=new ZealotLootTracker();l.onChat("§aA special Zealot has spawned nearby!",1000);assertTrue(l.special(1500));assertFalse(l.special(16000));}
    @Test void inventoryIncreaseIsRequiredForAcknowledgment(){var l=new ZealotLootTracker();l.begin(2);assertFalse(l.observe(2));assertFalse(l.observe(1));assertEquals(0,l.confirmedEyes());assertTrue(l.observe(3));assertEquals(1,l.confirmedEyes());assertFalse(l.observe(4));}
    @Test void abandonedAttemptDoesNotClaimPickup(){var l=new ZealotLootTracker();l.begin(0);l.abandon();assertFalse(l.observe(1));assertEquals(0,l.confirmedEyes());}
    @Test void timedOutEntityCannotImmediatelyRestartPickup(){var l=new ZealotLootTracker();l.begin(0);l.abandon(42,1000);assertFalse(l.eligible(42,2000));assertTrue(l.eligible(43,2000));assertTrue(l.eligible(42,61000));}
    @Test void pickupMatchesExactSanitizedName(){assertTrue(ZealotLootTracker.eye("§dSummoning Eye"));assertFalse(ZealotLootTracker.eye("Summoning Eye Fragment"));}
}
