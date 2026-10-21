package dev.veltraph.client.core;

import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

/** Actual original Core receipt boundaries only; authentic Root passive positives require native union tests. */
class ClosedCameraDeclarationPassiveBoundaryTest {
    private static final class Actor extends AbstractModule {
        Runnable body=()->{};Actor(String name){super(name,name,ModuleCategory.INTERNAL);}
        @Override public void onTick(){body.run();}
    }
    private static final class Rig {
        final ModuleManager root=new ModuleManager();final Actor actor=new Actor("actor");
        final AtomicReference<ModuleManager.ModuleTickDeclaration> before=new AtomicReference<>(),after=new AtomicReference<>(),closed=new AtomicReference<>();
        Rig(){root.register(actor);actor.setEnabled(true);root.observeActualModuleTicks(new ModuleManager.ActualModuleTickObserver(){
            public Object before(ModuleManager.ModuleTickDeclaration actual){before.set(actual);return actual;}
            public void after(ModuleManager.ModuleTickDeclaration actual,Object captured){after.set(actual);}
            public void closed(ModuleManager.ModuleTickDeclaration actual,Object captured){closed.set(actual);}
        });}
    }
    @Test void originalNormalReceiptRemainsExact(){var r=new Rig();r.root.tick();var d=r.closed.get();
        assertNotNull(d);assertSame(r.before.get(),d);assertSame(r.after.get(),d);assertTrue(d.normalReturnedCurrent(r.actor));
        assertTrue(r.root.lifecycleSameHistory(d.beforeSnapshot(),d.normalReturnedSnapshot()));}
    @Test void nullPassiveTransitionCannotCreateContinuation(){var r=new Rig();r.root.tick();var d=r.closed.get();var original=d.normalReturnedSnapshot();
        assertNull(d.capturePassiveContinuation(null,r.actor));assertSame(original,d.normalReturnedSnapshot());assertTrue(d.normalReturnedCurrent(r.actor));}
    @Test void openTickCannotStandInForClosedDeclaration(){var r=new Rig();var original=new AtomicReference<ModuleManager.LifecycleSnapshot>();
        r.actor.body=()->original.set(r.before.get().normalReturnedSnapshot());r.root.tick();assertNull(original.get());assertNotNull(r.closed.get().normalReturnedSnapshot());}
    @Test void foreignModuleCannotUseOriginalReceipt(){var r=new Rig();var foreign=new Actor("foreign");r.root.register(foreign);r.root.tick();
        assertFalse(r.closed.get().normalReturnedCurrent(foreign));assertNull(r.closed.get().capturePassiveContinuation(null,foreign));}
    @Test void foreignThreadCannotReadNormalReceipt()throws Exception{var r=new Rig();r.root.tick();var d=r.closed.get();var result=new AtomicReference<Boolean>();
        var t=new Thread(()->result.set(d.normalReturnedCurrent(r.actor)));t.start();t.join();assertEquals(Boolean.FALSE,result.get());assertTrue(d.normalReturnedCurrent(r.actor));}
    @Test void disableEnableAbaCannotRenewOriginalReceipt(){var r=new Rig();r.root.tick();var d=r.closed.get();r.actor.setEnabled(false);r.actor.setEnabled(true);
        assertFalse(d.normalReturnedCurrent(r.actor));assertNull(d.normalReturnedSnapshot());assertNull(d.capturePassiveContinuation(null,r.actor));}
    @Test void exceptionalOriginalTickHasNoNormalDeclaration(){var r=new Rig();r.actor.body=()->{throw new IllegalStateException("original");};r.root.tick();
        assertNotNull(r.closed.get());assertNull(r.after.get());assertFalse(r.closed.get().normalReturnedCurrent(r.actor));assertNull(r.closed.get().normalReturnedSnapshot());}
    @Test void nestedTickCannotMintClosedOriginalReceipt(){var r=new Rig();var entered=new boolean[1];r.actor.body=()->{if(!entered[0]){entered[0]=true;r.root.tick();}};r.root.tick();
        assertNotNull(r.closed.get());assertNull(r.after.get());assertFalse(r.closed.get().normalReturnedCurrent(r.actor));}
    @Test void laterUnchangedTickCannotReplaceOriginalSnapshotIdentity(){var r=new Rig();r.root.tick();var old=r.closed.get();var original=old.normalReturnedSnapshot();r.root.tick();
        assertNotSame(old,r.closed.get());assertSame(original,old.normalReturnedSnapshot());assertTrue(old.normalReturnedCurrent(r.actor));}
}
