package dev.veltraph.client.greenhouse;

import java.util.concurrent.Executor;
import java.util.stream.Stream;
import net.minecraft.SharedConstants;
import net.minecraft.commands.Commands;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.ReloadableServerResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.tags.TagLoader;
import net.minecraft.world.flag.FeatureFlags;

/** Vanilla 26.3 registry, tag, and default-component initialization for real ItemStack tests. */
final class GreenhouseNativeTestBootstrap {
    private static HolderLookup.Provider lookup;

    private GreenhouseNativeTestBootstrap() {}

    static synchronized HolderLookup.Provider lookup() {
        if (lookup != null) return lookup;
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        Executor direct = Runnable::run;
        // The only pack is the packaged vanilla SERVER_DATA pack. No world or server is started.
        try (var resources = new MultiPackResourceManager(PackType.SERVER_DATA,
                java.util.List.of(ServerPacksSource.createVanillaPackSource().fullResources()))) {
            var layers = RegistryLayer.createRegistryAccess();
            var tags = TagLoader.loadTagsForExistingRegistries(resources,
                    layers.getLayer(RegistryLayer.STATIC));
            var staticLookups = TagLoader.buildUpdatedLookups(
                    layers.getAccessForLoading(RegistryLayer.WORLD), tags);
            var world = RegistryDataLoader.load(resources, staticLookups,
                    RegistryDataLoader.WORLD_REGISTRIES, direct).join();
            var dimensionLookups = Stream.concat(staticLookups.stream(), world.listRegistries()).toList();
            var dimensions = RegistryDataLoader.load(resources, dimensionLookups,
                    RegistryDataLoader.DIMENSION_REGISTRIES, direct).join();
            layers = layers.replaceFrom(RegistryLayer.WORLD, world, dimensions);
            var loaded = ReloadableServerResources.loadResources(resources, layers, tags,
                    FeatureFlags.DEFAULT_FLAGS, Commands.CommandSelection.ALL,
                    PermissionSet.ALL_PERMISSIONS, direct, direct).join();
            // Vanilla applies the baked component initializers here, after registry/tag loading.
            loaded.updateComponentsAndStaticRegistryTags();
            lookup = loaded.fullRegistries().lookup();
            return lookup;
        }
    }
}
