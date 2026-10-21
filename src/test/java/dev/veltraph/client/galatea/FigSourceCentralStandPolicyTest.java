package dev.veltraph.client.galatea;
import dev.veltraph.client.world.BlockPos;
import dev.veltraph.client.world.Vec3;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class FigSourceCentralStandPolicyTest {
    static class Flat implements FigSourceGeometry.World {
        public boolean passable(BlockPos p){return p.y()>=70;}
        public boolean log(BlockPos p){return false;}
        public Object blockType(BlockPos p){return "flat";}
        public boolean leaves(BlockPos p){return false;}
        public boolean standing(BlockPos p){return p.y()==70;}
        public boolean dryStanding(BlockPos p){return standing(p);}
        public Vec3 standingEye(Vec3 p){return new Vec3(p.x(),p.y()+1.62,p.z());}
        public boolean sourceRay(Vec3 e,BlockPos p,boolean l){throw new AssertionError("Central/reachable helpers have no ray gate");}
    }
    private BlockPos choose(FigSourceGeometry.World w,Vec3 player){return FigSourceCentralStandPolicy.choose(new BlockPos(0,70,0),new BlockPos(0,80,0),w,player,0,()->.1);}
    @Test void stableTieUsesNativeXThenZOrder(){assertEquals(new BlockPos(-1,70,-1),choose(new Flat(),new Vec3(0,70,0)));}
    @Test void playerNearDifferentOffsetDoesNotReplaceBaseScoringOperand(){assertEquals(new BlockPos(-1,70,-1),choose(new Flat(),new Vec3(1.5,70,1.5)));}
    @Test void emptyDryCandidatesFallsBackToBase(){var w=new Flat(){public boolean dryStanding(BlockPos p){return false;}};assertEquals(new BlockPos(0,70,0),choose(w,new Vec3(0,70,0)));}
    @Test void randomOnlyDrawsAdmittedCandidatesAndChoosesLower(){int[] i={0};var w=new Flat(){public boolean dryStanding(BlockPos p){return p.x()==0&&p.z()>=0;}};
        assertEquals(new BlockPos(0,70,1),FigSourceCentralStandPolicy.choose(new BlockPos(0,70,0),new BlockPos(0,80,0),w,new Vec3(0,70,0),0,()->i[0]++==0?.4:.1));assertEquals(2,i[0]);}
    @Test void reachBoundaryIsThreeAxisStrictAndDoesNotUseRay(){var p=new BlockPos(0,70,0);assertEquals(0,FigSourceCentralStandPolicy.reachableCount(new Vec3(.5,74.75,.5),List.of(p)));assertEquals(1,FigSourceCentralStandPolicy.reachableCount(new Vec3(.5,74.749,.5),List.of(p)));}
    @Test void impossibleRandomValuesRefuse(){for(double value:new double[]{Double.NaN,Double.POSITIVE_INFINITY,-.01,.5})assertThrows(IllegalArgumentException.class,()->FigSourceCentralStandPolicy.choose(new BlockPos(0,70,0),new BlockPos(0,80,0),new Flat(),new Vec3(0,70,0),0,()->value));}
}
