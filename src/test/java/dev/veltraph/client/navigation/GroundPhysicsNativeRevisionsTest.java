package dev.veltraph.client.navigation;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GroundPhysicsNativeRevisionsTest {
    @Test void unchangedWatchedNativeObjectStaysCurrent() {
        var object = new Object(); var token = GroundPhysicsNativeRevisions.watch(object);
        assertNotNull(token); assertTrue(token.current()); assertTrue(token.current());
    }
    @Test void sameValueUpdateAndABAStillInvalidateOriginal() {
        var object = new Object(); var original = GroundPhysicsNativeRevisions.watch(object);
        GroundPhysicsNativeRevisions.mutation(object);
        GroundPhysicsNativeRevisions.mutation(object);
        assertFalse(original.current());
        var fresh = GroundPhysicsNativeRevisions.watch(object);
        assertNotNull(fresh); assertTrue(fresh.current()); assertFalse(original.current());
    }
    @Test void foreignActorDoesNotRetireUnrelatedProfile() {
        var actor = new Object(); var foreign = new Object();
        var token = GroundPhysicsNativeRevisions.watch(actor);
        GroundPhysicsNativeRevisions.mutation(foreign);
        assertTrue(token.current());
    }
    @Test void identityJournalNeverCallsUserEquality() {
        class Trap { @Override public boolean equals(Object other) { throw new AssertionError("equals callback"); }
                     @Override public int hashCode() { throw new AssertionError("hashCode callback"); } }
        var actor = new Trap(); var token = GroundPhysicsNativeRevisions.watch(actor);
        assertNotNull(token); assertTrue(token.current());
        GroundPhysicsNativeRevisions.mutation(new Trap()); assertTrue(token.current());
        GroundPhysicsNativeRevisions.mutation(actor); assertFalse(token.current());
    }
    @Test void nullCannotCreateOrReviveToken() {
        assertNull(GroundPhysicsNativeRevisions.watch(null));
        GroundPhysicsNativeRevisions.mutation(null);
    }
}
