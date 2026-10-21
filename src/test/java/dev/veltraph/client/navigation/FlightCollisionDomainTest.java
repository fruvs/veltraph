package dev.veltraph.client.navigation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class FlightCollisionDomainTest {
 @Test void nativeQueryIncludesNeighborHaloBeyondBody(){var d=FlightCollisionDomain.of(.2,76.001,.2,.8,77.8,.8);assertNotNull(d);assertEquals(-1,d.minX());assertEquals(1,d.maxX());assertEquals(75,d.minY());assertEquals(78,d.maxY());assertEquals(36,d.cells());}
 @Test void nativeEpsilonExpandsExactIntegerBoundary(){var d=FlightCollisionDomain.of(0,0,0,1,1,1);assertNotNull(d);assertEquals(-2,d.minX());assertEquals(2,d.maxX());assertEquals(125,d.cells());}
 @Test void fullyLoadedBodyCanStillRequireAdjacentChunk(){var d=FlightCollisionDomain.of(112.2,76.001,67.2,112.8,77.8,67.8);assertNotNull(d);assertEquals(7,112>>4);assertEquals(6,d.minX()>>4);assertEquals(7,d.maxX()>>4);}
 @Test void negativeBoundaryAlsoRequiresNegativeNeighbor(){var d=FlightCollisionDomain.of(-.8,1,-.8,-.2,2,-.2);assertNotNull(d);assertEquals(-2,d.minX());assertEquals(0,d.maxX());}
 @Test void nonfiniteQueryDomainNeverCertifiesLoad(){assertNull(FlightCollisionDomain.of(Double.NaN,0,0,1,1,1));}
 @Test void reversedOrUnboundedDomainUnavailable(){assertNull(FlightCollisionDomain.of(2,0,0,1,1,1));assertNull(FlightCollisionDomain.of(-1e100,0,0,1,1,1));}
 @Test void scaledBodyHaloIsLargerThanInterior(){var d=FlightCollisionDomain.of(111.9,76.001,66.9,113.1,79.6,68.1);assertNotNull(d);assertEquals(150,d.cells());assertTrue(d.cells()>3*4*3);}
}
