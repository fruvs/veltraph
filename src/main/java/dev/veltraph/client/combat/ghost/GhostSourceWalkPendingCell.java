package dev.veltraph.client.combat.ghost;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/** Exact native observation is pending, never UNKNOWN-as-AIR or a native-read permission.
 * Stackless internal control transfer releases a pure solver thread until this same future resolves. */
final class GhostSourceWalkPendingCell extends RuntimeException {
    private final CompletableFuture<?> availability;
    GhostSourceWalkPendingCell(CompletableFuture<?> availability){
        super(null,null,false,false);this.availability=Objects.requireNonNull(availability);
    }
    CompletableFuture<?> availability(){return availability;}
}
