package dev.veltraph.client.navigation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static dev.veltraph.client.navigation.CapturedProfileFinalDeadline.Result.*;
class CapturedProfileFinalDeadlineTest {
    @Test void originalEpisodeDeadlineAllowsBoundaryButNotOneNanosecondLater(){
        long original=100;
        assertEquals(CURRENT,CapturedProfileFinalDeadline.check(original+1_800_000_000L,original,0,true));
        assertEquals(EPISODE_EXPIRED,CapturedProfileFinalDeadline.check(original+1_800_000_001L,original,0,true));
    }
    @Test void planningOrPublicationDelayCannotResetOriginalBeginTime(){
        long original=100;
        assertEquals(CURRENT,CapturedProfileFinalDeadline.check(original+1_700_000_000L,original,0,true));
        assertEquals(EPISODE_EXPIRED,CapturedProfileFinalDeadline.check(original+1_900_000_000L,original,0,true));
    }
    @Test void terminalBrakingKeepsOriginalFourSecondDeadlineOutsideAirbornePhase(){
        long original=100;
        assertEquals(CURRENT,CapturedProfileFinalDeadline.check(original+4_000_000_000L,0,original,false));
        assertEquals(TERMINAL_EXPIRED,CapturedProfileFinalDeadline.check(original+4_000_000_001L,0,original,false));
    }
    @Test void rollbackOfEitherOriginalClockRefusesFinalFence(){
        assertEquals(CLOCK_ROLLBACK,CapturedProfileFinalDeadline.check(99,100,0,true));
        assertEquals(CLOCK_ROLLBACK,CapturedProfileFinalDeadline.check(99,0,100,false));
    }
}
