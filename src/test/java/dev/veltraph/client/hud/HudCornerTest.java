package dev.veltraph.client.hud;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pure tests for the HUD corner enum: top/left predicates and lenient parsing. */
class HudCornerTest {

    @Test
    void topLeftPredicates() {
        assertTrue(HudCorner.TOP_LEFT.isTop());
        assertTrue(HudCorner.TOP_LEFT.isLeft());
        assertTrue(HudCorner.TOP_RIGHT.isTop());
        assertFalse(HudCorner.TOP_RIGHT.isLeft());
        assertFalse(HudCorner.BOTTOM_LEFT.isTop());
        assertTrue(HudCorner.BOTTOM_LEFT.isLeft());
        assertFalse(HudCorner.BOTTOM_RIGHT.isTop());
        assertFalse(HudCorner.BOTTOM_RIGHT.isLeft());
    }

    @Test
    void parseAcceptsNamesAndShorthands() {
        assertEquals(HudCorner.TOP_LEFT, HudCorner.parse("tl").orElseThrow());
        assertEquals(HudCorner.TOP_RIGHT, HudCorner.parse("TOP_RIGHT").orElseThrow());
        assertEquals(HudCorner.BOTTOM_LEFT, HudCorner.parse("bottom-left").orElseThrow());
        assertEquals(HudCorner.BOTTOM_RIGHT, HudCorner.parse("br").orElseThrow());
    }

    @Test
    void parseRejectsGarbage() {
        assertTrue(HudCorner.parse("middle").isEmpty());
        assertTrue(HudCorner.parse(null).isEmpty());
        assertTrue(HudCorner.parse("").isEmpty());
    }
}
