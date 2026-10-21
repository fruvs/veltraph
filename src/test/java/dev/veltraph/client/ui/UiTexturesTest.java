package dev.veltraph.client.ui;

import org.junit.jupiter.api.Test;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The bundled UI textures must actually ship on the classpath under the documented paths. */
class UiTexturesTest {

    @Test
    void allBundledTexturesExistOnClasspath() {
        for (String tex : UiTextures.ALL) {
            String path = UiTextures.resourcePath(tex);
            assertTrue(path.startsWith("assets/veltraph/textures/ui/"), "well-formed path: " + path);
            assertNotNull(UiTexturesTest.class.getClassLoader().getResource(path),
                    "bundled texture missing from resources: " + path);
        }
    }

    @Test
    void registryIsNonEmptyAndNamespaced() {
        assertTrue(UiTextures.ALL.size() >= 4);
        assertTrue(UiTextures.NAMESPACE.equals("veltraph"));
    }

    @Test
    void bundledTextureDimensionsMatchDeclaredConstants() throws IOException {
        assertDims(UiTextures.PANEL, UiTextures.PANEL_W, UiTextures.PANEL_H);
        assertDims(UiTextures.CARD, UiTextures.CARD_W, UiTextures.CARD_H);
        assertDims(UiTextures.BUTTON, UiTextures.BUTTON_W, UiTextures.BUTTON_H);
        assertDims(UiTextures.TOGGLE, UiTextures.TOGGLE_W, UiTextures.TOGGLE_H);
        assertDims(UiTextures.RAIL, UiTextures.RAIL_W, UiTextures.RAIL_H);
        assertDims(UiTextures.THUMB, UiTextures.THUMB_W, UiTextures.THUMB_H);
        assertDims(UiTextures.LOGO, UiTextures.LOGO_W, UiTextures.LOGO_H);
        assertDims(UiTextures.ICONS, UiTextures.ICONS_ATLAS_W, UiTextures.ICON_SIZE);
    }

    @Test
    void cornerInsetsAreWithinTheSourceImage() {
        // A source corner inset must be at most half the smaller source dimension, or nine-slicing degrades.
        assertTrue(UiTextures.PANEL_INSET <= Math.min(UiTextures.PANEL_W, UiTextures.PANEL_H) / 2);
        assertTrue(UiTextures.CARD_INSET <= Math.min(UiTextures.CARD_W, UiTextures.CARD_H) / 2);
        assertTrue(UiTextures.BUTTON_INSET <= Math.min(UiTextures.BUTTON_W, UiTextures.BUTTON_H) / 2);
        assertTrue(UiTextures.TOGGLE_INSET <= Math.min(UiTextures.TOGGLE_W, UiTextures.TOGGLE_H) / 2);
        assertTrue(UiTextures.RAIL_INSET <= Math.min(UiTextures.RAIL_W, UiTextures.RAIL_H) / 2);
        assertTrue(UiTextures.THUMB_INSET <= Math.min(UiTextures.THUMB_W, UiTextures.THUMB_H) / 2);
        // The icon atlas is exactly ICON_COUNT square glyphs wide.
        assertEquals(UiTextures.ICON_SIZE * UiTextures.ICON_COUNT, UiTextures.ICONS_ATLAS_W);
    }

    private static void assertDims(String tex, int expectW, int expectH) throws IOException {
        int[] wh = pngSize(UiTextures.resourcePath(tex));
        assertEquals(expectW, wh[0], "width of " + tex);
        assertEquals(expectH, wh[1], "height of " + tex);
    }

    /** Read a PNG's pixel dimensions from its IHDR chunk (no AWT/ImageIO needed). */
    private static int[] pngSize(String resourcePath) throws IOException {
        try (InputStream in = UiTexturesTest.class.getClassLoader().getResourceAsStream(resourcePath)) {
            assertNotNull(in, "resource: " + resourcePath);
            DataInputStream d = new DataInputStream(in);
            d.skipBytes(16); // 8-byte signature + 4-byte IHDR length + 4-byte "IHDR" tag
            int w = d.readInt();
            int h = d.readInt();
            return new int[]{w, h};
        }
    }
}
