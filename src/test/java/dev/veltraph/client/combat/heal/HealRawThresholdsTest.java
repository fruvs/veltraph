package dev.veltraph.client.combat.heal;

import dev.veltraph.client.config.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HealRawThresholdsTest {
    @Test void bothRawThresholdChangesInvalidateFrozenSettingsWithoutConfusingPercentKeys(){
        var config=new MapConfigService();var captured=HealRawThresholds.fromConfig(config);
        assertEquals(new HealRawThresholds(1500,800),captured);assertTrue(captured.matches(config));
        config.set(GhostConfig.GHOST_HEALTH_TO_ACTIVATE,1501);assertFalse(captured.matches(config));
        config.set(GhostConfig.GHOST_HEALTH_TO_ACTIVATE,1500);assertTrue(captured.matches(config));
        config.set(GhostConfig.GHOST_EMERGENCY_HEALTH_TO_ACTIVATE,801);assertFalse(captured.matches(config));
        config.set(GhostConfig.GHOST_EMERGENCY_HEALTH_TO_ACTIVATE,800);
        config.set(GhostConfig.GHOST_HEALTH_TO_ACTIVATE_PCT,99);assertTrue(captured.matches(config));
    }
    @Test void rawKeysAreRecognizedByActualPersistenceHolderRegistry(){
        assertTrue(PersistableKeys.all().contains(GhostConfig.GHOST_HEALTH_TO_ACTIVATE));
        assertTrue(PersistableKeys.all().contains(GhostConfig.GHOST_EMERGENCY_HEALTH_TO_ACTIVATE));
    }
}
