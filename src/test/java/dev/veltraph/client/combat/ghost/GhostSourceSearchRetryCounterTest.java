package dev.veltraph.client.combat.ghost;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class GhostSourceSearchRetryCounterTest {
    @Test void firstFiveFailuresScheduleSameHandlerAfterTwentyTicksThenSixthSwitchesRecoveryChild(){
        var c=new GhostSourceSearchRetryCounter();for(int i=1;i<=5;i++){var f=c.recordIssuedFailure();assertEquals(i,f.attempts());
            assertEquals(GhostSourceSearchRetryCounter.Next.SCHEDULE_SAME_HANDLER_ENTRY,f.next());assertEquals(20,f.delayTicks());}
        var sixth=c.recordIssuedFailure();assertEquals(6,sixth.attempts());assertEquals(GhostSourceSearchRetryCounter.Next.SWITCH_PARENT_RECOVERY_CHILD,sixth.next());assertEquals(0,sixth.delayTicks());
    }
    @Test void explicitHandlerResetRestoresCounter(){
        var c=new GhostSourceSearchRetryCounter();for(int i=0;i<6;i++)c.recordIssuedFailure();c.resetFromMappedHandlerReset();
        assertEquals(0,c.attempts());assertEquals(1,c.recordIssuedFailure().attempts());
    }
}
