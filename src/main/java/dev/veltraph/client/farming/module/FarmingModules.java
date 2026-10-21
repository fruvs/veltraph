package dev.veltraph.client.farming.module;

import dev.veltraph.client.config.ConfigService;
import dev.veltraph.client.config.FarmingConfig;
import dev.veltraph.client.core.ModuleManager;
import dev.veltraph.client.farming.domain.FarmLayoutRegistry;
import dev.veltraph.client.rewarp.RewarpService;
import dev.veltraph.client.service.FarmServices;

import java.util.List;

/**
 * Factory/registration helper for the farming module family. Pure (no Minecraft), so the same wiring
 * is exercised by both the runtime bootstrap and unit tests.
 */
public final class FarmingModules {

    private FarmingModules() {
    }

    /** Construct all six farming modules against the given services/config/registry. */
    public static List<FarmModule> createAll(FarmServices services,
                                             ConfigService config,
                                             FarmLayoutRegistry registry) {
        return List.of(
                new CactusFarmModule(services, config, registry),
                new WartCropsFarmModule(services, config, registry),
                new PumpkinMelonFarmModule(services, config, registry),
                configuredCaneFlowers(services, config, registry),
                new MushroomFarmModule(services, config, registry),
                new CocoaFarmModule(services, config, registry));
    }

    private static SugarcaneFlowersFarmModule configuredCaneFlowers(FarmServices services,
            ConfigService config, FarmLayoutRegistry registry) {
        var module=new SugarcaneFlowersFarmModule(services,config,registry);
        module.bindSelection(()->new SugarcaneFlowersFarmModule.Selection(
                SugarcaneFlowersFarmModule.Design.valueOf(config.get(FarmingConfig.SUGARCANE_FARM_DESIGN).name()),
                SugarcaneFlowersFarmModule.Crop.valueOf(config.get(FarmingConfig.SUGARCANE_FARM_CROP).name())));
        return module;
    }

    /** Construct and register all six farming modules with the manager. */
    public static void registerAll(ModuleManager manager,
                                   FarmServices services,
                                   ConfigService config,
                                   FarmLayoutRegistry registry) {
        registerAll(manager, services, config, registry, null);
    }

    /**
     * Construct and register all six farming modules, injecting the shared {@link RewarpService} so the
     * {@code REWARP_REQUIRED} pre-start gate is enforced. A null {@code rewarp} skips the gate.
     */
    public static void registerAll(ModuleManager manager,
                                   FarmServices services,
                                   ConfigService config,
                                   FarmLayoutRegistry registry,
                                   RewarpService rewarp) {
        for (FarmModule module : createAll(services, config, registry)) {
            if (rewarp != null) {
                module.setRewarpService(rewarp);
            }
            manager.register(module);
        }
    }
}
