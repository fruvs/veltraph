package dev.veltraph.client.game;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HudVisibilityServiceTest {

    private static GameContext sb(GameArea area) {
        return new GameContext(true, true, area);
    }

    @Test
    void nothingVisibleOutsideSkyBlock() {
        GameContext bedwars = new GameContext(true, false, GameArea.BEDWARS_LOBBY);
        for (HudCategory cat : HudCategory.values()) {
            assertFalse(HudVisibilityService.isVisible(cat, bedwars, true), cat + " must hide outside SkyBlock");
            assertFalse(HudVisibilityService.isVisible(cat, GameContext.NONE, true), cat + " must hide off-Hypixel");
        }
    }

    @Test
    void gardenHudOnlyInGarden() {
        assertTrue(HudVisibilityService.isVisible(HudCategory.GARDEN, sb(GameArea.GARDEN), false));
        assertFalse(HudVisibilityService.isVisible(HudCategory.GARDEN, sb(GameArea.HUB), false));
    }

    @Test
    void pestAndVisitorOnlyInGarden() {
        assertTrue(HudVisibilityService.isVisible(HudCategory.PEST, sb(GameArea.GARDEN), false));
        assertTrue(HudVisibilityService.isVisible(HudCategory.VISITOR, sb(GameArea.GARDEN), false));
        assertFalse(HudVisibilityService.isVisible(HudCategory.PEST, sb(GameArea.ISLAND), false));
        assertFalse(HudVisibilityService.isVisible(HudCategory.VISITOR, sb(GameArea.ISLAND), false));
    }

    @Test
    void foragingInDocumentedAreasOnly() {
        assertTrue(HudVisibilityService.isVisible(HudCategory.FORAGING, sb(GameArea.THE_PARK), false));
        assertTrue(HudVisibilityService.isVisible(HudCategory.FORAGING, sb(GameArea.ISLAND), false));
        assertFalse(HudVisibilityService.isVisible(HudCategory.FORAGING, sb(GameArea.GARDEN), false));
    }

    @Test
    void ghostOnlyInDwarvenMines() {
        assertTrue(HudVisibilityService.isVisible(HudCategory.GHOST, sb(GameArea.DWARVEN_MINES), false));
        assertFalse(HudVisibilityService.isVisible(HudCategory.GHOST, sb(GameArea.THE_END), false));
    }

    @Test
    void scriptHudOnlyWhileScripting() {
        assertTrue(HudVisibilityService.isVisible(HudCategory.SCRIPT, sb(GameArea.GARDEN), true));
        assertFalse(HudVisibilityService.isVisible(HudCategory.SCRIPT, sb(GameArea.GARDEN), false));
    }

    @Test
    void fishingAndSlayerVisibleAnywhereInSkyBlock() {
        assertTrue(HudVisibilityService.isVisible(HudCategory.FISHING, sb(GameArea.CRIMSON_ISLE), false));
        assertTrue(HudVisibilityService.isVisible(HudCategory.SLAYER, sb(GameArea.SPIDERS_DEN), false));
    }
}
