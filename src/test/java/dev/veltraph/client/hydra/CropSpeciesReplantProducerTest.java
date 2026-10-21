package dev.veltraph.client.hydra;

import dev.veltraph.client.core.Module;
import dev.veltraph.client.core.ModuleManager;
import dev.veltraph.client.core.ModuleCategory;
import dev.veltraph.client.core.MacroState;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class CropSpeciesReplantProducerTest {
    private final Object player = new Object(), world = new Object(), connection = new Object(), owner = new Object();
    private CropSpeciesReplantWatcher.Scope scope(Object owned, boolean enabled) {
        return new CropSpeciesReplantWatcher.Scope(player, world, connection, owned, 1, enabled);
    }
    private void four(CropSpeciesReplantProducer producer) {
        for (int i = 0; i < 4; i++) {
            producer.attack(new SourceCropSpeciesReplants.Cell(i, 71, 0), "minecraft:wheat", i * 2000L);
            producer.tick(i * 2000L + 1000, cell -> Optional.of("minecraft:carrots"));
        }
    }
    @Test void lateSpeciesReadOwnerTransferCannotStopOrReleaseForeignKeys() {
        var current = new AtomicReference<>(scope(owner, true));
        var stops = new AtomicInteger(); var foreignHeld = new AtomicReference<>(true);
        var producer = new CropSpeciesReplantProducer(new CropSpeciesReplantWatcher(() -> 4), current::get,
                finding -> { stops.incrementAndGet(); foreignHeld.set(false); });
        four(producer);
        producer.attack(new SourceCropSpeciesReplants.Cell(99, 71, 0), "minecraft:wheat", 8000);
        producer.tick(9000, cell -> { current.set(scope(new Object(), true)); return Optional.of("minecraft:carrots"); });
        assertEquals(0, stops.get()); assertTrue(foreignHeld.get());
    }
    @Test void configOffDuringReadCannotDeliverThenConfigRestoreCannotResumeOldCache() {
        var current = new AtomicReference<>(scope(owner, true)); var findings = new AtomicInteger();
        var producer = new CropSpeciesReplantProducer(new CropSpeciesReplantWatcher(() -> 4), current::get, f -> findings.incrementAndGet());
        four(producer); producer.attack(new SourceCropSpeciesReplants.Cell(99, 71, 0), "minecraft:wheat", 8000);
        producer.tick(9000, cell -> { current.set(scope(owner, false)); return Optional.of("minecraft:carrots"); });
        current.set(scope(owner, true)); producer.tick(10_000, cell -> Optional.of("minecraft:carrots"));
        assertEquals(0, findings.get());
    }
    private static final class HeldModule implements Module {
        private boolean enabled, held;
        public String id() { return "owned_crop_fixture"; }
        public String displayName() { return id(); }
        public ModuleCategory category() { return ModuleCategory.FARMING; }
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean value) { enabled = value; if (value) onEnable(); else onDisable(); }
        public void onEnable() { held = true; }
        public void onDisable() { held = false; }
        public void onTick() {}
        public MacroState state() { return enabled ? MacroState.RUNNING : MacroState.STOPPED; }
    }
    @Test void actualManagerStopHookReleasesOwnedControlBeforeNotificationFailure() {
        var manager = new ModuleManager(); var module = new HeldModule(); manager.register(module); module.setEnabled(true);
        var stopHooks = new AtomicInteger(); manager.beforeStopAll(stopHooks::incrementAndGet);
        var producer = new CropSpeciesReplantProducer(new CropSpeciesReplantWatcher(() -> 4),
                () -> scope(manager.inputOwner().orElse(null), module.isEnabled()), finding -> {
                    manager.stopAll(); throw new IllegalStateException("notification failed after stop");
                });
        four(producer); producer.attack(new SourceCropSpeciesReplants.Cell(99, 71, 0), "minecraft:wheat", 8000);
        assertThrows(IllegalStateException.class, () -> producer.tick(9000, cell -> Optional.of("minecraft:carrots")));
        assertEquals(1, stopHooks.get()); assertFalse(module.isEnabled()); assertFalse(module.held);
    }
    @Test void noActiveOwnerNeverReadsWorldOrTouchesManualControls() {
        var reads = new AtomicInteger(); var findings = new AtomicInteger();
        var producer = new CropSpeciesReplantProducer(new CropSpeciesReplantWatcher(() -> 4),
                () -> scope(null, true), f -> findings.incrementAndGet());
        producer.attack(new SourceCropSpeciesReplants.Cell(1, 71, 1), "minecraft:wheat", 0);
        producer.tick(1000, cell -> { reads.incrementAndGet(); return Optional.of("minecraft:carrots"); });
        assertEquals(0, reads.get()); assertEquals(0, findings.get());
    }
    @Test void replacingNativeWorldDuringSamplingDropsWholeFinding() {
        var current = new AtomicReference<>(scope(owner, true)); var findings = new AtomicInteger();
        var producer = new CropSpeciesReplantProducer(new CropSpeciesReplantWatcher(() -> 4), current::get, f -> findings.incrementAndGet());
        four(producer); producer.attack(new SourceCropSpeciesReplants.Cell(99, 71, 0), "minecraft:wheat", 8000);
        producer.tick(9000, cell -> { current.set(new CropSpeciesReplantWatcher.Scope(player, new Object(), connection, owner, 1, true)); return Optional.of("minecraft:carrots"); });
        assertEquals(0, findings.get());
    }
}
