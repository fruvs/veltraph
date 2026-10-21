package dev.veltraph.client.farming.module;

import dev.veltraph.client.config.*;
import dev.veltraph.client.farming.domain.FarmLayoutRegistry;
import dev.veltraph.client.farming.movement.TurnDirection;
import dev.veltraph.client.farming.movement.Angles;
import dev.veltraph.client.farming.movement.planner.PumpkinOnEndRowPlanner;
import dev.veltraph.client.core.MacroState;
import dev.veltraph.client.service.*;
import dev.veltraph.client.service.stub.NoOpInventoryService;
import dev.veltraph.client.testutil.*;
import dev.veltraph.client.world.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Real two-column stem/slab wall semantics; no global-walkable cardinal-lane acceptance. */
class PumpkinMelonFarmModuleTest {
    private FakeClock clock;private FakePlayerService player;private FakeRotationService rotation;
    private RecordingInputService input;private TargetBreaking breaking;private MapConfigService config;
    private WorldService world;
    private PumpkinMelonFarmModule module;private final Map<BlockPos,String> blocks=new HashMap<>();
    private final Set<BlockPos> unloaded=new HashSet<>();
    private static final class TargetBreaking implements BlockBreakingService {
        boolean breaking;String id="minecraft:carved_pumpkin";BlockPos target=new BlockPos(1,64,1);
        public void setBreaking(boolean value){breaking=value;}public boolean isBreaking(){return breaking;}
        public String targetBlockId(){return id;}public Optional<BlockPos> targetBlockPos(){return Optional.ofNullable(target);}
    }
    @BeforeEach void setup(){
        clock=new FakeClock();player=new FakePlayerService();player.onGround=true;
        player.position=new Vec3(1.5,64,.5);player.blockPos=new BlockPos(1,64,0);
        rotation=new FakeRotationService();rotation.yaw=35;rotation.pitch=40;
        input=new RecordingInputService();breaking=new TargetBreaking();config=new MapConfigService();
        config.set(FarmingConfig.PUMPKIN_FARM_DESIGN,PumpkinFarmDesign.CAPTURED_DEFAULT);config.set(FarmingConfig.REWARP_REQUIRED,false);
        blocks.clear();unloaded.clear();
        for(int z=0;z<=20;z++){
            for(int x=0;x<=1;x++){
                blocks.put(new BlockPos(x,63,z),"minecraft:dirt");
                if(z>0)blocks.put(new BlockPos(x,64,z),"minecraft:carved_pumpkin");
            }
            for(int x:new int[]{-1,2}){
                blocks.put(new BlockPos(x,63,z),"minecraft:farmland");
                blocks.put(new BlockPos(x,64,z),z==0?"minecraft:sandstone":"minecraft:pumpkin_stem");
                blocks.put(new BlockPos(x,65,z),"minecraft:sandstone_slab");
            }
        }
        world=new WorldService(){
            public boolean isLoaded(BlockPos p){return !unloaded.contains(p);}
            public String blockId(BlockPos p){return blocks.getOrDefault(p,"minecraft:air");}
            public boolean isPassable(BlockPos p){String id=blockId(p);return id.equals("minecraft:air")||id.endsWith("_stem");}
            public boolean isStandable(BlockPos p){return !isPassable(p);}
        };
        module=new PumpkinMelonFarmModule(new FarmServices(clock,player,world,input,rotation,breaking,new NoOpInventoryService()),config,FarmLayoutRegistry.createDefault());
    }
    @Test void actualWallAndCropBandChooseDiagonalPressureRatherThanCardinalForward(){
        module.setEnabled(true);module.onTick();
        assertEquals(Direction.SOUTH,module.heading());assertEquals(TurnDirection.LEFT,module.laneSide());
        assertTrue(input.forward&&input.left);assertFalse(input.right||input.jump||input.sneak||input.sprint);
        assertTrue(breaking.breaking);assertEquals(35,rotation.yaw,.01);assertEquals(40,rotation.pitch,.01);
        assertTrue(module.diagnostics().contains("wall=EAST"));
    }
    @Test void cameraApproachIsSmoothAndMovementWaitsForDiagonalAlignment(){
        rotation.yaw=0;rotation.pitch=0;module.setEnabled(true);
        assertTrue(module.diagnostics().contains("state=ALIGNING"));
        for(int tick=0;tick<40;tick++){
            float yaw=rotation.yaw,pitch=rotation.pitch;advanceTick();
            assertTrue(Math.abs(Angles.signedDelta(yaw,rotation.yaw))<=9.15,"Yaw exceeds configured maximum plus mouse-step rounding");
            assertTrue(Math.abs(rotation.pitch-pitch)<=7.15,"Pitch exceeds configured maximum plus mouse-step rounding");
            if(Math.max(Math.abs(Angles.signedDelta(rotation.yaw,35)),Math.abs(rotation.pitch-40))>3){
                assertFalse(input.forward||input.left||input.right||breaking.breaking,"Movement/attack began before diagonal alignment");
                assertTrue(module.diagnostics().contains("state=ALIGNING"));
            }
        }
        assertEquals(35,rotation.yaw,.75);assertEquals(40,rotation.pitch,.75);
        assertEquals(PumpkinOnEndRowPlanner.PumpkinRowState.FARMING,module.rowState());
        assertTrue(input.forward&&input.left);assertTrue(breaking.breaking);
    }
    @Test void defaultCapturedPitchIsSeparateFromCommonAndInvalidValuesReleaseOnlyControlledInputs(){
        config.set(FarmingConfig.FARM_AIM_PITCH,0);assertEquals(40,config.get(FarmingConfig.PUMPKIN_CAPTURED_PITCH),.001);
        module.setEnabled(true);module.onTick();assertTrue(input.forward&&input.left);
        for(double value:new double[]{Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY,-91,91}){
            config.set(FarmingConfig.PUMPKIN_CAPTURED_PITCH,value);
            assertTrue(module.preStartCheck().orElseThrow().contains("Captured Default Gourd Pitch"));
            module.onTick();assertFalse(input.forward||input.left||input.right||breaking.breaking);
        }
    }
    @Test void optInAntiGhostCadenceReleasesAttackWithoutPulsingDiagonalMovement(){
        config.set(FarmingConfig.PUMPKIN_ANTI_GHOST_BLOCK,true);module.setEnabled(true);
        clock.setTick(0);module.onTick();assertFalse(breaking.breaking);assertTrue(input.forward&&input.left);
        clock.setTick(1);module.onTick();assertTrue(breaking.breaking);assertTrue(input.forward&&input.left);
    }
    @Test void antiGhostDisabledKeepsAllowedCropAttackHeld(){
        module.setEnabled(true);clock.setTick(0);module.onTick();assertTrue(breaking.breaking);
    }
    @Test void decorativeHeadAndStemAreNeverAuthorizedByCarvedNameAlone(){
        module.setEnabled(true);advanceTick();assertTrue(breaking.breaking);
        // A formerly valid crop must lose permission immediately when its bed changes.
        blocks.put(breaking.target.down(),"minecraft:stone");advanceTick();assertFalse(breaking.breaking);
        blocks.put(breaking.target.down(),"minecraft:dirt");advanceTick();assertTrue(breaking.breaking);
        breaking.target=new BlockPos(5,64,1);blocks.put(breaking.target,"minecraft:carved_pumpkin");blocks.put(breaking.target.down(),"minecraft:stone");
        advanceTick();assertFalse(breaking.breaking);
        for(String id:List.of("minecraft:pumpkin_stem","minecraft:dirt","minecraft:sandstone_slab","minecraft:air")){
            breaking.id=id;breaking.target=new BlockPos(5,64,1);blocks.put(breaking.target,id);advanceTick();
            assertFalse(breaking.breaking,"Structural crosshair must not authorize attack: "+id);
            assertEquals(PumpkinOnEndRowPlanner.PumpkinRowState.FARMING,module.rowState());
        }
    }
    @Test void unknownSupportAndAirbornePosePauseButDoNotManufactureMovement(){
        module.setEnabled(true);module.onTick();assertTrue(input.forward);
        unloaded.add(new BlockPos(1,63,1));module.onTick();assertFalse(input.forward||input.left||input.right||breaking.breaking);
        unloaded.clear();player.onGround=false;rotation.yaw=30;
        int before=rotation.aimCalls;float priorYaw=rotation.yaw;advanceTick();
        assertFalse(input.forward||input.left||input.right||breaking.breaking);assertTrue(rotation.aimCalls>before,"Airborne gating must preserve aim tracking");
        assertTrue(Math.abs(Angles.signedDelta(priorYaw,rotation.yaw))<=9.15);
    }
    @Test void disableReleasesEntireOwnedVector(){
        module.setEnabled(true);module.onTick();module.setEnabled(false);
        assertEquals(MacroState.STOPPED,module.state());assertTrue(input.releaseAllCount>0);
        assertReleased();
    }
    @Test void startupAlignmentIsDistinctFromFarmingAndCompletesThroughTicks(){
        rotation.yaw=10;rotation.pitch=15;module.setEnabled(true);
        assertEquals(MacroState.RUNNING,module.state());assertTrue(module.diagnostics().contains("state=ALIGNING"));assertReleased();
        advanceTick();assertTrue(module.diagnostics().contains("state=ALIGNING"));assertReleased();
        for(int tick=0;tick<40;tick++)advanceTick();
        assertTrue(module.diagnostics().contains("state=FARMING"));
        assertEquals(PumpkinOnEndRowPlanner.PumpkinRowState.FARMING,module.rowState());
        assertEquals(35,rotation.yaw,.75);assertEquals(40,rotation.pitch,.75);assertTrue(input.forward&&input.left&&breaking.breaking);
    }
    @Test void namedToolReadinessBlocksEmptyHotbarAndReassertsSelectedSlot(){
        var names=new ArrayList<>(Collections.nCopies(9,""));int[] selected={0};
        InventoryService inventory=new InventoryService(){
            public int selectedSlot(){return selected[0];}public void selectSlot(int slot){selected[0]=slot;}
            public List<String> hotbarItemNames(){return List.copyOf(names);}
        };
        module=new PumpkinMelonFarmModule(new FarmServices(clock,player,world,input,rotation,breaking,inventory),config,FarmLayoutRegistry.createDefault());
        assertTrue(module.preStartCheck().orElseThrow().contains("needs its tool"));assertFalse(module.isEnabled());assertReleased();
        names.set(2,"pumpkin dicer");assertTrue(module.preStartCheck().isEmpty());assertEquals(2,selected[0]);
        module.setEnabled(true);advanceTick();assertTrue(breaking.breaking&&input.forward&&input.left);
        selected[0]=0;advanceTick();assertEquals(2,selected[0]);assertTrue(breaking.breaking&&input.forward&&input.left);
    }
    @Test void loadedObstructionWithoutAuthoredConnectorStopsAfterObservedStillTicks(){
        module.setEnabled(true);advanceTick();assertEquals(PumpkinOnEndRowPlanner.PumpkinRowState.FARMING,module.rowState());
        blocks.put(new BlockPos(1,64,1),"minecraft:sandstone");
        advanceTick();assertEquals(PumpkinOnEndRowPlanner.PumpkinRowState.FARMING,module.rowState());assertFalse(breaking.breaking);
        advanceTick();assertEquals(PumpkinOnEndRowPlanner.PumpkinRowState.FARMING,module.rowState());
        advanceTick();assertTrue(module.diagnostics().contains("state=BLOCKED"));
        assertTrue(module.diagnostics().contains("no next loaded crop band on authored end walkway"));
        assertEquals(PumpkinOnEndRowPlanner.PumpkinRowState.ON_END_ROW,module.rowState());assertReleased();
    }
    @Test void decodedClassicalSidePressureKeepsLoadedRailDistinctFromAirEnd(){
        config.set(FarmingConfig.PUMPKIN_FARM_DESIGN,PumpkinFarmDesign.CLASSICAL);rotation.yaw=0;rotation.pitch=26;
        player.position=new Vec3(.5,64,.5);player.blockPos=new BlockPos(0,64,0);breaking.target=new BlockPos(0,64,1);
        blocks.put(new BlockPos(1,64,0),"minecraft:sandstone");module.setEnabled(true);
        for(int tick=0;tick<25;tick++)advanceTick();
        assertEquals(PumpkinOnEndRowPlanner.PumpkinRowState.FARMING,module.rowState());
        assertTrue(input.forward&&input.left);assertFalse(input.right);assertTrue(breaking.breaking);assertEquals(0,rotation.yaw,.001);
    }
    @Test void protectedStemReplacementFruitKeepsWorkingColumnsAngleMovementAndAttack(){
        for(String fruit:List.of("minecraft:pumpkin","minecraft:melon","minecraft:carved_pumpkin")){
            if(module.isEnabled())module.setEnabled(false);
            setup();module.setEnabled(true);advanceTick();
            BlockPos outside=new BlockPos(2,64,1);
            blocks.put(outside,fruit);
            if(fruit.equals("minecraft:melon"))blocks.put(new BlockPos(2,64,2),"minecraft:melon_stem");
            assertTrue(dev.veltraph.client.farming.movement.GourdCropTarget.harvestable(world,outside),"Outside fruit must be a real legal bed, not decorative test data");
            assertFalse(dev.veltraph.client.farming.movement.GourdCropTarget.harvestable(world,breaking.target),"Sole adjacent stem removal must actually orphan working carved fruit");
            for(int tick=0;tick<8;tick++){
                advanceTick();
                assertEquals(35,rotation.yaw,.01,"Protected outside fruit must not retarget35 to45");
                assertEquals(PumpkinOnEndRowPlanner.PumpkinRowState.FARMING,module.rowState());
                assertTrue(input.forward&&input.left&&breaking.breaking,"Orphan working crop remains harvestable within its owned bed");
                assertFalse(input.right||input.back||input.sneak||input.jump||input.sprint);
                assertFalse(module.diagnostics().contains("state=FOCUSING"));
                assertTrue(actualHarvestGuard(breaking.target));
                assertFalse(actualHarvestGuard(outside));
            }
        }
    }
    @Test void realBodyAndHeadAirGapStillUsesFortyFiveDegreeView(){
        module.setEnabled(true);advanceTick();
        blocks.put(new BlockPos(2,64,2),"minecraft:air");
        blocks.put(new BlockPos(2,65,2),"minecraft:air");
        for(int tick=0;tick<12;tick++)advanceTick();
        assertEquals(45,rotation.yaw,.75);
        assertEquals(PumpkinOnEndRowPlanner.PumpkinRowState.FARMING,module.rowState());
        assertTrue(input.forward&&input.left&&breaking.breaking);
        assertFalse(input.sneak||input.jump||input.sprint);
    }
    @Test void unloadedOutsideWallNeverManufacturesGapOrHarvestPermission(){
        module.setEnabled(true);advanceTick();
        BlockPos outside=new BlockPos(2,64,1);blocks.put(outside,"minecraft:pumpkin");
        unloaded.add(outside);unloaded.add(outside.up());
        for(int tick=0;tick<5;tick++)advanceTick();
        assertEquals(35,rotation.yaw,.01);
        assertFalse(actualHarvestGuard(outside));
        assertTrue(input.forward&&input.left&&breaking.breaking);
    }
    @Test void establishedBedRequiresLegalLoadedSoilExactHeightAndColumns(){
        module.setEnabled(true);advanceTick();
        BlockPos inside=breaking.target;blocks.put(new BlockPos(2,64,1),"minecraft:pumpkin");
        assertTrue(actualHarvestGuard(inside));
        blocks.put(inside.down(),"minecraft:stone");assertFalse(actualHarvestGuard(inside));
        blocks.put(inside.down(),"minecraft:dirt");unloaded.add(inside.down());assertFalse(actualHarvestGuard(inside));
        unloaded.clear();assertTrue(actualHarvestGuard(inside));
        BlockPos wrongHeight=new BlockPos(1,65,3);blocks.put(wrongHeight,"minecraft:pumpkin");blocks.put(wrongHeight.down(),"minecraft:dirt");
        assertFalse(actualHarvestGuard(wrongHeight));
        BlockPos wrongColumn=new BlockPos(3,64,3);blocks.put(wrongColumn,"minecraft:pumpkin");blocks.put(wrongColumn.down(),"minecraft:dirt");
        assertFalse(actualHarvestGuard(wrongColumn));
    }
    @Test void guardDoesNotAuthorizeFocusEndSuspensionConfigMismatchOrUnknownBand(){
        module.setEnabled(true);advanceTick();BlockPos target=breaking.target;
        var planner=capturedPlanner();var oldBand=planner.band();var oldState=planner.state();
        for(var state:List.of(dev.veltraph.client.farming.movement.planner.CapturedGourdWallPlanner.State.FOCUSING,
                dev.veltraph.client.farming.movement.planner.CapturedGourdWallPlanner.State.END_APPROACH,
                dev.veltraph.client.farming.movement.planner.CapturedGourdWallPlanner.State.END_TRANSFER)){
            plannerField("state",state);assertFalse(actualHarvestGuard(target));
        }
        plannerField("state",oldState);plannerField("band",null);assertFalse(actualHarvestGuard(target));
        plannerField("band",new dev.veltraph.client.farming.movement.planner.CapturedGourdWallPlanner.Band(Direction.EAST,Direction.WEST,1,"unknown"));
        assertFalse(actualHarvestGuard(target));plannerField("band",oldBand);
        config.set(FarmingConfig.PUMPKIN_FARM_DESIGN,PumpkinFarmDesign.CLASSICAL);assertFalse(actualHarvestGuard(target));
        config.set(FarmingConfig.PUMPKIN_FARM_DESIGN,PumpkinFarmDesign.CAPTURED_DEFAULT);
        module.suspendForRewarp();assertFalse(actualHarvestGuard(target));
        module.setEnabled(false);assertFalse(actualHarvestGuard(target));
    }
    @Test void bootstrapDoesNotInventOwnedColumnsWithoutStemEvidence(){
        for(int z=1;z<=20;z++)for(int x:new int[]{-1,2})blocks.put(new BlockPos(x,64,z),"minecraft:pumpkin");
        module.setEnabled(true);advanceTick();
        assertNull(module.capturedBand());assertReleased();
        assertFalse(actualHarvestGuard(new BlockPos(1,64,1)));
    }
    private boolean actualHarvestGuard(BlockPos position){
        try{var method=PumpkinMelonFarmModule.class.getDeclaredMethod("harvestableInteractionTarget",BlockPos.class);method.setAccessible(true);return (Boolean)method.invoke(module,position);}
        catch(ReflectiveOperationException error){throw new AssertionError(error);}
    }
    private dev.veltraph.client.farming.movement.planner.CapturedGourdWallPlanner capturedPlanner(){
        try{var field=PumpkinMelonFarmModule.class.getDeclaredField("capturedWallPlanner");field.setAccessible(true);return (dev.veltraph.client.farming.movement.planner.CapturedGourdWallPlanner)field.get(module);}
        catch(ReflectiveOperationException error){throw new AssertionError(error);}
    }
    private void plannerField(String name,Object value){
        try{var field=dev.veltraph.client.farming.movement.planner.CapturedGourdWallPlanner.class.getDeclaredField(name);field.setAccessible(true);field.set(capturedPlanner(),value);}
        catch(ReflectiveOperationException error){throw new AssertionError(error);}
    }
    private void advanceTick(){clock.advanceTick();clock.advanceMillis(50);module.onTick();}
    private void assertReleased(){assertFalse(input.forward||input.back||input.left||input.right||input.jump||input.sneak||input.sprint||breaking.breaking);}
}
