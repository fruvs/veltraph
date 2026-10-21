package dev.veltraph.client.hydra;

import dev.veltraph.client.config.*;
import dev.veltraph.client.core.Module;
import dev.veltraph.client.core.ModuleManager;
import dev.veltraph.client.core.ModuleCategory;
import dev.veltraph.client.core.MacroState;
import dev.veltraph.client.observation.unexpected.UnexpectedBehaviorConfig;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class CropSpeciesReplantDeliveryTest {
    private final Object player = new Object(), world = new Object(), connection = new Object();
    private static final class HeldModule implements Module {
        boolean enabled, held;
        public String id() { return "crop_delivery_fixture"; }
        public String displayName() { return id(); }
        public ModuleCategory category() { return ModuleCategory.FARMING; }
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean value) { enabled = value; if (value) onEnable(); else onDisable(); }
        public void onEnable() { held = true; }
        public void onDisable() { held = false; }
        public void onTick() {}
        public MacroState state() { return enabled ? MacroState.RUNNING : MacroState.STOPPED; }
    }
    private MapConfigService stoppingConfig() {
        var config = new MapConfigService(); config.set(UnexpectedBehaviorConfig.AUTO_STOP, true); return config;
    }
    private CropSpeciesReplantWatcher.Detection finding(ConfigService config, Object owner) {
        var scope = new CropSpeciesReplantWatcher.Scope(player, world, connection, owner, 1, true,
                CropSpeciesObserverConfig.capture(config).orElseThrow());
        return new CropSpeciesReplantWatcher.Detection(scope, 9000, 5, 4, List.of());
    }
    private HeldModule running(ModuleManager manager) {
        var module = new HeldModule(); manager.register(module); module.setEnabled(true); return module;
    }
    @Test void realManagerStopHookNotifyRevocationOrABAStopsWithoutStaleAlert() {
        for (boolean restore : new boolean[]{false, true}) {
            var config = stoppingConfig(); var manager = new ModuleManager(); var module = running(manager);
            var alerts = new AtomicInteger();
            manager.beforeStopAll(() -> {
                config.set(UnexpectedBehaviorConfig.NOTIFY, false);
                if (restore) config.set(UnexpectedBehaviorConfig.NOTIFY, true);
            });
            var binding = new CropSpeciesObserverConfig.CurrentStore(() -> config);
            var delivery = new CropSpeciesReplantDelivery(binding::capture, s -> s.world() == world,
                    manager::stopAll, (f, policy) -> alerts.incrementAndGet());
            delivery.deliver(finding(config, module));
            assertFalse(module.isEnabled()); assertFalse(module.held); assertEquals(0, alerts.get());
        }
    }
    @Test void realManagerStopHookCurrentStoreSwapStopsWithoutOldStoreAlert() {
        var configs = new AtomicReference<ConfigService>(stoppingConfig());
        var manager = new ModuleManager(); var module = running(manager); var alerts = new AtomicInteger();
        var original = finding(configs.get(), module);
        manager.beforeStopAll(() -> configs.set(stoppingConfig()));
        var binding = new CropSpeciesObserverConfig.CurrentStore(configs::get);
        var delivery = new CropSpeciesReplantDelivery(binding::capture, s -> s.world() == world,
                manager::stopAll, (f, policy) -> alerts.incrementAndGet());
        delivery.deliver(original);
        assertFalse(module.held); assertEquals(0, alerts.get());
        assertFalse(original.scope().configStamp().same(binding.capture().orElseThrow()));
    }
    @Test void realManagerStopHookWorldChangeRevokesNotificationAfterStop() {
        var config = stoppingConfig(); var currentWorld = new AtomicReference<>(world);
        var manager = new ModuleManager(); var module = running(manager); var alerts = new AtomicInteger();
        manager.beforeStopAll(() -> currentWorld.set(new Object()));
        var delivery = new CropSpeciesReplantDelivery(() -> CropSpeciesObserverConfig.capture(config),
                s -> s.world() == currentWorld.get(), manager::stopAll, (f, policy) -> alerts.incrementAndGet());
        delivery.deliver(finding(config, module));
        assertFalse(module.held); assertEquals(0, alerts.get());
    }
    @Test void intentionalOwnerDisableAllowsFreshNotificationAfterRelease() {
        var config = stoppingConfig(); var manager = new ModuleManager(); var module = running(manager);
        var alerts = new AtomicInteger();
        var delivery = new CropSpeciesReplantDelivery(() -> CropSpeciesObserverConfig.capture(config),
                s -> s.player() == player && s.world() == world && s.connection() == connection,
                manager::stopAll, (f, policy) -> {
                    assertFalse(module.isEnabled()); assertFalse(module.held); assertTrue(policy.notifyUser()); alerts.incrementAndGet();
                });
        delivery.deliver(finding(config, module)); assertEquals(1, alerts.get());
    }
    @Test void failedNotificationCannotPreventEarlierActualOwnedRelease() {
        var config = stoppingConfig(); var manager = new ModuleManager(); var module = running(manager);
        var delivery = new CropSpeciesReplantDelivery(() -> CropSpeciesObserverConfig.capture(config),
                s -> s.world() == world, manager::stopAll, (f, policy) -> { throw new IllegalStateException("alert failure"); });
        assertThrows(IllegalStateException.class, () -> delivery.deliver(finding(config, module)));
        assertFalse(module.held); assertFalse(module.isEnabled());
    }
    @Test void currentStoreBindingUsedByNativeConstructorResetsSharedProducerAfterReplacement() {
        var configs = new AtomicReference<ConfigService>(new MapConfigService());
        var binding = new CropSpeciesObserverConfig.CurrentStore(configs::get);
        var owner = new Object(); var findings = new AtomicInteger();
        var watcher = new CropSpeciesReplantWatcher(() -> 4);
        var producer = new CropSpeciesReplantProducer(watcher, () -> {
            var stamp = binding.capture().orElse(null);
            return new CropSpeciesReplantWatcher.Scope(player, world, connection, owner, 1,
                    stamp != null && stamp.enabled(), stamp);
        }, f -> findings.incrementAndGet());
        for (int i = 0; i < 4; i++) {
            producer.attack(new SourceCropSpeciesReplants.Cell(i, 71, 1), "minecraft:wheat", i * 2000L);
            producer.tick(i * 2000L + 1000, c -> Optional.of("minecraft:carrots"));
        }
        producer.attack(new SourceCropSpeciesReplants.Cell(99, 71, 1), "minecraft:wheat", 8000);
        var original = binding.capture().orElseThrow(); configs.set(new MapConfigService());
        assertFalse(original.same(binding.capture().orElseThrow()));
        producer.tick(9000, c -> Optional.of("minecraft:carrots"));
        assertEquals(0, findings.get()); assertEquals(0, watcher.pendingCount()); assertEquals(0, watcher.timestampCount());
    }
    @Test void configRevokedBeforeDeliveryNeverStopsCurrentControls() {
        var config = stoppingConfig(); var manager = new ModuleManager(); var module = running(manager); var alerts = new AtomicInteger();
        var original = finding(config, module); config.set(UnexpectedBehaviorConfig.ENABLED, false);
        var delivery = new CropSpeciesReplantDelivery(() -> CropSpeciesObserverConfig.capture(config),
                s -> s.world() == world, manager::stopAll, (f, policy) -> alerts.incrementAndGet());
        delivery.deliver(original);
        assertTrue(module.isEnabled()); assertTrue(module.held); assertEquals(0, alerts.get());
    }
}
