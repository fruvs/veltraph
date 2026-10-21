package dev.veltraph.client.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Actual Core body observations, distinct from successful normal-return receipts. */
class PendingActualOwnDisableTest {
    static class Parent extends AbstractModule {
        Runnable body=()->{};
        Parent(String id){super(id,id,ModuleCategory.INTERNAL);}
        public void onDisable(){body.run();}
    }
    @Test void actualBodyCanObserveOnlyItsOwnPrivatePendingCallAndItExpiresAfterReturn(){
        var root=new ModuleManager();var p=new Parent("p");root.register(p);p.setEnabled(true);
        var retained=new ModuleManager.PendingOwnDisable[1];
        p.body=()->{retained[0]=root.pendingActualOwnDisable(p).orElseThrow();assertSame(root,retained[0].registryIdentity());
            assertTrue(retained[0].current(p));assertSame(retained[0],root.pendingActualOwnDisable(p).orElseThrow());};
        p.setEnabled(false);assertFalse(retained[0].current(p));assertTrue(root.pendingActualOwnDisable(p).isEmpty());
    }
    @Test void publicOnDisableCannotMintActualInvocation(){
        var root=new ModuleManager();var p=new Parent("raw");root.register(p);p.setEnabled(true);
        p.body=()->assertTrue(root.pendingActualOwnDisable(p).isEmpty());p.onDisable();assertTrue(p.isEnabled());
    }
    @Test void AnotherParentCannotAdoptRealPendingCall(){
        var root=new ModuleManager();var p=new Parent("p");var q=new Parent("q");root.register(p);root.register(q);p.setEnabled(true);
        p.body=()->{var actual=root.pendingActualOwnDisable(p).orElseThrow();assertFalse(actual.current(q));
            assertTrue(root.pendingActualOwnDisable(q).isEmpty());};p.setEnabled(false);
    }
    @Test void unrelatedRosterMutationRevokesOriginalPendingProof(){
        var root=new ModuleManager();var p=new Parent("p");var q=new Parent("q");root.register(p);root.register(q);p.setEnabled(true);
        p.body=()->{var actual=root.pendingActualOwnDisable(p).orElseThrow();q.setEnabled(true);
            assertFalse(actual.current(p));assertTrue(root.pendingActualOwnDisable(p).isEmpty());};p.setEnabled(false);
    }
    @Test void reentrantEnableCannotRetainOriginalStopProof(){
        var root=new ModuleManager();var p=new Parent("reenter");root.register(p);p.setEnabled(true);
        p.body=()->{var actual=root.pendingActualOwnDisable(p).orElseThrow();p.setEnabled(true);
            assertFalse(actual.current(p));assertTrue(root.pendingActualOwnDisable(p).isEmpty());};p.setEnabled(false);
    }
    @Test void throwingBodyDoesNotKeepInProgressObservationAlive(){
        var root=new ModuleManager();var p=new Parent("throws");root.register(p);p.setEnabled(true);
        var retained=new ModuleManager.PendingOwnDisable[1];p.body=()->{retained[0]=root.pendingActualOwnDisable(p).orElseThrow();
            throw new IllegalStateException("original stop failed");};
        assertThrows(IllegalStateException.class,()->p.setEnabled(false));assertFalse(retained[0].current(p));
        assertTrue(root.pendingActualOwnDisable(p).isEmpty());assertTrue(root.currentOwnDisableReceipt(p).isEmpty());
    }
}
