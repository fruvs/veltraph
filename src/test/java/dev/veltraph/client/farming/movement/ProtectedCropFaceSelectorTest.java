package dev.veltraph.client.farming.movement;
import dev.veltraph.client.world.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
final class ProtectedCropFaceSelectorTest {
 @Test void actualOutlineTopCanClearProtectedLowerShape(){var eye=new Vec3(.5,1.62,.5);var boxes=List.of(new ProtectedCropFaceSelector.Box(2,1,0,3,2,1));var point=ProtectedCropFaceSelector.select(eye,4.5,true,true,boxes,p->p.y()>1.9).orElseThrow();assertTrue(point.y()<2&&point.y()>1.9);}
 @Test void noProtectedPrimaryHitLeavesOrdinaryForwardFieldIdle(){assertTrue(ProtectedCropFaceSelector.select(new Vec3(.5,1.62,.5),4.5,true,false,List.of(new ProtectedCropFaceSelector.Box(2,1,0,3,2,1)),p->true).isEmpty());}
 @Test void immatureTargetUnknownRayOcclusionAndReachFailClosed(){var boxes=List.of(new ProtectedCropFaceSelector.Box(2,1,0,3,2,1));var eye=new Vec3(.5,1.62,.5);assertTrue(ProtectedCropFaceSelector.select(eye,4.5,false,true,boxes,p->true).isEmpty());assertTrue(ProtectedCropFaceSelector.select(eye,4.5,true,true,boxes,p->false).isEmpty());assertTrue(ProtectedCropFaceSelector.select(eye,1,true,true,boxes,p->true).isEmpty());}
 @Test void forwardExceptionOnlyExplicitNativeWitnessCandidate(){var scope=new ReachableMatureCropSelector.Scope(this,this,this,this,1,1);var eye=new Vec3(.5,1.62,.5);var pos=new BlockPos(2,1,0);var regular=new ReachableMatureCropSelector.Crop(pos,new Vec3(2.5,1.9,.5),true,true,true,true);assertTrue(ReachableMatureCropSelector.selectMissed(scope,eye,4.5,List.of(regular),null,-90).isEmpty());var witnessed=new ReachableMatureCropSelector.Crop(pos,new Vec3(2.5,1.9,.5),true,true,true,true,true);assertTrue(ReachableMatureCropSelector.selectMissed(scope,eye,4.5,List.of(witnessed),null,-90).isPresent());}
 @Test void thinShapeAndInvalidShapeCannotCreateAiryAimPoint(){var eye=new Vec3(.5,1.62,.5);var point=ProtectedCropFaceSelector.select(eye,4.5,true,true,List.of(new ProtectedCropFaceSelector.Box(2,1.5,.4,3,1.51,.6)),p->true).orElseThrow();assertTrue(point.y()>1.5&&point.y()<1.51);assertTrue(ProtectedCropFaceSelector.select(eye,4.5,true,true,List.of(new ProtectedCropFaceSelector.Box(2,Double.NaN,0,3,2,1)),p->true).isEmpty());}
}
