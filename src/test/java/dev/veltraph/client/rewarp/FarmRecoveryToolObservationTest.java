package dev.veltraph.client.rewarp;
import dev.veltraph.client.config.*;
import dev.veltraph.client.farming.module.*;
import dev.veltraph.client.farming.domain.FarmLayoutRegistry;
import dev.veltraph.client.service.FarmServices;
import dev.veltraph.client.service.stub.NoOpRotationService;
import dev.veltraph.client.testutil.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
final class FarmRecoveryToolObservationTest {
    private record Fixture(CactusFarmModule farm,FakeInventoryService inventory) {}
    private Fixture selected(){
        var inventory=new FakeInventoryService().setItem(6,"Cactus Knife");var config=new MapConfigService();config.set(FarmingConfig.REWARP_REQUIRED,false);
        var farm=new CactusFarmModule(new FarmServices(new FakeClock(),new FakePlayerService(),new FakeWorldService(),new RecordingInputService(),new NoOpRotationService(),new RecordingBlockBreaking(),inventory),config,FarmLayoutRegistry.createDefault());
        assertTrue(farm.preStartCheck().isEmpty());farm.setEnabled(true);return new Fixture(farm,inventory);
    }
    @Test void passivePausedObservationPreservesManualSlotAndRejectsReadiness() {
        var f=selected();f.farm.suspendForRewarp();f.inventory.selected=1;int writes=f.inventory.selectCalls;
        assertFalse(FarmRecoveryToolObservation.ready(false,f.farm::selectedFarmingToolReady,f.farm::toolReadyForRewarp));
        assertEquals(1,f.inventory.selectedSlot());assertEquals(writes,f.inventory.selectCalls);
    }
    @Test void passiveHealthyObservationRequiresSelectedOriginalToolWithoutWriting() {
        var f=selected();int writes=f.inventory.selectCalls;
        assertTrue(FarmRecoveryToolObservation.ready(false,f.farm::selectedFarmingToolReady,f.farm::toolReadyForRewarp));
        assertEquals(writes,f.inventory.selectCalls);f.inventory.selected=1;
        assertFalse(FarmRecoveryToolObservation.ready(false,f.farm::selectedFarmingToolReady,f.farm::toolReadyForRewarp));assertEquals(1,f.inventory.selectedSlot());
    }
    @Test void actuallyOwnedRecoveryRetainsOriginalToolRepair() {
        var f=selected();f.farm.suspendForRewarp();f.inventory.selected=1;int writes=f.inventory.selectCalls;
        assertTrue(FarmRecoveryToolObservation.ready(true,f.farm::selectedFarmingToolReady,f.farm::toolReadyForRewarp));
        assertEquals(6,f.inventory.selectedSlot());assertEquals(writes+1,f.inventory.selectCalls);
    }
    @Test void ownedRecoveryDoesNotSelectRemovedOrReplacedTool() {
        for(String replacement:new String[]{"Stone",""}){var f=selected();f.farm.suspendForRewarp();f.inventory.selected=1;f.inventory.setItem(6,replacement);int writes=f.inventory.selectCalls;
            assertFalse(FarmRecoveryToolObservation.ready(true,f.farm::selectedFarmingToolReady,f.farm::toolReadyForRewarp));assertEquals(1,f.inventory.selectedSlot());assertEquals(writes,f.inventory.selectCalls);}
    }
    @Test void maintenanceLeaseDoesNotYieldToolCustodyToRecovery() {
        var f=selected();var token=new Object();assertTrue(f.farm.suspendForMaintenance(token));f.inventory.selected=1;int writes=f.inventory.selectCalls;
        assertFalse(FarmRecoveryToolObservation.ready(true,f.farm::selectedFarmingToolReady,f.farm::toolReadyForRewarp));assertEquals(1,f.inventory.selectedSlot());assertEquals(writes,f.inventory.selectCalls);
    }
}
