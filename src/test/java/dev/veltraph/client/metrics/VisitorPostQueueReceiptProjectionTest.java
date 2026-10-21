package dev.veltraph.client.metrics;

import dev.veltraph.client.core.*;
import dev.veltraph.client.config.AutoSellMode;
import dev.veltraph.client.inventory.selling.SellingAcknowledgment;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class VisitorPostQueueReceiptProjectionTest {
    private static final class Visitor extends AbstractModule {
        Visitor(){super("visitor_financial_parent","Visitor",ModuleCategory.INTERMEDIARY);}
        public void onEnable(){}public void onDisable(){}public void onTick(){}
    }
    private final FinancialOutcomeBindings.Scope scope=new FinancialOutcomeBindings.Scope(new Object(),new Object(),new Object());
    private SellingAcknowledgment receipt(String id,SellingAcknowledgment.Denomination currency){return new SellingAcknowledgment(id,AutoSellMode.NPC,"exact wheat",9,4,new BigDecimal("12"),BigDecimal.ZERO,new BigDecimal("12"),"Purse",currency,scope.player(),scope.world(),scope.connection(),new FinancialActionReceipt.Evidence(new Object(),2,3,4,1,"NPC"));}
    @Test void actualCoinReceiptKeepsExactNativeScopeEvidenceAndParentOwner(){var owner=new Visitor();var proof=receipt(UUID.randomUUID().toString(),SellingAcknowledgment.Denomination.COINS);var projected=VisitorPostQueueReceiptProjection.project(owner,List.of(proof)).getFirst();assertSame(owner,projected.receipt().owner());assertEquals(new BigDecimal("12"),projected.amountOrNull());assertTrue(projected.scope().same(scope));assertSame(proof.evidence(),projected.receipt().evidence().getFirst());}
    @Test void labelCannotConvertUnknownCurrencyIntoCoins(){var projected=VisitorPostQueueReceiptProjection.project(new Visitor(),List.of(receipt(UUID.randomUUID().toString(),SellingAcknowledgment.Denomination.UNKNOWN))).getFirst();assertNull(projected.amountOrNull());assertEquals(FinancialActionReceipt.Denomination.UNKNOWN,projected.receipt().denomination());}
    @Test void enabledParentStreamsOneAcknowledgmentOnceAndRetainsItAcrossFailure(){var manager=new ModuleManager();var owner=new Visitor();manager.register(owner);manager.enable(owner.id());var proofs=new AtomicReference<List<SellingAcknowledgment>>(List.of());var events=new ArrayList<BigDecimal>();var sink=new FinancialOutcomeBindings.Sink(){public boolean confirmed(String id,boolean expense,BigDecimal amount,long now){events.add(amount);return true;}};var bridge=new FinancialOutcomeBindings(manager,module->Optional.of(new FinancialOutcomeBindings.Binding(()->VisitorPostQueueReceiptProjection.project(module,proofs.get()),false)),()->scope,()->sink,()->true);bridge.afterFreshStart(owner,0);bridge.poll(1);assertTrue(events.isEmpty());proofs.set(List.of(receipt(UUID.randomUUID().toString(),SellingAcknowledgment.Denomination.COINS)));bridge.poll(2);bridge.poll(3);assertEquals(List.of(new BigDecimal("12")),events);manager.disable(owner.id());bridge.poll(4);assertEquals(1,events.size());manager.enable(owner.id());bridge.afterFreshStart(owner,5);bridge.poll(6);assertEquals(1,events.size(),"Preserved terminal evidence cannot be recredited after another activation");}
}
