package dev.veltraph.client.rewarp;

import dev.veltraph.client.world.Vec3;

import java.util.Optional;
















public final class RewarpService {

    private final RewarpStore store;

    private boolean returnPending;
    private RewarpPoint pendingTarget;

    public RewarpService() {
        this(new RewarpStore());
    }

    public RewarpService(RewarpStore store) {
        this.store = store;
    }

    public RewarpStore store() {
        return store;
    }

    /**
     * Pick the point to return to: nearest same-dimension point to {@code from}, else the last saved
     * point. Returns empty only when the store is empty.
     *
     * @param dimension current world dimension id (may be null → distance ranking is skipped)
     * @param from      current player position (may be null → falls back to last)
     */
    public Optional<RewarpPoint> selectTarget(String dimension, Vec3 from) {
        if (store.isEmpty()) {
            return Optional.empty();
        }
        if (dimension != null && from != null) {
            Optional<RewarpPoint> nearest = store.nearest(dimension, from);
            if (nearest.isPresent()) {
                return nearest;
            }
        }
        return store.last();
    }

    /** Physical local return never falls back across area/dimension boundaries. */
    public Optional<RewarpPoint> selectLocalTarget(dev.veltraph.client.game.GameArea area,String dimension,Vec3 from) {
        if(area==null||area==dev.veltraph.client.game.GameArea.UNKNOWN||dimension==null||from==null)return Optional.empty();
        return store.list().stream().filter(p->p.area()==area&&dimension.equals(p.dimension()))
                .min(java.util.Comparator.comparingDouble(p->from.squaredDistanceTo(p.arrivalPosition())));
    }

    /**
     * Request "return to the saved spot" as a recovery step. Selects the target via
     * {@link #selectTarget} and records it as pending. Returns the chosen target, or empty when no
     * rewarp point exists (in which case nothing is marked pending).
     *
     * <p>This request API sets state only; physical recovery is driven by SavedAnchorRecoveryModule. Callers (macro/HUD) read
     * {@link #isReturnPending()} / {@link #pendingTarget()} and may later drive movement.
     */
    public Optional<RewarpPoint> triggerReturn(String dimension, Vec3 from) {
        Optional<RewarpPoint> target = selectTarget(dimension, from);
        if (target.isEmpty()) {
            return Optional.empty();
        }
        returnPending = true;
        pendingTarget = target.get();
        return target;
    }

    /** Clear the pending-return flag (e.g. once the macro has acted on it). */
    public void clearPending() {
        returnPending = false;
        pendingTarget = null;
    }

    public boolean isReturnPending() {
        return returnPending;
    }

    public Optional<RewarpPoint> pendingTarget() {
        return Optional.ofNullable(pendingTarget);
    }

    // --- Read API the HUD target can later use ---

    /** Number of saved rewarp points. */
    public int savedCount() {
        return store.size();
    }

    /** The most recently saved point, or empty when none. */
    public Optional<RewarpPoint> lastPoint() {
        return store.last();
    }

    public boolean hasPoints() {
        return !store.isEmpty();
    }
}
