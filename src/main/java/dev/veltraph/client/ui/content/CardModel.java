package dev.veltraph.client.ui.content;

import java.util.List;
import java.util.Objects;

/**
 * Pure, Minecraft-free description of one Automations card. The {@code AutomationsScreen} renders its
 * widgets from a list of these, so the entire content layer is unit-testable without the MC runtime.
 *
 * <p>Two flavours:
 * <ul>
 *   <li><b>enabled</b> card — has a backing module ({@link #moduleId} non-null), shows a Start/Stop
 *       button and (for farming) layout/area/tool/state meta;</li>
 *   <li><b>disabled</b> card — {@link #comingSoon} is true, {@link #moduleId} is null, names a real
 *       recovered automation and carries a {@link #blocker} explaining <i>why</i> it is not yet active
 *       (e.g. {@code "Runtime not yet ported"}), so the placeholder is inspectable rather than a bare
 *       "Coming Soon". No category is ever empty.</li>
 * </ul>
 *
 * @param title       card heading (e.g. {@code "Wart/Crops"})
 * @param moduleId    backing module id for the Start/Stop toggle; {@code null} for disabled cards
 * @param comingSoon  whether this is a disabled placeholder (no implemented module yet)
 * @param metaLines   extra info lines shown under the title (layout/area/tool/state; or a hint)
 * @param modeOptions optional in-card mode cycler ({@code null}/empty = no cycler)
 * @param supportedCrops optional auto-detected crop list to surface (Wart/Crops); empty otherwise
 * @param blocker     for disabled cards, the reason it is not yet active (rendered as a muted line);
 *                    {@code null} for enabled cards
 * @param detail      optional structured detail (state machine / requirements / sources) shown in the
 *                    card's expandable detail drawer; {@code null} when the card has no extra detail
 */
public record CardModel(
        String title,
        String moduleId,
        boolean comingSoon,
        List<String> metaLines,
        List<ModeOption> modeOptions,
        List<String> supportedCrops,
        String blocker,
        CardDetail detail) {

    public CardModel {
        Objects.requireNonNull(title, "title");
        metaLines = metaLines == null ? List.of() : List.copyOf(metaLines);
        modeOptions = modeOptions == null ? List.of() : List.copyOf(modeOptions);
        supportedCrops = supportedCrops == null ? List.of() : List.copyOf(supportedCrops);
    }

    /** Whether this card has a non-empty structured detail drawer. */
    public boolean hasDetail() {
        return detail != null && !detail.isEmpty();
    }

    /** Return a copy of this card with the given detail attached (pure; for content builders). */
    public CardModel withDetail(CardDetail detail) {
        return new CardModel(title, moduleId, comingSoon, metaLines, modeOptions, supportedCrops, blocker, detail);
    }

    public boolean enabled() {
        return !comingSoon;
    }

    public boolean hasBlocker() {
        return blocker != null && !blocker.isBlank();
    }

    public boolean hasModeCycler() {
        return !modeOptions.isEmpty();
    }

    public boolean hasSupportedCrops() {
        return !supportedCrops.isEmpty();
    }

    /** Default blocker for an automation that is recovered but not yet wired to a runtime. */
    public static final String DEFAULT_BLOCKER = "Runtime not yet ported";

    /** A real, clickable, module-backed card. */
    public static CardModel module(String title, String moduleId, List<String> metaLines) {
        return new CardModel(title, moduleId, false, metaLines, List.of(), List.of(), null, null);
    }

    /**
     * A disabled, inspectable card that names a real recovered automation and explains its blocker.
     * {@code comingSoon=true}, {@code moduleId=null}.
     */
    public static CardModel disabled(String title, List<String> metaLines, String blocker) {
        Objects.requireNonNull(blocker, "blocker");
        return new CardModel(title, null, true, metaLines, List.of(), List.of(), blocker, null);
    }

    /**
     * Back-compat placeholder factory. Delegates to {@link #disabled} with the default blocker so the
     * card is still inspectable (carries a non-null blocker) rather than a bare "Coming Soon".
     */
    public static CardModel comingSoon(String title) {
        return disabled(title, List.of(), DEFAULT_BLOCKER);
    }

    /** One selectable mode in a card's mode cycler (label + the {@code FarmMovementMode} name). */
    public record ModeOption(String label, String movementMode) {
        public ModeOption {
            Objects.requireNonNull(label, "label");
            Objects.requireNonNull(movementMode, "movementMode");
        }
    }
}
