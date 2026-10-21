package dev.veltraph.client.combat.heal;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HealSourceEndLedgerTest {
    @Test void processEndTimestampBlocksOnlyNewAcquisitionAndUsesStrictBoundary(){
        var ledger=new HealSourceEndLedger();assertTrue(ledger.mayAcquire(100000));
        ledger.endBeforeRepeatOrRelease(100000);
        assertFalse(ledger.mayAcquire(100199));assertFalse(ledger.mayAcquire(100200));
        assertTrue(ledger.mayAcquire(100201));assertEquals(100000,ledger.endedAt());
    }
    @Test void repeatedEndOverwritesTimestampAndNoReadOrInterruptedResetManufacturesAnotherEnd(){
        var ledger=new HealSourceEndLedger();ledger.endBeforeRepeatOrRelease(100000);
        ledger.mayAcquire(100300);assertEquals(100000,ledger.endedAt());
        ledger.endBeforeRepeatOrRelease(100500);assertFalse(ledger.mayAcquire(100700));
        assertTrue(ledger.mayAcquire(100701));
    }
}
