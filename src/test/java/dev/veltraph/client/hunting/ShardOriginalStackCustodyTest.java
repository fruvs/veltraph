package dev.veltraph.client.hunting;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ShardOriginalStackCustodyTest {
    @Test void selectedEmptySlotSwapHasExactExpectedShardThenRetiresEmpty(){
        var c=new ShardOriginalStackCustody<String>();c.reset("empty");c.issuedSwap("cod2");assertTrue(c.matches("cod2"::equals));assertTrue(c.acknowledgeSwap("cod2"::equals));assertFalse(c.matches("empty"::equals));assertFalse(c.matches("foreign"::equals));
    }
    @Test void originalShardEachAcknowledgedStackRetiresOldIdentityBeforeNextStack(){
        var c=new ShardOriginalStackCustody<String>();c.reset("cod2");c.issuedUse("empty");assertTrue(c.matches("cod2"::equals));assertTrue(c.matches("empty"::equals));assertTrue(c.acknowledgeUse("empty"::equals));assertFalse(c.matches("cod2"::equals));c.issuedSwap("cod2-second");assertFalse(c.matches("cod2"::equals));assertTrue(c.acknowledgeSwap("cod2-second"::equals));c.issuedUse("empty");assertTrue(c.acknowledgeUse("empty"::equals));assertFalse(c.matches("cod2-second"::equals));
    }
    @Test void repeatedJobTransitionsAndCleanupCannotKeepOldPendingExemption(){
        var c=new ShardOriginalStackCustody<String>();c.reset("empty");for(String value:new String[]{"cod1","naga1"}){c.issuedSwap(value);assertTrue(c.acknowledgeSwap(value::equals));c.issuedUse("empty");assertTrue(c.acknowledgeUse("empty"::equals));c.cleanup();assertTrue(c.matches("empty"::equals));assertFalse(c.matches(value::equals));}c.issuedSwap("foreign");c.cleanup();assertFalse(c.matches("foreign"::equals));
    }
    @Test void aggregateQuantityDeltaCannotCommitWrongOriginalSlotAfterImage(){
        var c=new ShardOriginalStackCustody<String>();c.reset("original-cod2");c.issuedUse("empty");
        // Total4→2 caused by another stack disappearing is insufficient: original selected stack stayed.
        assertFalse(c.acknowledgeUse("original-cod2"::equals));assertTrue(c.matches("original-cod2"::equals));
        assertTrue(c.acknowledgeUse("empty"::equals));assertFalse(c.matches("original-cod2"::equals));
    }
}

