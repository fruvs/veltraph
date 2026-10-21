package dev.veltraph.client.farming.module;

import dev.veltraph.client.config.*;
import dev.veltraph.client.farming.domain.FarmLayoutRegistry;
import dev.veltraph.client.service.FarmServices;
import dev.veltraph.client.testutil.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FarmingSelectionIntegrationTest {
    @Test void factoryChoosesMatchingToolAndKeepsRunSelectionUntilRestart() {
        var config=new MapConfigService();
        config.set(FarmingConfig.REWARP_REQUIRED,false);
        config.set(FarmingConfig.FARMING_TOOL_SLOT,-1);
        config.set(FarmingConfig.SUGARCANE_FARM_DESIGN,SugarcaneFarmDesign.ACE_TWO_PLOT);
        config.set(FarmingConfig.SUGARCANE_FARM_CROP,SugarcaneFarmCrop.SUNFLOWER);
        var inventory=new FakeInventoryService().setItem(0,"Cane Cutter").setItem(2,"Sunflower Cutter");
        var services=new FarmServices(new FakeClock(),new FakePlayerService(),new FakeWorldService(),
                new RecordingInputService(),new FakeRotationService(45,0),new RecordingBlockBreaking(),inventory);
        var module=(SugarcaneFlowersFarmModule)FarmingModules.createAll(services,config,FarmLayoutRegistry.createDefault())
                .stream().filter(m->m.id().equals(SugarcaneFlowersFarmModule.ID)).findFirst().orElseThrow();
        assertTrue(module.preStartCheck().isEmpty());
        assertEquals(2,inventory.selected);
        module.setEnabled(true);
        String flowerCycle=dev.veltraph.client.rewarp.FarmCycleProfileIdentity.of(module);
        config.set(FarmingConfig.SUGARCANE_FARM_DESIGN,SugarcaneFarmDesign.RECOVERED);
        config.set(FarmingConfig.SUGARCANE_FARM_CROP,SugarcaneFarmCrop.CANE);
        assertEquals(SugarcaneFlowersFarmModule.Design.ACE_TWO_PLOT,module.design());
        assertEquals(SugarcaneFlowersFarmModule.Crop.SUNFLOWER,module.crop());
        assertEquals(flowerCycle,dev.veltraph.client.rewarp.FarmCycleProfileIdentity.of(module));
        module.setEnabled(false);
        assertTrue(module.preStartCheck().isEmpty());
        assertEquals(0,inventory.selected);
        module.setEnabled(true);
        assertEquals(SugarcaneFlowersFarmModule.Design.RECOVERED,module.design());
        assertEquals(SugarcaneFlowersFarmModule.Crop.CANE,module.crop());
        assertEquals(FarmLayoutRegistry.SUGARCANE_FLOWERS,dev.veltraph.client.rewarp.FarmCycleProfileIdentity.of(module));
        assertNotEquals(flowerCycle,dev.veltraph.client.rewarp.FarmCycleProfileIdentity.of(module));
        module.setEnabled(false);
    }
}
