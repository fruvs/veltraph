package dev.veltraph.client.farming.module;

import dev.veltraph.client.config.*;
import dev.veltraph.client.farming.domain.*;
import dev.veltraph.client.service.*;
import dev.veltraph.client.testutil.*;
import dev.veltraph.client.world.Vec3;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.ArrayList;
import static org.junit.jupiter.api.Assertions.*;

/** Actual six-family caller regressions; these pure adapters confer no native permission. */
class FarmOrdinaryToolReadinessTest {
    static final class Camera implements RotationService {
        int aims;float yaw,pitch;
        public float yaw(){return yaw;}public float pitch(){return pitch;}
        public void aim(float y,float p){aims++;yaw=y;pitch=p;}
        public void lookAt(Vec3 p){aims++;}
    }
    static final class Inventory implements InventoryService {
        final List<String> names=new ArrayList<>(java.util.Collections.nCopies(9,""));
        int selected,selections;boolean failSelection;Runnable afterSelection;
        public int selectedSlot(){return selected;}
        public List<String> hotbarItemNames(){return List.copyOf(names);}
        public void selectSlot(int slot){selections++;if(!failSelection)selected=slot;if(afterSelection!=null)afterSelection.run();}
    }
    record Rig(FarmModule farm,Inventory inventory,Camera camera,RecordingInputService input,RecordingBlockBreaking breaking) {}
    Rig start(int family) {
        var inv=new Inventory();inv.names.set(3,"original farming tool");
        var camera=new Camera();var input=new RecordingInputService();var breaking=new RecordingBlockBreaking();
        var config=new MapConfigService();config.set(FarmingConfig.REWARP_REQUIRED,false);config.set(FarmingConfig.FARMING_TOOL_SLOT,3);
        var services=new FarmServices(new FakeClock(),new FakePlayerService(),new FakeWorldService(),input,camera,breaking,inv);
        var registry=FarmLayoutRegistry.createDefault();FarmModule farm=switch(family){
            case 0->new WartCropsFarmModule(services,config,registry);
            case 1->new PumpkinMelonFarmModule(services,config,registry);
            case 2->new MushroomFarmModule(services,config,registry);
            case 3->new CocoaFarmModule(services,config,registry);
            case 4->new SugarcaneFlowersFarmModule(services,config,registry);
            case 5->new CactusFarmModule(services,config,registry);
            default->throw new AssertionError();
        };
        assertTrue(farm.preStartCheck().isEmpty());farm.setEnabled(true);return new Rig(farm,inv,camera,input,breaking);
    }
    void rejected(Rig r) {
        int selections=r.inventory.selections,aims=r.camera.aims;
        r.input.forward=true;r.input.sprint=true;r.breaking.breaking=true;
        var state=r.farm.maintenanceProgress();
        r.farm.onTick();
        assertEquals(selections,r.inventory.selections,r.farm.id()+" invalid cached slot must not be selected");
        assertEquals(aims,r.camera.aims,r.farm.id()+" tool refusal precedes family aim");
        assertFalse(r.input.forward);assertFalse(r.input.sprint);assertFalse(r.breaking.breaking);
        assertEquals(state,r.farm.maintenanceProgress(),r.farm.id()+" ordinary refusal preserves the engine checkpoint");
        assertTrue(r.farm.isEnabled(),"tool wait does not silently replace/restart the family");
    }
    @Test void missingOriginalToolStopsBeforeEveryFamilyAimAndSelection(){
        for(int family=0;family<6;family++){var r=start(family);r.inventory.names.set(3,"");r.inventory.selected=0;rejected(r);}
    }
    @Test void swappedOriginalToolStopsBeforeEveryFamilyEffect(){
        for(int family=0;family<6;family++){var r=start(family);r.inventory.names.set(3,"unrelated stone");r.inventory.selected=0;rejected(r);}
    }
    @Test void renamedOriginalToolRequiresNewAdmissionForEveryFamily(){
        for(int family=0;family<6;family++){var r=start(family);r.inventory.names.set(3,"renamed farming tool");rejected(r);}
    }
    @Test void MovingToolToAnotherSlotDoesNotAdoptTheNewLocation(){
        for(int family=0;family<6;family++){var r=start(family);r.inventory.names.set(4,r.inventory.names.get(3));r.inventory.names.set(3,"");r.inventory.selected=4;rejected(r);assertEquals(4,r.inventory.selected);}
    }
    @Test void selectedSlotDriftCanRestoreOnlyTheStillPresentOriginalTool(){
        for(int family=0;family<6;family++){var r=start(family);int before=r.inventory.selections;r.inventory.selected=7;
            assertTrue(r.farm.toolReady());assertEquals(3,r.inventory.selected);assertEquals(before+1,r.inventory.selections);
            assertTrue(r.farm.toolReady());assertEquals(before+1,r.inventory.selections,"no repeated slot packet when already selected");}
    }
    @Test void nativeOrFakeSelectionRefusalDoesNotReportReady(){
        var r=start(2);r.inventory.selected=8;r.inventory.failSelection=true;
        assertFalse(r.farm.toolReady());assertEquals(8,r.inventory.selected);
    }
    @Test void itemReplacementDuringSelectionIsRecheckedBeforeTheFamilyRuns(){
        var r=start(1);r.inventory.selected=7;r.inventory.afterSelection=()->r.inventory.names.set(3,"replacement");
        int before=r.camera.aims;r.farm.onTick();assertEquals(before,r.camera.aims);assertFalse(r.breaking.breaking);
    }
    @Test void restoredActualOriginalToolResumesTheExistingFamilyWithoutASecondStart(){
        var r=start(0);r.inventory.names.set(3,"");rejected(r);r.inventory.names.set(3,"original farming tool");
        var state=r.farm.maintenanceProgress();assertTrue(r.farm.toolReady());assertEquals(state,r.farm.maintenanceProgress());r.farm.onTick();
        assertTrue(r.farm.isEnabled());assertEquals(3,r.inventory.selected);
    }
    @Test void maintenanceSuspensionDoesNotInspectOrReselectFarmTools(){
        var r=start(3);Object token=new Object();assertTrue(r.farm.suspendForMaintenance(token));
        r.inventory.selected=7;r.inventory.names.set(3,"");int calls=r.inventory.selections,release=r.input.releaseAllCount;
        r.farm.onTick();assertEquals(calls,r.inventory.selections);assertEquals(release,r.input.releaseAllCount,"helper inputs are not erased");
    }
    @Test void cycleAndRewarpSuspensionDoNotRunFamilyToolSelection(){
        var r=start(2);assertTrue(r.farm.suspendForCycleReset());r.inventory.selected=7;int calls=r.inventory.selections;
        r.farm.onTick();assertEquals(calls,r.inventory.selections);assertTrue(r.farm.isSuspended());
        r=start(4);r.farm.suspendForRewarp();r.inventory.selected=7;calls=r.inventory.selections;r.farm.onTick();assertEquals(calls,r.inventory.selections);
    }
    @Test void manualDisableStillRetiresTheSameFamilyAndReleasesItsInputs(){
        var r=start(5);r.input.left=true;r.breaking.breaking=true;r.inventory.names.set(3,"");r.farm.setEnabled(false);
        assertFalse(r.farm.isEnabled());assertFalse(r.input.left);assertFalse(r.breaking.breaking);
    }
    @Test void publicPureServiceCannotCreateNativeItemPermission(){
        var r=start(2);assertFalse(r.farm.nativeSelectedFarmingToolCurrent(null));assertFalse(r.farm.usesNativeFarmingInventory());
    }
    @Test void enabledNormalCycleRetainsOriginalToolWithoutFreshStartOrSelection(){
        var r=start(0);int selections=r.inventory.selections;assertTrue(r.farm.suspendForCycleReset());
        assertTrue(r.farm.retainedCycleToolReady());assertEquals(selections,r.inventory.selections);
        assertTrue(r.farm.restartAfterCycleReset());assertTrue(r.farm.isEnabled());assertFalse(r.farm.isSuspended());
        assertEquals(selections,r.inventory.selections,"cycle restart does not select or replace the original handout");
    }
    @Test void normalCycleRefusesMissingOrMovedOriginalToolWithoutRepair(){
        var r=start(1);assertTrue(r.farm.suspendForCycleReset());r.inventory.names.set(3,"");r.inventory.names.set(4,"original farming tool");
        int before=r.inventory.selections;assertFalse(r.farm.retainedCycleToolReady());assertEquals(before,r.inventory.selections);
    }
    @Test void normalCycleRefusesChangedToolConfigurationCheckpoint(){
        var r=start(2);assertTrue(r.farm.suspendForCycleReset());r.farm.config.set(FarmingConfig.FARMING_TOOL_SLOT,4);
        assertFalse(r.farm.retainedCycleToolReady());assertEquals(3,r.inventory.selected);
    }
    @Test void ordinaryOrInterruptedRewarpCannotPretendToBeNormalCycleReadiness(){
        var r=start(3);assertFalse(r.farm.retainedCycleToolReady());r.farm.suspendForRewarp();assertFalse(r.farm.retainedCycleToolReady());
    }
    @Test void cycleReadinessRefusesWrongSelectedSlotAndPreservesIt(){
        var r=start(4);assertTrue(r.farm.suspendForCycleReset());r.inventory.selected=8;int before=r.inventory.selections;
        assertFalse(r.farm.retainedCycleToolReady());assertEquals(8,r.inventory.selected);assertEquals(before,r.inventory.selections);
    }
}
