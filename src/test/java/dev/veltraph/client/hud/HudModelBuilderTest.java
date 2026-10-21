package dev.veltraph.client.hud;

import dev.veltraph.client.core.MacroState;
import dev.veltraph.client.game.GameArea;
import dev.veltraph.client.game.GameContext;
import dev.veltraph.client.game.HudCategory;
import dev.veltraph.client.hydra.HydraState;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pure tests for the in-world HUD model builder (Target 5): visibility gates, content, area gating. */
class HudModelBuilderTest {

    private static GameContext sb(GameArea area) {
        return new GameContext(true, true, area);
    }

    private static HudInputs inputs(boolean hudEnabled, GameContext ctx, HudInputs.ModuleInfo mod) {
        return new HudInputs(hudEnabled, ctx, mod,
                HudInputs.HydraInfo.disabled(), HudInputs.RewarpInfo.none());
    }

    private static boolean hasText(HudModel model, String needle) {
        return model.lines().stream().anyMatch(l -> l.text().contains(needle));
    }

    // ---- Visibility ----

    @Test
    void hiddenWhenNotInSkyBlock() {
        GameContext bedwars = new GameContext(true, false, GameArea.BEDWARS_LOBBY);
        assertFalse(HudModelBuilder.build(inputs(true, bedwars, null)).visible());
        assertFalse(HudModelBuilder.build(inputs(true, GameContext.NONE, null)).visible());
    }

    @Test
    void hiddenWhenHudDisabled() {
        assertFalse(HudModelBuilder.build(inputs(false, sb(GameArea.GARDEN), null)).visible());
    }

    @Test
    void visibleInSkyBlockGarden() {
        HudModel model = HudModelBuilder.build(inputs(true, sb(GameArea.GARDEN), null));
        assertTrue(model.visible());
        assertTrue(hasText(model, HudModelBuilder.TITLE));
    }

    @Test
    void visibleAnywhereInSkyBlockEvenWhenIdle() {
        // The GENERIC panel shows anywhere in SkyBlock (not just the Garden).
        assertTrue(HudModelBuilder.build(inputs(true, sb(GameArea.HUB), null)).visible());
        assertTrue(HudModelBuilder.build(inputs(true, sb(GameArea.CRIMSON_ISLE), null)).visible());
    }

    @Test
    void hiddenForNullInputs() {
        assertFalse(HudModelBuilder.build(null).visible());
    }

    // ---- Content: active macro vs idle ----

    @Test
    void idleLineWhenNoMacroRunning() {
        HudModel model = HudModelBuilder.build(inputs(true, sb(GameArea.GARDEN), null));
        assertTrue(hasText(model, "Idle"));
    }

    @Test
    void linesIncludeActiveMacroAndState() {
        HudInputs.ModuleInfo mod = new HudInputs.ModuleInfo(
                "Wart/Crops", MacroState.RUNNING, "S-SHAPE row=3", HudCategory.GARDEN);
        HudModel model = HudModelBuilder.build(inputs(true, sb(GameArea.GARDEN), mod));
        assertTrue(hasText(model, "Wart/Crops"));
        assertTrue(hasText(model, "RUNNING"));
        assertTrue(hasText(model, "S-SHAPE row=3"));
        assertFalse(hasText(model, "Idle"));
    }

    @Test
    void pausedMacroLineUsesWarningRole() {
        HudInputs.ModuleInfo mod = new HudInputs.ModuleInfo(
                "Cactus", MacroState.PAUSED, "paused: aim", HudCategory.GARDEN);
        HudModel model = HudModelBuilder.build(inputs(true, sb(GameArea.GARDEN), mod));
        HudLine macroLine = model.lines().stream()
                .filter(l -> l.text().startsWith("Cactus")).findFirst().orElseThrow();
        assertEquals(HudLine.Role.WARNING, macroLine.role());
    }

    // ---- Content: hydra + rewarp ----

    @Test
    void linesIncludeHydraStatus() {
        HudInputs in = new HudInputs(true, sb(GameArea.GARDEN), null,
                new HudInputs.HydraInfo(true, HydraState.ALERTED, "Notch", "STAFF_NEARBY"),
                HudInputs.RewarpInfo.none());
        HudModel model = HudModelBuilder.build(in);
        assertTrue(hasText(model, "Anti-Staff"));
        assertTrue(hasText(model, "ALERTED"));
        assertTrue(hasText(model, "Notch"));
    }

    @Test
    void hydraOffShownWhenDisabled() {
        HudModel model = HudModelBuilder.build(inputs(true, sb(GameArea.GARDEN), null));
        assertTrue(hasText(model, "Anti-Staff: off"));
    }

    @Test
    void linesIncludeRewarpStatus() {
        HudInputs in = new HudInputs(true, sb(GameArea.GARDEN), null,
                HudInputs.HydraInfo.disabled(), new HudInputs.RewarpInfo(3, true));
        HudModel model = HudModelBuilder.build(in);
        assertTrue(hasText(model, "Rewarp: 3 saved"));
        assertTrue(hasText(model, "return pending"));
    }

    // ---- Area gating matches HudVisibilityService ----

    @Test
    void farmingMacroLineHiddenOutsideGarden() {
        // A farming macro (GARDEN category) running while the context is the Hub: the panel still shows
        // (GENERIC), but the macro line is suppressed because the GARDEN gate fails there.
        HudInputs.ModuleInfo mod = new HudInputs.ModuleInfo(
                "Wart/Crops", MacroState.RUNNING, "S-SHAPE", HudCategory.GARDEN);
        HudModel model = HudModelBuilder.build(inputs(true, sb(GameArea.HUB), mod));
        assertTrue(model.visible());
        assertFalse(hasText(model, "Wart/Crops"), "farming macro line must hide outside the Garden");
        assertTrue(hasText(model, "out of area"));
    }

    @Test
    void farmingMacroLineShownInsideGarden() {
        HudInputs.ModuleInfo mod = new HudInputs.ModuleInfo(
                "Wart/Crops", MacroState.RUNNING, "S-SHAPE", HudCategory.GARDEN);
        HudModel model = HudModelBuilder.build(inputs(true, sb(GameArea.GARDEN), mod));
        assertTrue(hasText(model, "Wart/Crops"));
    }

    @Test
    void titleIsFirstLine() {
        List<HudLine> lines = HudModelBuilder.build(inputs(true, sb(GameArea.GARDEN), null)).lines();
        assertEquals(HudModelBuilder.TITLE, lines.get(0).text());
        assertEquals(HudLine.Role.TITLE, lines.get(0).role());
    }

    @Test
    void longStatusLineIsTrimmed() {
        String longStatus = "x".repeat(200);
        HudInputs.ModuleInfo mod = new HudInputs.ModuleInfo(
                "Wart/Crops", MacroState.RUNNING, longStatus, HudCategory.GARDEN);
        HudModel model = HudModelBuilder.build(inputs(true, sb(GameArea.GARDEN), mod));
        for (HudLine line : model.lines()) {
            assertTrue(line.text().length() <= 45, "each HUD line stays short: " + line.text().length());
        }
    }
}
