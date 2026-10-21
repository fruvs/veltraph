package dev.veltraph.client.hydra;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class CropSpeciesReplantWatcherTest {
    private final Object player = new Object(), world = new Object(), connection = new Object(), owner = new Object();
    private CropSpeciesReplantWatcher.Scope scope(long generation, boolean enabled) {
        return new CropSpeciesReplantWatcher.Scope(player, world, connection, owner, generation, enabled);
    }
    private SourceCropSpeciesReplants.Cell cell(int x) { return new SourceCropSpeciesReplants.Cell(x, 71, 4); }
    private Optional<String> carrot(SourceCropSpeciesReplants.Cell cell) { return Optional.of("minecraft:carrots"); }
    private void one(CropSpeciesReplantWatcher watcher, int x, long start) {
        watcher.attack(scope(1, true), cell(x), "minecraft:wheat", start);
        watcher.poll(scope(1, true), start + 1000, this::carrot);
    }
    @Test void exactOriginalSpeciesCatalogIncludesSeventeenAndExcludesSoilAir() {
        assertEquals(17, CropSpeciesReplantWatcher.SPECIES.size());
        assertTrue(CropSpeciesReplantWatcher.SPECIES.contains("minecraft:pumpkin_stem"));
        assertTrue(CropSpeciesReplantWatcher.SPECIES.contains("minecraft:carved_pumpkin"));
        assertFalse(CropSpeciesReplantWatcher.SPECIES.contains("minecraft:farmland"));
        assertFalse(CropSpeciesReplantWatcher.SPECIES.contains("minecraft:air"));
    }
    @Test void thresholdSampledOnceAndResetNeverRerollsIt() {
        var calls = new AtomicInteger();
        var watcher = new CropSpeciesReplantWatcher(() -> { calls.incrementAndGet(); return 7; });
        watcher.reset(); watcher.attack(scope(1, true), cell(0), "minecraft:wheat", 0);
        watcher.poll(scope(2, true), 1000, this::carrot); watcher.reset();
        assertEquals(1, calls.get()); assertEquals(7, watcher.threshold());
    }
    @Test void invalidRngBoundsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new CropSpeciesReplantWatcher(() -> 3));
        assertThrows(IllegalArgumentException.class, () -> new CropSpeciesReplantWatcher(() -> 8));
    }
    @Test void currentSpeciesReadOnlyAtInclusiveOneSecondNotAttackTime() {
        var watcher = new CropSpeciesReplantWatcher(() -> 4);
        var reads = new AtomicInteger();
        watcher.attack(scope(1, true), cell(0), "minecraft:wheat", 0);
        assertTrue(watcher.poll(scope(1, true), 999, c -> { reads.incrementAndGet(); return carrot(c); }).isEmpty());
        assertEquals(0, reads.get());
        watcher.poll(scope(1, true), 1000, c -> { reads.incrementAndGet(); return carrot(c); });
        assertEquals(1, reads.get()); assertEquals(1, watcher.timestampCount()); assertEquals(0, watcher.pendingCount());
    }
    @Test void sameSpeciesNewbornAndMetadataChangesNeverCount() {
        var watcher = new CropSpeciesReplantWatcher(() -> 4);
        watcher.attack(scope(1, true), cell(0), "minecraft:wheat", 0);
        // Native adapter passes block species, so AGE7 -> AGE0 is this exact same-species input.
        watcher.poll(scope(1, true), 1000, c -> Optional.of("minecraft:wheat"));
        assertEquals(0, watcher.timestampCount()); assertEquals(0, watcher.pendingCount());
    }
    @Test void airUnknownAndNoncropAreConsumedWithoutFinding() {
        for (var replacement : List.of(Optional.of("minecraft:air"), Optional.of("minecraft:dirt"), Optional.<String>empty())) {
            var watcher = new CropSpeciesReplantWatcher(() -> 4);
            watcher.attack(scope(1, true), cell(0), "minecraft:wheat", 0);
            assertTrue(watcher.poll(scope(1, true), 1000, c -> replacement).isEmpty());
            assertEquals(0, watcher.pendingCount()); assertEquals(0, watcher.timestampCount());
        }
    }
    @Test void onlyRecognizedAttackedCropIsCached() {
        var watcher = new CropSpeciesReplantWatcher(() -> 4);
        watcher.attack(scope(1, true), cell(0), "minecraft:dirt", 0);
        watcher.attack(scope(1, true), cell(1), null, 0);
        assertEquals(0, watcher.pendingCount());
    }
    @Test void repeatedAttackOverwritesSamePositionAndRestartsDelay() {
        var watcher = new CropSpeciesReplantWatcher(() -> 4);
        watcher.attack(scope(1, true), cell(0), "minecraft:wheat", 0);
        watcher.attack(scope(1, true), cell(0), "minecraft:carrots", 700);
        watcher.poll(scope(1, true), 1000, c -> Optional.of("minecraft:potatoes"));
        assertEquals(1, watcher.pendingCount()); assertEquals(0, watcher.timestampCount());
        watcher.poll(scope(1, true), 1700, c -> Optional.of("minecraft:potatoes"));
        assertEquals(1, watcher.timestampCount());
    }
    @Test void equalMillisecondMismatchesCollapseAsOriginalTimestampSet() {
        var watcher = new CropSpeciesReplantWatcher(() -> 4);
        for (int i = 0; i < 8; i++) watcher.attack(scope(1, true), cell(i), "minecraft:wheat", 0);
        assertTrue(watcher.poll(scope(1, true), 1000, this::carrot).isEmpty());
        assertEquals(1, watcher.timestampCount()); assertEquals(0, watcher.pendingCount());
    }
    @Test void eachMismatchUsesItsOwnExecutionClockWithinOnePoll() {
        var watcher = new CropSpeciesReplantWatcher(() -> 4);
        for (int i = 0; i < 5; i++) watcher.attack(scope(1, true), cell(i), "minecraft:wheat", 0);
        var clock = new AtomicLong(1000);
        var finding = watcher.poll(scope(1, true), 1000, this::carrot, clock::getAndIncrement).orElseThrow();
        assertEquals(5, finding.count()); assertEquals(0, watcher.timestampCount());
        assertEquals(5, finding.examples().size());
    }
    @Test void countMustStrictlyExceedEachSourceThreshold() {
        for (int threshold : new int[]{4, 7}) {
            var watcher = new CropSpeciesReplantWatcher(() -> threshold);
            for (int i = 0; i < threshold; i++) one(watcher, i, i * 2000L);
            assertEquals(threshold, watcher.timestampCount());
            watcher.attack(scope(1, true), cell(99), "minecraft:wheat", threshold * 2000L);
            var finding = watcher.poll(scope(1, true), threshold * 2000L + 1000, this::carrot).orElseThrow();
            assertEquals(threshold + 1, finding.count()); assertEquals(0, watcher.timestampCount());
        }
    }
    @Test void exactSixtySecondsRetainedThenStrictlyOlderPurged() {
        var watcher = new CropSpeciesReplantWatcher(() -> 4); one(watcher, 0, 0);
        watcher.poll(scope(1, true), 61_000, this::carrot); assertEquals(1, watcher.timestampCount());
        watcher.poll(scope(1, true), 61_001, this::carrot); assertEquals(0, watcher.timestampCount());
    }
    @Test void contextOwnerGenerationOrDisableCannotCarryObservations() {
        for (var changed : List.of(
                new CropSpeciesReplantWatcher.Scope(new Object(), world, connection, owner, 1, true),
                new CropSpeciesReplantWatcher.Scope(player, new Object(), connection, owner, 1, true),
                new CropSpeciesReplantWatcher.Scope(player, world, new Object(), owner, 1, true),
                new CropSpeciesReplantWatcher.Scope(player, world, connection, new Object(), 1, true),
                scope(2, true), scope(1, false))) {
            var watcher = new CropSpeciesReplantWatcher(() -> 4); one(watcher, 0, 0);
            watcher.attack(scope(1, true), cell(1), "minecraft:wheat", 1001);
            assertTrue(watcher.poll(changed, 2001, this::carrot).isEmpty());
            assertEquals(0, watcher.pendingCount()); assertEquals(0, watcher.timestampCount());
        }
    }
    @Test void clockRollbackDiscardsPendingAndCountedHistory() {
        var watcher = new CropSpeciesReplantWatcher(() -> 4); one(watcher, 0, 0);
        watcher.attack(scope(1, true), cell(1), "minecraft:wheat", 1001);
        watcher.poll(scope(1, true), 999, this::carrot);
        assertEquals(0, watcher.pendingCount()); assertEquals(0, watcher.timestampCount());
    }
    @Test void originalResetClearsHitsButStillPendingPositionRemains() {
        var source = new SourceCropSpeciesReplants(() -> 4);
        source.attack(cell(0), "minecraft:wheat", 0);
        source.poll(1000, this::carrot, () -> 1000);
        source.attack(cell(1), "minecraft:wheat", 1001);
        source.clearCount();
        assertEquals(0, source.timestampCount()); assertEquals(1, source.pendingCount());
        source.poll(2001, this::carrot, () -> 2001);
        assertEquals(1, source.timestampCount()); assertEquals(0, source.pendingCount());
    }
    @Test void boundedOverflowCannotCreateFalseDetection() {
        var watcher = new CropSpeciesReplantWatcher(() -> 4);
        for (int i = 0; i <= CropSpeciesReplantWatcher.MAX_PENDING; i++) watcher.attack(scope(1, true), cell(i), "minecraft:wheat", 0);
        assertEquals(0, watcher.pendingCount());
        assertTrue(watcher.poll(scope(1, true), 1000, this::carrot).isEmpty());
    }
}
