package dev.veltraph.client.galatea;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class FigSourceClickHistoryTest {
    @Test void fiftyMillisecondsStrictBoundary(){var h=new FigSourceClickHistory(10);assertTrue(h.admit(100,true,true));assertFalse(h.admit(149,true,true));assertTrue(h.admit(150,true,true));}
    @Test void windowExpiryIsStrictTimestampLessThan(){var h=new FigSourceClickHistory(10);for(int i=0;i<10;i++)assertTrue(h.admit(100+i*50,true,true));assertFalse(h.admit(1100,true,true));assertTrue(h.admit(1101,true,true));}
    @Test void previewDoesNotRecordAndEnvironmentRefusalDoesNotPrune(){var h=new FigSourceClickHistory(10);assertTrue(h.admit(100,true,false));assertEquals(0,h.recorded());h.admit(100,true,true);assertFalse(h.admit(2000,false,true));assertEquals(1,h.recorded());assertTrue(h.admit(2000,true,true));assertEquals(1,h.recorded());}
    @Test void attemptHistoryIsIndependentOfInnerKeyEvent(){var h=new FigSourceClickHistory(10);assertTrue(h.admit(100,true,true));assertEquals(1,h.recorded());assertFalse(h.admit(101,true,true));}
}
