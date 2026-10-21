package dev.veltraph.client.navigation;

import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

final class DormantGroundPublicationTest {
    @Test void currentReadsLivePlanningFenceWithoutCachingPermission() {
        var available = new AtomicBoolean(true);
        var actor = new DormantGroundPublication(available::get);
        assertTrue(actor.current()); available.set(false); assertFalse(actor.current());
        available.set(true); assertTrue(actor.current());
    }
    @Test void refusedCameraNeverCreatesAQualifiedTicket() {
        var actor = new DormantGroundPublication(() -> true);
        assertTrue(actor.current()); assertTrue(actor.publishYaw(30) <= 0);
        assertTrue(actor.publishYaw(Float.NaN) <= 0);
    }
    @Test void everyInputAndReleaseRefusesWhileGeometryIsCurrent() {
        var actor = new DormantGroundPublication(() -> true);
        assertSame(actor, GroundPublicationInputs.require(actor, actor));
        assertThrows(GroundPublicationRevokedException.class, () -> actor.setForwardHeld(true));
        assertThrows(GroundPublicationRevokedException.class, () -> actor.setBackHeld(false));
        assertThrows(GroundPublicationRevokedException.class, () -> actor.setLeftHeld(true));
        assertThrows(GroundPublicationRevokedException.class, () -> actor.setRightHeld(false));
        assertThrows(GroundPublicationRevokedException.class, () -> actor.setSneakHeld(true));
        assertThrows(GroundPublicationRevokedException.class, () -> actor.setJumpHeld(false));
        assertThrows(GroundPublicationRevokedException.class, () -> actor.setSprintHeld(true));
        assertThrows(GroundPublicationRevokedException.class, actor::releaseAllMovement);
    }
    @Test void accidentallyTickedCandidateCannotReachAKeyEffect() {
        var actor = new DormantGroundPublication(() -> true);
        var keys = new AtomicInteger(); var cameraAcknowledgements = new AtomicInteger();
        assertFalse(GroundPublicationFence.publish(actor, () -> true, true, 45,
            cameraAcknowledgements::incrementAndGet, keys::incrementAndGet));
        assertEquals(0, keys.get()); assertEquals(0, cameraAcknowledgements.get());
    }
    @Test void candidateCleanupHasNoCallbackOrPermissionRead() {
        var reads = new AtomicInteger();
        var actor = new DormantGroundPublication(() -> { reads.incrementAndGet(); return true; });
        actor.cleanupOwned(); assertEquals(0, reads.get());
    }
    @Test void foreignActorCannotBindDormantInput() {
        var first = new DormantGroundPublication(() -> true);
        var foreign = new DormantGroundPublication(() -> true);
        assertThrows(IllegalArgumentException.class, () -> GroundPublicationInputs.require(first, foreign));
    }
}
