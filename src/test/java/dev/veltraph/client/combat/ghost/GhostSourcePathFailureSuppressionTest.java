package dev.veltraph.client.combat.ghost;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class GhostSourcePathFailureSuppressionTest {
    GhostSourcePathFailureSuppression.Scope scope(){return new GhostSourcePathFailureSuppression.Scope(new Object(),new Object(),new Object(),1,new Object(),new Object(),new Object());}
    @Test void sameIssuedFailedJobStoresNonNullUuidForStrictSixtySecondWindow(){
        var ledger=new GhostSourcePathFailureSuppression();var s=scope();var uuid=UUID.randomUUID();
        assertTrue(ledger.issue(s).completeFailure(s,uuid,false,0));assertTrue(ledger.suppressed(uuid,59999));assertFalse(ledger.suppressed(uuid,60000));
        var retained=ledger.capture(uuid);ledger.cleanupExpired(60001);assertTrue(retained.current());
    }
    @Test void positivePathAndNullTargetDoNotStoreAndDuplicateCallbackCannotReplay(){
        var ledger=new GhostSourcePathFailureSuppression();var s=scope();var uuid=UUID.randomUUID();var issued=ledger.issue(s);
        assertFalse(issued.completeFailure(s,uuid,true,0));assertFalse(issued.completeFailure(s,uuid,false,1));assertFalse(ledger.suppressed(uuid,1));
        assertFalse(ledger.issue(s).completeFailure(s,null,false,0));
    }
    @Test void staleJobHandlerGenerationOrPwcNeverStores(){
        var ledger=new GhostSourcePathFailureSuppression();var s=scope();var uuid=UUID.randomUUID();
        var changed=new GhostSourcePathFailureSuppression.Scope(s.job(),s.parent(),s.child(),2,s.player(),s.world(),s.connection());
        var stale=ledger.issue(s);assertFalse(stale.completeFailure(changed,uuid,false,0));assertFalse(stale.completeFailure(s,uuid,false,1));assertFalse(ledger.issue(s).completeFailure(scope(),uuid,false,0));
        assertFalse(ledger.suppressed(uuid,0));
    }
    @Test void perUuidOverwriteCleanupAndReinsertRevokeStampWithoutRevokingUnrelatedUuid(){
        var ledger=new GhostSourcePathFailureSuppression();var s=scope();var uuid=UUID.randomUUID();var other=UUID.randomUUID();
        var empty=ledger.capture(uuid);ledger.issue(s).completeFailure(s,other,false,0);assertTrue(empty.current());
        ledger.issue(s).completeFailure(s,uuid,false,0);assertFalse(empty.current());var present=ledger.capture(uuid);
        ledger.issue(s).completeFailure(s,uuid,false,0);assertFalse(present.current());present=ledger.capture(uuid);
        ledger.cleanupExpired(600000);assertTrue(present.current());ledger.cleanupExpired(600001);assertFalse(present.current());
        ledger.issue(s).completeFailure(s,uuid,false,600001);assertFalse(present.current());
    }
}
