package dev.veltraph.client.navigation;
import dev.veltraph.client.world.Vec3;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class GroundBroadEdgeProvenanceTest {
    @Test void genuineVariedPieceCarriesPerEdgeBasisButFallbackDoesNot(){var geometry=new DynamicGroundRoutePlanner.Geometry(){public boolean current(){return true;}public boolean standing(Vec3 p){return true;}public boolean sweep(Vec3 a,Vec3 b){return true;}};var p=DynamicGroundRoutePlanner.plan(new Vec3(0,70,0),List.of(new Vec3(0,70,20)),1,0,geometry);assertEquals(DynamicGroundRoutePlanner.Outcome.VARIED,p.outcome());assertEquals(p.course().size(),p.broadEdges().size());assertTrue(p.broadEdges().stream().allMatch(Boolean::booleanValue));var fallback=new DynamicGroundRoutePlanner.Plan(DynamicGroundRoutePlanner.Outcome.ORIGINAL,List.of(new Vec3(0,70,2)),"original");assertEquals(List.of(false),fallback.broadEdges());}
    @Test void ordinaryWalkerCannotProduceBroadBasisAndCertifiedWalkerRetainsExactPreviousPoint(){var w=new WalkController(1);var origin=new Vec3(0,70,0);var points=List.of(new Vec3(0,70,2),new Vec3(0,70,5));w.startCourse(points,origin,100,.14);assertNull(w.locomotionContinuationBasis().previous());w.startCertifiedDynamicCourse(points,origin,100,.14,null,List.of(true,false));assertEquals(origin,w.locomotionContinuationBasis().previous());assertEquals(List.of(true,false),w.locomotionContinuationBasis().broadEdges());}
}
