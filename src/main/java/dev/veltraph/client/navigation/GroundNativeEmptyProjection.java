package dev.veltraph.client.navigation;

import java.util.Collection;
import java.util.List;

/** Pure empty metadata projection. Null means execute the original nonempty projection; it never
 * means unknown rows may be discarded. Runtime callers use a fresh native ImmutableSet snapshot or
 * their privately immutable captured-effect list. No native value, permission or context is cached.
 */
public final class GroundNativeEmptyProjection {
    private GroundNativeEmptyProjection() {}
    public static <E> List<E> ifEmpty(Collection<?> rows) {
        return rows.isEmpty()?List.of():null;
    }
}
