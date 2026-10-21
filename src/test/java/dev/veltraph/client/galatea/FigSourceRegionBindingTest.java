package dev.veltraph.client.galatea;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FigSourceRegionBindingTest {
    @Test void exactNativeLineBindsMarshRuleWithoutGlobalAtlasClaim() {
        var binding=new FigSourceRegionBinding("Observed realm line",FigSourceRegionBinding.Region.MOONGLADE_MARSH);
        assertEquals(FigSourceRegionBinding.Region.MOONGLADE_MARSH,binding.requireCurrent(List.of("Other line","Observed realm line")));
    }
    @Test void otherAreaIsExplicitAndNotMissingDataDefault() {
        var binding=new FigSourceRegionBinding("Observed other realm",FigSourceRegionBinding.Region.OTHER_AREA);
        assertEquals(FigSourceRegionBinding.Region.OTHER_AREA,binding.requireCurrent(List.of("Observed other realm")));
        assertThrows(IllegalStateException.class,()->binding.requireCurrent(List.of()));
        assertThrows(IllegalStateException.class,()->binding.requireCurrent(List.of("Observed other realm extra")));
    }
    @Test void unboundOrUnknownRegionFailsParsing() {
        assertThrows(RuntimeException.class,()->FigSourceRegionBinding.parse(""));
        assertThrows(RuntimeException.class,()->FigSourceRegionBinding.parse("{\"schema\":1,\"line\":\"Observed\",\"region\":\"UNKNOWN\"}"));
    }
    @Test void recoveredExactCaptionsBindNativeObservedRegion() {
        assertEquals(FigSourceRegionBinding.Region.MOONGLADE_MARSH,FigSourceRegionBinding.observed(List.of("⏣ Moonglade Marsh")));
        assertEquals(FigSourceRegionBinding.Region.OTHER_AREA,FigSourceRegionBinding.observed(List.of("Area: Lotus Atoll")));
        assertEquals(FigSourceRegionBinding.Region.UNBOUND,FigSourceRegionBinding.observed(List.of("Galatea")));
        assertEquals(FigSourceRegionBinding.Region.UNBOUND,FigSourceRegionBinding.observed(List.of("Moonglade Marsh","Lotus Atoll")));
    }
}
