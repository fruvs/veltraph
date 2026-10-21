package dev.veltraph.client.farming.module;

import dev.veltraph.client.config.*;
import dev.veltraph.client.farming.domain.FarmLayoutRegistry;
import dev.veltraph.client.farming.movement.planner.MelonKingDeWaterPlanner;
import dev.veltraph.client.service.*;
import dev.veltraph.client.service.stub.*;
import dev.veltraph.client.testutil.*;
import dev.veltraph.client.world.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Authored inner snow support and outer blocking bank are distinct physical cells. */
class MelonKingDeEndGateTest {
    private FakePlayerService actor;private Map<BlockPos,String> blocks;private RecordingInputService input;
    private PumpkinMelonFarmModule create(boolean support,boolean targetLoaded,boolean targetBlocked) {
        return create(support,targetLoaded,targetBlocked,false);
    }
    private PumpkinMelonFarmModule create(boolean support,boolean targetLoaded,boolean targetBlocked,boolean panelBehindBody) {
        var player=new FakePlayerService();player.position=new Vec3(.7,64,1.2);
        var cells=new HashMap<BlockPos,String>();
        actor=player;blocks=cells;input=new RecordingInputService();
        for(int z=0;z<=4;z++)cells.put(new BlockPos(0,63,z),"minecraft:snow");
        if(targetBlocked)for(int z=0;z<=4;z++)cells.put(new BlockPos(1,64,z),"minecraft:dirt");
        if(panelBehindBody)for(int x=0;x<=1;x++)for(int z=0;z<=4;z++) {
            cells.put(new BlockPos(x,65,z),"minecraft:oak_trapdoor");
            cells.put(new BlockPos(x,63,z),"minecraft:soul_sand");
        }
        cells.put(new BlockPos(-1,64,3),"minecraft:water");cells.put(new BlockPos(-1,63,3),"minecraft:soul_sand");
        WorldService world=new WorldService(){
            public boolean isLoaded(BlockPos p){return targetLoaded||p.x()!=1;}
            public String blockId(BlockPos p){return cells.getOrDefault(p,"minecraft:air");}
            public boolean isPassable(BlockPos p){return Set.of("minecraft:air","minecraft:water").contains(blockId(p));}
            public boolean isStandable(BlockPos p){return !isPassable(p);}
            public boolean hasGroundSupport(Vec3 p,double width){return support;}
            public boolean bodyClear(Vec3 p,double width,double height){return panelBehindBody||WorldService.super.bodyClear(p,width,height);}
        };
        var config=new MapConfigService();config.set(FarmingConfig.REWARP_REQUIRED,false);
        config.set(FarmingConfig.PUMPKIN_FARM_DESIGN,PumpkinFarmDesign.MELONKINGDE_WATER);
        var farm=new PumpkinMelonFarmModule(new FarmServices(new FakeClock(),player,world,input,new FakeRotationService(0,-58.5f),new NoOpBlockBreakingService(),new NoOpInventoryService()),config,FarmLayoutRegistry.createDefault());
        farm.setEnabled(true);return farm;
    }
    @Test void knownOuterBankDoesNotRequireWalkingFloorBehindIt() {
        var farm=create(true,true,true);for(int i=0;i<4;i++)farm.onTick();
        assertEquals(MelonKingDeWaterPlanner.State.TRANSFERRING,farm.waterState());
    }
    @Test void unloadedOuterBankNeverAuthorizesTransfer() {
        var farm=create(true,false,true);for(int i=0;i<4;i++)farm.onTick();
        assertEquals(MelonKingDeWaterPlanner.State.FARMING,farm.waterState());assertTrue(farm.diagnostics().contains("unsupported"));
    }
    @Test void unsupportedPassableSpaceNeverCountsAsKnownCap() {
        var farm=create(true,true,false);for(int i=0;i<4;i++)farm.onTick();
        assertEquals(MelonKingDeWaterPlanner.State.FARMING,farm.waterState());assertTrue(farm.diagnostics().contains("unsupported"));
    }
    @Test void capExceptionRequiresActualActorGroundSupport() {
        var farm=create(false,true,true);for(int i=0;i<4;i++)farm.onTick();
        assertEquals(MelonKingDeWaterPlanner.State.FARMING,farm.waterState());assertTrue(farm.diagnostics().contains("unsupported"));
    }
    @Test void thinPanelBehindActorDoesNotInventAStationaryRowEnd() {
        var farm=create(true,true,false,true);for(int i=0;i<6;i++)farm.onTick();
        assertEquals(MelonKingDeWaterPlanner.State.FARMING,farm.waterState());assertTrue(farm.diagnostics().contains("water lane"));
    }
    @Test void unsupportedAirDoesNotReceiveEntryPressure() {
        var farm=create(true,true,false);actor.position=new Vec3(.7,65.125,1.2);actor.onGround=false;farm.onTick();
        assertFalse(input.forward||input.left||input.right);
    }
    @Test void loadedDryCapEntryRecognizesTheSupportedDrySoulGapBeforeSourceWater() {
        var farm=create(true,true,false);blocks.put(new BlockPos(0,63,1),"minecraft:polished_andesite");
        blocks.put(new BlockPos(0,62,1),"minecraft:soul_sand");blocks.put(new BlockPos(1,62,1),"minecraft:soul_sand");blocks.put(new BlockPos(2,62,1),"minecraft:soul_sand");
        blocks.put(new BlockPos(2,63,1),"minecraft:water");actor.position=new Vec3(.7,64,1.7);farm.onTick();
        assertTrue(input.back);assertFalse(input.forward||input.jump||input.sneak||input.sprint);
        assertTrue(farm.diagnostics().contains("walking-floor height"));
    }
}
