package dev.veltraph.client.metrics;

import dev.veltraph.client.core.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FinancialOutcomeBindingsTest {
    private static final class Owner extends AbstractModule {
        FinancialOutcomeBindings.Outcome receipt;int reads,resets;boolean cleanup;
        Owner(String id){super(id,id,ModuleCategory.MISC);}
        @Override public void onEnable(){resets++;}
        @Override public void onDisable(){}
        @Override public void onTick(){}
        @Override public boolean hasPendingCleanup(){return cleanup;}
        Optional<FinancialOutcomeBindings.Outcome> read(){reads++;return Optional.ofNullable(receipt);}
    }
    private static final class Sink implements FinancialOutcomeBindings.Sink {
        final List<FinancialOutcomeBindings.Outcome> received=new ArrayList<>();final Set<String> ids=new HashSet<>();
        final FinancialOutcomeBindings.Scope scope;Sink(FinancialOutcomeBindings.Scope scope){this.scope=scope;}
        @Override public boolean confirmed(String id,boolean expense,BigDecimal amount,long now){
            if(!ids.add(id))return false;received.add(new FinancialOutcomeBindings.Outcome(id,expense,amount,scope));return true;
        }
    }
    private final ModuleManager manager=new ModuleManager();
    private final AtomicReference<FinancialOutcomeBindings.Scope> scope=new AtomicReference<>(new FinancialOutcomeBindings.Scope(new Object(),new Object(),new Object()));
    private final AtomicBoolean enabled=new AtomicBoolean(true);
    private final AtomicReference<Sink> sink=new AtomicReference<>(new Sink(scope.get()));
    private final FinancialOutcomeBindings bridge=new FinancialOutcomeBindings(manager,m->m instanceof Owner owner?
            Optional.of(new FinancialOutcomeBindings.Binding(owner::read)):Optional.empty(),scope::get,sink::get,enabled::get);
    private Owner start(String id){var owner=new Owner(id);manager.register(owner);manager.enable(id);bridge.afterFreshStart(owner,0);return owner;}
    private FinancialOutcomeBindings.Outcome receipt(BigDecimal amount){return new FinancialOutcomeBindings.Outcome(UUID.randomUUID().toString(),false,amount,scope.get());}
    @Test void disabledDoesNotReadAndEnablingMidRunNeverArmsHistoricalReceipt(){
        enabled.set(false);var owner=start("selling");owner.receipt=receipt(BigDecimal.TEN);manager.disable(owner.id());bridge.poll(0);
        assertEquals(0,owner.reads);enabled.set(true);bridge.poll(1);assertTrue(sink.get().received.isEmpty());assertEquals(1,owner.resets);
    }
    @Test void exactCompleteDisabledOwnerIsCreditedOnceAndNextActualStartGetsIndependentUuid(){
        var owner=start("selling");bridge.poll(0);assertTrue(sink.get().received.isEmpty());
        owner.receipt=receipt(BigDecimal.TEN);manager.disable(owner.id());bridge.poll(1);bridge.poll(2);
        assertEquals(1,sink.get().received.size());assertEquals(BigDecimal.TEN,sink.get().received.getFirst().amountOrNull());
        manager.enable(owner.id());bridge.afterFreshStart(owner,3);bridge.poll(4);assertEquals(1,sink.get().received.size());
        owner.receipt=receipt(BigDecimal.ONE);manager.disable(owner.id());bridge.poll(5);assertEquals(2,sink.get().received.size());assertEquals(2,owner.resets);
    }
    @Test void unknownCashStaysNullAndNoReceiptIsNotAZeroOutcome(){
        var noReceipt=start("failed");manager.disable(noReceipt.id());bridge.poll(1);assertTrue(sink.get().received.isEmpty());
        var unknown=start("unknown");unknown.receipt=receipt(null);manager.disable(unknown.id());bridge.poll(2);
        assertEquals(1,sink.get().received.size());assertNull(sink.get().received.getFirst().amountOrNull());
    }
    @Test void twoViewsOfOneCanonicalSaleCannotCreateTwoCashEvents(){
        var direct=start("direct");var wrapper=start("wrapper");var acknowledged=receipt(BigDecimal.TEN);
        direct.receipt=wrapper.receipt=acknowledged;manager.disable(direct.id());manager.disable(wrapper.id());bridge.poll(1);bridge.poll(2);
        assertEquals(1,sink.get().received.size());assertEquals(acknowledged.id(),sink.get().received.getFirst().id());
    }
    @Test void disableOrCollectorReplacementInvalidatesInFlightOutcome(){
        var owner=start("selling");enabled.set(false);bridge.poll(1);enabled.set(true);owner.receipt=receipt(BigDecimal.TEN);bridge.poll(2);assertTrue(sink.get().received.isEmpty());
        bridge.afterFreshStart(owner,3);var replacement=new Sink(scope.get());sink.set(replacement);owner.receipt=receipt(BigDecimal.ONE);bridge.poll(4);assertTrue(replacement.received.isEmpty());
    }
    @Test void replacedWorldConnectionOrPlayerCannotCreditOldReceipt(){
        for(int which=0;which<3;which++){
            var owner=start("scope"+which);var old=scope.get();owner.receipt=receipt(BigDecimal.TEN);
            scope.set(new FinancialOutcomeBindings.Scope(which==0?new Object():old.player(),which==1?new Object():old.world(),which==2?new Object():old.connection()));
            bridge.poll(which);scope.set(old);bridge.poll(which+1);assertTrue(sink.get().received.isEmpty());
        }
    }
    @Test void staleProofScopeAndUnregisteredOwnerCannotCredit(){
        var owner=start("selling");owner.receipt=new FinancialOutcomeBindings.Outcome(UUID.randomUUID().toString(),false,BigDecimal.TEN,
                new FinancialOutcomeBindings.Scope(new Object(),scope.get().world(),scope.get().connection()));bridge.poll(1);assertTrue(sink.get().received.isEmpty());
        var impostor=new Owner(owner.id());impostor.setEnabled(true);impostor.receipt=receipt(BigDecimal.ONE);bridge.afterFreshStart(impostor,2);bridge.poll(3);
        assertEquals(0,impostor.reads);assertTrue(sink.get().received.isEmpty());
    }
    @Test void oldDeferredCompleteAndPendingCleanupCannotBecomeAFreshCashEvent(){
        var owner=start("selling");owner.receipt=receipt(BigDecimal.TEN);bridge.poll(1);assertEquals(1,sink.get().received.size());
        bridge.afterFreshStart(owner,2);bridge.poll(3);assertEquals(1,sink.get().received.size());
        owner.receipt=null;owner.cleanup=true;manager.disable(owner.id());bridge.poll(4);owner.cleanup=false;owner.receipt=receipt(BigDecimal.ONE);bridge.poll(5);
        assertEquals(2,sink.get().received.size());
    }
    @Test void canonicalUuidAndNonnegativeCashAreRequired(){
        assertThrows(IllegalArgumentException.class,()->new FinancialOutcomeBindings.Outcome("quoted 100 Coins",false,BigDecimal.TEN,scope.get()));
        assertThrows(IllegalArgumentException.class,()->new FinancialOutcomeBindings.Outcome(UUID.randomUUID().toString(),true,BigDecimal.ONE.negate(),scope.get()));
    }
}
