package dev.veltraph.client.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Uses genuine lifecycle/setState producers; returning to the same state cannot restore old history. */
class StoredEnableHistoryProducerTest {
    private static final class Actor extends AbstractModule {
        Actor(String id){super(id,id,ModuleCategory.INTERNAL);}
        void moveState(MacroState state){setState(state);}
    }
    @Test void currentEnabledStampRetainsActualEnableHistory(){
        var actor=new Actor("enabled");var root=new ModuleManager();root.register(actor);actor.setEnabled(true);
        var original=actor.storedLifecycle();assertTrue(original.known());assertTrue(original.enabled());
        assertTrue(actor.storedEnableHistoryCurrent(original));assertTrue(actor.storedLifecycleCurrent(original));assertSame(original,actor.storedLifecycle());
    }
    @Test void sameStateWritePreservesHistory(){
        var actor=new Actor("same");new ModuleManager().register(actor);actor.setEnabled(true);var original=actor.storedLifecycle();
        actor.moveState(MacroState.RUNNING);assertTrue(actor.storedEnableHistoryCurrent(original));assertTrue(actor.storedLifecycleCurrent(original));assertSame(original,actor.storedLifecycle());
    }
    @Test void stateChangeAndReturnRefusesOldStampDespiteEqualState(){
        var actor=new Actor("aba");new ModuleManager().register(actor);actor.setEnabled(true);var original=actor.storedLifecycle();
        actor.moveState(MacroState.PAUSED);assertFalse(actor.storedEnableHistoryCurrent(original));
        actor.moveState(MacroState.RUNNING);assertEquals(MacroState.RUNNING,actor.state());assertTrue(actor.isEnabled());
        assertFalse(actor.storedEnableHistoryCurrent(original));assertFalse(actor.storedLifecycleCurrent(original));assertFalse(original.same(actor.storedLifecycle()));
        assertTrue(actor.storedEnableHistoryCurrent(actor.storedLifecycle()));
    }
    @Test void actualDisableAndReenableCannotRestoreOldHistory(){
        var actor=new Actor("run");new ModuleManager().register(actor);actor.setEnabled(true);var original=actor.storedLifecycle();
        actor.setEnabled(false);assertFalse(actor.storedEnableHistoryCurrent(original));actor.setEnabled(true);
        assertTrue(actor.isEnabled());assertFalse(actor.storedEnableHistoryCurrent(original));assertFalse(original.same(actor.storedLifecycle()));
    }
    @Test void foreignActorStampWithSameIdAndStateRefuses(){
        var first=new Actor("same_id");var second=new Actor("same_id");first.setEnabled(true);second.setEnabled(true);
        assertFalse(first.storedEnableHistoryCurrent(second.storedLifecycle()));assertFalse(first.storedLifecycleCurrent(second.storedLifecycle()));
    }
    @Test void ambiguousRegistryAssociationRefusesEnabledHistory(){
        var actor=new Actor("ambiguous");var first=new ModuleManager();first.register(actor);actor.setEnabled(true);var original=actor.storedLifecycle();
        new ModuleManager().register(actor);assertTrue(actor.isEnabled());assertFalse(actor.storedEnableHistoryCurrent(original));assertFalse(actor.storedEnableHistoryCurrent(actor.storedLifecycle()));
    }
    @Test void disabledStampCannotQualifyEnabledHistory(){
        var actor=new Actor("disabled");var original=actor.storedLifecycle();assertFalse(actor.storedEnableHistoryCurrent(original));
        actor.setEnabled(true);assertFalse(actor.storedEnableHistoryCurrent(original));assertFalse(actor.storedEnableHistoryCurrent(null));
    }
}
