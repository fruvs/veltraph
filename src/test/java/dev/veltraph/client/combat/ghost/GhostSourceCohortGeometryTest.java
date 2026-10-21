package dev.veltraph.client.combat.ghost;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class GhostSourceCohortGeometryTest {
    @Test void strictSourceHeightAndOriginalDestinationAreBothRequired(){
        assertFalse(GhostSourceCohortGeometry.sourcePositionEligible(GhostSourceDestination.MAIN_AREA,150,73.50001,50)); // Original destination excludes Y below 74.
        assertTrue(GhostSourceCohortGeometry.sourcePositionEligible(GhostSourceDestination.MAIN_AREA,150,74,50));
        assertTrue(GhostSourceCohortGeometry.sourcePositionEligible(GhostSourceDestination.MAIN_AREA,150,78.49999,50));
        assertFalse(GhostSourceCohortGeometry.sourcePositionEligible(GhostSourceDestination.MAIN_AREA,150,78.5,50));
        assertFalse(GhostSourceCohortGeometry.sourcePositionEligible(GhostSourceDestination.SECONDARY_AREA,0,73.5,100));
        assertTrue(GhostSourceCohortGeometry.sourcePositionEligible(GhostSourceDestination.SECONDARY_AREA,0,73.50001,100));
        assertFalse(GhostSourceCohortGeometry.sourcePositionEligible(GhostSourceDestination.MAIN_AREA,183,76,50));
    }
    @Test void unavailableNonfiniteAndInvertedGeometryDeclinesBeforeNativeObservation(){
        for(double value:new double[]{Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY}){
            assertFalse(GhostSourceCohortGeometry.sourcePositionEligible(GhostSourceDestination.MAIN_AREA,value,76,50));
            assertFalse(GhostSourceCohortGeometry.sourcePositionEligible(GhostSourceDestination.MAIN_AREA,150,value,50));
            assertFalse(GhostSourceCohortGeometry.finiteBox(0,0,0,1,1,value));
        }
        assertFalse(GhostSourceCohortGeometry.sourcePositionEligible(null,150,76,50));
        assertFalse(GhostSourceCohortGeometry.finiteBox(2,0,0,1,1,1));
        assertTrue(GhostSourceCohortGeometry.finiteBox(-100,-6,-100,100,6,100));
    }
}
