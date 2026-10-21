package dev.veltraph.client.galatea;
import dev.veltraph.client.world.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Pure receipt/fence integration; synthetic identities do not claim native mining or server proof. */
class FigScaffoldToolWearFenceTest {
    final Object owner=new Object(),player=new Object(),world=new Object(),connection=new Object(),tree=new Object(),guard=new Object();
    final long generation=22,epoch=1126,deadline=2000;final String settings="unchanged source config";
    final BlockPos target=new BlockPos(3,76,0);
    final FigHarvestPickupLedger.Scope scope=new FigHarvestPickupLedger.Scope(owner,player,world,connection,4,tree,4);
    final List<String> gear=Collections.nCopies(5,"original exact gear");
    List<Object> inventory(int damage,int wood){var rows=new ArrayList<Object>(Collections.nCopies(41,"original empty"));rows.set(0,"original oak count"+wood);rows.set(1,"diamond_axe count1 DAMAGE"+damage+" exact other components");rows.set(2,"original scaffold components count1");return rows;}
    FigScaffoldAttemptFence.Frame frame(List<?> rows){return new FigScaffoldAttemptFence.Frame(player,world,connection,owner,epoch,1,rows,gear,settings,true);}
    FigScaffoldAttemptFence fence(){var f=new FigScaffoldAttemptFence();assertTrue(f.begin(generation,1000,deadline,frame(inventory(5,1))));return f;}
    FigToolWearLedger.Cell cell(int damage){return new FigToolWearLedger.Cell(1,1,damage,inventory(damage,1).get(1),"exact axe stack/count and ALL components except DAMAGE","native diamond_axe",1);}
    FigToolWearLedger.Receipt wearReceipt(){var before=cell(5);var action=new FigToolWearLedger.Action(scope,generation,267,target,"original log state",guard,1,before,epoch,settings,deadline,1000,12,268,13);
        return new FigToolWearLedger.Receipt(FigToolWearLedger.Evidence.CORRELATED_NATIVE_ACTION,action,new FigToolWearLedger.Ack(270,1100,13,true),272,1110,before,cell(6));}
    FigToolWearLedger wearLedger(){var ledger=new FigToolWearLedger();assertTrue(ledger.stage(wearReceipt()));return ledger;}
    FigToolWearLedger.Permit review(FigToolWearLedger ledger,List<?> after){return ledger.review(scope,generation,1,inventory(5,1),after,1200,epoch,settings,deadline).orElseThrow();}
    boolean admit(FigScaffoldAttemptFence f,FigToolWearLedger ledger,FigToolWearLedger.Permit permit,FigScaffoldAttemptFence.Frame actual){return f.admitWear(generation,1200,actual,1,permit,ledger,scope,null,null);}
    FigHarvestPickupLedger harvestLedger(){var ledger=new FigHarvestPickupLedger();ledger.bind(scope,Set.of(target),deadline);return ledger;}
    FigHarvestPickupLedger.Correlation pickupReceipt(){var before=new FigHarvestPickupLedger.Cell(0,"original oak components",1,inventory(6,1).get(0));var after=new FigHarvestPickupLedger.Cell(0,"original oak components",2,inventory(6,2).get(0));var item=new UUID(1,2);
        return new FigHarvestPickupLedger.Correlation(FigHarvestPickupLedger.Evidence.CORRELATED_NATIVE_CHAIN,scope,
                new FigHarvestPickupLedger.Break(267,target,1000,270,1100,"original log state"),
                new FigHarvestPickupLedger.Drop(item,42,target,new Vec3(3.5,76.5,.5),"original oak components",1,268,269,1050,1),
                new FigHarvestPickupLedger.Pickup(item,275,player,1,1150),
                new FigHarvestPickupLedger.Delivery(276,List.of(new FigHarvestPickupLedger.CellDelta(0,before,after,1)),1160));}
    FigHarvestPickupLedger.Permit pickupPermit(FigHarvestPickupLedger ledger,FigToolWearLedger.Permit wear){assertTrue(ledger.stageCorrelation(pickupReceipt()));return ledger.review(scope,generation,wear.after(),inventory(6,2),Set.of(1,2)).orElseThrow();}

    @Test void capturedDamageFiveToSixRemainsStrictUntilCompleteOwnedReceiptCommits(){
        var f=fence();var actual=frame(inventory(6,1));assertFalse(f.valid(generation,1200,actual));var ledger=wearLedger();var permit=review(ledger,actual.inventory());
        assertTrue(admit(f,ledger,permit,actual));assertTrue(f.valid(generation,1200,actual));assertEquals(inventory(6,1),f.inventoryBaseline());assertTrue(ledger.receipts().isEmpty());assertEquals(deadline,f.deadline());
    }
    @Test void damageAloneOrAbsentPermitNeverRefreshesTheOriginalInventory(){
        var f=fence();var ledger=new FigToolWearLedger();assertTrue(ledger.review(scope,generation,1,inventory(5,1),inventory(6,1),1200,epoch,settings,deadline).isEmpty());
        assertFalse(admit(f,ledger,null,frame(inventory(6,1))));assertEquals(inventory(5,1),f.inventoryBaseline());
    }
    @Test void everyOriginalNativeGuardPrecedesDamageAllowanceAndLeavesReceiptsUnspent(){
        var changedGear=new ArrayList<>(gear);changedGear.set(0,"changed helmet");var after=inventory(6,1);
        var bad=List.of(new FigScaffoldAttemptFence.Frame(new Object(),world,connection,owner,epoch,1,after,gear,settings,true),
                new FigScaffoldAttemptFence.Frame(player,new Object(),connection,owner,epoch,1,after,gear,settings,true),
                new FigScaffoldAttemptFence.Frame(player,world,new Object(),owner,epoch,1,after,gear,settings,true),
                new FigScaffoldAttemptFence.Frame(player,world,connection,new Object(),epoch,1,after,gear,settings,true),
                new FigScaffoldAttemptFence.Frame(player,world,connection,owner,epoch+1,1,after,gear,settings,true),
                new FigScaffoldAttemptFence.Frame(player,world,connection,owner,epoch,1,after,changedGear,settings,true),
                new FigScaffoldAttemptFence.Frame(player,world,connection,owner,epoch,1,after,gear,"changed source config",true),
                new FigScaffoldAttemptFence.Frame(player,world,connection,owner,epoch,2,after,gear,settings,true),
                new FigScaffoldAttemptFence.Frame(player,world,connection,owner,epoch,1,after,gear,settings,false));
        for(var actual:bad){var f=fence();var ledger=wearLedger();var permit=review(ledger,after);assertFalse(admit(f,ledger,permit,actual));assertEquals(inventory(5,1),f.inventoryBaseline());assertEquals(1,ledger.receipts().size());assertTrue(admit(f,ledger,permit,frame(after)));}
    }
    @Test void generationAndOriginalTimeBoundsCannotSpendAReviewedPermit(){
        var f=fence();var ledger=wearLedger();var permit=review(ledger,inventory(6,1));for(long now:new long[]{999,2001})assertFalse(f.admitWear(generation,now,frame(inventory(6,1)),1,permit,ledger,scope,null,null));
        assertFalse(f.admitWear(generation+1,1200,frame(inventory(6,1)),1,permit,ledger,scope,null,null));assertEquals(1,ledger.receipts().size());assertEquals(inventory(5,1),f.inventoryBaseline());assertTrue(admit(f,ledger,permit,frame(inventory(6,1))));
    }
    @Test void unaccountedOtherSlotsCannotHideBehindAValidAxeWearProjection(){
        var f=fence();var ledger=wearLedger();var actual=inventory(6,1);actual.set(30,"foreign item or component");var permit=review(ledger,actual);assertEquals(inventory(6,1),permit.after());
        assertFalse(admit(f,ledger,permit,frame(actual)));assertEquals(inventory(5,1),f.inventoryBaseline());assertEquals(1,ledger.receipts().size());
    }
    @Test void wearCannotMoveToAnArbitraryUnissuedAxeSlot(){
        var f=fence();var ledger=wearLedger();var permit=review(ledger,inventory(6,1));assertFalse(f.admitWear(generation,1200,frame(inventory(6,1)),2,permit,ledger,scope,null,null));
        assertEquals(inventory(5,1),f.inventoryBaseline());assertEquals(1,ledger.receipts().size());
    }
    @Test void delayedOwnedAxeWearAfterAnOwnedAbilitySelectionKeepsLatestSelectedSlotGuard(){
        var f=fence();var ledger=wearLedger();var permit=review(ledger,inventory(6,1));
        var selectedAbility=new FigScaffoldAttemptFence.Frame(player,world,connection,owner,epoch,4,inventory(5,1),gear,settings,true);
        assertTrue(f.selected(generation,1100,4,selectedAbility));
        var manualSlot=new FigScaffoldAttemptFence.Frame(player,world,connection,owner,epoch,3,inventory(6,1),gear,settings,true);
        assertFalse(admit(f,ledger,permit,manualSlot));assertEquals(inventory(5,1),f.inventoryBaseline());assertEquals(1,ledger.receipts().size());
        var actual=new FigScaffoldAttemptFence.Frame(player,world,connection,owner,epoch,4,inventory(6,1),gear,settings,true);
        assertTrue(admit(f,ledger,permit,actual));assertTrue(f.valid(generation,1200,actual));assertEquals(4,f.expectedSlot());assertEquals(deadline,f.deadline());
    }
    @Test void delayedWearCannotAdoptAReplacedOriginalAxeOrChangeTheCurrentlyHeldAbility(){
        for(int slot:new int[]{1,4}){var f=fence();var ledger=wearLedger();var permit=review(ledger,inventory(6,1));
            assertTrue(f.selected(generation,1100,4,new FigScaffoldAttemptFence.Frame(player,world,connection,owner,epoch,4,inventory(5,1),gear,settings,true)));
            var replaced=inventory(6,1);replaced.set(slot,"replacement tool or foreign held item components");
            assertFalse(admit(f,ledger,permit,new FigScaffoldAttemptFence.Frame(player,world,connection,owner,epoch,4,replaced,gear,settings,true)));
            assertEquals(inventory(5,1),f.inventoryBaseline());assertEquals(1,ledger.receipts().size());}
    }
    @Test void anActionFromAnotherOwnerCannotAuthorizeTheOriginalAxeWear(){
        var r=wearReceipt();var a=r.action();var foreignScope=new FigHarvestPickupLedger.Scope(new Object(),player,world,connection,4,tree,4);
        var foreignAction=new FigToolWearLedger.Action(foreignScope,generation,a.issueSerial(),target,a.originalLogState(),guard,1,a.before(),epoch,settings,deadline,a.issued(),a.startSequence(),a.stopSerial(),a.stopSequence());
        var ledger=new FigToolWearLedger();assertTrue(ledger.stage(new FigToolWearLedger.Receipt(r.evidence(),foreignAction,r.ack(),r.appliedInventorySerial(),r.appliedAt(),r.before(),r.after())));
        assertTrue(ledger.review(scope,generation,1,inventory(5,1),inventory(6,1),1200,epoch,settings,deadline).isEmpty());
        var foreignPermit=ledger.review(foreignScope,generation,1,inventory(5,1),inventory(6,1),1200,epoch,settings,deadline).orElseThrow();var f=fence();
        assertFalse(admit(f,ledger,foreignPermit,frame(inventory(6,1))));assertEquals(inventory(5,1),f.inventoryBaseline());assertEquals(1,ledger.receipts().size());
    }
    @Test void changedTreeOrActivationCannotCommitAnOldNativeAction(){
        var f=fence();var ledger=wearLedger();var permit=review(ledger,inventory(6,1));
        for(var changed:List.of(new FigHarvestPickupLedger.Scope(owner,player,world,connection,5,tree,4),new FigHarvestPickupLedger.Scope(owner,player,world,connection,4,new Object(),4),new FigHarvestPickupLedger.Scope(owner,player,world,connection,4,tree,5)))
            assertFalse(f.admitWear(generation,1200,frame(inventory(6,1)),1,permit,ledger,changed,null,null));
        assertEquals(1,ledger.receipts().size());assertEquals(inventory(5,1),f.inventoryBaseline());assertTrue(admit(f,ledger,permit,frame(inventory(6,1))));
    }
    @Test void aPermitReviewedAgainstDifferentFrozenEpochConfigOrDeadlineStillCannotAdmit(){
        var r=wearReceipt();var a=r.action();
        for(int drift=0;drift<3;drift++){var changed=new FigToolWearLedger.Action(scope,generation,a.issueSerial(),target,a.originalLogState(),guard,1,a.before(),drift==0?epoch+1:epoch,drift==1?"changed source config":settings,drift==2?deadline+1:deadline,a.issued(),a.startSequence(),a.stopSerial(),a.stopSequence());
            var ledger=new FigToolWearLedger();assertTrue(ledger.stage(new FigToolWearLedger.Receipt(r.evidence(),changed,r.ack(),r.appliedInventorySerial(),r.appliedAt(),r.before(),r.after())));
            var permit=ledger.review(scope,generation,1,inventory(5,1),inventory(6,1),1200,changed.epoch(),changed.settings(),changed.originalDeadline()).orElseThrow();var f=fence();assertFalse(admit(f,ledger,permit,frame(inventory(6,1))));assertEquals(1,ledger.receipts().size());assertEquals(inventory(5,1),f.inventoryBaseline());}
    }
    @Test void replayCannotCommitAnotherFenceOrConsumeAnOperationTwice(){
        var ledger=wearLedger();var first=review(ledger,inventory(6,1));var second=review(ledger,inventory(6,1));assertTrue(admit(fence(),ledger,first,frame(inventory(6,1))));var other=fence();
        assertFalse(admit(other,ledger,second,frame(inventory(6,1))));assertFalse(ledger.stage(wearReceipt()));assertEquals(inventory(5,1),other.inventoryBaseline());
    }
    @Test void exactWearAndExactPositivePickupShareOneSuccessfulFenceCommit(){
        var f=fence();var wear=wearLedger();var wearPermit=review(wear,inventory(6,2));var harvest=harvestLedger();var pickup=pickupPermit(harvest,wearPermit);
        assertTrue(f.admitWear(generation,1200,frame(inventory(6,2)),1,wearPermit,wear,scope,pickup,harvest));assertTrue(f.valid(generation,1200,frame(inventory(6,2))));assertEquals(inventory(6,2),f.inventoryBaseline());assertTrue(wear.receipts().isEmpty());assertFalse(harvest.commit(pickup,scope,()->true));
    }
    @Test void rejectedInnerHarvestCommitCannotConsumeWearOrChangeFence(){
        var f=fence();var wear=wearLedger();var wearPermit=review(wear,inventory(6,2));var harvest=harvestLedger();var pickup=pickupPermit(harvest,wearPermit);
        harvest.bind(new FigHarvestPickupLedger.Scope(owner,player,world,connection,5,new Object(),5),Set.of(target),deadline);
        assertFalse(f.admitWear(generation,1200,frame(inventory(6,2)),1,wearPermit,wear,scope,pickup,harvest));assertEquals(inventory(5,1),f.inventoryBaseline());assertEquals(1,wear.receipts().size());
        harvest.bind(scope,Set.of(target),deadline);var fresh=pickupPermit(harvest,wearPermit);assertTrue(f.admitWear(generation,1200,frame(inventory(6,2)),1,wearPermit,wear,scope,fresh,harvest));
    }
    @Test void aPositiveInventoryCountWithoutItsOwnReceiptIsStillDenied(){
        var f=fence();var ledger=wearLedger();var permit=review(ledger,inventory(6,2));assertFalse(admit(f,ledger,permit,frame(inventory(6,2))));assertEquals(inventory(5,1),f.inventoryBaseline());assertEquals(1,ledger.receipts().size());
    }
    @Test void cancellationAfterReviewCannotSpendTheActionOrAdvanceTheBaseline(){
        var f=fence();var ledger=wearLedger();var permit=review(ledger,inventory(6,1));f.cancel();assertFalse(admit(f,ledger,permit,frame(inventory(6,1))));assertEquals(inventory(5,1),f.inventoryBaseline());assertEquals(1,ledger.receipts().size());
    }
    @Test void failedCasPreservesPermitForTheSubsequentExactFenceCommit(){
        var f=fence();var ledger=wearLedger();var permit=review(ledger,inventory(6,1));assertFalse(ledger.commit(permit,scope,()->false));assertEquals(1,ledger.receipts().size());assertEquals(inventory(5,1),f.inventoryBaseline());assertTrue(admit(f,ledger,permit,frame(inventory(6,1))));
    }
    @Test void readOnlyFenceChecksDoNotConsumeTheReceiptOrAdoptDamage(){
        var f=fence();var ledger=wearLedger();review(ledger,inventory(6,1));assertTrue(f.harvestCustody(generation,1200,frame(inventory(6,1))));assertFalse(f.valid(generation,1200,frame(inventory(6,1))));assertEquals(1,ledger.receipts().size());assertEquals(inventory(5,1),f.inventoryBaseline());
    }
}
