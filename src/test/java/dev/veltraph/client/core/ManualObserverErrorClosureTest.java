package dev.veltraph.client.core;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Actual production manual-stop callbacks; no Root/native permissions or substitute receipts. */
class ManualObserverErrorClosureTest {
    static final class Actor extends AbstractModule {
        int stops; Runnable stop=()->{};
        Actor(String id){super(id,id,ModuleCategory.INTERNAL);}
        public void onDisable(){stops++;stop.run();}
    }
    static final class Observer implements ModuleManager.ManualStopObserver {
        final List<String> order=new ArrayList<>();
        Runnable before=()->{},after=()->{},closed=()->{};
        int starts,ends,closes; ModuleManager.ManualStopReceipt receipt;
        public Object before(ModuleManager.ManualStopStart actual){starts++;order.add("before");before.run();return actual;}
        public void after(ModuleManager.ManualStopReceipt actual,Object captured){ends++;receipt=actual;order.add("after");after.run();}
        public void closedNoGameplayStop(ModuleManager.OwnNoGameplayStopReceipt actual,Object captured){closes++;order.add("closed");closed.run();}
    }
    @Test void beforeObserverErrorCannotSkipExplicitOriginalDisableAndNextStopStillWorks(){
        var root=new ModuleManager();var a=new Actor("before-error");root.register(a);a.setEnabled(true);
        var o=new Observer();o.before=()->{throw new AssertionError("capture error");};root.observeManualStops(o);
        assertTrue(root.disable(a.id()));assertFalse(a.isEnabled());assertEquals(1,a.stops);assertEquals(1,o.starts);assertEquals(0,o.ends);assertEquals(0,o.closes);
        a.setEnabled(true);o.before=()->{};assertTrue(root.disable(a.id()));assertEquals(2,a.stops);assertEquals(1,o.ends);assertEquals(1,o.closes);
    }
    @Test void afterObserverErrorCannotSkipActualClosedNotification(){
        var root=new ModuleManager();var a=new Actor("after-error");root.register(a);a.setEnabled(true);
        var o=new Observer();o.after=()->{throw new AssertionError("completion error");};root.observeManualStops(o);
        assertTrue(root.disable(a.id()));assertEquals(List.of("before","after","closed"),o.order);assertEquals(1,a.stops);assertEquals(1,o.closes);assertFalse(o.receipt.current());assertFalse(a.isEnabled());
    }
    @Test void closedObserverErrorDoesNotAlterSuccessfulOriginalStopOrNextStop(){
        var root=new ModuleManager();var a=new Actor("close-error");root.register(a);a.setEnabled(true);
        var o=new Observer();o.closed=()->{throw new AssertionError("closed error");};root.observeManualStops(o);
        assertTrue(root.disable(a.id()));a.setEnabled(true);assertTrue(root.disable(a.id()));assertEquals(2,a.stops);assertEquals(2,o.closes);assertFalse(a.isEnabled());
    }
    @Test void originalDisableErrorIdentitySurvivesObserverCaptureErrorAndNoNormalReceipt(){
        var root=new ModuleManager();var a=new Actor("original-error");root.register(a);a.setEnabled(true);
        var original=new AssertionError("original disable");a.stop=()->{throw original;};
        var o=new Observer();o.before=()->{throw new AssertionError("extra capture");};root.observeManualStops(o);
        assertSame(original,assertThrows(AssertionError.class,()->root.disable(a.id())));assertEquals(1,a.stops);assertEquals(0,o.ends);assertEquals(0,o.closes);assertFalse(a.isEnabled());
    }
    @Test void businessBeforeDisableErrorRemainsOriginalAndDoesNotRunDisableOrGrantCompletion(){
        var root=new ModuleManager();var a=new Actor("business-error");root.register(a);a.setEnabled(true);
        var original=new AssertionError("business callback");root.beforeDisable(m->{throw original;});var o=new Observer();root.observeManualStops(o);
        assertSame(original,assertThrows(AssertionError.class,()->root.disable(a.id())));assertTrue(a.isEnabled());assertEquals(0,a.stops);assertEquals(0,o.ends);assertEquals(0,o.closes);
    }
}
