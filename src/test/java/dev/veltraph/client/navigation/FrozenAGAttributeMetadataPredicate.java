package dev.veltraph.client.navigation;
import java.util.List;
/** Source-pinned metadata predicate only; no SDK/native attribute is constructed or admitted. */
final class FrozenAGAttributeMetadataPredicate {
    static boolean invalid(List<?> attributes,Class<?> expectedClass){
        return attributes.stream().anyMatch(a -> a == null || a.getClass() != expectedClass || !((Object)a instanceof GroundPhysicsRevisionTracked));
    }
}
