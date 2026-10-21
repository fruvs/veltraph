package dev.veltraph.client.navigation;
import dev.veltraph.client.world.Vec3;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class GroundSprintContinuationTest {
    private static WalkController.ContinuationBasis broad(Vec3 origin,List<Vec3> points){return new WalkController.ContinuationBasis(origin,points,java.util.Collections.nCopies(points.size(),true));}
    @Test void offOriginalCurveCannotFabricateActualToFirstRecoverySegment(){var actual=new Vec3(1,70,0);var basis=broad(new Vec3(0,70,0),List.of(new Vec3(0,70,2),new Vec3(0,70,5)));assertTrue(GroundSprintContinuation.hint(true,true,actual,26.565f,26.565f,basis).isEmpty());}
    @Test void originalOrMixedFallbackCannotClaimBroadWindow(){var origin=new Vec3(0,70,0);var points=List.of(new Vec3(0,70,2),new Vec3(0,70,5));assertTrue(GroundSprintContinuation.hint(true,true,origin,0,0,new WalkController.ContinuationBasis(origin,points,List.of(false,false))).isEmpty());assertTrue(GroundSprintContinuation.hint(true,true,origin,0,0,new WalkController.ContinuationBasis(origin,points,List.of(true,false))).isEmpty());}
    @Test void activeOwnedSprintCanUseFreshAlignedBrakingRayInsideAdmittedCurvedCourse(){var p=new Vec3(0,70,0);var course=List.of(new Vec3(.1,70,1),new Vec3(.2,70,2),new Vec3(.3,70,3.2),new Vec3(.4,70,4));var target=GroundSprintContinuation.hint(true,true,p,0,-6,broad(p,course)).orElseThrow();assertTrue(p.horizontalDistanceTo(target)>=3);double bearing=Math.toDegrees(Math.atan2(-target.x(),target.z()));assertTrue(Math.abs(bearing)<8);assertTrue(Math.abs(bearing+6)<8);}
    @Test void ownershipNativeSprintAndRemainingDistanceCannotBeInferred(){var p=new Vec3(0,70,0);var c=List.of(new Vec3(0,70,4));assertTrue(GroundSprintContinuation.hint(false,true,p,0,0,broad(p,c)).isEmpty());assertTrue(GroundSprintContinuation.hint(true,false,p,0,0,broad(p,c)).isEmpty());assertTrue(GroundSprintContinuation.hint(true,true,p,0,0,broad(p,List.of(new Vec3(0,70,2.99)))).isEmpty());}
    @Test void rayCannotLeaveAdmittedCorridorOrCrossElevationOrTurnLimit(){var p=new Vec3(0,70,0);assertTrue(GroundSprintContinuation.hint(true,true,p,0,0,broad(p,List.of(new Vec3(2,70,2),new Vec3(4,70,4)))).isEmpty());assertTrue(GroundSprintContinuation.hint(true,true,p,0,16,broad(p,List.of(new Vec3(0,70,4)))).isEmpty());assertTrue(GroundSprintContinuation.hint(true,true,p,0,0,broad(p,List.of(new Vec3(0,71,4)))).isEmpty());}
}
