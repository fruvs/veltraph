package dev.veltraph.client.combat.ghost;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class GhostSourceRecoveryConcurrencyTest {
    @Test void sixthChildPreparationAndDeadlineContinueWhileFallbackIsSoleActuator(){
        var turn=GhostSourceRecoveryConcurrency.turn(true,true);
        assertTrue(turn.prepareLeaving());assertTrue(turn.observeLeavingDeadline());assertTrue(turn.observeLeavingEvent());
        assertEquals(GhostSourceRecoveryConcurrency.Writer.FALLBACK,turn.writer());
        var life=new GhostSourceLeavingLifecycle();life.begin(List.of(new GhostSourceWalkFrontier.Cell(1,76,1)),n->0);
        life.captured(true,GhostSourceLeavingLifecycle.Policy.ETHERWARP_OUT,true,10,n->0);
        assertFalse(life.toolDue(14));assertTrue(life.toolDue(15)); // Fallback movement does not pause or recreate this deadline.
        life.toolSelected();assertFalse(life.toolDue(16));
    }
    @Test void fallbackCompletionChangesOnlyActuatorPriorityRatherThanRestartingLeavingClock(){
        var life=new GhostSourceLeavingLifecycle();life.begin(List.of(new GhostSourceWalkFrontier.Cell(1,76,1)),n->0);
        life.captured(true,GhostSourceLeavingLifecycle.Policy.ETHERWARP_OUT,true,10,n->0);
        assertEquals(GhostSourceRecoveryConcurrency.Writer.FALLBACK,GhostSourceRecoveryConcurrency.turn(true,true).writer());
        assertEquals(GhostSourceRecoveryConcurrency.Writer.LEAVING,GhostSourceRecoveryConcurrency.turn(false,true).writer());
        life.pathSucceeded();assertTrue(life.toolDue(15)); // R3/R5 permit the original delay after early cursor completion too.
    }
    @Test void leavingAbsenceCannotGrantLeavingActuationDuringFallbackOrIdle(){
        var onlyFallback=GhostSourceRecoveryConcurrency.turn(true,false);assertFalse(onlyFallback.prepareLeaving());
        assertEquals(GhostSourceRecoveryConcurrency.Writer.FALLBACK,onlyFallback.writer());
        assertEquals(GhostSourceRecoveryConcurrency.Writer.NONE,GhostSourceRecoveryConcurrency.turn(false,false).writer());
    }
}
