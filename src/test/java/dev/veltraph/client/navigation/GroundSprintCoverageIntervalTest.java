package dev.veltraph.client.navigation;
import dev.veltraph.client.world.Vec3;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class GroundSprintCoverageIntervalTest {
    private static WalkController.ContinuationBasis basis(Vec3 origin,List<Vec3> points,List<Boolean> bits){return new WalkController.ContinuationBasis(origin,points,bits);}
    @Test void nonDyadicSharedStripBoundaryIsCoveredWithoutRecursiveApproximation(){var a=new Vec3(0,70,0);var points=List.of(new Vec3(0,70,1),new Vec3(0,70,4));assertTrue(GroundSprintContinuation.covered(a,new Vec3(0,70,3.000001),basis(a,points,List.of(true,true))));}
    @Test void actualR2PositiveCurvedRayKeepsOriginalExpectation(){var a=new Vec3(0,70,0);var points=List.of(new Vec3(.1,70,1),new Vec3(.2,70,2),new Vec3(.3,70,3.2),new Vec3(.4,70,4));assertTrue(GroundSprintContinuation.hint(true,true,a,0,-6,basis(a,points,List.of(true,true,true,true))).isPresent());}
    @Test void uncoveredOriginalFallbackGapCannotBeBridgedByIntervalUnion(){var a=new Vec3(0,70,0);var points=List.of(new Vec3(0,70,1),new Vec3(0,70,2),new Vec3(0,70,4));assertFalse(GroundSprintContinuation.covered(a,new Vec3(0,70,3.000001),basis(a,points,List.of(true,false,true))));}
    @Test void exactTinyFallbackGapIsNotMergedByAnEpsilon(){var a=new Vec3(0,70,0);double after=Math.nextUp(1.0);var points=List.of(new Vec3(0,70,1),new Vec3(0,70,after),new Vec3(0,70,4));assertFalse(GroundSprintContinuation.covered(a,new Vec3(0,70,3.000001),basis(a,points,List.of(true,false,true))));}
    @Test void finiteStripSidesAndEndsRemainExactNotCapsules(){var origin=new Vec3(0,70,0);var b=basis(origin,List.of(new Vec3(0,70,4)),List.of(true));assertTrue(GroundSprintContinuation.covered(new Vec3(.7,70,0),new Vec3(.7,70,3),b));assertFalse(GroundSprintContinuation.covered(new Vec3(.700001,70,0),new Vec3(.700001,70,3),b));assertFalse(GroundSprintContinuation.covered(new Vec3(0,70,-.001),new Vec3(0,70,3),b));}
}
