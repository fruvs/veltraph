package dev.veltraph.client.command;

/**
 * Which Veltraph screen a client command opens. Pure mapping (no Minecraft) so it is unit-testable.
 *
 * <ul>
 *   <li>{@code /veltraph} → {@link #SETTINGS}</li>
 *   <li>{@code /vmacro}, {@code /veltraphmacro} → {@link #AUTOMATIONS}</li>
 * </ul>
 */
public enum CommandTarget {
    SETTINGS,
    AUTOMATIONS,
    NONE;

    public static CommandTarget forLiteral(String literal) {
        return switch (literal) {
            case "veltraph" -> SETTINGS;
            case "vmacro", "veltraphmacro" -> AUTOMATIONS;
            default -> NONE;
        };
    }
}
