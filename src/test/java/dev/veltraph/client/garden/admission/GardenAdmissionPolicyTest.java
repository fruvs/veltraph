package dev.veltraph.client.garden.admission;
import dev.veltraph.client.config.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class GardenAdmissionPolicyTest {
    @Test void exactStoreAndUnchangedPolicyAreRequired(){var c=new MapConfigService();var p=GardenAdmissionPolicy.capture(c).orElseThrow();assertTrue(p.current(c));assertFalse(p.current(new MapConfigService()));}
    @Test void changeAndRevertStillRevokesPendingAdmission(){var c=new MapConfigService();var p=GardenAdmissionPolicy.capture(c).orElseThrow();c.set(GardenAdmissionConfig.AUTOSTART_PESTS_DURING_FARMING,false);c.reset(GardenAdmissionConfig.AUTOSTART_PESTS_DURING_FARMING);assertFalse(p.current(c));}
    @Test void equalWriteDoesNotInventRevocation(){var c=new MapConfigService();var p=GardenAdmissionPolicy.capture(c).orElseThrow();c.set(GardenAdmissionConfig.AUTOSTART_PESTS_DURING_FARMING,true);assertTrue(p.current(c));}
    @Test void rowMappingAndThresholdMutationRevoke(){var c=new MapConfigService();var p=GardenAdmissionPolicy.capture(c).orElseThrow();c.set(GardenAdmissionConfig.GARDEN_ADMISSION_BINDINGS,"different");assertFalse(p.current(c));p=GardenAdmissionPolicy.capture(c).orElseThrow();c.set(GardenAdmissionConfig.ACTIVATE_VISITOR_THRESHOLD,"4:5");assertFalse(p.current(c));}
}
