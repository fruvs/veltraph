package dev.veltraph.client.combat.ghost;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import dev.veltraph.client.combat.ghost.GhostSourceJumpBoostGate.*;
class GhostSourceJumpBoostGateTest {
    @Test void sourceDefaultAndStrictFiveMinuteBoundaryDoNotInventCooldown(){
        assertFalse(GhostSourceJumpBoostGate.SOURCE_MODIFY_DEFAULT);
        assertEquals(Result.NOT_REQUIRED,GhostSourceJumpBoostGate.evaluate(Observed.YES,Observed.YES,false,300001,0));
        assertEquals(Result.NOT_REQUIRED,GhostSourceJumpBoostGate.evaluate(Observed.YES,Observed.YES,true,300000,0));
        assertEquals(Result.MODIFY_REQUIRED,GhostSourceJumpBoostGate.evaluate(Observed.YES,Observed.YES,true,300001,0));
    }
    @Test void absentEffectCookieUnavailableAndUnknownReceiptsStayDistinct(){
        assertEquals(Result.NOT_REQUIRED,GhostSourceJumpBoostGate.evaluate(Observed.NO,Observed.UNKNOWN,true,300001,0));
        assertEquals(Result.NOT_REQUIRED,GhostSourceJumpBoostGate.evaluate(Observed.YES,Observed.NO,true,300001,0));
        assertEquals(Result.UNKNOWN,GhostSourceJumpBoostGate.evaluate(Observed.UNKNOWN,Observed.YES,true,300001,0));
        assertEquals(Result.UNKNOWN,GhostSourceJumpBoostGate.evaluate(Observed.YES,Observed.UNKNOWN,true,300001,0));
    }
}
