package dev.veltraph.client.navigation;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class FlightFootprintCoverageTest {
 private static FlightFootprintCoverage.Face f(double a,double b,double c,double d){return new FlightFootprintCoverage.Face(a,b,c,d);}
 @Test void fullNativeFootprintCovered(){assertTrue(FlightFootprintCoverage.covers(f(0,1,0,1),List.of(f(0,1,0,1))));}
 @Test void scaledFootprintNeedsOuterSupport(){assertFalse(FlightFootprintCoverage.covers(f(-.6,.6,-.6,.6),List.of(f(-.3,.3,-.3,.3))));}
 @Test void adjacentNativeTopFacesCoverSeam(){assertTrue(FlightFootprintCoverage.covers(f(-.6,.6,-.6,.6),List.of(f(-1,0,-1,1),f(0,1,-1,1))));}
 @Test void outerCornersDoNotProveInteriorSupport(){assertFalse(FlightFootprintCoverage.covers(f(0,1,0,1),List.of(f(0,.2,0,1),f(.8,1,0,1),f(0,1,0,.2),f(0,1,.8,1))));}
 @Test void singleUlpNativeGapIsNotRoundedAway(){assertFalse(FlightFootprintCoverage.covers(f(0,1,0,1),List.of(f(0,.5,0,1),f(Math.nextUp(.5),1,0,1))));}
 @Test void crossingFaceUnionCoversCompleteFootprint(){assertTrue(FlightFootprintCoverage.covers(f(0,1,0,1),List.of(f(0,.5,0,.5),f(.5,1,0,.5),f(0,1,.5,1))));}
 @Test void partialSideGapRemainsUnsupported(){assertFalse(FlightFootprintCoverage.covers(f(0,1,0,1),List.of(f(0,1,0,.5),f(0,.9,.5,1))));}
 @Test void nonfiniteFacesRefused(){assertFalse(FlightFootprintCoverage.covers(f(0,1,0,1),List.of(f(Double.NaN,1,0,1))));}
 @Test void emptyObservedNativeFacesRefused(){assertFalse(FlightFootprintCoverage.covers(f(0,1,0,1),List.of()));}
 @Test void faceObservationBoundCannotBeExpandedByDuplicates(){var faces=java.util.Collections.nCopies(513,f(0,1,0,1));assertFalse(FlightFootprintCoverage.covers(f(0,1,0,1),faces));}
}
