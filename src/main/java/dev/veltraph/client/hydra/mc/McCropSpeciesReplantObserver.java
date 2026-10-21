package dev.veltraph.client.hydra.mc;

import dev.veltraph.client.core.ModuleManager;
import dev.veltraph.client.farming.module.FarmModule;
import dev.veltraph.client.hydra.CropSpeciesReplantProducer;
import dev.veltraph.client.hydra.SourceCropSpeciesReplants;
import dev.veltraph.client.hydra.CropSpeciesReplantWatcher;
import dev.veltraph.client.service.mc.McBlockBreakingService;
import dev.veltraph.client.service.mc.McOwnedRotation;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import dev.veltraph.client.config.ConfigService;
import dev.veltraph.client.hydra.CropSpeciesObserverConfig;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.LongSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;

/** Native owned start-destroy intent plus fresh one-second block-species read; read only. */
public final class McCropSpeciesReplantObserver {
    // Original threshold is a static final sampled once per loaded class, never per activation/poll.
    private static final int SOURCE_THRESHOLD = ThreadLocalRandom.current().nextInt(4, 8);
    private final Minecraft client;
    private final ModuleManager modules;
    private final CropSpeciesObserverConfig.CurrentStore config;
    private final LongSupplier generation;
    private final CropSpeciesReplantProducer producer;
    public record Attack(long serial, long insertionMillis, SourceCropSpeciesReplants.Cell cell,
                         net.minecraft.world.level.block.state.BlockState originalState,
                         CropSpeciesReplantWatcher.Scope scope) {}
    public record Diagnostic(CropSpeciesReplantWatcher.Observation observation, long attacks,
                             Attack lastAttack, long detections, CropSpeciesReplantWatcher.Detection lastDetection) {}
    private long attacks, detections;
    private Attack lastAttack;
    private CropSpeciesReplantWatcher.Detection lastDetection;
    /** Cached/read-only diagnostics; no new scope predicate, block read or source polling. */
    public Diagnostic diagnostic() { return new Diagnostic(producer.observation(), attacks, lastAttack, detections, lastDetection); }
    public McCropSpeciesReplantObserver(Minecraft client, ModuleManager modules,
            Supplier<ConfigService> config, LongSupplier generation,
            Consumer<CropSpeciesReplantWatcher.Detection> findings) {
        this.client = client; this.modules = modules;
        this.config = new CropSpeciesObserverConfig.CurrentStore(config); this.generation = generation;
        producer = new CropSpeciesReplantProducer(
                new CropSpeciesReplantWatcher(() -> SOURCE_THRESHOLD),
                this::scope, finding -> {
                    try { detections++; lastDetection = finding; }
                    catch (Throwable unavailable) { /* Reporting cannot suppress the computed finding. */ }
                    findings.accept(finding);
                });
    }
    private CropSpeciesReplantWatcher.Scope scope() {
        var settings = config.capture().orElse(null);
        var p = client.player;
        var world = client.level;
        var candidate = modules.inputOwner().orElse(null);
        FarmModule owner = candidate instanceof FarmModule farm ? farm : null;
        boolean admitted = client == Minecraft.getInstance() && client.isSameThread() && settings != null && settings.enabled()
                && p != null && world != null && client.getConnection() != null && client.gameMode != null
                && p.isAlive() && p.level() == world && client.getCameraEntity() == p
                && !client.isPaused() && !world.tickRateManager().isEntityFrozen(p)
                && client.gui.screen() == null && p.containerMenu == p.inventoryMenu
                && p.containerMenu.getCarried().isEmpty() && owner != null && owner.isEnabled()
                && !owner.isSuspended() && owner.selectedFarmingToolReady()
                && modules.get(owner.id()).orElse(null) == owner && McOwnedRotation.authority(client) == owner;
        if (admitted) {
            var guard = McBlockBreakingService.floorObservationGuard(owner, List.of());
            admitted = guard.present() && guard.ownerMatches() && guard.nativeMatches();
        }
        return new CropSpeciesReplantWatcher.Scope(p, world, client.getConnection(), owner,
                generation.getAsLong(), admitted, settings);
    }
    private net.minecraft.world.level.block.state.BlockState readState(SourceCropSpeciesReplants.Cell cell) {
        if (client.level == null) return null;
        var pos = new BlockPos(cell.x(), cell.y(), cell.z());
        if (!client.level.isLoaded(pos) || client.level.isOutsideBuildHeight(pos)) return null;
        return client.level.getBlockState(pos);
    }
    private Optional<String> read(SourceCropSpeciesReplants.Cell cell) {
        var state = readState(cell);
        return state == null ? Optional.empty() : Optional.of(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
    }
    public void attack(MultiPlayerGameMode mode, BlockPos pos) {
        try {
            var observed = scope();
            if (mode != client.gameMode || pos == null || !observed.valid()
                    || !McBlockBreakingService.allowsDestroyBlock(mode, pos)) { producer.reset(); return; }
            var cell = new SourceCropSpeciesReplants.Cell(pos.getX(), pos.getY(), pos.getZ());
            var originalState = readState(cell);
            var species = originalState == null ? Optional.<String>empty()
                    : Optional.of(BuiltInRegistries.BLOCK.getKey(originalState.getBlock()).toString());
            var fresh = scope();
            if (!observed.same(fresh) || !fresh.valid()) { producer.reset(); return; }
            if (species.isPresent()) {
                long inserted = System.currentTimeMillis();
                producer.attack(cell, species.get(), inserted);
                try {
                    if (producer.contains(cell)) { attacks++; lastAttack = new Attack(attacks, inserted, cell, originalState, fresh); }
                } catch (Throwable unavailable) { /* Diagnostic lookup/allocation never clears source pending work. */ }
            }
        } catch (Throwable unavailable) { producer.reset(); }
    }
    public void tick(long now) {
        try { producer.tick(now, this::read, System::currentTimeMillis); }
        catch (Throwable unavailable) { producer.reset(); }
    }
    public void reset() { producer.reset(); }
}
