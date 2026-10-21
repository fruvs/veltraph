package dev.veltraph.client.mining.vein;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class VeinCompletionFenceTest {
 @Test void airFirstWaitsForInventory(){var f=new VeinCompletionFence(1000,2000);f.air(1100);assertEquals(VeinCompletionFence.Result.WAIT,f.result(1150,true));f.inventory(1200,true);assertTrue(f.consume(1250,true));}
 @Test void inventoryFirstWaitsForAir(){var f=new VeinCompletionFence(1000,2000);f.inventory(1100,true);assertEquals(VeinCompletionFence.Result.WAIT,f.result(1150,true));f.air(1200);assertTrue(f.consume(1250,true));}
 @Test void unrelatedInventoryRejects(){var f=new VeinCompletionFence(1000,2000);f.air(1100);f.inventory(1200,false);assertEquals(VeinCompletionFence.Result.REJECTED,f.result(1250,true));}
 @Test void lateInventoryCannotCredit(){var f=new VeinCompletionFence(1000,2000);f.air(1100);f.inventory(2000,true);assertFalse(f.consume(2000,true));}
 @Test void expiredAirCannotCredit(){var f=new VeinCompletionFence(1000,2000);f.inventory(1100,true);f.air(2001);assertFalse(f.consume(2001,true));}
 @Test void foreignRootAfterAirBeforeInventoryRejects(){var f=new VeinCompletionFence(1000,2000);f.air(1100);f.inventory(1200,true);assertFalse(f.consume(1250,false));}
 @Test void completionCannotReplay(){var f=new VeinCompletionFence(1000,2000);f.air(1100);f.inventory(1200,true);assertTrue(f.consume(1250,true));assertFalse(f.consume(1250,true));}
 @Test void unrelatedUnchangedInventoryBeforeAirDoesNotQualify(){var f=new VeinCompletionFence(1000,2000);assertFalse(f.unchangedInventory(1100,false,false));f.air(1200);assertEquals(VeinCompletionFence.Result.WAIT,f.result(1250,true));}
 @Test void toolSnapshotBeforeAirDoesNotQualifyPositiveWear(){var f=new VeinCompletionFence(1000,2000);assertFalse(f.unchangedInventory(1100,true,false));f.air(1200);assertEquals(VeinCompletionFence.Result.WAIT,f.result(1250,true));f.inventory(1300,true);assertTrue(f.consume(1350,true));}
 @Test void knownZeroToolCoveredSnapshotCanPrecedeAir(){var f=new VeinCompletionFence(1000,2000);assertTrue(f.unchangedInventory(1100,true,true));f.air(1200);assertTrue(f.consume(1250,true));}
 @Test void unchangedToolSnapshotStrictlyAfterAirIsActualObservation(){var f=new VeinCompletionFence(1000,2000);f.air(1100);assertFalse(f.unchangedInventory(1100,true,false));assertTrue(f.unchangedInventory(1200,true,false));assertTrue(f.consume(1250,true));}
 @Test void unrelatedSlotAfterAirDoesNotQualify(){var f=new VeinCompletionFence(1000,2000);f.air(1100);assertFalse(f.unchangedInventory(1200,false,false));assertEquals(VeinCompletionFence.Result.WAIT,f.result(1250,true));}
 @Test void oldInventoryDoesNotQualifyZeroWear(){var f=new VeinCompletionFence(1000,2000);f.inventory(999,true);f.air(1100);assertFalse(f.consume(1250,true));}
}
