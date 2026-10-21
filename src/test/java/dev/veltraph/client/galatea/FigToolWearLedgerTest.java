package dev.veltraph.client.galatea;
import dev.veltraph.client.world.BlockPos;
import dev.veltraph.client.galatea.FigHarvestPickupLedger.Scope;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static dev.veltraph.client.galatea.FigToolWearLedger.*;

/** STAGED uncompiled/unrun: exact observed native cost, not a server callback or general DAMAGE ignore. */
class FigToolWearLedgerTest {
    private final Scope scope=new Scope(new Object(),new Object(),new Object(),new Object(),4,new Object(),4);
    private final Object guard=new Object();private final Cell before=new Cell(1,1,5,"axeDamage5","allOtherComponents","nativeToolCost1",1);
    private Cell after(int count,int damage,Object unit,int cost){return new Cell(1,count,damage,"axeDamage"+damage,unit,"nativeToolCost1",cost);}
    private Action action(long generation,long epoch,String settings,long deadline){return new Action(scope,generation,265,new BlockPos(3,76,0),"native permitted LOG",guard,1,before,epoch,settings,deadline,100,12,267,13);}
    private Receipt receipt(Action action,Cell after,Ack ack){return new Receipt(Evidence.CORRELATED_NATIVE_ACTION,action,ack,272,180,before,after);}
    private Receipt receipt(){return receipt(action(22,1126,"cfg",1000),after(1,6,"allOtherComponents",1),new Ack(270,170,13,true));}
    private List<?> inventory(Object tool,Object pickup){var values=new ArrayList<Object>(Collections.nCopies(43,"empty"));values.set(1,tool);values.set(0,pickup);return values;}
    private Optional<Permit> review(FigToolWearLedger ledger,long g,long epoch,String settings,long deadline,List<?> original,List<?> actual){return ledger.review(scope,g,1,original,actual,200,epoch,settings,deadline);}
    @Test void exactCostProducesOneSlotProjectionAndQueryableReceipt(){var ledger=new FigToolWearLedger();assertTrue(ledger.stage(receipt()));var original=inventory("axeDamage5","log1");var actual=inventory("axeDamage6","log1");var p=review(ledger,22,1126,"cfg",1000,original,actual).orElseThrow();assertEquals(actual,p.after());assertEquals(1,p.receipts().size());assertTrue(p.boundTo(22,1126,"cfg",1000));}
    @Test void simultaneousOtherSlotChangeRemainsUnaccountedInWearProjection(){var ledger=new FigToolWearLedger();ledger.stage(receipt());var original=inventory("axeDamage5","log1");var actual=inventory("axeDamage6","log2");var p=review(ledger,22,1126,"cfg",1000,original,actual).orElseThrow();assertEquals(inventory("axeDamage6","log1"),p.after());assertNotEquals(actual,p.after());}
    @Test void falseCasDoesNotSpendNativeEvidenceThenTrueCasSpendsOnce(){var ledger=new FigToolWearLedger();ledger.stage(receipt());var p=review(ledger,22,1126,"cfg",1000,inventory("axeDamage5","log1"),inventory("axeDamage6","log1")).orElseThrow();assertFalse(ledger.commit(p,scope,()->false));assertEquals(1,ledger.receipts().size());assertTrue(ledger.commit(p,scope,()->true));assertFalse(ledger.commit(p,scope,()->true));assertTrue(ledger.receipts().isEmpty());}
    @Test void newerAttemptCannotLaunderOldReceipt(){var ledger=new FigToolWearLedger();ledger.stage(receipt());assertTrue(review(ledger,23,1126,"cfg",1000,inventory("axeDamage5","x"),inventory("axeDamage6","x")).isEmpty());}
    @Test void epochSettingsDeadlineDriftCannotRebindReceipt(){var ledger=new FigToolWearLedger();ledger.stage(receipt());for(var p:List.of(new Object[]{1127L,"cfg",1000L},new Object[]{1126L,"changed",1000L},new Object[]{1126L,"cfg",1001L}))assertTrue(review(ledger,22,(Long)p[0],(String)p[1],(Long)p[2],inventory("axeDamage5","x"),inventory("axeDamage6","x")).isEmpty());}
    @Test void repairZeroCostAndCountNegativeDoNotStage(){for(var after:List.of(after(1,4,"allOtherComponents",1),after(1,5,"allOtherComponents",1),after(0,6,"allOtherComponents",1),after(1,6,"allOtherComponents",0)))assertFalse(new FigToolWearLedger().stage(receipt(action(22,1126,"cfg",1000),after,new Ack(270,170,13,true))));}
    @Test void foreignComponentsToolReplacementOrExcessCostDoNotStage(){for(var after:List.of(after(1,6,"replacedComponents",1),after(1,7,"allOtherComponents",1),new Cell(1,1,6,"otherTool","allOtherComponents","different nativeTool",1)))assertFalse(new FigToolWearLedger().stage(receipt(action(22,1126,"cfg",1000),after,new Ack(270,170,13,true))));}
    @Test void noAirOrUnmatchedSequenceCannotStage(){for(var ack:List.of(new Ack(0,170,13,true),new Ack(270,170,12,true),new Ack(270,170,13,false),new Ack(270,170,-2,true)))assertFalse(new FigToolWearLedger().stage(receipt(action(22,1126,"cfg",1000),after(1,6,"allOtherComponents",1),ack)));}
    @Test void explicitServerAirPayloadMayUseOnlyDocumentedMinusOneSequence(){assertTrue(new FigToolWearLedger().stage(receipt(action(22,1126,"cfg",1000),after(1,6,"allOtherComponents",1),new Ack(270,170,-1,true))));}
    @Test void inventoryBeforeAirDoesNotInventOrdering(){var r=receipt(action(22,1126,"cfg",1000),after(1,6,"allOtherComponents",1),new Ack(270,190,13,true));assertTrue(new FigToolWearLedger().stage(r));}
    @Test void nullBeforeAndMalformedStopPairRefuseWithoutThrowing(){var a=action(22,1126,"cfg",1000);var broken=new Action(scope,22,265,a.target(),a.originalLogState(),guard,1,null,1126,"cfg",1000,100,12,267,13);assertFalse(new FigToolWearLedger().stage(receipt(broken,after(1,6,"allOtherComponents",1),new Ack(270,170,13,true))));var stop=new Action(scope,22,265,a.target(),a.originalLogState(),guard,1,before,1126,"cfg",1000,100,12,267,-1);assertFalse(new FigToolWearLedger().stage(receipt(stop,after(1,6,"allOtherComponents",1),new Ack(270,170,-1,true))));}
    @Test void replayCannotReuseOperationOrDelivery(){var ledger=new FigToolWearLedger();assertTrue(ledger.stage(receipt()));assertFalse(ledger.stage(receipt()));}
}
