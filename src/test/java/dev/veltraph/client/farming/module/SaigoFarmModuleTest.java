package dev.veltraph.client.farming.module;

import dev.veltraph.client.config.FarmingConfig;
import dev.veltraph.client.config.MapConfigService;
import dev.veltraph.client.config.PumpkinFarmDesign;
import dev.veltraph.client.farming.domain.FarmLayoutRegistry;
import dev.veltraph.client.service.FarmServices;
import dev.veltraph.client.service.WorldService;
import dev.veltraph.client.service.stub.NoOpInventoryService;
import dev.veltraph.client.testutil.*;
import dev.veltraph.client.world.BlockPos;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SaigoFarmModuleTest {
    private final FakePlayerService player=new FakePlayerService();
    private final RecordingInputService input=new RecordingInputService();
    private final RecordingBlockBreaking breaking=new RecordingBlockBreaking();
    private final FakeRotationService rotation=new FakeRotationService();
    private final MapConfigService config=new MapConfigService();
    private final WorldService floor=new WorldService() {
        public boolean isLoaded(BlockPos p){return true;}
        public boolean isPassable(BlockPos p){return p.y()>=64;}
        public boolean isStandable(BlockPos p){return p.y()==63;}
        public String blockId(BlockPos p){return p.y()==63?"minecraft:white_wool":"minecraft:air";}
    };
    private PumpkinMelonFarmModule create() {
        config.set(FarmingConfig.PUMPKIN_FARM_DESIGN,PumpkinFarmDesign.SAIGO_CURRENT);
        rotation.yaw=90;rotation.pitch=25.8f;player.onGround=true;
        return new PumpkinMelonFarmModule(new FarmServices(new FakeClock(),player,floor,input,rotation,
                breaking,new NoOpInventoryService()),config,FarmLayoutRegistry.createDefault());
    }
    @Test void saigoCapturesCardinalHeadingAndGuardsNonCropAttack() {
        var module=create();module.setEnabled(true);module.onTick();
        assertEquals(PumpkinFarmDesign.SAIGO_CURRENT,module.design());
        assertEquals(90,rotation.yaw,0.01);assertEquals(25.8,rotation.pitch,0.01);
        assertTrue(input.forward&&input.left&&input.sprint);
        assertFalse(breaking.breaking,"actual crosshair has no crop; never attack the source structure");
        module.setEnabled(false);
        assertFalse(input.forward||input.left||input.sprint||breaking.breaking);
    }
    @Test void airborneNonWaterPauseAndRewarpReleaseSprint() {
        var module=create();module.setEnabled(true);module.onTick();assertTrue(input.sprint);
        player.onGround=false;module.onTick();
        assertFalse(input.sprint||input.forward||input.left||breaking.breaking);
        player.onGround=true;module.onTick();assertTrue(input.sprint);
        module.suspendForRewarp();module.onTick();assertFalse(input.sprint||input.forward||input.left);
        module.resumeAfterRewarp();module.onTick();assertTrue(input.sprint&&input.forward&&input.left);
        module.setEnabled(false);assertFalse(input.sprint);
    }
    @Test void groundedCornerFruitRequiresItsLoadedAuthoredSoilBed() {
        final boolean[] bedExists={true};
        WorldService cropStep=new WorldService() {
            public boolean isLoaded(BlockPos p){return true;}
            public boolean isPassable(BlockPos p){return p.y()>=64||(p.y()==62&&!bedExists[0]);}
            public boolean isStandable(BlockPos p){return p.y()==63;}
            public String blockId(BlockPos p){
                if(p.y()==63)return "minecraft:melon";
                if(p.y()==62&&bedExists[0])return "minecraft:dirt";
                return "minecraft:air";
            }
        };
        config.set(FarmingConfig.PUMPKIN_FARM_DESIGN,PumpkinFarmDesign.SAIGO_CURRENT);
        rotation.pitch=25.8f;player.onGround=true;
        var module=new PumpkinMelonFarmModule(new FarmServices(new FakeClock(),player,cropStep,input,
                rotation,breaking,new NoOpInventoryService()),config,FarmLayoutRegistry.createDefault());
        module.setEnabled(true);module.onTick();
        assertTrue(input.forward&&input.left&&input.sprint,"valid corner melon on its soil bed is grounded support");
        bedExists[0]=false;module.onTick();
        assertFalse(input.forward||input.left||input.sprint,"crop over absent soil cannot authorize motion");
        module.setEnabled(false);
    }
    @Test void normalCycleSuspensionIsDistinctAndRequiresAnExplicitRestart() {
        var module=create();module.setEnabled(true);module.onTick();
        assertTrue(module.suspendForCycleReset());assertTrue(module.isSuspended());
        assertTrue(module.isSuspendedForCycleReset());assertFalse(module.isSuspendedForRewarp());
        module.onTick();assertFalse(input.forward||input.left||input.sprint);
        module.resumeAfterRewarp();assertTrue(module.isSuspendedForCycleReset(),"failsafe seam cannot resume a normal cycle");
        assertThrows(IllegalStateException.class,module::suspendForRewarp);
        assertTrue(module.restartAfterCycleReset());assertFalse(module.isSuspended());
        module.onTick();assertTrue(input.forward&&input.left&&input.sprint);
        assertFalse(module.restartAfterCycleReset(),"no duplicate unsuspended restart");
        module.setEnabled(false);assertFalse(module.isSuspended());
    }
}
