package dev.veltraph.client.ui.content;

import java.util.Objects;

/**
 * Pure, Minecraft-free description of one Settings row. The {@code SettingsScreen} renders its widgets
 * from a list of these, so the whole Settings content layer is unit-testable without the MC runtime.
 *
 * <p>Row kinds:
 * <ul>
 *   <li>{@link Kind#SECTION} — a section header label (no control);</li>
 *   <li>{@link Kind#TOGGLE} — a boolean config row keyed by {@link #configKey};</li>
 *   <li>{@link Kind#ENUM} — an enum cycler config row keyed by {@link #configKey} over {@link #enumType};</li>
 *   <li>{@link Kind#VALUE} — an editable string/number config row keyed by {@link #configKey};</li>
 *   <li>{@link Kind#BUTTON} — a clickable action button that runs the dev-test {@link #actionId};</li>
 *   <li>{@link Kind#INFO} — a read-only status line whose dynamic text the screen resolves from
 *       {@link #actionId} (an info id);</li>
 *   <li>{@link Kind#COMING_SOON} — a disabled, inspectable placeholder that names a recovered config group
 *       via {@link #label} and explains its {@link #blocker}.</li>
 * </ul>
 *
 * @param kind      row kind
 * @param label     row label / section title / button label
 * @param configKey backing {@code ConfigKey} name for TOGGLE/ENUM/VALUE rows; {@code null} otherwise
 * @param enumType  the enum class for ENUM rows; {@code null} otherwise
 * @param blocker   for COMING_SOON rows, the reason the config group is not yet active; {@code null} otherwise
 * @param actionId  for BUTTON rows, the dev-test action id; for INFO rows, the info id; {@code null} otherwise
 */
public record RowModel(
        Kind kind,
        String label,
        String configKey,
        Class<? extends Enum<?>> enumType,
        String blocker,
        String actionId) {

    public enum Kind { SECTION, TOGGLE, ENUM, VALUE, BUTTON, INFO, COMING_SOON }

    public RowModel {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(label, "label");
    }

    public boolean enabled() {
        return kind != Kind.COMING_SOON;
    }

    public boolean hasBlocker() {
        return blocker != null && !blocker.isBlank();
    }

    /** Default blocker for a recovered config group that is not yet wired to a runtime. */
    public static final String DEFAULT_BLOCKER = "Runtime not yet ported";

    public static RowModel section(String label) {
        return new RowModel(Kind.SECTION, label, null, null, null, null);
    }

    public static RowModel toggle(String label, String configKey) {
        return new RowModel(Kind.TOGGLE, label, Objects.requireNonNull(configKey, "configKey"),
                null, null, null);
    }

    public static RowModel enumRow(String label, String configKey, Class<? extends Enum<?>> enumType) {
        return new RowModel(Kind.ENUM, label,
                Objects.requireNonNull(configKey, "configKey"),
                Objects.requireNonNull(enumType, "enumType"), null, null);
    }

    public static RowModel value(String label, String configKey) {
        return new RowModel(Kind.VALUE, label, Objects.requireNonNull(configKey, "configKey"),
                null, null, null);
    }

    /** A clickable button that runs the dev-test action {@code actionId}. */
    public static RowModel button(String label, String actionId) {
        return new RowModel(Kind.BUTTON, label, null, null, null,
                Objects.requireNonNull(actionId, "actionId"));
    }

    /** A read-only status line; the screen resolves the dynamic text from {@code infoId}. */
    public static RowModel info(String label, String infoId) {
        return new RowModel(Kind.INFO, label, null, null, null,
                Objects.requireNonNull(infoId, "infoId"));
    }

    /** A disabled, inspectable Settings row that names a recovered config group and explains its blocker. */
    public static RowModel disabled(String label, String blocker) {
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(blocker, "blocker");
        return new RowModel(Kind.COMING_SOON, label, null, null, blocker, null);
    }

    /**
     * Back-compat placeholder factory. Delegates to {@link #disabled} with the default blocker so the
     * row is still inspectable (carries a non-null blocker) rather than a bare "Coming Soon".
     */
    public static RowModel comingSoon() {
        return disabled("Coming Soon", DEFAULT_BLOCKER);
    }
}
