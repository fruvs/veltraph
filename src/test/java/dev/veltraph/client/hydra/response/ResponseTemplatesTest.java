package dev.veltraph.client.hydra.response;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResponseTemplatesTest {

    @Test
    void blankConfigUsesBuiltInDefaults() {
        ResponseTemplates t = ResponseTemplates.build("", null, "  ", false);
        assertSame(ResponseTemplates.DEFAULT_FIRST, t.first());
        assertSame(ResponseTemplates.DEFAULT_SECOND, t.second());
        assertSame(ResponseTemplates.DEFAULT_THIRD, t.third());
    }

    @Test
    void extremeSwapsFirstListOnlyWhenNoCustomFirst() {
        ResponseTemplates extreme = ResponseTemplates.build("", "", "", true);
        assertSame(ResponseTemplates.EXTREME_FIRST, extreme.first());
        // A custom first list overrides the extreme built-in.
        ResponseTemplates custom = ResponseTemplates.build("yo|hey", "", "", true);
        assertEquals(2, custom.first().size());
        assertEquals("yo", custom.first().get(0));
    }

    @Test
    void customListsParseOnPipeAndTrim() {
        ResponseTemplates t = ResponseTemplates.build(" a | b |c ", "x|y", "z", false);
        assertEquals(java.util.List.of("a", "b", "c"), t.first());
        assertEquals(java.util.List.of("x", "y"), t.second());
        assertEquals(java.util.List.of("z"), t.third());
    }

    @Test
    void commaSurvivesInsideAMessage() {
        ResponseTemplates t = ResponseTemplates.build("hello, staff|bye", "", "", false);
        assertEquals("hello, staff", t.first().get(0));
        assertEquals("bye", t.first().get(1));
    }

    @Test
    void pickIsDeterministicAcrossTheRollRange() {
        var list = java.util.List.of("a", "b", "c", "d");
        assertEquals("a", ResponseTemplates.pick(list, 0.0));
        assertEquals("b", ResponseTemplates.pick(list, 0.25));
        assertEquals("d", ResponseTemplates.pick(list, 0.99));
        // Out-of-range rolls are clamped, never throw.
        assertEquals("a", ResponseTemplates.pick(list, -1.0));
        assertEquals("d", ResponseTemplates.pick(list, 5.0));
        assertNull(ResponseTemplates.pick(java.util.List.of(), 0.5));
        assertNull(ResponseTemplates.pick(null, 0.5));
    }

    @Test
    void defaultsExcludeCommandShapedLines() {
        // The safe defaults must not silently run a command (no leading-slash entries).
        for (String line : ResponseTemplates.DEFAULT_FIRST) {
            assertTrue(!line.startsWith("/"), line);
        }
    }

    @Test
    void parityDefaultsMatchOriginalYamlExactly() {
        // Parity build → the exact original antistaff-messages.yml lists (incl. command-shaped lines).
        ResponseTemplates t = ResponseTemplates.build("", "", "", false, true);
        assertEquals(java.util.List.of(
                "LOL", "?", "nice check", "/chat a", "..", "/chat all", "HAHA", "aight", "?chat a",
                "nice", "omfg", "omg"), t.first());
        assertEquals(java.util.List.of(
                "can i continue now", "???", "pls", "please", "ty", "LMFAOOOO", "lmfao", "idiots"), t.second());
        assertEquals(java.util.List.of("ty", "pce"), t.third());
    }

    @Test
    void parityFirstListIncludesCommandShapedLines() {
        assertTrue(ResponseTemplates.PARITY_FIRST.contains("/chat a"));
        assertTrue(ResponseTemplates.PARITY_FIRST.contains("/chat all"));
        assertTrue(ResponseTemplates.PARITY_FIRST.contains("?chat a"));
    }

    @Test
    void customOverridesStillWinUnderParity() {
        ResponseTemplates t = ResponseTemplates.build("only this", "", "", false, true);
        assertEquals(java.util.List.of("only this"), t.first());
        // unspecified lists still fall back to the parity defaults
        assertEquals(ResponseTemplates.PARITY_SECOND, t.second());
    }
}
