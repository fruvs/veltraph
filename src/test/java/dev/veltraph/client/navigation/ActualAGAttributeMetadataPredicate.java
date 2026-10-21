package dev.veltraph.client.navigation;
import java.util.List;
/** Source-pinned metadata predicate only; no SDK/native attribute is constructed or admitted. */
final class ActualAGAttributeMetadataPredicate {
    static boolean invalid(List<?> attributes,Class<?> expectedClass){
        for(int attributeIndex=0;attributeIndex<attributes.size();attributeIndex++) {
            var a=attributes.get(attributeIndex);
            if(a==null||a.getClass()!=expectedClass||!((Object)a instanceof GroundPhysicsRevisionTracked))return true;
        }
        return false;
    }
}
