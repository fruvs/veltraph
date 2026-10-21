package dev.veltraph.client.fishing.worm;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class WormCleanupAuthorityTest {
    private WormCleanupAuthority.Observation o(boolean context,boolean owner,boolean epoch,boolean menu,boolean selected,boolean matches){return new WormCleanupAuthority.Observation(true,context,owner,epoch,true,menu,true,true,selected,matches,matches,matches);}
    @Test void intentionalDisabledOwnerStillReleasesCameraAndRestoresReservedRod(){
        // ModuleLifecycle sets enabled=false before its onDisable callback; enabled is deliberately not cleanup permission.
        var result=WormCleanupAuthority.decide(o(true,true,true,true,true,true));assertTrue(result.cancelCamera());assertTrue(result.restoreRod());
    }
    @Test void foreignOwnerOrWorldCannotCancelOrRestore(){for(var observation:new WormCleanupAuthority.Observation[]{o(true,false,true,true,true,true),o(false,true,true,true,true,true)}){var result=WormCleanupAuthority.decide(observation);assertFalse(result.cancelCamera());assertFalse(result.restoreRod());}}
    @Test void manualSlotOrComponentMutationNeverRestores(){for(var observation:new WormCleanupAuthority.Observation[]{o(true,true,true,true,false,true),o(true,true,true,true,true,false)}){var result=WormCleanupAuthority.decide(observation);assertTrue(result.cancelCamera());assertFalse(result.restoreRod());}}
    @Test void menuRefusalCancelsOwnCameraButCannotManipulateInventory(){var result=WormCleanupAuthority.decide(o(true,true,true,false,true,true));assertTrue(result.cancelCamera());assertFalse(result.restoreRod());}
    @Test void replacedCameraEpochIsNotCancelled(){var result=WormCleanupAuthority.decide(o(true,true,false,true,true,true));assertFalse(result.cancelCamera());assertTrue(result.restoreRod());}
    @Test void offThreadCannotMutateAnything(){var result=WormCleanupAuthority.decide(new WormCleanupAuthority.Observation(false,true,true,true,true,true,true,true,true,true,true,true));assertFalse(result.cancelCamera());assertFalse(result.restoreRod());}
}
