package dev.veltraph.client.game;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Multi-signal location detection: Garden from scoreboard, Garden from the tab fallback when the
 * scoreboard title is missing, SkyBlock through color codes, BedWars exclusion, and the unknown case.
 */
class GardenDetectionTest {

    private static final String BRAND = "Hypixel BungeeCord (1.21)";
    private static final String ADDR = "mc.hypixel.net";

    @Test
    void gardenFromScoreboardTitleAndLine() {
        LocationSignals s = LocationSignals.ofScoreboard(BRAND, ADDR, "SKYBLOCK",
                List.of("§7Purse: §61.2M", "§a⏣ §rThe Garden", "§ewww.hypixel.net"));
        LocationResolution r = GameContextResolver.resolve(s);
        assertTrue(r.context().inSkyBlock());
        assertEquals(GameArea.GARDEN, r.context().area());
        assertEquals(LocationSource.SCOREBOARD_LINE, r.source());
    }

    @Test
    void gardenFromTabFallbackWhenTitleMissing() {
        // No SkyBlock in the scoreboard title; SkyBlock + Garden come from the tab (footer + player lines).
        LocationSignals s = LocationSignals.of(BRAND, ADDR,
                "", List.of(),
                "You are playing SkyBlock",
                List.of("§a⏣ The Garden", "§7Purse: §65,000,000"));
        LocationResolution r = GameContextResolver.resolve(s);
        assertTrue(r.context().onHypixel());
        assertTrue(r.context().inSkyBlock(), "SkyBlock detected from tab footer signal");
        assertEquals(GameArea.GARDEN, r.context().area());
        assertEquals(LocationSource.TAB_PLAYER, r.source());
    }

    @Test
    void gardenFromTellTaleSignalsWithoutLocationLine() {
        // The Garden scoreboard sometimes shows only Plot / Copper / Visitors / Pests, no "The Garden".
        LocationSignals s = LocationSignals.ofScoreboard(BRAND, ADDR, "§6§lSKYBLOCK",
                List.of("§7Plot: §a4", "§6Copper: §c12,345", "§dVisitors: §f2"));
        LocationResolution r = GameContextResolver.resolve(s);
        assertTrue(r.context().inSkyBlock());
        assertEquals(GameArea.GARDEN, r.context().area(), "Garden resolved from tell-tale signals");
    }

    @Test
    void skyBlockDetectedThroughColorCodes() {
        LocationSignals s = LocationSignals.ofScoreboard(BRAND, ADDR, "§6§lSKY BLOCK",
                List.of("§a⏣ §rHub", "§6Bank: §e10M"));
        LocationResolution r = GameContextResolver.resolve(s);
        assertTrue(r.context().inSkyBlock());
        assertEquals(GameArea.HUB, r.context().area());
    }

    @Test
    void bedWarsThroughColorCodesIsNotSkyBlock() {
        LocationSignals s = LocationSignals.ofScoreboard(BRAND, ADDR, "§c§lBED WARS",
                List.of("§7Mode: §aSolo", "§fDiamond I"));
        LocationResolution r = GameContextResolver.resolve(s);
        assertTrue(r.context().onHypixel());
        assertFalse(r.context().inSkyBlock(), "BedWars must never count as SkyBlock");
        assertEquals(GameArea.BEDWARS_LOBBY, r.context().area());
    }

    @Test
    void unknownAreaWhenNoLocationSignal() {
        LocationSignals s = LocationSignals.ofScoreboard(BRAND, ADDR, "SKYBLOCK",
                List.of("§7Purse: §60"));
        LocationResolution r = GameContextResolver.resolve(s);
        assertTrue(r.context().inSkyBlock());
        assertEquals(GameArea.UNKNOWN, r.context().area());
    }

    @Test
    void legacyOverloadStillResolves() {
        GameContext ctx = GameContextResolver.resolve(BRAND, ADDR, "SKYBLOCK", List.of("⏣ The Garden"));
        assertEquals(GameArea.GARDEN, ctx.area());
    }
}
