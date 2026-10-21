package dev.veltraph.client.ui;

import java.util.List;

/**
 * Registry of the bundled Veltraph UI textures under {@code assets/veltraph/textures/ui/}. Pure: holds
 * the namespace + resource paths (and the classpath location) so the asset set is unit-testable and the
 * renderer can build the texture identifiers from these constants.
 */
public final class UiTextures {

    public static final String NAMESPACE = "veltraph";

    /** Texture paths (relative to the namespace's {@code textures/} root, as Identifier paths). */
    public static final String LOGO = "textures/ui/logo.png";
    public static final String PANEL = "textures/ui/panel.png";
    public static final String CARD = "textures/ui/card.png";
    public static final String BUTTON = "textures/ui/button.png";
    public static final String TOGGLE = "textures/ui/toggle.png";
    public static final String RAIL = "textures/ui/rail.png";
    public static final String THUMB = "textures/ui/thumb.png";
    public static final String ICONS = "textures/ui/icons.png";

    // Source-image geometry of the bundled skin (high-resolution baked corners; the renderer
    // nine-slices and downscales these to a crisp small on-screen corner). Centralised here so the
    // draw call sites and the resource test reference one source of truth.
    public static final int PANEL_W = 192,  PANEL_H = 192,  PANEL_INSET = 56;
    public static final int CARD_W = 192,   CARD_H = 144,   CARD_INSET = 44;
    public static final int BUTTON_W = 160, BUTTON_H = 64,  BUTTON_INSET = 26;
    public static final int TOGGLE_W = 144, TOGGLE_H = 72,  TOGGLE_INSET = 36;
    public static final int RAIL_W = 160,   RAIL_H = 72,    RAIL_INSET = 26;
    public static final int THUMB_W = 48,   THUMB_H = 192,  THUMB_INSET = 24;
    public static final int LOGO_W = 64,    LOGO_H = 64;

    /** Category-icon atlas geometry: {@link #ICON_COUNT} glyphs of {@link #ICON_SIZE}px in a row. */
    public static final int ICON_SIZE = 64;
    public static final int ICON_COUNT = 8;
    public static final int ICONS_ATLAS_W = ICON_SIZE * ICON_COUNT;

    public static final List<String> ALL = List.of(LOGO, PANEL, CARD, BUTTON, TOGGLE, RAIL, THUMB, ICONS);

    private UiTextures() {
    }

    /** Classpath location of a bundled texture (for resource existence checks / loading). */
    public static String resourcePath(String texturePath) {
        return "assets/" + NAMESPACE + "/" + texturePath;
    }
}
