package dev.veltraph.client.core;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Real Core setEnabled/stop bodies; callback observations are asserted outside contained observers. */
class OriginalStopClosedProducerTest {
    static final class Actor extends AbstractModule {
        Runnable disabling=()->{};
        Actor(){super("closed_producer_actor","Actor",ModuleCategory.INTERNAL);}
        @Override public void onDisable(){disabling.run();}
    }
    static final class Observations implements ModuleManager.ActualDisableObserver {
        final ModuleManager root;final List<String> order=new ArrayList<>();
        ModuleManager.ActualDisableStart start;ModuleManager.ManualDisableInstruction instruction;
        boolean selectedBefore,selectedAfter,afterClosed,closePendingGone,closeOriginalSame;
        int before,after,closed;
        boolean failAfter,failClose;
        Observations(ModuleManager root){this.root=root;}
        public Object before(ModuleManager.ActualDisableStart actual){before++;start=actual;instruction=actual.originalManualInstruction();selectedBefore=instruction!=null&&instruction.currentFor(actual);order.add("before");return actual;}
        public void after(ModuleManager.OwnDisableReceipt actual,Object captured){after++;afterClosed=actual.closedCurrent();selectedAfter=instruction!=null&&instruction.currentFor(start);order.add("after");if(failAfter)throw new AssertionError("contained after");}
        public void closed(ModuleManager.ActualDisableStart actual,Object captured){closed++;closePendingGone=root.actualOwnDisableModule()==null;closeOriginalSame=actual==start&&captured==start;order.add("closed");if(failClose)throw new AssertionError("contained close");}
    }
    @Test void explicitDisableSelectsActualInstructionOnlyAfterCallbackAndClosesOnce(){
        var root=new ModuleManager();var actor=new Actor();root.register(actor);actor.setEnabled(true);
        var observed=new Observations(root);root.observeActualDisables(observed);
        root.beforeDisable(m->observed.order.add("provider"));actor.disabling=()->observed.order.add("body");
        assertTrue(root.disable(actor.id()));
        assertEquals(List.of("provider","before","body","after","closed"),observed.order);
        assertTrue(observed.selectedBefore);assertTrue(observed.selectedAfter);assertTrue(observed.afterClosed);
        assertTrue(observed.closePendingGone);assertTrue(observed.closeOriginalSame);
        assertEquals(1,observed.before);assertEquals(1,observed.after);assertEquals(1,observed.closed);
        assertFalse(actor.isEnabled());assertNull(observed.start.originalManualInstruction());assertFalse(observed.instruction.currentFor(observed.start));
    }
    @Test void failingActualDisableEmitsCloseButNoNormalReceipt(){
        var root=new ModuleManager();var actor=new Actor();root.register(actor);actor.setEnabled(true);
        var observed=new Observations(root);root.observeActualDisables(observed);var calls=new int[1];
        actor.disabling=()->{calls[0]++;throw new AssertionError("original body");};
        assertDoesNotThrow(root::stopAll);
        assertEquals(1,calls[0]);assertEquals(1,observed.before);assertEquals(0,observed.after);assertEquals(1,observed.closed);
        assertTrue(observed.selectedBefore);assertTrue(observed.closePendingGone);assertTrue(observed.closeOriginalSame);
        assertFalse(actor.isEnabled());assertNull(observed.start.originalManualInstruction());
    }
    @Test void passiveAfterAndCloseFailuresCannotChangeOriginalBodyOrRetainPending(){
        var root=new ModuleManager();var actor=new Actor();root.register(actor);actor.setEnabled(true);
        var observed=new Observations(root);observed.failAfter=true;observed.failClose=true;root.observeActualDisables(observed);var calls=new int[1];actor.disabling=()->calls[0]++;
        assertDoesNotThrow(root::stopAll);
        assertEquals(1,calls[0]);assertEquals(1,observed.after);assertEquals(1,observed.closed);assertTrue(observed.afterClosed);assertTrue(observed.closePendingGone);
        assertNull(root.actualOwnDisableModule());assertFalse(actor.isEnabled());
    }
    @Test void providerRawSetterCannotBorrowLaterOriginalInstruction(){
        var root=new ModuleManager();var actor=new Actor();root.register(actor);actor.setEnabled(true);
        var observed=new Observations(root);root.observeActualDisables(observed);root.beforeDisable(m->m.setEnabled(false));
        root.stopAll();assertEquals(1,observed.before);assertEquals(1,observed.after);assertEquals(1,observed.closed);
        assertFalse(observed.selectedBefore);assertFalse(observed.selectedAfter);assertFalse(actor.isEnabled());assertNull(root.actualOwnDisableModule());
    }
    @Test void noGameplayReceiptFollowsLegacyCallbackAndClosedOriginalInvocation(){
        var root=new ModuleManager();var actor=new Actor();root.register(actor);actor.setEnabled(true);
        var order=new ArrayList<String>();var start=new ModuleManager.ManualStopStart[1];var legacy=new ModuleManager.ManualStopReceipt[1];
        var closed=new ModuleManager.OwnNoGameplayStopReceipt[1];var facts=new boolean[3];
        root.observeManualStops(new ModuleManager.ManualStopObserver(){
            public Object before(ModuleManager.ManualStopStart actual){start[0]=actual;return actual;}
            public void after(ModuleManager.ManualStopReceipt actual,Object captured){legacy[0]=actual;facts[0]=actual.current();order.add("legacy");}
            public void closedNoGameplayStop(ModuleManager.OwnNoGameplayStopReceipt actual,Object captured){closed[0]=actual;facts[1]=!start[0].startingCurrent()&&!legacy[0].current();facts[2]=captured==start[0];order.add("closed");}
        });root.stopAll();assertEquals(List.of("legacy","closed"),order);assertTrue(facts[0]);assertTrue(facts[1]);assertTrue(facts[2]);assertNotNull(closed[0]);
        assertFalse(closed[0].matches(start[0],null));assertNull(closed[0].normalReturnedLifecycle(start[0],null));assertFalse(actor.isEnabled());
    }
    @Test void nestedManualStopDoesNotEmitClosedNoGameplayGrant(){
        var root=new ModuleManager();var actor=new Actor();root.register(actor);actor.setEnabled(true);var count=new int[1];var nested=new boolean[1];
        root.observeManualStops(new ModuleManager.ManualStopObserver(){public Object before(ModuleManager.ManualStopStart actual){return actual;}
            public void after(ModuleManager.ManualStopReceipt actual,Object captured){}
            public void closedNoGameplayStop(ModuleManager.OwnNoGameplayStopReceipt actual,Object captured){count[0]++;}});
        root.beforeDisable(m->{if(!nested[0]){nested[0]=true;root.stopAll();}});root.stopAll();assertEquals(0,count[0]);assertFalse(actor.isEnabled());
    }
}
