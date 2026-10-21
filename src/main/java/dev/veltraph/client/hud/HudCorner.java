package dev.veltraph.client.hud;

import java.util.Locale;
import java.util.Optional;

/**
 * Which screen corner the in-world HUD panel anchors to. Pure (no Minecraft) so it is config-friendly
 * and unit-testable; the renderer maps this to a panel position given the screen size and a margin.
 */
public enum HudCorner {
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT;

    public boolean isTop() {
        return this == TOP_LEFT || this == TOP_RIGHT;
    }

    public boolean isLeft() {
        return this == TOP_LEFT || this == BOTTOM_LEFT;
    }

    /** Parse a corner name (case-insensitive, accepts {@code tl/tr/bl/br} shorthands). */
    public static Optional<HudCorner> parse(String token) {
        if (token == null) {
            return Optional.empty();
        }
        return switch (token.trim().toLowerCase(Locale.ROOT)) {
            case "tl", "top_left", "top-left", "topleft" -> Optional.of(TOP_LEFT);
            case "tr", "top_right", "top-right", "topright" -> Optional.of(TOP_RIGHT);
            case "bl", "bottom_left", "bottom-left", "bottomleft" -> Optional.of(BOTTOM_LEFT);
            case "br", "bottom_right", "bottom-right", "bottomright" -> Optional.of(BOTTOM_RIGHT);
            default -> Optional.empty();
        };
    }
}
