package dev.veltraph.client.farming.module;

import dev.veltraph.client.config.FarmingConfig;
import dev.veltraph.client.config.PumpkinFarmDesign;
import dev.veltraph.client.farming.movement.SShapeState;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Passive family phase checks, not native ownership or jump permission. */
class GroundedCropModulePhaseTest {
    @Test void normalCropStartupIsCoveredDespiteToolPreparationAndSuspensionIsExcluded() throws Exception {
        var rig=new FarmMaintenanceWitnessTest.Rig();var farm=(WartCropsFarmModule)rig.start("wart");
        var engine=farm.engine();set(engine,"fixedRowSteering",true);set(engine,"normalReferenceView",true);
        set(engine,"state",SShapeState.PREPARE_TOOL);assertTrue(farm.groundedCropWorkActive());
        farm.suspendForRewarp();assertFalse(farm.groundedCropWorkActive());
        farm.setEnabled(false);assertFalse(farm.groundedCropWorkActive());
    }

    @Test void mkdStartupAndTransfersAreCoveredWhileOtherGourdDesignsAreExcluded() throws Exception {
        var rig=new FarmMaintenanceWitnessTest.Rig();rig.config.set(FarmingConfig.PUMPKIN_FARM_DESIGN,PumpkinFarmDesign.MELONKINGDE_WATER);
        rig.config.set(FarmingConfig.ROTATION_USE_MOUSE_GCD,false);
        var farm=(PumpkinMelonFarmModule)rig.start("gourd");assertTrue(farm.groundedCropWorkActive());
        farm.suspendForRewarp();assertFalse(farm.groundedCropWorkActive());farm.setEnabled(false);
        rig.config.set(FarmingConfig.PUMPKIN_FARM_DESIGN,PumpkinFarmDesign.SAIGO_CURRENT);
        var other=(PumpkinMelonFarmModule)rig.start("gourd");assertFalse(other.groundedCropWorkActive());
    }

    private static void set(Object target,String name,Object value) throws Exception {
        var field=target.getClass().getDeclaredField(name);field.setAccessible(true);field.set(target,value);
    }
}
