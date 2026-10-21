package dev.veltraph.client.movement.device;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Production lifecycle kernel tests only; model objects never prove native loan/cleanup permission. */
final class DeviceStartCustodyStateTest {
    private final Object owner=new Object();
    @Test void ordinaryQualifiedSelectionFinishesExactlyOnce(){var s=new DeviceStartCustodyState(owner);
        assertTrue(s.open(owner));assertTrue(s.selectionReturned(owner));assertTrue(s.finish(owner));
        assertFalse(s.finish(owner));assertFalse(s.interrupt(owner));assertFalse(s.beginCleanup(owner));}
    @Test void missingSelectionCannotAdvanceToAiming(){var s=new DeviceStartCustodyState(owner);assertFalse(s.finish(owner));assertTrue(s.open(owner));}
    @Test void genuineStopInsideSelectionNeverAdvancesAfterReturn(){var s=new DeviceStartCustodyState(owner);
        assertTrue(s.interrupt(owner));assertFalse(s.selectionReturned(owner));assertFalse(s.finish(owner));}
    @Test void stopAfterSelectionBeforeGuardCannotAdvance(){var s=new DeviceStartCustodyState(owner);
        assertTrue(s.selectionReturned(owner));assertTrue(s.interrupt(owner));assertFalse(s.open(owner));assertFalse(s.finish(owner));}
    @Test void exactInterruptedTokenOwnsOneCleanupAttempt(){var s=new DeviceStartCustodyState(owner);assertTrue(s.interrupt(owner));
        assertTrue(s.beginCleanup(owner));assertTrue(s.cleanupInFlight(owner));assertFalse(s.beginCleanup(owner));
        s.endCleanup(owner);assertFalse(s.cleanupInFlight(owner));assertFalse(s.beginCleanup(owner));}
    @Test void reentrantCancelDoesNotInvokeAnotherCleanup(){var s=new DeviceStartCustodyState(owner);assertTrue(s.interrupt(owner));assertTrue(s.beginCleanup(owner));
        assertFalse(s.interrupt(owner));assertFalse(s.beginCleanup(owner));assertTrue(s.cleanupInFlight(owner));}
    @Test void foreignOrCopiedIdentityCannotSelectStopCleanupOrFinish(){var s=new DeviceStartCustodyState(owner);var copy=new Object();
        assertFalse(s.selectionReturned(copy));assertFalse(s.interrupt(copy));assertFalse(s.beginCleanup(copy));assertFalse(s.finish(copy));
        assertTrue(s.interrupt(owner));assertFalse(s.beginCleanup(copy));assertTrue(s.beginCleanup(owner));assertFalse(s.cleanupInFlight(copy));}
    @Test void closingAbandonsOpenOrInterruptedProducerWithoutAuthority(){
        for(boolean interrupted:new boolean[]{false,true}){var s=new DeviceStartCustodyState(owner);if(interrupted)assertTrue(s.interrupt(owner));
            s.close(owner);assertFalse(s.open(owner));assertFalse(s.selectionReturned(owner));assertFalse(s.interrupt(owner));
            assertFalse(s.beginCleanup(owner));assertFalse(s.cleanupInFlight(owner));assertFalse(s.finish(owner));}}
    @Test void callerCannotInventCleanupWhileStartIsMerelyOpen(){var s=new DeviceStartCustodyState(owner);
        assertFalse(s.beginCleanup(owner));assertFalse(s.cleanupInFlight(owner));assertTrue(s.selectionReturned(owner));assertTrue(s.finish(owner));}
    @Test void failedCleanupUnwindsAndNeverReplaysOrRearms(){var s=new DeviceStartCustodyState(owner);assertTrue(s.interrupt(owner));assertTrue(s.beginCleanup(owner));
        try{throw new IllegalStateException("original fixture error");}catch(IllegalStateException original){assertEquals("original fixture error",original.getMessage());}
        finally{s.endCleanup(owner);s.close(owner);}assertFalse(s.beginCleanup(owner));assertFalse(s.finish(owner));}
}
