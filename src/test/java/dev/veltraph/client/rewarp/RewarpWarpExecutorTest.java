package dev.veltraph.client.rewarp;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The physical rewarp action is the recovered /warp garden command. */
class RewarpWarpExecutorTest {

    @Test
    void normalizesToNoLeadingSlash() {
        assertEquals("warp garden", RewarpWarpExecutor.normalizeCommand("/warp garden"));
        assertEquals("warp garden", RewarpWarpExecutor.normalizeCommand("///warp garden"));
        assertEquals("warp garden", RewarpWarpExecutor.normalizeCommand(" warp garden "));
    }

    @Test
    void blankUsesRecoveredDefault() {
        assertEquals("warp garden", RewarpWarpExecutor.normalizeCommand(""));
        assertEquals("warp garden", RewarpWarpExecutor.normalizeCommand(null));
    }

    @Test
    void executeSendsNormalizedCommand() {
        AtomicReference<String> sent = new AtomicReference<>();
        RewarpWarpExecutor executor = new RewarpWarpExecutor(command -> {
            sent.set(command);
            return true;
        });
        assertTrue(executor.execute("/warp garden"));
        assertEquals("warp garden", sent.get());
    }
}
