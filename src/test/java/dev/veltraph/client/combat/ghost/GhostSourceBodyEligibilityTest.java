package dev.veltraph.client.combat.ghost;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class GhostSourceBodyEligibilityTest {
    @Test void sourceFreshGraceBypassesInvalidBodyChecksButDoesNotGrantDefensiveAttackSafety(){
        assertTrue(GhostSourceBodyEligibility.eligible(true,false,false,false,true));
        assertFalse(GhostSourceBodyEligibility.defensiveBodySafe(false,false,true));
    }
    @Test void graceReadyRequiresAliveNativeTickingAndDeadFlagOrHealthClear(){
        assertFalse(GhostSourceBodyEligibility.eligible(true,true,false,true,false));
        assertFalse(GhostSourceBodyEligibility.eligible(true,true,true,false,false));
        assertFalse(GhostSourceBodyEligibility.eligible(true,true,true,true,true));
        assertTrue(GhostSourceBodyEligibility.eligible(true,true,true,true,false));
    }
    @Test void nonlivingDoesNotBorrowLivingGraceOrDeadField(){
        assertFalse(GhostSourceBodyEligibility.eligible(false,false,false,true,false));
        assertTrue(GhostSourceBodyEligibility.eligible(false,false,true,true,true));
    }
}
