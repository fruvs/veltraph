package dev.veltraph.client.rewarp;

import dev.veltraph.client.world.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Ordered, in-memory collection of saved {@link RewarpPoint}s. Pure (no Minecraft dependency) and
 * serialization-friendly: it only holds a {@link List} of plain {@link RewarpPoint} records, so the
 * later JSON-config target can persist {@link #list()} directly without reworking this layer.
 *
 * <p>NOTE: persistence is deliberately out of scope here — Target 2 keeps the store in memory behind
 * {@code RewarpService}; the data model is simply kept clean so a later target can serialize it.
 *
 * <p>Not thread-safe; all access happens on the client thread.
 */
public final class RewarpStore {

    private final List<RewarpPoint> points = new ArrayList<>();

    /** Append a point to the end of the ordered list. */
    public void add(RewarpPoint point) {
        if (point == null) {
            throw new IllegalArgumentException("point must not be null");
        }
        points.add(point);
    }

    /** Remove every saved point. */
    public void clear() {
        points.clear();
    }

    /**
     * Replace the point at {@code index} with {@code point}.
     *
     * @throws IndexOutOfBoundsException if {@code index} is not a valid position
     */
    public void replace(int index, RewarpPoint point) {
        if (point == null) {
            throw new IllegalArgumentException("point must not be null");
        }
        if (index < 0 || index >= points.size()) {
            throw new IndexOutOfBoundsException("no rewarp point at index " + index + " (size " + points.size() + ")");
        }
        points.set(index, point);
    }

    /**
     * Replace the most recently added point with {@code point}.
     *
     * @throws IllegalStateException if the store is empty
     */
    public void replaceLast(RewarpPoint point) {
        if (points.isEmpty()) {
            throw new IllegalStateException("no rewarp points to replace");
        }
        replace(points.size() - 1, point);
    }

    /** The point at {@code index}, or empty if out of range. */
    public Optional<RewarpPoint> get(int index) {
        if (index < 0 || index >= points.size()) {
            return Optional.empty();
        }
        return Optional.of(points.get(index));
    }

    /** The most recently added point, or empty when none. */
    public Optional<RewarpPoint> last() {
        return points.isEmpty() ? Optional.empty() : Optional.of(points.get(points.size() - 1));
    }

    /** An immutable snapshot of the ordered points (safe to iterate / serialize). */
    public List<RewarpPoint> list() {
        return List.copyOf(points);
    }

    public int size() {
        return points.size();
    }

    public boolean isEmpty() {
        return points.isEmpty();
    }

    /**
     * The saved point nearest to {@code from} in the same {@code dimension}, by squared block distance.
     * Points in other dimensions are ignored (a rewarp into the wrong world is meaningless). Returns
     * empty if no same-dimension point exists.
     */
    public Optional<RewarpPoint> nearest(String dimension, Vec3 from) {
        RewarpPoint best = null;
        double bestSq = Double.MAX_VALUE;
        for (RewarpPoint p : points) {
            if (dimension != null && !dimension.equals(p.dimension())) {
                continue;
            }
            double sq = squaredDistance(p, from);
            if (sq < bestSq) {
                bestSq = sq;
                best = p;
            }
        }
        return Optional.ofNullable(best);
    }

    private static double squaredDistance(RewarpPoint p, Vec3 from) {
        return p.arrivalPosition().squaredDistanceTo(from);
    }
}
