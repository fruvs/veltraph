package dev.veltraph.client.combat.ghost;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class GhostSourceWalkForbiddenCellsTest {
    @Test void blankUnknownAndExplicitEmptyPolicyHaveDifferentAuthority(){
        var unknown=GhostSourceWalkForbiddenCells.fromActualConfiguredJson("");assertFalse(unknown.available());assertThrows(IllegalStateException.class,unknown::requireObserved);
        var observed=GhostSourceWalkForbiddenCells.fromActualConfiguredJson("[]");assertTrue(observed.available());assertTrue(observed.requireObserved().isEmpty());
        assertEquals(GhostSourceWalkForbiddenCells.Provenance.MODERN_CONFIGURED_OBSERVED,observed.provenance());
    }
    @Test void configuredCoordinatesAreExactIntegersWithoutTruncationOrOverflow(){
        var p=GhostSourceWalkForbiddenCells.fromActualConfiguredJson("[[1,76,-2],[1,76,-2]]");assertEquals(1,p.requireObserved().size());
        assertThrows(ArithmeticException.class,()->GhostSourceWalkForbiddenCells.fromActualConfiguredJson("[[1.5,76,0]]"));
        assertThrows(ArithmeticException.class,()->GhostSourceWalkForbiddenCells.fromActualConfiguredJson("[[2147483648,76,0]]"));
        assertThrows(IllegalArgumentException.class,()->GhostSourceWalkForbiddenCells.fromActualConfiguredJson("[[1,2]]"));
    }
}
