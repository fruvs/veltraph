package dev.veltraph.client.metrics;
import dev.veltraph.client.core.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ForagingReceiptProjectionTest {
    static class Owner extends AbstractModule {Owner(String id){super(id,id,ModuleCategory.FORAGING);}}
    final Owner owner=new Owner("foraging_chest_fill");final ModuleManager manager=new ModuleManager();
    final FinancialOutcomeBindings.Scope scope=new FinancialOutcomeBindings.Scope(new Object(),new Object(),new Object());
    final AtomicReference<List<FinancialActionReceipt>> receipts=new AtomicReference<>(List.of());final List<FinancialOutcomeBindings.Outcome> published=new ArrayList<>();
    final FinancialOutcomeBindings.Sink sink=new FinancialOutcomeBindings.Sink(){public boolean confirmed(String id,boolean expense,BigDecimal amount,long now){throw new AssertionError("Typed evidence was discarded");}public boolean confirmed(FinancialOutcomeBindings.Outcome outcome,long now){published.add(outcome);return true;}};
    final FinancialOutcomeBindings bridge=new FinancialOutcomeBindings(manager,module->Optional.of(ForagingReceiptProjection.binding(module,receipts::get)),()->scope,()->sink,()->true);
    ForagingReceiptProjectionTest(){manager.register(owner);manager.enable(owner.id());bridge.afterFreshStart(owner,0);}
    FinancialActionReceipt proof(dev.veltraph.client.core.Module actualOwner,FinancialOutcomeBindings.Scope actualScope,FinancialActionReceipt.Kind kind,FinancialActionReceipt.Denomination denomination){return new FinancialActionReceipt(UUID.randomUUID().toString(),actualOwner,kind,denomination,BigDecimal.TEN,actualScope,List.of(new FinancialActionReceipt.Evidence(new Object(),4,2,3,17,"applied-container-inventory-and-purse")));}
    FinancialActionReceipt purchase(){return proof(owner,scope,FinancialActionReceipt.Kind.PURCHASE,FinancialActionReceipt.Denomination.COINS);}
    @Test void acceptedAcquisitionPublishesOnceWhileOwnerStillActive(){var receipt=purchase();receipts.set(List.of(receipt));bridge.poll(1);bridge.poll(2);assertEquals(1,published.size());assertSame(receipt,published.getFirst().receipt());assertTrue(published.getFirst().expense());assertEquals(BigDecimal.TEN,published.getFirst().amountOrNull());assertTrue(owner.isEnabled());}
    @Test void acknowledgedAcquireSurvivesLaterStoreFailureAndFinalPollDeduplicates(){var receipt=purchase();receipts.set(List.of(receipt));manager.disable(owner.id());bridge.poll(1);bridge.poll(2);assertEquals(1,published.size());assertEquals(receipt.id(),published.getFirst().id());}
    @Test void pendingPartialCancelWithoutAcceptedProofPublishesNothing(){bridge.poll(1);manager.disable(owner.id());bridge.poll(2);assertTrue(published.isEmpty());}
    @Test void foreignOwnerScopeAndNonPurchaseCannotPublish(){var other=new Owner("foreign");receipts.set(List.of(proof(other,scope,FinancialActionReceipt.Kind.PURCHASE,FinancialActionReceipt.Denomination.COINS),proof(owner,new FinancialOutcomeBindings.Scope(new Object(),scope.world(),scope.connection()),FinancialActionReceipt.Kind.PURCHASE,FinancialActionReceipt.Denomination.COINS),proof(owner,scope,FinancialActionReceipt.Kind.SALE,FinancialActionReceipt.Denomination.COINS)));bridge.poll(1);assertTrue(published.isEmpty());}
    @Test void freshActivationExcludesOldReceiptAndAdmitsOnlyNewUuid(){var old=purchase();receipts.set(List.of(old));manager.disable(owner.id());bridge.poll(1);manager.enable(owner.id());bridge.afterFreshStart(owner,2);bridge.poll(3);assertEquals(1,published.size());var fresh=purchase();receipts.set(List.of(old,fresh));bridge.poll(4);assertEquals(2,published.size());assertNotEquals(published.getFirst().id(),published.getLast().id());}
    @Test void unknownDenominationKeepsEvidenceWithoutInventingCoinExpense(){var unknown=proof(owner,scope,FinancialActionReceipt.Kind.PURCHASE,FinancialActionReceipt.Denomination.UNKNOWN);receipts.set(List.of(unknown));bridge.poll(1);assertEquals(1,published.size());assertNull(published.getFirst().amountOrNull());assertSame(unknown,published.getFirst().receipt());}
}
