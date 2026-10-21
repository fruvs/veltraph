package dev.veltraph.client.combat.ghost;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class GhostSourceEntityGraceWindowTest {
    @Test void firstCallNotReadyUntilImmutableSessionThresholdEquality(){
        var ledger=new GhostSourceEntityGraceWindow(120);Object entity=new Object();
        assertFalse(ledger.ready(entity,0));assertFalse(ledger.ready(entity,119));assertTrue(ledger.ready(entity,120));
        assertEquals(120,ledger.graceMs());
    }
    @Test void actualEntityIdentityNotReusedUuidOrEqualObjectControlsFirstSeen(){
        var ledger=new GhostSourceEntityGraceWindow(219);Object first=new String("same UUID"),replacement=new String("same UUID");
        assertFalse(ledger.ready(first,0));assertTrue(ledger.ready(first,219));assertFalse(ledger.ready(replacement,219));
        assertFalse(ledger.ready(replacement,437));assertTrue(ledger.ready(replacement,438));
    }
    @Test void explicitCleanupKeepsEqualityThenNewEntryRevokesOldStamp(){
        var ledger=new GhostSourceEntityGraceWindow(120);Object entity=new Object();ledger.ready(entity,0);var stamp=ledger.capture(entity);
        ledger.cleanupExpired(60000);assertTrue(stamp.current());ledger.cleanupExpired(60001);assertFalse(stamp.current());
        assertFalse(ledger.ready(entity,60001));assertFalse(stamp.current());assertTrue(ledger.capture(entity).current());
    }
    @Test void observingAnotherEntityDoesNotRevokeFirstEntityFrameButTimeBoundaryDoesChangeReadyState(){
        var ledger=new GhostSourceEntityGraceWindow(150);Object first=new Object();ledger.ready(first,0);var stamp=ledger.capture(first);
        ledger.ready(new Object(),100);assertTrue(stamp.current());assertFalse(stamp.readyAt(149));assertTrue(stamp.readyAt(150));
        assertThrows(IllegalArgumentException.class,()->new GhostSourceEntityGraceWindow(220));
    }
}
