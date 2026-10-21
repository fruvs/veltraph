package dev.veltraph.client.mining.vein;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
final class VeinWatchedRevisionsTest {
    @Test void repeatedSameValueMutationAndAbaCannotRestoreOldToken() {
        var registry=new VeinWatchedRevisions.Registry();
        Object actual=new Object(); var original=registry.watch(actual); assertTrue(original.current());
        registry.mutation(actual); assertFalse(original.current());
        var afterFirst=registry.watch(actual); assertTrue(afterFirst.current());
        registry.mutation(actual); assertFalse(afterFirst.current()); assertFalse(original.current());
    }
    @Test void callerEqualSubstituteAndUnwatchedMutationsCannotAlterActualHistory() {
        var registry=new VeinWatchedRevisions.Registry();
        Object actual=new String("actual"), equalSubstitute=new String("actual"), foreign=new Object();
        var token=registry.watch(actual); assertTrue(token.current());
        registry.mutation(equalSubstitute); registry.mutation(foreign); assertTrue(token.current());
        registry.mutation(actual); assertFalse(token.current());
    }
    @Test void genuineRestKeepsPositionWhileOutAndBackChangesRemainVisible() {
        var registry=new VeinWatchedRevisions.Registry(); Object actual=new Object(); var original=registry.watch(actual);
        assertFalse(VeinWatchedRevisions.positionChanged(112.875,96,128.125,112.875,96,128.125));
        assertTrue(original.current());
        assertTrue(VeinWatchedRevisions.positionChanged(112.875,96,128.125,112.625,96,128.125));
        registry.mutation(actual); registry.mutation(actual); assertFalse(original.current());
    }
    @Test void saturationRefusesNewIdentityAndDoesNotEvictExistingProof() {
        var registry=new VeinWatchedRevisions.Registry(); var keep=new java.util.ArrayList<Object>();
        for(int i=0;i<128;i++){Object object=new Object();keep.add(object);assertNotNull(registry.watch(object));}
        assertNull(registry.watch(new Object())); assertTrue(registry.watch(keep.getFirst()).current());
    }
    @Test void weakRegistryHasNoStrongNativeOwnerOrPayload() {
        var entry=java.util.Arrays.stream(VeinWatchedRevisions.class.getDeclaredClasses()).filter(c->c.getSimpleName().equals("Entry")).findFirst().orElseThrow();
        assertTrue(java.util.Arrays.stream(entry.getDeclaredFields()).allMatch(f->f.getType().isPrimitive()||f.getType()==java.lang.ref.WeakReference.class));
    }
}
