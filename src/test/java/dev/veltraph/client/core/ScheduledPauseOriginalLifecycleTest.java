package dev.veltraph.client.core;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ScheduledPauseOriginalLifecycleTest {
    static final class Actor extends AbstractModule {Actor(String id){super(id,id,ModuleCategory.MINING);}void stateTo(MacroState next){setState(next);}}
    static final class Run {final ModuleManager manager=new ModuleManager();final Actor owner=new Actor("owner"),other=new Actor("other");final ModuleManager.LifecycleSnapshot before;
        Run(){manager.register(owner);manager.register(other);owner.setEnabled(true);before=manager.lifecycleSnapshot();}}
    @Test void exactOriginalRunningToPausedIsRecognizedWithoutAdoptingANewSnapshot(){var r=new Run();r.owner.stateTo(MacroState.PAUSED);assertFalse(r.manager.lifecycleCurrent(r.before));assertTrue(r.manager.lifecyclePausedCurrent(r.before,r.owner));assertTrue(r.manager.originalEnableLifetimeCurrent(r.before,r.owner));}
    @Test void stateAbaRefusesPauseProofButOldEnableStillHasCleanupIdentity(){var r=new Run();r.owner.stateTo(MacroState.PAUSED);r.owner.stateTo(MacroState.RUNNING);r.owner.stateTo(MacroState.PAUSED);assertFalse(r.manager.lifecyclePausedCurrent(r.before,r.owner));assertTrue(r.manager.originalEnableLifetimeCurrent(r.before,r.owner));}
    @Test void enableAbaCannotBeStoppedThroughOldCleanupProjection(){var r=new Run();r.owner.setEnabled(false);r.owner.setEnabled(true);r.owner.stateTo(MacroState.PAUSED);assertFalse(r.manager.lifecyclePausedCurrent(r.before,r.owner));assertFalse(r.manager.originalEnableLifetimeCurrent(r.before,r.owner));}
    @Test void anotherActorsMutationRefusesWholeOriginalPausedHistory(){var r=new Run();r.owner.stateTo(MacroState.PAUSED);r.other.setEnabled(true);assertFalse(r.manager.lifecyclePausedCurrent(r.before,r.owner));assertTrue(r.manager.originalEnableLifetimeCurrent(r.before,r.owner));}
    @Test void inputProviderOrRegistryChangesCannotCreatePausedAdmission(){for(boolean register:java.util.List.of(false,true)){var r=new Run();r.owner.stateTo(MacroState.PAUSED);if(register)r.manager.register(new Actor("new"));else r.manager.leasedInputOwner(()->r.other);assertFalse(r.manager.lifecyclePausedCurrent(r.before,r.owner));assertTrue(r.manager.originalEnableLifetimeCurrent(r.before,r.owner));}}
    @Test void unchangedRunningStoppedAndForeignActorCannotClaimNormalPause(){var r=new Run();assertFalse(r.manager.lifecyclePausedCurrent(r.before,r.owner));assertFalse(r.manager.lifecyclePausedCurrent(r.before,r.other));r.owner.setEnabled(false);assertFalse(r.manager.lifecyclePausedCurrent(r.before,r.owner));assertFalse(r.manager.originalEnableLifetimeCurrent(r.before,r.owner));}
}
