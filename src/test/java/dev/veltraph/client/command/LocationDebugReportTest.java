package dev.veltraph.client.command;

import dev.veltraph.client.game.GameContextResolver;
import dev.veltraph.client.game.LocationResolution;
import dev.veltraph.client.game.LocationSignals;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** The /veltraphdebug location report must surface the raw + normalized evidence and the verdict. */
class LocationDebugReportTest {

    @Test
    void reportContainsRawNormalizedAndResolvedEvidence() {
        LocationSignals s = LocationSignals.of(
                "Hypixel BungeeCord (1.21)", "mc.hypixel.net",
                "§6§lSKYBLOCK", List.of("§a⏣ §rThe Garden", "§7Purse: §61.2M"),
                "You are playing SkyBlock", List.of("§7Plot: §a4"));
        LocationResolution r = GameContextResolver.resolve(s);
        String text = LocationDebugReport.text(s, r);

        assertTrue(text.contains("Veltraph location debug"), "has header");
        assertTrue(text.contains("brand:"), "has brand line");
        assertTrue(text.contains("sidebar title raw:"), "has raw title");
        assertTrue(text.contains("sidebar title norm:"), "has normalized title");
        assertTrue(text.contains("tab footer"), "has tab footer");
        assertTrue(text.contains("resolved:"), "has verdict");
        assertTrue(text.contains("Garden"), "names the resolved area");
        assertTrue(text.contains("source:"), "has source/confidence");
    }

    @Test
    void reportIsBoundedAndNullSafe() {
        LocationResolution none = new LocationResolution(
                dev.veltraph.client.game.GameContext.NONE,
                dev.veltraph.client.game.LocationSource.NONE, "", List.of());
        List<String> lines = LocationDebugReport.lines(null, none);
        assertTrue(lines.get(0).contains("Veltraph location debug"));
        // Many normalized rows must be truncated, not dumped wholesale.
        LocationSignals many = LocationSignals.ofScoreboard("Hypixel BungeeCord", "mc.hypixel.net",
                "SKYBLOCK", manyRows());
        String rowsLine = LocationDebugReport.lines(many, GameContextResolver.resolve(many)).stream()
                .filter(l -> l.contains("sidebar rows norm:")).findFirst().orElse("");
        assertTrue(rowsLine.contains("more)"), "long row list is truncated with a (+N more) marker");
    }

    private static List<String> manyRows() {
        return java.util.stream.IntStream.range(0, 40)
                .mapToObj(i -> "row " + i)
                .toList();
    }
}
