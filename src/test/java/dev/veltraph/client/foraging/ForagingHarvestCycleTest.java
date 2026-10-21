package dev.veltraph.client.foraging;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ForagingHarvestCycleTest {
 @Test void delayedConnectedLogRemovalsBelongToOneObservedAbilityOutcome() {
  var c=new ForagingHarvestCycle();c.activate(false,0,1000);
  c.observe(1,true,100,1000);assertTrue(c.waiting(500));
  c.observe(4,true,700,1000);assertTrue(c.waiting(1500));assertFalse(c.waiting(1700));
  assertEquals(4,c.largestBatch());assertEquals(1,c.multiLogBatches());assertEquals(1,c.activations());
 }
 @Test void serverSweepOutcomeIsRecognizedEvenWithoutKnownStat() {
  var c=new ForagingHarvestCycle();c.activate(false,0,1000);c.observe(7,false,100,1000);
  assertTrue(c.waiting(500));assertEquals(7,c.largestBatch());assertEquals(1,c.multiLogBatches());
 }
 @Test void ordinaryMiningDoesNotWaitForAnInventedAbilityCooldown() {
  var c=new ForagingHarvestCycle();c.activate(false,0,1000);c.observe(1,false,100,1000);
  assertFalse(c.waiting(101));c.activate(false,101,1000);assertEquals(2,c.activations());
 }
 @Test void missingThrowOutcomeCanRetryAfterBoundedWindowAndRollbackReopensWait() {
  var c=new ForagingHarvestCycle();c.activate(true,0,1000);assertTrue(c.waiting(999));assertFalse(c.waiting(1000));assertFalse(c.active());
  c.activate(true,1000,1000);c.observe(4,true,1100,1000);c.observe(2,true,1200,1000);
  assertTrue(c.waiting(1500));assertEquals(4,c.largestBatch());
  c.observe(3,true,1600,1000);assertEquals(4,c.largestBatch());
 }
}
