package dev.veltraph.client.metrics;

import dev.veltraph.client.core.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FinancialReceiptAccountingTest {
    static final class Owner extends AbstractModule {
        final List<FinancialOutcomeBindings.Outcome> results=new ArrayList<>();boolean cleanup;
        Owner(String id){super(id,id,ModuleCategory.MISC);}
        public void onEnable(){}public void onDisable(){}public void onTick(){}
        public boolean hasPendingCleanup(){return cleanup;}
    }
    final ModuleManager manager=new ModuleManager();
    final FinancialOutcomeBindings.Scope scope=new FinancialOutcomeBindings.Scope(new Object(),new Object(),new Object());
    final LocalSessionMetrics.Context context=new LocalSessionMetrics.Context(scope.player(),scope.world(),scope.connection());
    FinancialActionReceipt receipt(Owner owner,FinancialActionReceipt.Kind kind,FinancialActionReceipt.Denomination denom,String amount){return new FinancialActionReceipt(UUID.randomUUID().toString(),owner,kind,denom,amount==null?null:new BigDecimal(amount),scope,List.of(new FinancialActionReceipt.Evidence(new Object(),5,3,4,1,"NATIVE_RECEIPT")));}
    LocalSessionMetrics metrics(){var metrics=new LocalSessionMetrics(MetricContracts.empty(),0,1000,1000,0);metrics.bind(context,Map.of(),0);return metrics;}
    @Test void twoPurchasesAndFinalDisabledSaleAreCreditedOnceWithExactAttribution(){
        var metrics=metrics();var owner=new Owner("batch");manager.register(owner);manager.enable(owner.id());
        var sink=new FinancialOutcomeBindings.Sink(){public boolean confirmed(String id,boolean expense,BigDecimal value,long now){throw new AssertionError("Typed evidence was lost");}public boolean confirmed(FinancialOutcomeBindings.Outcome outcome,long now){return metrics.financial(context,outcome.receipt(),now);}};
        var bridge=new FinancialOutcomeBindings(manager,m->Optional.of(new FinancialOutcomeBindings.Binding(()->owner.results,false)),()->scope,()->sink,()->true);
        bridge.afterFreshStart(owner,0);var purchase=receipt(owner,FinancialActionReceipt.Kind.PURCHASE,FinancialActionReceipt.Denomination.COINS,"6");owner.results.add(new FinancialOutcomeBindings.Outcome(purchase));bridge.poll(1);bridge.poll(1);
        owner.results.add(new FinancialOutcomeBindings.Outcome(receipt(owner,FinancialActionReceipt.Kind.PURCHASE,FinancialActionReceipt.Denomination.COINS,"4")));bridge.poll(2);
        owner.results.add(new FinancialOutcomeBindings.Outcome(receipt(owner,FinancialActionReceipt.Kind.SALE,FinancialActionReceipt.Denomination.COINS,"12")));manager.disable(owner.id());bridge.poll(3);bridge.poll(4);
        assertEquals(new BigDecimal("12"),metrics.snapshot(4).knownProceeds());assertEquals(new BigDecimal("10"),metrics.snapshot(4).knownCosts());assertEquals(new BigDecimal("2"),metrics.snapshot(4).completeRealizedNet());assertEquals(3,metrics.financialEvidence().size());assertSame(owner,metrics.financialEvidence().getFirst().owner());assertSame(purchase.evidence().getFirst().menu(),metrics.financialEvidence().getFirst().evidence().getFirst().menu());
    }
    @Test void chocolateAndUnknownFeesKeepNativeAmountsAndCoinNetUnavailable(){
        var metrics=metrics();var owner=new Owner("factory");var chocolate=receipt(owner,FinancialActionReceipt.Kind.PURCHASE,FinancialActionReceipt.Denomination.CHOCOLATE,"660");
        assertTrue(metrics.financial(context,chocolate,1));assertFalse(metrics.financial(context,chocolate,1));assertEquals(new BigDecimal("660"),metrics.financialEvidence().getFirst().nativeAmountOrNull());assertNull(chocolate.coinAmountOrNull());
        assertTrue(metrics.financial(context,receipt(owner,FinancialActionReceipt.Kind.CRAFT_FEE,FinancialActionReceipt.Denomination.UNKNOWN,"1"),2));assertEquals(BigDecimal.ZERO,metrics.snapshot(2).knownCosts());assertNull(metrics.snapshot(2).completeRealizedNet());assertEquals(2,metrics.snapshot(2).unknownCashEvents());
    }
    @Test void mismatchedOwnerOrScopeCannotPublishBatchReceipt(){
        var owner=new Owner("owner");var foreign=new Owner("foreign");manager.register(owner);manager.enable(owner.id());var accepted=new AtomicInteger();var sink=new FinancialOutcomeBindings.Sink(){public boolean confirmed(String id,boolean expense,BigDecimal value,long now){accepted.incrementAndGet();return true;}};
        var bridge=new FinancialOutcomeBindings(manager,m->Optional.of(new FinancialOutcomeBindings.Binding(()->owner.results,false)),()->scope,()->sink,()->true);bridge.afterFreshStart(owner,0);
        owner.results.add(new FinancialOutcomeBindings.Outcome(receipt(foreign,FinancialActionReceipt.Kind.SALE,FinancialActionReceipt.Denomination.COINS,"100")));bridge.poll(1);assertEquals(0,accepted.get());
        var metrics=metrics();var changed=new LocalSessionMetrics.Context(scope.player(),new Object(),scope.connection());assertFalse(metrics.financial(changed,receipt(owner,FinancialActionReceipt.Kind.SALE,FinancialActionReceipt.Denomination.COINS,"100"),2));assertTrue(metrics.financialEvidence().isEmpty());
    }
    @Test void historicalBatchBaselineAndObservationDisableRetirePendingActions(){
        var owner=new Owner("batch");manager.register(owner);manager.enable(owner.id());var enabled=new AtomicBoolean(true);var accepted=new AtomicInteger();var sink=new FinancialOutcomeBindings.Sink(){public boolean confirmed(String id,boolean expense,BigDecimal value,long now){accepted.incrementAndGet();return true;}};
        owner.results.add(new FinancialOutcomeBindings.Outcome(receipt(owner,FinancialActionReceipt.Kind.PURCHASE,FinancialActionReceipt.Denomination.COINS,"6")));
        var bridge=new FinancialOutcomeBindings(manager,m->Optional.of(new FinancialOutcomeBindings.Binding(()->owner.results,false)),()->scope,()->sink,enabled::get);bridge.afterFreshStart(owner,0);bridge.poll(1);assertEquals(0,accepted.get());enabled.set(false);bridge.poll(2);owner.results.add(new FinancialOutcomeBindings.Outcome(receipt(owner,FinancialActionReceipt.Kind.SALE,FinancialActionReceipt.Denomination.COINS,"12")));enabled.set(true);manager.disable(owner.id());bridge.poll(3);assertEquals(0,accepted.get());
    }
    @Test void receiptRequiresCanonicalIdentityAndNonnegativeValues(){
        var owner=new Owner("native");assertThrows(IllegalArgumentException.class,()->receipt(owner,FinancialActionReceipt.Kind.PURCHASE,FinancialActionReceipt.Denomination.COINS,"-1"));
        assertThrows(IllegalArgumentException.class,()->new FinancialActionReceipt.Evidence(new Object(),1,1,2,0,"receipt"));
        var proof=receipt(owner,FinancialActionReceipt.Kind.PURCHASE,FinancialActionReceipt.Denomination.COINS,"6");assertThrows(IllegalArgumentException.class,()->new FinancialOutcomeBindings.Outcome(proof.id(),false,BigDecimal.ONE,scope,proof));
    }
}
