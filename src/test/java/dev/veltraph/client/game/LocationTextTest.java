package dev.veltraph.client.game;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Text-normalization pipeline used to match location signals. */
class LocationTextTest {

    @Test
    void stripsColorCodes() {
        assertEquals("Hello", LocationText.stripColorCodes("§aHe§lllo§r"));
        assertEquals("The Garden", LocationText.stripColorCodes("§a§lThe Garden"));
        assertEquals("", LocationText.stripColorCodes(null));
    }

    @Test
    void sanitizeRemovesGlyphsButKeepsReadableText() {
        String s = LocationText.sanitize("§a⏣ §rThe Garden!");
        // ⏣ and ! are dropped; letters/space kept.
        assertTrue(s.contains("The Garden"), "kept readable location: '" + s + "'");
        assertTrue(!s.contains("⏣") && !s.contains("!"), "dropped glyph/punctuation noise");
    }

    @Test
    void normalizeCollapsesWhitespaceAndLowercases() {
        assertEquals("the garden", LocationText.normalize("§a⏣ §rThe   Garden"));
        assertEquals("purse: 1,234", LocationText.normalize("§6Purse: §e1,234"));
        assertEquals("", LocationText.normalize(null));
    }

    @Test
    void titleCasesWords() {
        assertEquals("The Garden", LocationText.titleCase("the garden"));
        assertEquals("Crystal Hollows", LocationText.titleCase("CRYSTAL HOLLOWS"));
        assertEquals("Dwarven Mines", LocationText.titleCase("dwarven_mines".replace("_", " ")));
    }
}
