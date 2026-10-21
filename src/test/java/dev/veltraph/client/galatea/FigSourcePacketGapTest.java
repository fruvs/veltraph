package dev.veltraph.client.galatea;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class FigSourcePacketGapTest {
    @Test void noPacketGapIsStrictAboveFiveHundred(){var g=new FigSourcePacketGap();assertFalse(g.observed(500));assertTrue(g.observed(501));}
    @Test void detectedGapRemainsForStrictHundredAfterReceipt(){var g=new FigSourcePacketGap();g.received(501);assertTrue(g.observed(600));assertFalse(g.observed(601));}
    @Test void ordinaryPacketDoesNotRefreshDetectedGap(){var g=new FigSourcePacketGap();g.received(501);g.received(550);assertEquals(501,g.lastDetectedGapAt());assertFalse(g.observed(601));assertEquals(550,g.lastPacketAt());}
}
