package dev.veltraph.client.builder;
import dev.veltraph.client.world.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class Source95ToolApproachTest {
    private final BlockPos anchor=new BlockPos(0,70,0),west=new BlockPos(-1,0,0);
    private Source95ToolApproach.Terrain flat(boolean visible){return new Source95ToolApproach.Terrain(){public Optional<Vec3> supportedFeet(BlockPos p){return p.y()==70&&p.x()<0?Optional.of(new Vec3(p.x()+.5,70,p.z()+.5)):Optional.empty();}public boolean requiredFace(Vec3 eye,Vec3 aim,BlockPos p,BlockPos d){return visible&&eye.x()<p.x()&&eye.y()>aim.y();}};}
    @Test void selectsActualFeetPlaneInsteadOfUnsupportedPlusOne(){var result=Source95ToolApproach.candidates(anchor,west,1.62,flat(true));assertFalse(result.isEmpty());assertEquals(new BlockPos(-2,70,0),result.getFirst().node());assertEquals(new Vec3(-1.5,70,.5),result.getFirst().feet());assertTrue(result.stream().allMatch(s->s.node().y()==70));}
    @Test void obstructedOrWrongFaceNeverCreatesAnApproach(){assertTrue(Source95ToolApproach.candidates(anchor,west,1.62,flat(false)).isEmpty());}
    @Test void unsupportedOrOutOfReachFrontierCannotStart(){var absent=new Source95ToolApproach.Terrain(){public Optional<Vec3> supportedFeet(BlockPos p){return Optional.empty();}public boolean requiredFace(Vec3 a,Vec3 b,BlockPos p,BlockPos d){fail("Unsupported cells must not raycast");return true;}};assertTrue(Source95ToolApproach.candidates(anchor,west,1.62,absent).isEmpty());assertTrue(Source95ToolApproach.candidates(anchor,west,4,flat(true)).stream().allMatch(s->new Vec3(s.feet().x(),s.feet().y()+4,s.feet().z()).distanceTo(new Vec3(.001,70.5,.5))<=4.5));}
    @Test void constrainedFrontierCannotCrossGardenOrForgeSupportCoordinates(){var count=new int[]{0};var terrain=new Source95ToolApproach.Terrain(){public Optional<Vec3> supportedFeet(BlockPos p){count[0]++;assertTrue(p.x()>=-240&&p.x()<=239&&p.z()>=-240&&p.z()<=239&&p.y()>=0&&p.y()<=255);return Optional.of(new Vec3(p.x()+100,p.y(),p.z()+.5));}public boolean requiredFace(Vec3 a,Vec3 b,BlockPos p,BlockPos d){fail("Forged surface coordinates must not raycast");return true;}};assertTrue(Source95ToolApproach.candidates(new BlockPos(-239,70,0),west,1.62,terrain).isEmpty());assertTrue(count[0]<=36);}
    @Test void standingWestSurfaceHasWestRayWhileAboveAnchorHasTopRay(){
        // Independent cube slab intersection: for a WEST ray, its x=0 entry must lie below y=71.
        double westEntryY=71.62+(70.5-71.62)*(1.5/1.511);assertTrue(westEntryY>70&&westEntryY<71);
        double aboveEntryX=.5+(.011-.5)*((71-72.62)/(70.5-72.62));assertTrue(aboveEntryX>0&&aboveEntryX<1);
        var result=Source95ToolApproach.candidates(anchor,west,1.62,new Source95ToolApproach.Terrain(){
            public Optional<Vec3> supportedFeet(BlockPos p){return p.y()==70&&p.x()<0?Optional.of(new Vec3(p.x()+.5,70,p.z()+.5)):Optional.empty();}
            public boolean requiredFace(Vec3 eye,Vec3 aim,BlockPos p,BlockPos d){double t=(0-eye.x())/(aim.x()+.01-eye.x());double y=eye.y()+(aim.y()-eye.y())*t;double z=eye.z()+(aim.z()-eye.z())*t;return t>=0&&t<=1&&y>=70&&y<=71&&z>=0&&z<=1;}
        });assertEquals(new BlockPos(-2,70,0),result.getFirst().node());
    }
    @Test void selectsClosestActualHorizontalStandingPoseBeforeOneGuardedRouteSearch(){var result=Source95ToolApproach.candidates(anchor,west,1.62,flat(true));assertEquals(new BlockPos(-1,70,0),Source95ToolApproach.closest(result,new Vec3(2.5,71,.5)).orElseThrow().node());assertEquals(new BlockPos(-3,70,0),Source95ToolApproach.closest(result,new Vec3(-2.5,70,.5)).orElseThrow().node());assertTrue(Source95ToolApproach.closest(List.of(),new Vec3(0,70,0)).isEmpty());}
    @Test void directionRotatesFrontierAndRejectsVerticalFace(){var terrain=new Source95ToolApproach.Terrain(){public Optional<Vec3> supportedFeet(BlockPos p){return p.y()==70?Optional.of(new Vec3(p.x()+.5,70,p.z()+.5)):Optional.empty();}public boolean requiredFace(Vec3 a,Vec3 b,BlockPos p,BlockPos d){return true;}};assertEquals(new BlockPos(0,70,-2),Source95ToolApproach.candidates(anchor,new BlockPos(0,0,-1),1.62,terrain).getFirst().node());assertThrows(IllegalArgumentException.class,()->Source95ToolApproach.candidates(anchor,new BlockPos(0,1,0),1.62,terrain));}
}
