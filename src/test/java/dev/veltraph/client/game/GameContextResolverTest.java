package dev.veltraph.client.game;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** SkyBlock vs BedWars/lobby/non-Hypixel detection. */
class GameContextResolverTest {

    @Test
    void skyBlockGardenDetected() {
        GameContext ctx = GameContextResolver.resolve(
                "Hypixel BungeeCord (1.21)", "mc.hypixel.net",
                "SKYBLOCK", List.of("Purse: 1.2M", "⏣ The Garden", "www.hypixel.net"));
        assertTrue(ctx.onHypixel());
        assertTrue(ctx.inSkyBlock());
        assertEquals(GameArea.GARDEN, ctx.area());
    }

    @Test
    void skyBlockHubDetected() {
        GameContext ctx = GameContextResolver.resolve(
                "Hypixel BungeeCord", "hypixel.net",
                "SKYBLOCK", List.of("⏣ Hub", "Bank: 10M"));
        assertTrue(ctx.inSkyBlock());
        assertEquals(GameArea.HUB, ctx.area());
    }

    @Test
    void bedWarsIsHypixelButNotSkyBlock() {
        GameContext ctx = GameContextResolver.resolve(
                "Hypixel BungeeCord", "mc.hypixel.net",
                "BED WARS", List.of("Mode: Solo", "Diamond I"));
        assertTrue(ctx.onHypixel());
        assertFalse(ctx.inSkyBlock());
        assertEquals(GameArea.BEDWARS_LOBBY, ctx.area());
    }

    @Test
    void nonHypixelServerIsNeither() {
        GameContext ctx = GameContextResolver.resolve(
                "vanilla", "play.example.com", "Some Server", List.of("anything"));
        assertFalse(ctx.onHypixel());
        assertFalse(ctx.inSkyBlock());
        assertEquals(GameArea.UNKNOWN, ctx.area());
    }

    @Test
    void hypixelDetectedByAddressWhenBrandMissing() {
        assertTrue(GameContextResolver.isHypixel(null, "mc.hypixel.net"));
        assertTrue(GameContextResolver.isHypixel(null, "alpha.hypixel.net"));
        assertFalse(GameContextResolver.isHypixel("vanilla", "localhost"));
    }

    @Test
    void skyBlockButUnknownAreaWhenNoLocationLine() {
        GameContext ctx = GameContextResolver.resolve(
                "Hypixel BungeeCord", "mc.hypixel.net", "SKYBLOCK", List.of("Purse: 0"));
        assertTrue(ctx.inSkyBlock());
        assertEquals(GameArea.UNKNOWN, ctx.area());
    }

    @Test
    void limboIsHypixelButNotSkyBlock() {
        GameContext ctx = GameContextResolver.resolve(
                "Hypixel BungeeCord", "mc.hypixel.net", "LIMBO", List.of("You are currently AFK."));
        assertTrue(ctx.onHypixel());
        assertFalse(ctx.inSkyBlock());
        assertEquals(GameArea.LIMBO, ctx.area());
    }
}
