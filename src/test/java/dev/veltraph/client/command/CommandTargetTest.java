package dev.veltraph.client.command;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Client-command literal → target screen mapping. */
class CommandTargetTest {

    @Test
    void veltraphOpensSettings() {
        assertEquals(CommandTarget.SETTINGS, CommandTarget.forLiteral("veltraph"));
    }

    @Test
    void macroCommandsOpenAutomations() {
        assertEquals(CommandTarget.AUTOMATIONS, CommandTarget.forLiteral("vmacro"));
        assertEquals(CommandTarget.AUTOMATIONS, CommandTarget.forLiteral("veltraphmacro"));
    }

    @Test
    void unknownLiteralMapsToNone() {
        assertEquals(CommandTarget.NONE, CommandTarget.forLiteral("something"));
    }
}
