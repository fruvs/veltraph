package dev.veltraph.client.foraging.supply;
import dev.veltraph.client.core.*;
import dev.veltraph.client.inventory.bottle.*;
import dev.veltraph.client.metrics.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
final class ForagingSupplyFinancialProofTest {
    final Object player=new Object(),world=new Object(),connection=new Object(),menu=new Object(),screen=new Object();
    final AbstractModule owner=new AbstractModule("supply","Supply",ModuleCategory.FORAGING){};
    final FinancialOutcomeBindings.Scope scope=new FinancialOutcomeBindings.Scope(player,world,connection);
    BottleFillReceipts.Receipt receipt(BottleFillReceipts.Kind kind,int revision,long sequence,Object nativeMenu){return new BottleFillReceipts.Receipt(sequence,new BottleFillReceipts.Scope(player,world,connection,nativeMenu,screen,2),kind,revision,Map.of(),Map.of());}
    @Test void exactNativeSequenceAndRevisionRemainUnmodified(){var result=ForagingSupplyFinancialProof.accept(owner,UUID.randomUUID(),menu,2,4,scope,10,List.of(receipt(BottleFillReceipts.Kind.CONTAINER_CONTENT,6,37,menu))).orElseThrow();assertEquals(37,result.evidence().getFirst().sequence());assertEquals(6,result.evidence().getFirst().receiptRevision());assertEquals(4,result.evidence().getFirst().requestRevision());assertSame(menu,result.evidence().getFirst().menu());assertEquals(10,result.coinAmountOrNull().longValueExact());}
    @Test void revisionlessPlayerReceiptCannotBecomeExpenseEvidence(){assertTrue(ForagingSupplyFinancialProof.accept(owner,UUID.randomUUID(),menu,2,4,scope,10,List.of(receipt(BottleFillReceipts.Kind.PLAYER_SLOT,-1,37,menu))).isEmpty());}
    @Test void foreignMenuOlderRevisionAndNoReceiptDeny(){for(var receipts:List.of(List.of(receipt(BottleFillReceipts.Kind.CONTAINER_CONTENT,6,37,new Object())),List.of(receipt(BottleFillReceipts.Kind.CONTAINER_SLOT,3,37,menu)),List.<BottleFillReceipts.Receipt>of()))assertTrue(ForagingSupplyFinancialProof.accept(owner,UUID.randomUUID(),menu,2,4,scope,10,receipts).isEmpty());}
}
