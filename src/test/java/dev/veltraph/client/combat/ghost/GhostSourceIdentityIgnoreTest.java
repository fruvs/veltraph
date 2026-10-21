package dev.veltraph.client.combat.ghost;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class GhostSourceIdentityIgnoreTest {
    @Test void queryDoesNotExpireAndCleanupKeepsTenSecondEquality(){
        var ledger=new GhostSourceIdentityIgnore();Object entity=new Object();ledger.observeIgnored(entity,0);
        assertTrue(ledger.contains(entity));ledger.cleanup(10000,e->false);assertTrue(ledger.contains(entity));
        ledger.cleanup(10001,e->false);assertFalse(ledger.contains(entity));
    }
    @Test void entityIdentityNotEqualLabelOrUuidControlsIgnore(){
        var ledger=new GhostSourceIdentityIgnore();Object first=new String("same UUID"),replacement=new String("same UUID");
        ledger.observeIgnored(first,0);assertTrue(ledger.contains(first));assertFalse(ledger.contains(replacement));
    }
    @Test void invalidBodyCleanupAndExplicitClearRevokeRevision(){
        var ledger=new GhostSourceIdentityIgnore();Object entity=new Object();ledger.observeIgnored(entity,0);long before=ledger.revision();
        ledger.cleanup(0,e->e==entity);assertFalse(ledger.contains(entity));assertTrue(ledger.revision()>before);
        ledger.observeIgnored(entity,0);before=ledger.revision();ledger.clear();assertFalse(ledger.contains(entity));assertTrue(ledger.revision()>before);
    }
}
