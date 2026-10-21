package dev.veltraph.client.foraging.scheduling;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
class ForagingPauseGateTest {
    @Test void exactTicketRequiresRebindAndReleasesOnce(){var g=new ForagingPauseGate();var token=new Object();var releases=new AtomicInteger();assertTrue(g.pause(token,true,100,releases::incrementAndGet));assertFalse(g.pause(token,true,110,releases::incrementAndGet));assertEquals(1,releases.get());assertEquals(-1,g.resume(token,120));assertFalse(g.rebound(new Object()));assertTrue(g.rebound(token));assertEquals(300,g.resume(token,400));assertFalse(g.paused());}
    @Test void foreignTicketCannotResumeOrOwn(){var g=new ForagingPauseGate();var token=new Object();assertTrue(g.pause(token,true,100,()->{}));assertFalse(g.owns(new Object()));assertEquals(-1,g.resume(new Object(),400));assertTrue(g.owns(token));}
    @Test void manualStopInvalidatesOldTicketAndLaterRun(){var g=new ForagingPauseGate();var old=new Object();var fresh=new Object();assertTrue(g.pause(old,true,100,()->{}));g.clear();assertTrue(g.pause(fresh,true,200,()->{}));assertFalse(g.rebound(old));assertEquals(-1,g.resume(old,400));assertTrue(g.owns(fresh));}
    @Test void unsafeBoundaryNullTicketAndBackwardsClockDeny(){var g=new ForagingPauseGate();assertFalse(g.pause(null,true,100,()->fail()));assertFalse(g.pause(new Object(),false,100,()->fail()));var token=new Object();assertTrue(g.pause(token,true,100,()->{}));assertTrue(g.rebound(token));assertEquals(-1,g.resume(token,99));assertTrue(g.paused());}
}
