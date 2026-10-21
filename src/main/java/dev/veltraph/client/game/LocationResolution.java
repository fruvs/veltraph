package dev.veltraph.client.game;

import java.util.List;

/**
 * The result of resolving {@link LocationSignals} to a {@link GameContext}, plus provenance for
 * debugging: which {@link LocationSource} decided it, the text that matched, and human-readable notes.
 *
 * @param context     the resolved game context
 * @param source      which signal class determined the area (or SkyBlock flag)
 * @param matchedText the normalized text that triggered the match ("" if none)
 * @param notes       short diagnostic notes (e.g. why SkyBlock was/ wasn't detected)
 */
public record LocationResolution(GameContext context, LocationSource source, String matchedText,
                                 List<String> notes) {

    public static final LocationResolution NONE =
            new LocationResolution(GameContext.NONE, LocationSource.NONE, "", List.of("no signals"));

    public LocationResolution {
        notes = notes == null ? List.of() : List.copyOf(notes);
        matchedText = matchedText == null ? "" : matchedText;
    }

    /** Confidence label derived from the deciding source, for the debug command. */
    public String confidence() {
        return switch (source) {
            case SCOREBOARD_TITLE, SCOREBOARD_LINE -> "high (scoreboard)";
            case TAB_FOOTER, TAB_PLAYER -> "medium (tab fallback)";
            case BRAND -> "low (brand only)";
            case NONE -> "none";
        };
    }
}
