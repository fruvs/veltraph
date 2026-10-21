package dev.veltraph.client.combat.heal;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HealSourceEntryTimingTest {
    @Test void strictDebounceRequiresMoreThanTwoHundredMillisecondsAfterPriorEnd(){
        assertFalse(HealSourceEntryTiming.elapsed(100199,100000));
        assertFalse(HealSourceEntryTiming.elapsed(100200,100000));
        assertTrue(HealSourceEntryTiming.elapsed(100201,100000));
        assertFalse(HealSourceEntryTiming.elapsed(99999,100000));
    }
}
