package dev.veltraph.client.galatea;
import dev.veltraph.client.world.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class FigHarvestPickupLedgerTest {
    final Object owner=new Object(),player=new Object(),world=new Object(),connection=new Object(),tree=new Object();
    final BlockPos block=new BlockPos(2,70,0);final Object unit="oak full canonical components count1";
    final FigHarvestPickupLedger.Scope scope=new FigHarvestPickupLedger.Scope(owner,player,world,connection,1,tree,2);
    final List<String> gear=Collections.nCopies(5,"protected gear");
    List<Object> inventory(String wood){var list=new ArrayList<Object>(Collections.nCopies(36,"empty canonical"));list.set(0,wood);list.set(1,"protected axe");list.set(2,"protected scaffold ability");return list;}
    FigScaffoldAttemptFence.Frame frame(List<?> items){return new FigScaffoldAttemptFence.Frame(player,world,connection,owner,767,1,items,gear,"unchanged source settings",true);}
    FigHarvestPickupLedger ledger(){var ledger=new FigHarvestPickupLedger();ledger.bind(scope,Set.of(block),2000);return ledger;}
    FigHarvestPickupLedger.Correlation receipt(UUID item,long take,long batch,int contribution,int afterCount){
        var before=new FigHarvestPickupLedger.Cell(0,unit,1,"oak full count1");var after=new FigHarvestPickupLedger.Cell(0,unit,afterCount,"oak full count"+afterCount);
        // Spawn before ACK and inventory before Take are both legal transport arrangements.
        return new FigHarvestPickupLedger.Correlation(FigHarvestPickupLedger.Evidence.CORRELATED_NATIVE_CHAIN,scope,
                new FigHarvestPickupLedger.Break(1,block,900,2,1000,"original oak block"),
                new FigHarvestPickupLedger.Drop(item,42,block,new Vec3(2.5,70.5,.5),unit,contribution,3,4,950,1),
                new FigHarvestPickupLedger.Pickup(item,take,player,contribution,1100),
                new FigHarvestPickupLedger.Delivery(batch,List.of(new FigHarvestPickupLedger.CellDelta(0,before,after,contribution)),1050));
    }
    @Test void knownF97972CountOneToTwoReproducesStrictFenceThenOnlyCompletedReceiptAllowsIt(){
        var before=inventory("oak full count1");var after=inventory("oak full count2");var fence=new FigScaffoldAttemptFence();assertTrue(fence.begin(10,1000,2000,frame(before)));assertFalse(fence.valid(10,1200,frame(after)));
        var ledger=ledger();assertTrue(ledger.stageCorrelation(receipt(UUID.randomUUID(),5,6,1,2)));var permit=ledger.review(scope,10,before,after,Set.of(1,2)).orElseThrow();
        assertTrue(fence.admitHarvest(10,1200,frame(after),permit,ledger,scope));assertTrue(fence.valid(10,1200,frame(after)));assertEquals(after,fence.inventoryBaseline());
    }
    @Test void mereKnownLogCountOrWrongComponentChangeHasNoPermit(){
        var ledger=ledger();var before=inventory("oak full count1");var after=inventory("oak full count2");assertTrue(ledger.review(scope,10,before,after,Set.of(1,2)).isEmpty());
        assertTrue(ledger.stageCorrelation(receipt(UUID.randomUUID(),5,6,1,2)));assertTrue(ledger.review(scope,10,before,inventory("oak foreign lore count2"),Set.of(1,2)).isEmpty());
    }
    @Test void partialOrUncoveredNativeInventoryChangesCannotRefreshBaseline(){
        var ledger=ledger();assertTrue(ledger.stageCorrelation(receipt(UUID.randomUUID(),5,6,1,2)));var changed=inventory("oak full count2");changed.set(30,"foreign new item");assertTrue(ledger.review(scope,10,inventory("oak full count1"),changed,Set.of(1,2)).isEmpty());assertTrue(ledger.review(scope,10,inventory("oak full count1"),inventory("oak full count3"),Set.of(1,2)).isEmpty());
    }
    @Test void twoDistinctNativeItemsMayCoverOneAppliedInventoryBatchExactly(){
        var ledger=ledger();var before=inventory("oak full count1");var after=inventory("oak full count4");
        assertTrue(ledger.stageCorrelation(receipt(UUID.randomUUID(),5,9,1,4)));assertTrue(ledger.review(scope,10,before,after,Set.of(1,2)).isEmpty());
        assertTrue(ledger.stageCorrelation(receipt(UUID.randomUUID(),6,9,2,4)));var permit=ledger.review(scope,10,before,after,Set.of(1,2)).orElseThrow();assertTrue(ledger.commit(permit,scope,()->true));assertFalse(ledger.commit(permit,scope,()->true));
    }
    @Test void ambiguousOriginWrongRecipientAndOutOfTreeBlockDoNotStage(){
        var ledger=ledger();var c=receipt(UUID.randomUUID(),5,6,1,2);var d=c.drop();
        var ambiguous=new FigHarvestPickupLedger.Drop(d.itemUuid(),d.nativeEntityId(),d.correlatedBlock(),d.spawnPosition(),d.canonicalUnitIdentity(),d.availableCount(),d.spawnSerial(),d.dataSerial(),d.spawnedMillis(),2);
        assertFalse(ledger.stageCorrelation(new FigHarvestPickupLedger.Correlation(c.evidence(),c.scope(),c.broken(),ambiguous,c.pickup(),c.delivery())));
        var wrongPickup=new FigHarvestPickupLedger.Pickup(d.itemUuid(),5,new Object(),1,1100);assertFalse(ledger.stageCorrelation(new FigHarvestPickupLedger.Correlation(c.evidence(),c.scope(),c.broken(),d,wrongPickup,c.delivery())));
        var wrongBreak=new FigHarvestPickupLedger.Break(1,new BlockPos(9,70,9),900,2,1000,"oak");assertFalse(ledger.stageCorrelation(new FigHarvestPickupLedger.Correlation(c.evidence(),c.scope(),wrongBreak,d,c.pickup(),c.delivery())));
    }
    @Test void replayAndWrongNativeScopeOrGenerationCannotAuthorizeAnotherPickup(){
        var ledger=ledger();var c=receipt(UUID.randomUUID(),5,6,1,2);assertTrue(ledger.stageCorrelation(c));assertFalse(ledger.stageCorrelation(c));
        var other=new FigHarvestPickupLedger.Scope(owner,player,new Object(),connection,1,tree,2);assertTrue(ledger.review(other,10,inventory("oak full count1"),inventory("oak full count2"),Set.of(1,2)).isEmpty());
        var permit=ledger.review(scope,10,inventory("oak full count1"),inventory("oak full count2"),Set.of(1,2)).orElseThrow();var fence=new FigScaffoldAttemptFence();assertTrue(fence.begin(11,1000,2000,frame(inventory("oak full count1"))));assertFalse(fence.admitHarvest(11,1200,frame(inventory("oak full count2")),permit,ledger,scope));
    }
    @Test void protectedToolSlotAndNonInventoryFenceDriftStayRejected(){
        var ledger=ledger();assertTrue(ledger.stageCorrelation(receipt(UUID.randomUUID(),5,6,1,2)));var before=inventory("oak full count1");var after=inventory("oak full count2");assertTrue(ledger.review(scope,10,before,after,Set.of(0,1,2)).isEmpty());
        var permit=ledger.review(scope,10,before,after,Set.of(1,2)).orElseThrow();var fence=new FigScaffoldAttemptFence();assertTrue(fence.begin(10,1000,2000,frame(before)));
        var changed=new FigScaffoldAttemptFence.Frame(player,world,connection,owner,768,1,after,gear,"unchanged source settings",true);assertFalse(fence.admitHarvest(10,1200,changed,permit,ledger,scope));assertEquals(before,fence.inventoryBaseline());
        var changedGear=new ArrayList<>(gear);changedGear.set(0,"foreign helmet");var frame=new FigScaffoldAttemptFence.Frame(player,world,connection,owner,767,1,after,changedGear,"unchanged source settings",true);assertFalse(fence.admitHarvest(10,1200,frame,permit,ledger,scope));
    }
    @Test void failedOrThrowingFenceCasCannotConsumeAnyReceiptOrAdvanceBaseline(){
        var ledger=ledger();var before=inventory("oak full count1");var after=inventory("oak full count2");assertTrue(ledger.stageCorrelation(receipt(UUID.randomUUID(),5,6,1,2)));var permit=ledger.review(scope,10,before,after,Set.of(1,2)).orElseThrow();var fence=new FigScaffoldAttemptFence();assertTrue(fence.begin(10,1000,2000,frame(before)));
        assertFalse(ledger.commit(permit,scope,()->false));assertEquals(before,fence.inventoryBaseline());assertFalse(fence.valid(10,1200,frame(after)));
        assertThrows(IllegalStateException.class,()->ledger.commit(permit,scope,()->{throw new IllegalStateException("CAS unavailable");}));assertEquals(before,fence.inventoryBaseline());
        assertTrue(fence.admitHarvest(10,1200,frame(after),permit,ledger,scope));assertFalse(ledger.commit(permit,scope,()->true));
    }
    @Test void invalidSecondRecordLeavesAtomicBatchEntirelyUnstaged(){
        var ledger=ledger();var a=receipt(UUID.randomUUID(),5,9,1,2);var b=receipt(UUID.randomUUID(),6,10,1,2);var invalid=new FigHarvestPickupLedger.Pickup(b.drop().itemUuid(),6,new Object(),1,1100);
        var bad=new FigHarvestPickupLedger.Correlation(b.evidence(),b.scope(),b.broken(),b.drop(),invalid,b.delivery());assertFalse(ledger.stageBatch(List.of(a,bad)));assertTrue(ledger.review(scope,10,inventory("oak full count1"),inventory("oak full count2"),Set.of(1,2)).isEmpty());assertTrue(ledger.stageBatch(List.of(a)));
    }
    @Test void sharedBatchOversupplyOrIncompatibleAfterCellsDoNotStage(){
        var ledger=ledger();var a=receipt(UUID.randomUUID(),5,9,1,2);var b=receipt(UUID.randomUUID(),6,9,1,2);assertFalse(ledger.stageBatch(List.of(a,b)));assertTrue(ledger.review(scope,10,inventory("oak full count1"),inventory("oak full count2"),Set.of(1,2)).isEmpty());
        var different=receipt(UUID.randomUUID(),7,9,1,3);assertFalse(ledger.stageBatch(List.of(a,different)));
    }
    @Test void staleActivationOrTreeIdentityCannotSpendAReviewedPermit(){
        var ledger=ledger();var before=inventory("oak full count1");var after=inventory("oak full count2");assertTrue(ledger.stageCorrelation(receipt(UUID.randomUUID(),5,6,1,2)));var permit=ledger.review(scope,10,before,after,Set.of(1,2)).orElseThrow();
        var fresh=new FigHarvestPickupLedger.Scope(owner,player,world,connection,2,new Object(),3);ledger.bind(fresh,Set.of(block),2000);var fence=new FigScaffoldAttemptFence();assertTrue(fence.begin(10,1000,2000,frame(before)));assertFalse(fence.admitHarvest(10,1200,frame(after),permit,ledger,fresh));assertEquals(before,fence.inventoryBaseline());
    }
    @Test void originalDeadlineAndAllPositiveQuantityBoundsRemainStrict(){
        var ledger=new FigHarvestPickupLedger();ledger.bind(scope,Set.of(block),1000);assertFalse(ledger.stageCorrelation(receipt(UUID.randomUUID(),5,6,1,2)));
        var valid=ledger();var c=receipt(UUID.randomUUID(),5,6,1,2);var wrong=new FigHarvestPickupLedger.Pickup(c.drop().itemUuid(),5,player,2,1100);assertFalse(valid.stageCorrelation(new FigHarvestPickupLedger.Correlation(c.evidence(),c.scope(),c.broken(),c.drop(),wrong,c.delivery())));
    }
}
