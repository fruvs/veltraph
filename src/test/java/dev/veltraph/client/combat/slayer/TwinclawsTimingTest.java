package dev.veltraph.client.combat.slayer;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TwinclawsTimingTest {
    @Test void strictCountdownRequiresTheActualWholeLabel() {
        assertEquals(1.25,TwinclawsTiming.seconds("§cTWINCLAWS §e1,25s"));
        for(String value:List.of("TWINCLAWS","Player: TWINCLAWS 1s","TWINCLAWS NaNs","TWINCLAWS 20s","TWINCLAWS -1s","TWINCLAWS 1s extra"))assertTrue(Double.isNaN(TwinclawsTiming.seconds(value)));
    }
    @Test void stablePlateDoesNotGuessAnEarlierAbilityWindowAndRemovalRetainsBoundedQuietWindow() {
        var timing=new TwinclawsTiming();var signal=List.of(new TwinclawsTiming.Signal(5,"TWINCLAWS 1.5s"));
        var first=timing.observe(2,signal,1000);assertEquals(1500,first.remainingMs());
        var later=timing.observe(2,signal,1500);assertEquals(first.castSerial(),later.castSerial());assertEquals(1500,later.remainingMs());
        assertTrue(timing.observe(2,List.of(),2000).active());assertFalse(timing.observe(2,List.of(),2250).active());
    }
    @Test void AmbiguousCountdownSuppressesTimedAbilityAndNewCastOrBossResetsIdentity() {
        var timing=new TwinclawsTiming();var a=timing.observe(2,List.of(new TwinclawsTiming.Signal(5,"TWINCLAWS 0.2s")),1000);
        var b=timing.observe(2,List.of(new TwinclawsTiming.Signal(5,"TWINCLAWS 1.5s")),1100);assertTrue(b.castSerial()>a.castSerial());
        var ambiguous=timing.observe(2,List.of(new TwinclawsTiming.Signal(5,"TWINCLAWS 1s"),new TwinclawsTiming.Signal(6,"TWINCLAWS 1s")),1200);
        assertTrue(ambiguous.active());assertFalse(ambiguous.timed());assertFalse(timing.observe(3,List.of(),1250).active());
    }
}
