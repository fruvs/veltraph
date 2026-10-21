package dev.veltraph.client.hydra;

import dev.veltraph.client.config.*;
import dev.veltraph.client.observation.unexpected.UnexpectedBehaviorConfig;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

final class CropSpeciesObserverConfigTest {
    @TempDir Path directory;
    private final Object player = new Object(), world = new Object(), connection = new Object(), owner = new Object();
    private CropSpeciesReplantWatcher.Scope scope(ConfigService store) {
        var stamp = CropSpeciesObserverConfig.capture(store).orElse(null);
        return new CropSpeciesReplantWatcher.Scope(player, world, connection, owner, 1,
                stamp != null && stamp.enabled(), stamp);
    }
    private static SourceCropSpeciesReplants.Cell cell(int x) { return new SourceCropSpeciesReplants.Cell(x, 71, 1); }
    private void fourAndPending(CropSpeciesReplantProducer producer) {
        for (int i = 0; i < 4; i++) {
            producer.attack(cell(i), "minecraft:wheat", i * 2000L);
            producer.tick(i * 2000L + 1000, c -> Optional.of("minecraft:carrots"));
        }
        producer.attack(cell(99), "minecraft:wheat", 8000);
    }
    @Test void actualMapUnobservedEnabledFalseTrueCannotDeliverPreDisableCache() {
        var config = new MapConfigService(); var findings = new AtomicInteger();
        var watcher = new CropSpeciesReplantWatcher(() -> 4);
        var producer = new CropSpeciesReplantProducer(watcher, () -> scope(config), f -> findings.incrementAndGet());
        fourAndPending(producer);
        config.set(UnexpectedBehaviorConfig.ENABLED, false); config.set(UnexpectedBehaviorConfig.ENABLED, true);
        assertEquals(2, config.revision(UnexpectedBehaviorConfig.ENABLED).orElseThrow());
        producer.tick(9000, c -> Optional.of("minecraft:carrots"));
        assertEquals(0, findings.get()); assertEquals(0, watcher.pendingCount()); assertEquals(0, watcher.timestampCount());
    }
    @Test void actualDurableJsonLoadsCannotHideUnobservedEnabledFalseTrue() throws Exception {
        var config = new JsonConfigService(CropSpeciesObserverConfig.KEYS); var findings = new AtomicInteger();
        var watcher = new CropSpeciesReplantWatcher(() -> 4);
        var producer = new CropSpeciesReplantProducer(watcher, () -> scope(config), f -> findings.incrementAndGet());
        fourAndPending(producer); var file = directory.resolve("observer.json");
        Files.writeString(file, ConfigCodec.encode(Map.of(UnexpectedBehaviorConfig.ENABLED.name(), false), List.of()));
        config.load(file, null); assertFalse(config.isDirty());
        Files.writeString(file, ConfigCodec.encode(Map.of(UnexpectedBehaviorConfig.ENABLED.name(), true), List.of()));
        config.load(file, null); assertFalse(config.isDirty());
        assertEquals(2, config.revision(UnexpectedBehaviorConfig.ENABLED).orElseThrow());
        producer.tick(9000, c -> Optional.of("minecraft:carrots"));
        assertEquals(0, findings.get()); assertEquals(0, watcher.pendingCount()); assertEquals(0, watcher.timestampCount());
    }
    @Test void newlyEnabledAutoStopCannotUsePreviouslyAccumulatedHits() {
        var config = new MapConfigService(); var stopped = new AtomicInteger();
        var producer = new CropSpeciesReplantProducer(new CropSpeciesReplantWatcher(() -> 4),
                () -> scope(config), f -> { if (f.scope().configStamp().autoStop()) stopped.incrementAndGet(); });
        fourAndPending(producer); config.set(UnexpectedBehaviorConfig.AUTO_STOP, true);
        producer.tick(9000, c -> Optional.of("minecraft:carrots")); assertEquals(0, stopped.get());
        for (int i = 0; i < 5; i++) {
            producer.attack(cell(i), "minecraft:wheat", 10_000 + i * 2000L);
            producer.tick(11_000 + i * 2000L, c -> Optional.of("minecraft:carrots"));
        }
        assertEquals(1, stopped.get());
    }
    @Test void notificationAndCooldownChangeAndRevertAlsoResetOldCache() {
        for (boolean notification : new boolean[]{true, false}) {
            var config = new MapConfigService(); var findings = new AtomicInteger();
            var watcher = new CropSpeciesReplantWatcher(() -> 4);
            var producer = new CropSpeciesReplantProducer(watcher, () -> scope(config), f -> findings.incrementAndGet());
            fourAndPending(producer);
            if (notification) { config.set(UnexpectedBehaviorConfig.NOTIFY, false); config.set(UnexpectedBehaviorConfig.NOTIFY, true); }
            else { config.set(UnexpectedBehaviorConfig.ALERT_COOLDOWN_MS, 1000L); config.set(UnexpectedBehaviorConfig.ALERT_COOLDOWN_MS, 5000L); }
            producer.tick(9000, c -> Optional.of("minecraft:carrots"));
            assertEquals(0, findings.get()); assertEquals(0, watcher.pendingCount());
        }
    }
    @Test void unrelatedConfigurationChangeDoesNotHideRealWrongSpeciesFinding() {
        var config = new MapConfigService(); var findings = new AtomicInteger();
        var producer = new CropSpeciesReplantProducer(new CropSpeciesReplantWatcher(() -> 4), () -> scope(config), f -> findings.incrementAndGet());
        fourAndPending(producer); config.set(ConfigKey.ofBool("UNRELATED_FIXTURE_OPTION", false), true);
        producer.tick(9000, c -> Optional.of("minecraft:carrots")); assertEquals(1, findings.get());
    }
    @Test void lateActualMapChangeAndRevertDuringSpeciesReadDeniesStopAndForeignRelease() {
        var config = new MapConfigService(); var findings = new AtomicInteger(); var held = new AtomicReference<>(true);
        var producer = new CropSpeciesReplantProducer(new CropSpeciesReplantWatcher(() -> 4), () -> scope(config), f -> { findings.incrementAndGet(); held.set(false); });
        fourAndPending(producer);
        producer.tick(9000, c -> { config.set(UnexpectedBehaviorConfig.ENABLED, false); config.set(UnexpectedBehaviorConfig.ENABLED, true); return Optional.of("minecraft:carrots"); });
        assertEquals(0, findings.get()); assertTrue(held.get());
    }
    @Test void equalValueNewStoreCannotReuseOriginalStoresEpochs() {
        var config = new AtomicReference<ConfigService>(new MapConfigService()); var findings = new AtomicInteger();
        var producer = new CropSpeciesReplantProducer(new CropSpeciesReplantWatcher(() -> 4), () -> scope(config.get()), f -> findings.incrementAndGet());
        fourAndPending(producer); config.set(new MapConfigService());
        producer.tick(9000, c -> Optional.of("minecraft:carrots")); assertEquals(0, findings.get());
    }
    private ConfigService unsupported(MapConfigService delegate) {
        return new ConfigService() {
            public <T> T get(ConfigKey<T> key) { return delegate.get(key); }
            public <T> void set(ConfigKey<T> key, T value) { delegate.set(key, value); }
            public boolean isSet(ConfigKey<?> key) { return delegate.isSet(key); }
            public void reset(ConfigKey<?> key) { delegate.reset(key); }
        };
    }
    @Test void unsupportedRevisionCapabilityCannotSampleOrReleaseControls() {
        var config = unsupported(new MapConfigService()); var reads = new AtomicInteger(); var findings = new AtomicInteger();
        assertTrue(CropSpeciesObserverConfig.capture(config).isEmpty());
        var producer = new CropSpeciesReplantProducer(new CropSpeciesReplantWatcher(() -> 4), () -> scope(config), f -> findings.incrementAndGet());
        producer.attack(cell(1), "minecraft:wheat", 0);
        producer.tick(1000, c -> { reads.incrementAndGet(); return Optional.of("minecraft:carrots"); });
        assertEquals(0, reads.get()); assertEquals(0, findings.get());
    }
    @Test void configMutationDuringSnapshotReadCannotCreateTornStamp() {
        var values = new MapConfigService();
        var config = new ConfigService() {
            private boolean change = true;
            public OptionalLong revision(ConfigKey<?> key) { return values.revision(key); }
            public <T> T get(ConfigKey<T> key) {
                T value = values.get(key);
                if (change && key == UnexpectedBehaviorConfig.NOTIFY) {
                    change = false; values.set(UnexpectedBehaviorConfig.ENABLED, false); values.set(UnexpectedBehaviorConfig.ENABLED, true);
                }
                return value;
            }
            public <T> void set(ConfigKey<T> key, T value) { values.set(key, value); }
            public boolean isSet(ConfigKey<?> key) { return values.isSet(key); }
            public void reset(ConfigKey<?> key) { values.reset(key); }
        };
        assertTrue(CropSpeciesObserverConfig.capture(config).isEmpty());
        assertTrue(CropSpeciesObserverConfig.capture(config).isPresent());
    }
}
