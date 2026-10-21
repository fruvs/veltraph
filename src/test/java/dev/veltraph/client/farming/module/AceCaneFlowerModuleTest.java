package dev.veltraph.client.farming.module;

import dev.veltraph.client.config.*;
import dev.veltraph.client.farming.domain.FarmLayoutRegistry;
import dev.veltraph.client.farming.movement.planner.AceCaneFlowerPlanner;
import dev.veltraph.client.service.*;
import dev.veltraph.client.service.stub.NoOpInventoryService;
import dev.veltraph.client.testutil.*;
import dev.veltraph.client.world.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.function.Predicate;
import static org.junit.jupiter.api.Assertions.*;

class AceCaneFlowerModuleTest {
    static final class World extends FakeWorldService {
        Map<BlockPos,String> blocks=new HashMap<>(); boolean loaded=true,blocked;
        @Override public boolean isLoaded(BlockPos p){return loaded;}
        @Override public String blockId(BlockPos p){return blocks.getOrDefault(p,"minecraft:air");}
        @Override public boolean bodyClear(Vec3 p,double width,double height){return !blocked;}
    }
    static final class Breaking implements BlockBreakingService {
        boolean held; Predicate<BlockPos> guard; BlockPos target=new BlockPos(0,65,1);
        public void setBreaking(boolean b){held=b;}
        public boolean isBreaking(){return held;}
        public Optional<BlockPos> targetBlockPos(){return Optional.of(target);}
        public void installTargetGuard(Object owner,Predicate<BlockPos> p){guard=p;}
        public void clearTargetGuard(Object owner){guard=null;}
    }
    static final class Fixture {
        World world=new World(); FakePlayerService player=new FakePlayerService();
        RecordingInputService input=new RecordingInputService(); Breaking breaking=new Breaking();
        FakeRotationService rotation=new FakeRotationService(45,0);
        SugarcaneFlowersFarmModule module;
        Fixture(SugarcaneFlowersFarmModule.Crop crop) {
            player.position=new Vec3(.5,64,.5); player.blockPos=new BlockPos(0,64,0);player.onGround=true;
            module=new SugarcaneFlowersFarmModule(new FarmServices(new FakeClock(),player,world,input,rotation,breaking,new NoOpInventoryService()),new MapConfigService(),FarmLayoutRegistry.createDefault());
            module.setDesign(SugarcaneFlowersFarmModule.Design.ACE_TWO_PLOT); module.setCrop(crop);module.setEnabled(true);
        }
    }
    @Test void exactDiagonalKeepsCreatorChordAndSprint() {
        var f=new Fixture(SugarcaneFlowersFarmModule.Crop.CANE); f.module.onTick();
        assertEquals(45,f.rotation.yaw(),.01); assertTrue(f.input.forward);assertTrue(f.input.right);assertTrue(f.input.sprint);
        assertFalse(f.input.left);assertFalse(f.input.back);
    }
    @Test void sunflowerUpperRequiresMatchingLowerAndLoadedServerCells() {
        var f=new Fixture(SugarcaneFlowersFarmModule.Crop.SUNFLOWER);
        f.world.blocks.put(f.breaking.target,"minecraft:sunflower"); f.world.blocks.put(f.breaking.target.down(),"minecraft:sunflower");
        f.module.onTick();assertTrue(f.breaking.held);assertTrue(f.breaking.guard.test(f.breaking.target));
        f.world.blocks.put(f.breaking.target.down(),"minecraft:rose_bush");f.module.onTick();assertFalse(f.breaking.held);
        f.world.loaded=false;f.module.onTick();assertFalse(f.input.forward);assertFalse(f.input.sprint);assertFalse(f.breaking.guard.test(f.breaking.target));
    }
    @Test void stillnessWithoutLoadedBodyCollisionDoesNotTurn() {
        var f=new Fixture(SugarcaneFlowersFarmModule.Crop.CANE);
        f.world.blocks.put(new BlockPos(0,64,1),"minecraft:white_carpet");f.module.onTick();
        f.world.blocks.clear();for(int i=0;i<20;i++)f.module.onTick();
        assertEquals(AceCaneFlowerPlanner.Phase.ROW_LEFT,f.module.acePhase());
        f.world.blocked=true;f.module.onTick();assertEquals(AceCaneFlowerPlanner.Phase.ROW_CHORD,f.module.acePhase());
    }
    @Test void localAuthorWallsChooseReturnAndLeftStartsWithoutFixedCoordinates() {
        var f=new Fixture(SugarcaneFlowersFarmModule.Crop.CANE);f.module.setEnabled(false);
        f.world.blocks.put(new BlockPos(-1,64,0),"minecraft:white_stained_glass");f.world.blocks.put(new BlockPos(1,64,0),"minecraft:white_stained_glass");
        f.module.setEnabled(true);assertEquals(AceCaneFlowerPlanner.Phase.LOOP_CHORD,f.module.acePhase());f.module.setEnabled(false);
        f.world.blocks.clear();f.world.blocks.put(new BlockPos(0,64,-1),"minecraft:white_stained_glass");
        f.module.setEnabled(true);assertEquals(AceCaneFlowerPlanner.Phase.LOOP_EXIT,f.module.acePhase());f.module.setEnabled(false);
        f.world.blocks.clear();f.world.blocks.put(new BlockPos(0,64,1),"minecraft:white_stained_glass");
        f.module.setEnabled(true);assertEquals(AceCaneFlowerPlanner.Phase.ROW_LEFT,f.module.acePhase());
    }
    @Test void adjacentSoulLaneDisambiguatesNormalNorthernEdgeFromReturn() {
        var f=new Fixture(SugarcaneFlowersFarmModule.Crop.CANE);f.module.setEnabled(false);
        f.world.blocks.put(new BlockPos(0,64,-1),"minecraft:white_stained_glass");f.world.blocks.put(new BlockPos(0,63,1),"minecraft:soul_sand");
        f.module.setEnabled(true);assertEquals(AceCaneFlowerPlanner.Phase.ROW_CHORD,f.module.acePhase());
    }
    @Test void boundSelectionChoosesFlowerToolBeforeStartAndStaysCapturedWhileActive() {
        var f=new Fixture(SugarcaneFlowersFarmModule.Crop.CANE);f.module.setEnabled(false);
        var selection=new java.util.concurrent.atomic.AtomicReference<>(new SugarcaneFlowersFarmModule.Selection(SugarcaneFlowersFarmModule.Design.ACE_TWO_PLOT,SugarcaneFlowersFarmModule.Crop.SUNFLOWER));
        f.module.bindSelection(selection::get);assertEquals(SugarcaneFlowersFarmModule.Crop.SUNFLOWER,f.module.crop());
        assertTrue(f.module.toolKeywords().contains("flower"));
        f.module.configurationReadinessError();
        selection.set(new SugarcaneFlowersFarmModule.Selection(SugarcaneFlowersFarmModule.Design.RECOVERED,SugarcaneFlowersFarmModule.Crop.CANE));
        // A stopped profile/HUD read may reflect new config; the validated start retains its tool choice.
        f.module.activeProfile();assertEquals(SugarcaneFlowersFarmModule.Crop.CANE,f.module.crop());
        assertTrue(f.module.toolKeywords().contains("flower"));
        f.module.setEnabled(true);assertEquals(SugarcaneFlowersFarmModule.Design.ACE_TWO_PLOT,f.module.design());
        assertEquals(SugarcaneFlowersFarmModule.Crop.SUNFLOWER,f.module.crop());
        assertThrows(IllegalStateException.class,()->f.module.bindSelection(selection::get));
        f.module.onTick();assertTrue(f.input.forward);assertTrue(f.input.right);
        f.module.setEnabled(false);assertEquals(SugarcaneFlowersFarmModule.Design.RECOVERED,f.module.design());
        f.module.configurationReadinessError();f.module.setEnabled(true);
        assertEquals(SugarcaneFlowersFarmModule.Crop.CANE,f.module.crop());
    }
    @Test void finalPreStartSelectsActualFlowerSlotRatherThanCaneSlot() {
        var f=new Fixture(SugarcaneFlowersFarmModule.Crop.CANE);f.module.setEnabled(false);
        class Inventory implements InventoryService {
            int slot;
            public int selectedSlot(){return slot;}
            public void selectSlot(int value){slot=value;}
            public List<String> hotbarItemNames(){return List.of("cane tool","flower cutter","","","","","","","");}
        }
        var inventory=new Inventory();var config=new MapConfigService();config.set(FarmingConfig.REWARP_REQUIRED,false);
        var m=new SugarcaneFlowersFarmModule(new FarmServices(new FakeClock(),f.player,f.world,f.input,f.rotation,f.breaking,inventory),config,FarmLayoutRegistry.createDefault());
        var selection=new java.util.concurrent.atomic.AtomicReference<>(new SugarcaneFlowersFarmModule.Selection(SugarcaneFlowersFarmModule.Design.ACE_TWO_PLOT,SugarcaneFlowersFarmModule.Crop.SUNFLOWER));
        m.bindSelection(selection::get);assertTrue(m.preStartCheck().isEmpty());assertEquals(1,inventory.selectedSlot());
        selection.set(new SugarcaneFlowersFarmModule.Selection(SugarcaneFlowersFarmModule.Design.RECOVERED,SugarcaneFlowersFarmModule.Crop.CANE));
        m.setEnabled(true);assertEquals(SugarcaneFlowersFarmModule.Crop.SUNFLOWER,m.crop());
        m.setEnabled(false);assertTrue(m.preStartCheck().isEmpty());assertEquals(0,inventory.selectedSlot());
        m.setEnabled(true);assertEquals(SugarcaneFlowersFarmModule.Design.RECOVERED,m.design());
    }
    @Test void unsupportedRecoveredFlowerIsRejectedWithoutChangingPersistedSelection() {
        var f=new Fixture(SugarcaneFlowersFarmModule.Crop.CANE);f.module.setEnabled(false);
        var selection=new java.util.concurrent.atomic.AtomicReference<>(new SugarcaneFlowersFarmModule.Selection(SugarcaneFlowersFarmModule.Design.RECOVERED,SugarcaneFlowersFarmModule.Crop.ROSE_BUSH));
        f.module.bindSelection(selection::get);
        assertTrue(f.module.preStartCheck().orElseThrow().contains("Ace two-plot"));
        assertEquals(SugarcaneFlowersFarmModule.Design.RECOVERED,f.module.design());
        assertEquals(SugarcaneFlowersFarmModule.Crop.ROSE_BUSH,f.module.crop());
        assertEquals(SugarcaneFlowersFarmModule.Crop.ROSE_BUSH,selection.get().crop());
        selection.set(new SugarcaneFlowersFarmModule.Selection(SugarcaneFlowersFarmModule.Design.ACE_TWO_PLOT,SugarcaneFlowersFarmModule.Crop.ROSE_BUSH));
        assertTrue(f.module.configurationReadinessError().isEmpty());
    }
    @Test void cycleIdentityUsesPreparedAndActiveEngineThenChangesOnRestart() {
        var f=new Fixture(SugarcaneFlowersFarmModule.Crop.CANE);f.module.setEnabled(false);
        var flower=new SugarcaneFlowersFarmModule.Selection(SugarcaneFlowersFarmModule.Design.ACE_TWO_PLOT,SugarcaneFlowersFarmModule.Crop.SUNFLOWER);
        var cane=new SugarcaneFlowersFarmModule.Selection(SugarcaneFlowersFarmModule.Design.RECOVERED,SugarcaneFlowersFarmModule.Crop.CANE);
        var selection=new java.util.concurrent.atomic.AtomicReference<>(flower);f.module.bindSelection(selection::get);
        assertEquals(flower,f.module.capturedSelection());f.module.configurationReadinessError();selection.set(cane);
        f.module.activeProfile();assertEquals(SugarcaneFlowersFarmModule.Crop.CANE,f.module.crop());
        assertEquals(flower,f.module.capturedSelection(),"Prepared identity follows the tool-validated engine, not a newer stopped HUD value");
        f.module.setEnabled(true);for(int i=0;i<5;i++){f.module.onTick();assertEquals(flower,f.module.capturedSelection());}
        f.module.setEnabled(false);assertEquals(cane,f.module.capturedSelection());assertNotEquals(flower,f.module.capturedSelection());
        f.module.configurationReadinessError();f.module.setEnabled(true);assertEquals(cane,f.module.capturedSelection());
    }
    @Test void unboundDefaultAndDistinctCropIdentitiesRemainExplicit() {
        var f=new Fixture(SugarcaneFlowersFarmModule.Crop.CANE);f.module.setEnabled(false);
        f.module.setDesign(SugarcaneFlowersFarmModule.Design.RECOVERED);
        assertEquals(new SugarcaneFlowersFarmModule.Selection(SugarcaneFlowersFarmModule.Design.RECOVERED,SugarcaneFlowersFarmModule.Crop.CANE),f.module.capturedSelection());
        f.module.setDesign(SugarcaneFlowersFarmModule.Design.ACE_TWO_PLOT);var cane=f.module.capturedSelection();
        f.module.setCrop(SugarcaneFlowersFarmModule.Crop.SUNFLOWER);assertNotEquals(cane,f.module.capturedSelection());
    }
    @Test void pauseRearmsOnceAndDisableReleasesSprintGuardAndKeys() {
        var f=new Fixture(SugarcaneFlowersFarmModule.Crop.CANE);f.module.onTick();
        f.module.suspendForRewarp();f.module.onTick();assertFalse(f.input.sprint);assertFalse(f.input.forward);
        f.module.resumeAfterRewarp();f.module.onTick();assertTrue(f.input.forward);
        f.world.blocks.put(new BlockPos(0,64,1),"minecraft:white_carpet");f.module.onTick();
        assertEquals(AceCaneFlowerPlanner.Phase.ROW_LEFT,f.module.acePhase());f.module.resumeAfterRewarp();
        assertEquals(AceCaneFlowerPlanner.Phase.ROW_LEFT,f.module.acePhase());
        assertThrows(IllegalStateException.class,()->f.module.setCrop(SugarcaneFlowersFarmModule.Crop.ROSE_BUSH));
        f.module.setEnabled(false);assertNull(f.breaking.guard);assertFalse(f.input.forward);assertFalse(f.input.left);assertFalse(f.input.sprint);
    }
}
