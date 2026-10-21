package dev.veltraph.client.garden.pests.tracker;
import dev.veltraph.client.garden.pests.PestBoxGeometry;
import dev.veltraph.client.world.Vec3;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PestPlotSignalsTest {
    final PestBoxGeometry.Plot a=new PestBoxGeometry.Plot(1,new Vec3(.5,70,-96.5),0,0),b=new PestBoxGeometry.Plot(2,new Vec3(-96.5,70,.5),0,0);
    final PestTrackedBindings.Signals settings=new PestTrackedBindings.Signals("Plots: ",", ",PestTrackedBindings.CounterSource.TAB,"Pests: ",List.of(a,b));
    @Test void nativeSourceIntegerListAndCounterResolveClosestKnownPlot(){var c=PestPlotSignals.parse(settings,List.of("§aPlots: 2, 1","Pests: 2"),List.of(),8).orElseThrow();assertEquals(2,c.total());assertEquals(a,c.next(new Vec3(.5,70,-96.5)).orElseThrow());}
    @Test void duplicateUnknownLockedScopeOrMalformedIdsNeverAuthorizeTravel(){for(String line:List.of("Plots: 1, 1","Plots: 1, 3","Plots: 1, x","Plots: 0","Plots: 1, "))assertTrue(PestPlotSignals.parse(settings,List.of(line,"Pests: 2"),List.of(),8).isEmpty());}
    @Test void missingDuplicateOrOverBudgetCounterIsUnknown(){for(var lines:List.of(List.of("Plots: 1"),List.of("Plots: 1","Pests: 1","Pests: 1"),List.of("Plots: 1","Pests: 9")))assertTrue(PestPlotSignals.parse(settings,lines,List.of(),8).isEmpty());}
    @Test void nativeZeroIsDistinctFromAbsentCounterAndDoesNotNeedPlotList(){assertEquals(0,PestPlotSignals.parse(settings,List.of("Pests: 0"),List.of(),8).orElseThrow().total());assertTrue(PestPlotSignals.parse(settings,List.of(),List.of(),8).isEmpty());}
}
