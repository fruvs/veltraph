package dev.veltraph.client.combat.ghost;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class GhostSourceDestinationTest {
    @Test void mainSourceBlockInclusiveCuboidBecomesMaxExclusiveNativeBox(){
        var d=GhostSourceDestination.MAIN_AREA;
        assertTrue(d.contains(114,74,34));assertTrue(d.contains(182.999,99.999,100.999));
        assertFalse(d.contains(183,76,50));assertFalse(d.contains(150,100,50));assertFalse(d.contains(150,76,101));
    }
    @Test void sourceSecondaryExtendedAndWholeMistUseTheirOwnActualBounds(){
        assertTrue(GhostSourceDestination.SECONDARY_AREA.contains(-73,66,92));
        assertFalse(GhostSourceDestination.SECONDARY_AREA.contains(6,76,100));
        assertTrue(GhostSourceDestination.SECONDARY_AREA_EXTENDED.contains(60.5,76,30));
        assertFalse(GhostSourceDestination.SECONDARY_AREA.contains(60.5,76,30));
        assertTrue(GhostSourceDestination.WHOLE_MIST.contains(-73,74,34));assertFalse(GhostSourceDestination.WHOLE_MIST.contains(0,73.999,100));
    }
}
