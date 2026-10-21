package dev.veltraph.client.hydra;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FloorChangeWatcherTest {

    @Test
    void repeatedChangesOverThresholdFireWhileAnchorIsStable() {
        FloorChangeWatcher watcher = new FloorChangeWatcher(10_000L, 3);
        assertFalse(watcher.observe("0,64,0", snapshot(false, false, false, false), 0L));
        assertFalse(watcher.observe("0,64,0", snapshot(true, false, false, false), 100L));
        assertFalse(watcher.observe("0,64,0", snapshot(true, true, false, false), 200L));
        assertFalse(watcher.observe("0,64,0", snapshot(true, true, true, false), 300L));

        assertTrue(watcher.observe("0,64,0", snapshot(true, true, true, true), 400L));
    }

    @Test
    void movingToNewAnchorResetsTheBaseline() {
        FloorChangeWatcher watcher = new FloorChangeWatcher(10_000L, 1);
        assertFalse(watcher.observe("0,64,0", snapshot(false, false), 0L));
        assertFalse(watcher.observe("0,64,0", snapshot(true, false), 100L));

        assertFalse(watcher.observe("1,64,0", snapshot(false, false), 200L));
        assertFalse(watcher.observe("1,64,0", snapshot(true, false), 300L));
    }

    @Test
    void oldChangesExpireFromTheRollingWindow() {
        FloorChangeWatcher watcher = new FloorChangeWatcher(500L, 1);
        assertFalse(watcher.observe("0,64,0", snapshot(false, false), 0L));
        assertFalse(watcher.observe("0,64,0", snapshot(true, false), 100L));

        assertFalse(watcher.observe("0,64,0", snapshot(true, true), 700L));
    }

    @Test
    void resetClearsAccumulatedChanges() {
        FloorChangeWatcher watcher = new FloorChangeWatcher(10_000L, 1);
        assertFalse(watcher.observe("0,64,0", snapshot(false, false), 0L));
        assertFalse(watcher.observe("0,64,0", snapshot(true, false), 100L));
        watcher.reset();

        assertFalse(watcher.observe("0,64,0", snapshot(true, false), 200L));
        assertFalse(watcher.observe("0,64,0", snapshot(true, true), 300L));
    }

    private static Map<String, Boolean> snapshot(boolean... values) {
        Map<String, Boolean> out = new HashMap<>();
        for (int i = 0; i < values.length; i++) {
            out.put("p" + i, values[i]);
        }
        return out;
    }
}
