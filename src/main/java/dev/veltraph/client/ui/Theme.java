package dev.veltraph.client.ui;

/**
 * Veltraph UI color palette (ARGB {@code 0xAARRGGBB}). A cohesive dark indigo skin with a teal accent —
 * a deliberate Veltraph identity that does not resemble vanilla Minecraft grey buttons, and is not a
 * clone of the original obfuscated UI. Pure (no Minecraft dependency).
 */
public final class Theme {

    private Theme() {
    }

    // Backdrop veil drawn over the (already dimmed) game behind the panel.
    public static final int BACKDROP_VEIL = 0xA406080C;

    // Panel body — subtle vertical gradient with a soft drop shadow.
    public static final int PANEL_TOP = 0xF31C2431;
    public static final int PANEL_BOTTOM = 0xF310141D;
    public static final int PANEL_SHADOW = 0x76000000;

    // Header strip.
    public static final int HEADER_TOP = 0xEE263445;
    public static final int HEADER_BOTTOM = 0xE8192432;

    // Category rail.
    public static final int RAIL_TOP = 0xD9151B27;
    public static final int RAIL_BOTTOM = 0xD010151F;
    public static final int RAIL_SELECTED = 0xD72D3A50;

    // Cards / raised surfaces.
    public static final int CARD_TOP = 0xE72B374B;
    public static final int CARD_BOTTOM = 0xE51A2231;
    public static final int PANEL_RAISED = 0xD9253042;

    // Accent (teal) family.
    public static final int ACCENT = 0xFF45D0C0;
    public static final int ACCENT_DIM = 0xFF1E6E66;
    public static final int ACCENT_BRIGHT = 0xFF7CF0E4;

    // Header tab buttons.
    public static final int TAB_ACTIVE = 0xE94A807D;
    public static final int TAB_INACTIVE = 0xB9222D3E;

    // Text.
    public static final int TEXT = 0xFFE8ECF4;
    public static final int TEXT_MUTED = 0xFF98A0B4;
    public static final int TEXT_DISABLED = 0xFF565E70;
    public static final int TITLE = 0xFF7CF0E4;

    // Lines / borders.
    public static final int BORDER = 0x9968819C;
    public static final int BORDER_FOCUS = 0xFF45D0C0;
    public static final int DIVIDER = 0xFF2A3243;

    // State colors.
    public static final int ON = 0xFF45C18A;
    public static final int OFF = 0xFF3A3F4D;
    public static final int STOP = 0xFFCB5C57;
    public static final int WARNING = 0xFFE0A53B;

    // Buttons (neutral) + interaction overlays.
    public static final int BUTTON_TOP = 0xE83B4D63;
    public static final int BUTTON_BOTTOM = 0xE4233043;
    public static final int HOVER_OVERLAY = 0x22FFFFFF;
    public static final int PRESS_OVERLAY = 0x28000000;

    // Scrollbar.
    public static final int SCROLL_TRACK = 0x33000000;
    public static final int SCROLL_THUMB = 0xFF4A5468;
    public static final int SCROLL_THUMB_HOVER = 0xFF45D0C0;
}
