package dev.veltraph.client.scheduling;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
final class ScheduledContestObservationTest {
    @Test void headerRequiresObservedCountdown(){
        assertEquals(new ScheduledContestObservation(true,-1),ScheduledContestObservation.read(List.of("§eJacob's Contest","Wheat")));
        var missing=ScheduledContestObservation.read(List.of("Jacob's Contest","Time Left: 2m60s"));
        assertTrue(missing.active());assertEquals(-1,missing.remainingSeconds());
        assertFalse(new RestReadiness(true,false,false,false,false,missing.active(),missing.remainingSeconds(),0,100,0).permits());
    }
    @Test void knownClockAndConflictingClocksUseSmallestObservedWindow(){
        assertEquals(new ScheduledContestObservation(true,155),ScheduledContestObservation.read(List.of("Jacob's Contest","§aWheat 2m35s")));
        assertEquals(30,ScheduledContestObservation.read(List.of("Jacob's Contest","Time Left: 2m35s","0m30s")).remainingSeconds());
    }
    @Test void unrelatedTimesAndInactiveHeadersNeverProveContestTime(){
        assertEquals(new ScheduledContestObservation(false,0),ScheduledContestObservation.read(List.of("Wheat 2m35s")));
        assertEquals(-1,ScheduledContestObservation.read(List.of("Jacob's Contest","Next contest: 12m30s","Total Time: 59m59s")).remainingSeconds());
        assertEquals(new ScheduledContestObservation(false,0),ScheduledContestObservation.read(null));
    }
}
