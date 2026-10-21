package dev.veltraph.client.navigation;

import java.util.LinkedHashMap;
import java.util.List;

/** Bounded geometry observations only. Revalidate after the final role callback with native-only
 * readers; no snapshot/Boolean here grants gameplay input or contact/arrival authority. */
public final class GroundGeometryCellJournal {
    public record Cell(int x, int y, int z) {}
    public record Box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {}
    public record Observation(Object nativeState, List<Box> shape, float friction, float speedFactor,
            List<Box> supportShape, boolean sturdyUp) {
        public Observation {
            shape = shape == null ? null : List.copyOf(shape);
            supportShape = supportShape == null ? null : List.copyOf(supportShape);
        }
        public Observation(Object nativeState, List<Box> shape, float friction, float speedFactor) {
            this(nativeState, shape, friction, speedFactor, shape, true);
        }
        boolean same(Observation other) {
            return other != null && nativeState != null && nativeState == other.nativeState
                    && shape != null && shape.equals(other.shape) && supportShape != null
                    && supportShape.equals(other.supportShape) && sturdyUp == other.sturdyUp
                    && Float.floatToIntBits(friction) == Float.floatToIntBits(other.friction)
                    && Float.floatToIntBits(speedFactor) == Float.floatToIntBits(other.speedFactor);
        }
    }
    @FunctionalInterface public interface NativeReader { Observation read(Cell cell); }
    private final LinkedHashMap<Cell,Observation> cells = new LinkedHashMap<>();
    private final int limit; private boolean refused;
    public GroundGeometryCellJournal(int limit) {
        if (limit < 1 || limit > 4096) throw new IllegalArgumentException("Bounded cell limit required");
        this.limit = limit;
    }
    public boolean observe(Cell cell, Observation value) {
        if (refused || cell == null || value == null || value.nativeState() == null || value.shape() == null || value.supportShape() == null) {
            refused = true; return false;
        }
        var old = cells.get(cell);
        if (old != null && !old.same(value) || old == null && cells.size() >= limit) {
            refused = true; return false;
        }
        cells.putIfAbsent(cell, value); return true;
    }
    public int size() { return cells.size(); }
    /** Intended caller performs one final actual role/custody callback BEFORE this method.
     * Reader/budget are then native-only, bounded and must not invoke module/role callbacks. */
    public boolean revalidate(NativeReader reader, java.util.function.BooleanSupplier nativeBudget) {
        if (refused || cells.isEmpty() || reader == null || nativeBudget == null) return false;
        for (var entry : cells.entrySet()) {
            if (!nativeBudget.getAsBoolean() || !entry.getValue().same(reader.read(entry.getKey()))) return false;
        }
        return nativeBudget.getAsBoolean();
    }
}
