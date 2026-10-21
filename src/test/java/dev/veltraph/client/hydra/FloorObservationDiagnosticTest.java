package dev.veltraph.client.hydra;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
final class FloorObservationDiagnosticTest {
    @Test void diagnosticRecordsExactComparedCellsWithoutChangingThreshold(){var watcher=new FloorChangeWatcher(10000,1);assertFalse(watcher.observe("same",Map.of("feet",false,"head",false),100));assertFalse(watcher.observe("same",Map.of("feet",true,"head",false),101));assertEquals(List.of("feet"),watcher.diagnostic().changedCells());assertEquals(1,watcher.diagnostic().windowCount());assertTrue(watcher.observe("same",Map.of("feet",true,"head",true),102));assertEquals(List.of("head"),watcher.diagnostic().changedCells());assertEquals(2,watcher.diagnostic().windowCount());assertTrue(watcher.diagnostic().fire());}
    @Test void cachedDiagnosticGetterDoesNotObserveOrAccumulate(){var watcher=new FloorChangeWatcher(10000,1);watcher.observe("same",Map.of("feet",false),100);watcher.observe("same",Map.of("feet",true),101);var cached=watcher.diagnostic();for(int i=0;i<100;i++)assertSame(cached,watcher.diagnostic());assertEquals(1,cached.windowCount());assertFalse(cached.fire());}
    @Test void newAnchorRecordsBaselineInsteadOfWorldMutation(){var watcher=new FloorChangeWatcher(10000,1);watcher.observe("old",Map.of("feet",false),100);assertFalse(watcher.observe("new",Map.of("feet",true),101));assertEquals("new anchor baseline",watcher.diagnostic().reason());assertEquals(0,watcher.diagnostic().windowCount());assertTrue(watcher.diagnostic().changedCells().isEmpty());}
    @Test void diagnosticMapsCannotBeChangedByInputMutation(){var source=new HashMap<String,Boolean>();source.put("feet",false);var watcher=new FloorChangeWatcher();watcher.observe("same",source,100);source.put("feet",true);assertEquals(false,watcher.diagnostic().after().get("feet"));assertThrows(UnsupportedOperationException.class,()->watcher.diagnostic().after().put("foreign",true));}
}
