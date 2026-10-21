package dev.veltraph.client.combat.ghost;
import org.junit.jupiter.api.Test;
import dev.veltraph.client.combat.ghost.GhostSourceWalkFrontier.Cell;
import static org.junit.jupiter.api.Assertions.*;
final class GhostSourceWalkPrefetchTest {
    @Test void finiteWindowCoversLocalFootHeadSupportWithout325CellPrism(){var center=new Cell(-5,76,-9);var cells=GhostSourceWalkPrefetch.around(center);
        assertEquals(45,cells.size());assertEquals(45,new java.util.HashSet<>(cells).size());assertTrue(cells.contains(center));
        assertTrue(cells.contains(new Cell(-6,74,-10)));assertTrue(cells.contains(new Cell(-4,78,-8)));assertFalse(cells.contains(new Cell(-5,79,-9)));
        assertThrows(UnsupportedOperationException.class,()->cells.clear());}
    @Test void invalidCellArithmeticDoesNotClampIntoGuessedWorldCells(){assertThrows(ArithmeticException.class,()->GhostSourceWalkPrefetch.around(new Cell(Integer.MAX_VALUE,76,0)));}
}
