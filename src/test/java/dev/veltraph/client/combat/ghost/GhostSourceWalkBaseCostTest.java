package dev.veltraph.client.combat.ghost;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class GhostSourceWalkBaseCostTest {
    @Test void approximateSourceDiagonalLiteralsAreNotExactSquareRoots(){
        assertEquals(1,GhostSourceWalkBaseCost.observedBase(1,0,0,0,76,76));
        assertEquals(1.41,GhostSourceWalkBaseCost.observedBase(1,0,1,0,76,76));
        assertNotEquals(Math.sqrt(2),GhostSourceWalkBaseCost.observedBase(1,0,1,0,76,76));
        assertEquals(1.73,GhostSourceWalkBaseCost.observedBase(1,1,1,1,76,77));
    }
    @Test void standingHeightTruncationAndAsymmetricDeepDropRisePenaltiesRemainExact(){
        assertEquals(1,GhostSourceWalkBaseCost.observedBase(1,1,0,.9,76,77));
        assertEquals(1.41,GhostSourceWalkBaseCost.observedBase(1,1,0,1,76,77));
        assertEquals(5,GhostSourceWalkBaseCost.observedBase(1,-2,0,-2,78,76));
        assertEquals(5.73,GhostSourceWalkBaseCost.observedBase(1,2,0,2,76,78));
        assertThrows(IllegalArgumentException.class,()->GhostSourceWalkBaseCost.observedBase(1,0,0,Double.NaN,76,76));
    }
}
