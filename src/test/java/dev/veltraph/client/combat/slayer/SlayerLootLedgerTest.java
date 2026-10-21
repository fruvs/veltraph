package dev.veltraph.client.combat.slayer;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class SlayerLootLedgerTest {
 @Test void disappearanceAndInventoryGainWithoutTrackedReceiptCannotDeliver(){var l=new SlayerLootLedger();var id=UUID.randomUUID();l.track(id,"full components",2,3);assertFalse(l.inventory(id,20));assertFalse(l.complete());assertEquals(0,l.delivered());}
 @Test void wrongEntityOrFullComponentsDoNotAbsorbUnrelatedLoot(){var l=new SlayerLootLedger();var id=UUID.randomUUID();l.track(id,"full components",2,3);l.receipt(UUID.randomUUID(),"full components",2);l.receipt(id,"changed components",2);assertFalse(l.inventory(id,5));assertFalse(l.complete());}
 @Test void partialNativeReceiptNeedsFullDeliveryAndCountsOnlyTrackedUnits(){var l=new SlayerLootLedger();var id=UUID.randomUUID();l.track(id,"full components",2,3);l.receipt(id,"full components",1);assertFalse(l.inventory(id,4));l.receipt(id,"full components",1);assertFalse(l.inventory(id,4));assertTrue(l.inventory(id,5));assertTrue(l.complete());assertEquals(2,l.delivered());l.receipt(id,"full components",2);assertFalse(l.inventory(id,7));assertEquals(2,l.delivered());}
 @Test void excessMergedOrContradictoryReceiptFailsClosed(){var l=new SlayerLootLedger();var id=UUID.randomUUID();l.track(id,"full components",1,0);l.receipt(id,"full components",2);assertTrue(l.invalid());assertFalse(l.inventory(id,2));assertFalse(l.complete());}
 @Test void twoSameContentReceiptsCannotReuseOneInventoryGain(){var l=new SlayerLootLedger();var a=UUID.randomUUID();var b=UUID.randomUUID();l.track(a,"same",1,0);l.track(b,"same",1,0);l.receipt(a,"same",1);l.receipt(b,"same",1);assertTrue(l.inventory(a,1));assertFalse(l.inventory(b,1));assertFalse(l.complete());assertTrue(l.inventory(b,2));assertTrue(l.complete());}
 @Test void allTrackedDropsNeedTheirOwnDelivery(){var l=new SlayerLootLedger();var a=UUID.randomUUID();var b=UUID.randomUUID();l.track(a,"one",1,0);l.track(b,"two",1,0);l.receipt(a,"one",1);assertTrue(l.inventory(a,1));assertFalse(l.complete());l.receipt(b,"two",1);assertTrue(l.inventory(b,1));assertTrue(l.complete());}
}
