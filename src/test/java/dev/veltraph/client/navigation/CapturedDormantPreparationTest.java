package dev.veltraph.client.navigation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class CapturedDormantPreparationTest {
    @Test void genuineDormantCursorCanReadGeometryOwnershipButCannotPublishAnyControls(){
        var dormant=new DormantGroundPublication(()->true);
        assertTrue(dormant.current());assertSame(dormant,dormant.actor());assertEquals(-1,dormant.publishYaw(20));
        assertThrows(GroundPublicationRevokedException.class,()->dormant.setForwardHeld(true));
        assertThrows(GroundPublicationRevokedException.class,()->dormant.setBackHeld(true));
        assertThrows(GroundPublicationRevokedException.class,()->dormant.setLeftHeld(true));
        assertThrows(GroundPublicationRevokedException.class,()->dormant.setRightHeld(true));
        assertThrows(GroundPublicationRevokedException.class,()->dormant.setJumpHeld(true));
        assertThrows(GroundPublicationRevokedException.class,()->dormant.setSneakHeld(true));
        assertThrows(GroundPublicationRevokedException.class,()->dormant.setSprintHeld(true));
    }
    @Test void dormantOwnershipVetoRemainsRealAndNoCleanupAuthorityAppears(){
        var current=new java.util.concurrent.atomic.AtomicBoolean(true);var dormant=new DormantGroundPublication(current::get);
        assertTrue(dormant.current());current.set(false);assertFalse(dormant.current());
        dormant.cleanupOwned();assertThrows(GroundPublicationRevokedException.class,dormant::releaseAllMovement);
    }
}
