package dev.veltraph.client.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CombatFinishingLifecycleTest {
    private static final class Actor extends AbstractModule {
        Actor(String id){super(id,id,ModuleCategory.COMBAT);}
        void stateForTest(MacroState value){setState(value);}
    }
    @Test void onlyDistinctActuallyEnabledStoredParentsAndChildrenCanBeCaptured() {
        var parent=new Actor("parent");var child=new Actor("child");
        assertNull(CombatFinishingLifecycle.capture(null,child));assertNull(CombatFinishingLifecycle.capture(parent,parent));
        assertNull(CombatFinishingLifecycle.capture(parent,child));parent.setEnabled(true);
        assertNull(CombatFinishingLifecycle.capture(parent,child));child.setEnabled(true);
        assertTrue(CombatFinishingLifecycle.capture(parent,child).current());
    }
    @Test void unregisteredChildDisableEnableAbaRevokesActualStoredVersion() {
        var parent=new Actor("parent");var child=new Actor("child");parent.setEnabled(true);child.setEnabled(true);
        var proof=CombatFinishingLifecycle.capture(parent,child);child.setEnabled(false);child.setEnabled(true);
        assertFalse(proof.current());
    }
    @Test void parentDisableEnableAbaRevokesActualStoredVersion() {
        var parent=new Actor("parent");var child=new Actor("child");parent.setEnabled(true);child.setEnabled(true);
        var proof=CombatFinishingLifecycle.capture(parent,child);parent.setEnabled(false);parent.setEnabled(true);
        assertFalse(proof.current());
    }
    /** Historical test identifier retained; protected state mutations now have stored history. */
    @Test void stateSnapshotDeniesObservedChangeButDoesNotAttestHistoricalStateAba() {
        var parent=new Actor("parent");var child=new Actor("child");parent.setEnabled(true);child.setEnabled(true);
        var parentSnapshot=parent.storedLifecycle();var childSnapshot=child.storedLifecycle();
        assertTrue(parentSnapshot.known());assertTrue(childSnapshot.known());
        assertTrue(parentSnapshot.enabled());assertTrue(childSnapshot.enabled());
        var proof=CombatFinishingLifecycle.capture(parent,child);assertNotNull(proof);assertTrue(proof.current());
        child.stateForTest(MacroState.STOPPED);assertFalse(proof.current());
        child.stateForTest(MacroState.RUNNING);
        assertTrue(child.isEnabled());assertEquals(MacroState.RUNNING,child.state());
        assertTrue(child.storedLifecycle().known());
        assertFalse(proof.current(),"Returning to the observed value must not revive the captured run");
        assertFalse(childSnapshot.same(child.storedLifecycle()));
        var fresh=CombatFinishingLifecycle.capture(parent,child);assertNotNull(fresh);assertTrue(fresh.current());
        assertTrue(parentSnapshot.same(parent.storedLifecycle()),"The unchanged parent remains the original known snapshot");
    }

    @Test void parentObservedStateAbaRevokesTheCapturedChildRun() {
        var parent=new Actor("parent");var child=new Actor("child");parent.setEnabled(true);child.setEnabled(true);
        var childSnapshot=child.storedLifecycle();var proof=CombatFinishingLifecycle.capture(parent,child);
        assertNotNull(proof);assertTrue(proof.current());assertTrue(childSnapshot.known());
        parent.stateForTest(MacroState.STOPPED);assertFalse(proof.current());
        parent.stateForTest(MacroState.RUNNING);
        assertTrue(parent.isEnabled());assertEquals(MacroState.RUNNING,parent.state());
        assertTrue(parent.storedLifecycle().known());
        assertFalse(proof.current(),"A changed parent cannot regain custody of the child's old captured run");
        assertTrue(childSnapshot.same(child.storedLifecycle()),"The child did not cause this invalidation");
        assertTrue(CombatFinishingLifecycle.capture(parent,child).current());
    }

    @Test void repeatedSameStateObservationKeepsTheKnownCapturedRun() {
        var parent=new Actor("parent");var child=new Actor("child");parent.setEnabled(true);child.setEnabled(true);
        var parentSnapshot=parent.storedLifecycle();var childSnapshot=child.storedLifecycle();
        var proof=CombatFinishingLifecycle.capture(parent,child);assertNotNull(proof);
        parent.stateForTest(MacroState.RUNNING);child.stateForTest(MacroState.RUNNING);
        assertTrue(proof.current(),"A repeated observation is not a lifecycle transition");
        assertTrue(parentSnapshot.known());assertTrue(childSnapshot.known());
        assertTrue(parentSnapshot.same(parent.storedLifecycle()));assertTrue(childSnapshot.same(child.storedLifecycle()));
    }
}
