package dev.veltraph.client.hydra;

import java.util.*;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;

/** Exact readable species/time algorithm, independent of native custody and modern cache limits. */
public final class SourceCropSpeciesReplants {
    public static final Set<String> SPECIES = Set.of(
            "minecraft:cocoa", "minecraft:nether_wart", "minecraft:potatoes",
            "minecraft:carrots", "minecraft:wheat", "minecraft:carved_pumpkin",
            "minecraft:pumpkin", "minecraft:melon", "minecraft:red_mushroom",
            "minecraft:brown_mushroom", "minecraft:cactus", "minecraft:sugar_cane",
            "minecraft:sunflower", "minecraft:rose_bush", "minecraft:poppy",
            "minecraft:melon_stem", "minecraft:pumpkin_stem");
    public record Cell(int x, int y, int z) {}
    public record Replacement(Cell cell, String before, String after) {}
    public record Detection(long timeMillis, int count, int threshold, List<Replacement> examples) {}
    @FunctionalInterface public interface SpeciesReader { Optional<String> read(Cell cell); }
    private record Pending(long timeMillis, String species) {}
    private final int threshold;
    private final Map<Cell, Pending> pending = new LinkedHashMap<>();
    private final Set<Long> timestamps = new HashSet<>();
    public SourceCropSpeciesReplants(IntSupplier lifetimeThreshold) {
        threshold = Objects.requireNonNull(lifetimeThreshold).getAsInt();
        if (threshold < 4 || threshold > 7) throw new IllegalArgumentException("Threshold must be 4..7");
    }
    public int threshold() { return threshold; }
    public int pendingCount() { return pending.size(); }
    public int timestampCount() { return timestamps.size(); }
    /** Bounded by the modern wrapper; read-only diagnostic copy, never a source poll. */
    public Set<Long> timestampSnapshot() { return Set.copyOf(timestamps); }
    public boolean contains(Cell cell) { return pending.containsKey(cell); }
    /** Original reset(true) clears only hits, not the still-pending position map. */
    public void clearCount() { timestamps.clear(); }
    /** Explicit modern teardown; not the reference reset(true) operation. */
    public void clearAll() { pending.clear(); timestamps.clear(); }
    public void attack(Cell cell, String species, long now) {
        if (cell != null && species != null && SPECIES.contains(species))
            pending.put(cell, new Pending(now, species));
    }
    public Optional<Detection> poll(long pollTime, SpeciesReader reader, LongSupplier mismatchClock) {
        Objects.requireNonNull(reader); Objects.requireNonNull(mismatchClock);
        timestamps.removeIf(time -> time < pollTime - 60_000L);
        var examples = new ArrayList<Replacement>(8);
        var iterator = pending.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (pollTime - entry.getValue().timeMillis() < 1000L) continue;
            var original = entry.getValue().species();
            var current = reader.read(entry.getKey());
            iterator.remove();
            if (current.isPresent() && SPECIES.contains(current.get()) && !original.equals(current.get())) {
                timestamps.add(mismatchClock.getAsLong());
                if (examples.size() < 8) examples.add(new Replacement(entry.getKey(), original, current.get()));
            }
        }
        if (timestamps.size() <= threshold) return Optional.empty();
        int count = timestamps.size(); timestamps.clear();
        return Optional.of(new Detection(pollTime, count, threshold, List.copyOf(examples)));
    }
}
