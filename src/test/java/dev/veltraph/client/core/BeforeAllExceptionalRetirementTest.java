package dev.veltraph.client.core;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Actual private Core transitions; no public onDisable substitution or native permission assertions. */
class BeforeAllExceptionalRetirementTest {
    static final class Actor extends AbstractModule {
        final List<String> order;int disables;Runnable cleanup=()->{};
        Actor(String id,List<String> order){super(id,id,ModuleCategory.INTERNAL);this.order=order;}
        public void onDisable(){disables++;order.add("disable:"+id());cleanup.run();}
    }
    static final class Observer implements ModuleManager.ManualStopObserver {
        int normal,closed;ModuleManager.ManualStopStart start;
        public Object before(ModuleManager.ManualStopStart actual){start=actual;return actual;}
        public void after(ModuleManager.ManualStopReceipt actual,Object captured){normal++;}
        public void closedNoGameplayStop(ModuleManager.OwnNoGameplayStopReceipt actual,Object captured){closed++;}
    }
    private static Observer observe(ModuleManager root){var observer=new Observer();root.observeManualStops(observer);return observer;}
    private static Actor actor(ModuleManager root,String id,List<String> order){var actor=new Actor(id,order);root.register(actor);actor.setEnabled(true);return actor;}
    @Test void beforeAllFailureStillRetiresExactOldActorsOnceAndNeverPublishesNormalStop(){
        var root=new ModuleManager();var order=new ArrayList<String>();var a=actor(root,"a",order);var b=actor(root,"b",order);var observer=observe(root);
        var primary=new IllegalStateException("before-all failure");
        root.beforeStopAll(()->{order.add("beforeAll");throw primary;});root.beforeDisable(m->order.add("before:"+m.id()));
        assertSame(primary,assertThrows(IllegalStateException.class,root::stopAll));
        assertEquals(List.of("beforeAll","before:a","disable:a","before:b","disable:b"),order);
        assertFalse(a.isEnabled());assertFalse(b.isEnabled());assertEquals(1,a.disables);assertEquals(1,b.disables);
        assertEquals(0,observer.normal);assertEquals(0,observer.closed);assertFalse(observer.start.startingCurrent());
    }
    @Test void beforeEachFailurePreservesPrimaryAndStillAttemptsOriginalSetter(){
        var root=new ModuleManager();var order=new ArrayList<String>();var a=actor(root,"a",order);observe(root);
        var primary=new AssertionError("before-all");var secondary=new IllegalStateException("before-disable");
        root.beforeStopAll(()->{throw primary;});root.beforeDisable(m->{order.add("before:"+m.id());throw secondary;});
        assertSame(primary,assertThrows(AssertionError.class,root::stopAll));assertArrayEquals(new Throwable[]{secondary},primary.getSuppressed());
        assertFalse(a.isEnabled());assertEquals(1,a.disables);assertEquals(List.of("before:a","disable:a"),order);
    }
    @Test void failedSetterIsAttemptedOnceAndKeepsNoNormalReceiptOrLaterUnprovedCleanup(){
        var root=new ModuleManager();var order=new ArrayList<String>();var a=actor(root,"a",order);var b=actor(root,"b",order);var observer=observe(root);
        var primary=new IllegalStateException("before-all");var secondary=new AssertionError("native cleanup prefix");a.cleanup=()->{throw secondary;};
        root.beforeStopAll(()->{throw primary;});
        assertSame(primary,assertThrows(IllegalStateException.class,root::stopAll));assertArrayEquals(new Throwable[]{secondary},primary.getSuppressed());
        assertFalse(a.isEnabled());assertEquals(1,a.disables);assertTrue(b.isEnabled());assertEquals(0,b.disables);
        assertEquals(0,observer.normal);assertEquals(0,observer.closed);
    }
    @Test void reenabledSameActorAndNewActorCannotBeAdoptedAfterBeforeAllFailure(){
        var root=new ModuleManager();var order=new ArrayList<String>();var old=actor(root,"old",order);observe(root);var primary=new IllegalStateException("before-all");
        var fresh=new Actor("new",order);
        root.beforeStopAll(()->{old.setEnabled(false);old.setEnabled(true);root.register(fresh);fresh.setEnabled(true);throw primary;});
        assertSame(primary,assertThrows(IllegalStateException.class,root::stopAll));assertTrue(old.isEnabled());assertTrue(fresh.isEnabled());assertEquals(1,old.disables);assertEquals(0,fresh.disables);
    }
    @Test void replacementCallbackAndInputBindingRevokeOldCleanupEvenWhenActorStampUnchanged(){
        var root=new ModuleManager();var order=new ArrayList<String>();var old=actor(root,"old",order);observe(root);var primary=new IllegalStateException("before-all");
        root.beforeStopAll(()->{root.beforeDisable(m->{throw new AssertionError("replacement callback must not run");});root.leasedInputOwner(()->old);throw primary;});
        assertSame(primary,assertThrows(IllegalStateException.class,root::stopAll));assertTrue(old.isEnabled());assertEquals(0,old.disables);assertEquals(0,primary.getSuppressed().length);
    }
    @Test void beforeEachReentryRefusesOldSetterAndSelfSuppression(){
        var root=new ModuleManager();var order=new ArrayList<String>();var old=actor(root,"old",order);observe(root);var primary=new IllegalStateException("same failure");
        root.beforeStopAll(()->{throw primary;});root.beforeDisable(m->{old.setEnabled(false);old.setEnabled(true);throw primary;});
        assertSame(primary,assertThrows(IllegalStateException.class,root::stopAll));assertTrue(old.isEnabled());assertEquals(1,old.disables);assertEquals(0,primary.getSuppressed().length);
    }
    @Test void inputBindingReplacementAloneRefusesUntouchedOriginalActor(){
        var root=new ModuleManager();var order=new ArrayList<String>();var old=actor(root,"old",order);observe(root);var primary=new IllegalStateException("input binding replacement");
        root.beforeStopAll(()->{root.leasedInputOwner(()->old);throw primary;});
        root.beforeDisable(m->{throw new AssertionError("original callback must refuse changed binding");});
        assertSame(primary,assertThrows(IllegalStateException.class,root::stopAll));assertTrue(old.isEnabled());assertEquals(0,old.disables);assertEquals(0,primary.getSuppressed().length);
    }
    @Test void callbackReplacementAloneRefusesUntouchedOriginalActor(){
        var root=new ModuleManager();var order=new ArrayList<String>();var old=actor(root,"old",order);observe(root);var primary=new IllegalStateException("callback replacement");
        root.beforeStopAll(()->{root.beforeDisable(m->{throw new AssertionError("replacement callback must not run");});throw primary;});
        assertSame(primary,assertThrows(IllegalStateException.class,root::stopAll));assertTrue(old.isEnabled());assertEquals(0,old.disables);assertEquals(0,primary.getSuppressed().length);
    }
    @Test void nestedStopRevokesOuterCleanupDespiteUnchangedActorStamp(){
        var root=new ModuleManager();var order=new ArrayList<String>();var old=actor(root,"old",order);var observer=observe(root);
        var outer=new IllegalStateException("outer stop");var inner=new IllegalStateException("nested stop");var nested=new java.util.concurrent.atomic.AtomicBoolean();
        root.beforeStopAll(()->{
            if(nested.getAndSet(true))throw inner;
            assertSame(inner,assertThrows(IllegalStateException.class,root::stopAll));throw outer;
        });
        root.beforeDisable(m->{throw new AssertionError("revoked nested source must not run actor cleanup");});
        assertSame(outer,assertThrows(IllegalStateException.class,root::stopAll));assertTrue(old.isEnabled());assertEquals(0,old.disables);
        assertEquals(0,observer.normal);assertEquals(0,observer.closed);assertEquals(0,outer.getSuppressed().length);
    }
    @Test void successfulStopKeepsOriginalOrderAndNormalReceipt(){
        var root=new ModuleManager();var order=new ArrayList<String>();var old=actor(root,"old",order);var observer=observe(root);
        root.beforeStopAll(()->order.add("beforeAll"));root.beforeDisable(m->order.add("before:"+m.id()));root.stopAll();
        assertEquals(List.of("beforeAll","before:old","disable:old"),order);assertFalse(old.isEnabled());assertEquals(1,observer.normal);assertEquals(1,observer.closed);
    }
}
