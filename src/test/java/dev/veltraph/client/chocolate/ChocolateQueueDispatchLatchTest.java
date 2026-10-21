package dev.veltraph.client.chocolate;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static dev.veltraph.client.chocolate.ChocolateFactoryTest.*;

class ChocolateQueueDispatchLatchTest {
    static class CallbackPort extends Port {
        Runnable callback=()->{};boolean throwing;
        @Override public int currentHotbarSlot(){return 0;}
        @Override public ChocolateFactoryRuntime.Delivery click(ChocolateFactoryObservation o,int slot){clicks++;callback.run();if(throwing)throw new IllegalStateException("Unknown input result");return delivery;}
    }
    static class FencedPort extends CallbackPort {
        @Override public ChocolateFactoryRuntime.Delivery click(ChocolateFactoryObservation o,int slot,ChocolateFactoryRuntime.DispatchFence fence){if(!fence.current(o,slot))return ChocolateFactoryRuntime.Delivery.DENIED;callback.run();if(!fence.current(o,slot))return ChocolateFactoryRuntime.Delivery.DENIED;clicks++;return delivery;}
    }
    static ChocolateFactoryRuntime selected(CallbackPort p){var rt=new ChocolateFactoryRuntime(p);assertTrue(rt.selectQueue(profile(),two(observation(1,1,"500","old"),false),List.of("employee","capacity"),n("200"),n("100"),1000));return rt;}
    @Test void recursiveQueueTickCannotIssueSecondInput(){var p=new CallbackPort();var rt=selected(p);p.callback=()->rt.tick(profile(),1000);rt.tick(profile(),1000);assertEquals(1,p.clicks);assertEquals(ChocolateFactoryRuntime.State.WAITING_RECEIPT,rt.state());assertEquals(1,rt.queued());}
    @Test void cancelDuringInputKeepsOriginalUnknownWithoutRetryOrNextStep(){var p=new CallbackPort();var rt=selected(p);var original=rt.selection();p.callback=rt::cancel;rt.tick(profile(),1000);assertEquals(ChocolateFactoryRuntime.State.UNKNOWN,rt.state());assertSame(original,rt.selection());assertEquals(0,rt.queued());assertFalse(rt.advanceQueue(profile(),1500));rt.tick(profile(),1500);assertEquals(1,p.clicks);assertEquals(BigDecimal.ZERO,rt.spent());assertTrue(rt.acknowledgedUpgrades().isEmpty());}
    @Test void finalNativeFenceRefusesEffectAfterCallbackStop(){var p=new FencedPort();var rt=selected(p);p.callback=rt::cancel;rt.tick(profile(),1000);assertEquals(0,p.clicks);assertEquals(ChocolateFactoryRuntime.State.UNKNOWN,rt.state());rt.tick(profile(),1500);assertEquals(0,p.clicks);}
    @Test void recursiveReceiptCannotManufactureSpendBeforeInputReturns(){var p=new CallbackPort();var rt=selected(p);p.observation=two(observation(2,2,"400","new"),false);p.callback=()->{rt.tick(profile(),1500);assertEquals(BigDecimal.ZERO,rt.spent());assertTrue(rt.acknowledgedUpgrades().isEmpty());};rt.tick(profile(),1000);assertEquals(ChocolateFactoryRuntime.State.WAITING_RECEIPT,rt.state());rt.tick(profile(),1500);assertEquals(ChocolateFactoryRuntime.State.ACKNOWLEDGED,rt.state());assertEquals(n("100"),rt.spent());assertEquals(1,p.clicks);}
    @Test void exceptionAfterEffectCannotRearmAndReleasesInFlightLatch(){var p=new CallbackPort();var rt=selected(p);p.throwing=true;rt.tick(profile(),1000);assertEquals(ChocolateFactoryRuntime.State.UNKNOWN,rt.state());rt.tick(profile(),1500);assertEquals(1,p.clicks);assertEquals(1000,rt.requestSentAtMillis());assertTrue(rt.acknowledgedUpgrades().isEmpty());}
    @Test void inFlightManualDurabilityRestoresOriginalPendingAndPostReturnRetryWorks(){
        var p=new CallbackPort();var rt=new ChocolateFactoryRuntime(p);assertTrue(rt.select(profile(),observation(1,1,"200","old"),"employee",n("100"),BigDecimal.ZERO,1000));
        var memory=new AtomicReference<ChocolateFactoryHistory>();var disk=new AtomicReference<ChocolateFactoryHistory>();var pendingRef=new AtomicReference<ChocolateFactoryModule.Journal>();
        p.callback=()->{
            rt.cancel();var pending=new ChocolateFactoryModule.Journal(1,"local-test","2026-09-30","UNKNOWN","employee","100","0","cancelled","0",rt.selection().intentId());pendingRef.set(pending);
            var plan=ChocolateQueueManualReconciliation.prepare(rt,profile(),true,pending,"local-test",true,observation(2,2,"100","new"),0,1500);var original=rt.manualClosureSnapshot();
            memory.set(ChocolateFactoryHistory.decode(ChocolateFactoryHistory.decode("").with(pending)));disk.set(memory.get());
            assertFalse(rt.matchesManualClosure(plan));assertThrows(IllegalStateException.class,()->rt.closeManualReconciliation(plan));
            assertFalse(ChocolateManualReconciliationTransaction.commit(pending,plan.resolved(),j->memory.set(ChocolateFactoryHistory.decode(memory.get().withManualQueueJournal(j))),()->{disk.set(memory.get());return true;},()->rt.matchesManualClosure(plan),()->rt.closeManualReconciliation(plan),()->rt.restoreOwnedManualClosure(original)));
            assertEquals(pending,disk.get().profiles().get("local-test"));assertTrue(disk.get().pending());assertEquals(BigDecimal.ZERO,rt.manualDeclaredDebit());
            assertFalse(rt.selectQueue(profile(),observation(1,1,"200","old"),List.of("employee"),n("100"),BigDecimal.ZERO,1500));
        };
        rt.tick(profile(),1000);assertEquals(ChocolateFactoryRuntime.State.UNKNOWN,rt.state());
        var plan=ChocolateQueueManualReconciliation.prepare(rt,profile(),true,pendingRef.get(),"local-test",true,observation(2,2,"100","new"),0,1500);var original=rt.manualClosureSnapshot();
        assertTrue(ChocolateManualReconciliationTransaction.commit(plan.pending(),plan.resolved(),j->memory.set(ChocolateFactoryHistory.decode(memory.get().withManualQueueJournal(j))),()->{disk.set(memory.get());return true;},()->rt.matchesManualClosure(plan),()->rt.closeManualReconciliation(plan),()->rt.restoreOwnedManualClosure(original)));
        assertEquals(ChocolateFactoryRuntime.State.IDLE,rt.state());assertEquals(n("100"),rt.manualDeclaredDebit());assertEquals(BigDecimal.ZERO,rt.spent());assertTrue(rt.acknowledgedUpgrades().isEmpty());assertFalse(disk.get().pending());assertEquals(1,p.clicks);
    }
    @Test void originalDeadlineRemainsDispatchTimeAcrossReentrantTick(){var p=new CallbackPort();var rt=selected(p);p.callback=()->rt.tick(profile(),1500);rt.tick(profile(),1000);p.observation=two(observation(2,2,"400","new"),false);rt.tick(profile(),3001);assertEquals(ChocolateFactoryRuntime.State.UNKNOWN,rt.state());assertEquals(1000,rt.requestSentAtMillis());assertEquals(BigDecimal.ZERO,rt.spent());assertEquals(1,p.clicks);}
    @Test void typedFinalFenceRejectsStopAfterLastNativeValidationAndExpiresAfterDispatch(){
        var trace=new java.util.ArrayList<String>();
        var captured=new AtomicReference<ChocolateFactoryRuntime.DispatchFence>();
        var p=new CallbackPort(){
            @Override public ChocolateFactoryRuntime.Delivery click(ChocolateFactoryObservation o,int slot,ChocolateFactoryRuntime.DispatchFence fence){
                captured.set(fence);assertTrue(fence.current(o,slot));assertFalse(fence.current(o,slot+1));assertFalse(fence.current(observation(1,1,"500","old"),slot));
                // Model the real order: caller authority, then native codec validation callback,
                // then concrete callback-free intent fence, immediately before the effect.
                trace.add("earlier-authority-true");callback.run();trace.add("last-identity-validation-complete");
                if(!fence.current(o,slot)){trace.add("final-fence-false");return ChocolateFactoryRuntime.Delivery.DENIED;}
                trace.add("binding-consumed");clicks++;return ChocolateFactoryRuntime.Delivery.SENT;
            }
        };
        var rt=selected(p);p.callback=rt::cancel;rt.tick(profile(),1000);
        assertEquals(List.of("earlier-authority-true","last-identity-validation-complete","final-fence-false"),trace);
        assertEquals(0,p.clicks);assertEquals(ChocolateFactoryRuntime.State.UNKNOWN,rt.state());assertFalse(captured.get().current());
        rt.tick(profile(),1500);assertEquals(0,p.clicks);assertEquals(BigDecimal.ZERO,rt.spent());
    }

    @Test void typedFinalFenceAdmitsUnchangedValidationExactlyOnceThenExpires(){
        var trace=new java.util.ArrayList<String>();var captured=new AtomicReference<ChocolateFactoryRuntime.DispatchFence>();
        var p=new CallbackPort(){
            @Override public ChocolateFactoryRuntime.Delivery click(ChocolateFactoryObservation o,int slot,ChocolateFactoryRuntime.DispatchFence fence){
                captured.set(fence);assertTrue(fence.current(o,slot));trace.add("earlier-authority-true");
                callback.run();trace.add("last-identity-validation-complete");
                if(!fence.current(o,slot)){trace.add("final-fence-false");return ChocolateFactoryRuntime.Delivery.DENIED;}
                trace.add("final-fence-true");trace.add("binding-consumed");clicks++;trace.add("native-input");return ChocolateFactoryRuntime.Delivery.SENT;
            }
        };
        var rt=selected(p);p.callback=()->rt.tick(profile(),1000);rt.tick(profile(),1000);
        assertEquals(List.of("earlier-authority-true","last-identity-validation-complete","final-fence-true","binding-consumed","native-input"),trace);
        assertEquals(1,p.clicks);assertEquals(ChocolateFactoryRuntime.State.WAITING_RECEIPT,rt.state());assertFalse(captured.get().current());
    }

}
