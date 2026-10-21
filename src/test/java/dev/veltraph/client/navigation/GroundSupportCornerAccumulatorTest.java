package dev.veltraph.client.navigation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class GroundSupportCornerAccumulatorTest {
    private record Raw(double x1,double y1,double z1,double x2,double y2,double z2) {}
    /** Reference uses the pinned ORIGINAL native AABB normalization and supportHeight fold. */
    private static double original(double px,double pz,int by,List<Raw> rows){
        double corner=Double.NaN;for(var r:rows){var b=new AABB(r.x1,r.y1,r.z1,r.x2,r.y2,r.z2);
            if(px>=b.minX&&px<=b.maxX&&pz>=b.minZ&&pz<=b.maxZ)corner=Double.isNaN(corner)?by+b.maxY:Math.max(corner,by+b.maxY);}
        return corner;
    }
    private static double candidate(GroundSupportCornerAccumulator a,double px,double pz,int by,List<Raw> rows){a.reset(px,pz,by);for(var r:rows)a.accept(r.x1,r.y1,r.z1,r.x2,r.y2,r.z2);return a.height();}
    private static void parity(double px,double pz,int by,List<Raw> rows){double expected=original(px,pz,by,rows);double actual=candidate(new GroundSupportCornerAccumulator(),px,pz,by,rows);assertEquals(Double.doubleToLongBits(expected),Double.doubleToLongBits(actual));}
    @Test void fullCubePartialSlabAndDisjointShapesMatchOriginal(){
        var rows=List.of(new Raw(0,0,0,1,.9375,1),new Raw(.25,0,.25,.75,1,.75),new Raw(2,0,2,3,2,3));
        for(double x:new double[]{0,.249999999,.25,.5,.75,.750000001,1,2,3})for(double z:new double[]{0,.25,.5,.75,1,2,3})parity(x,z,69,rows);
    }
    @Test void reversedAndDegenerateBoundsUseNativeMinMax(){
        for(var r:List.of(new Raw(1,1,1,0,0,0),new Raw(.5,.9375,.5,.5,.9375,.5),new Raw(-0D,-0D,-0D,0D,0D,0D)))for(double p:new double[]{-0D,0D,.5,1})parity(p,p,0,List.of(r));
    }
    @Test void inclusiveThresholdsNextUpNextDownAndAllFourCornerOffsets(){
        var rows=List.of(new Raw(0,0,0,1,1,1));for(double p:new double[]{Math.nextDown(0D),-0D,0D,Math.nextUp(0D),Math.nextDown(1D),1D,Math.nextUp(1D)})parity(p,p,69,rows);
        for(double dx:new double[]{-.3,.3})for(double dz:new double[]{-.3,.3}){int bx=(int)Math.floor(.5+dx),bz=(int)Math.floor(.12+dz);parity(.5+dx-bx,.12+dz-bz,69,rows);}
    }
    @Test void nonFiniteAndNaNBoundsPreserveOrderedFoldSemantics(){
        double nan=Double.longBitsToDouble(0x7ff8000000000001L);var rows=List.of(new Raw(0,0,0,1,Double.POSITIVE_INFINITY,1),new Raw(0,0,0,1,nan,1),new Raw(0,0,0,1,1,1));parity(.5,.5,69,rows);
        for(int axis=0;axis<6;axis++)for(double v:new double[]{Double.NEGATIVE_INFINITY,Double.POSITIVE_INFINITY,nan,-0D}){double[] a={0,0,0,1,1,1};a[axis]=v;parity(.5,.5,0,List.of(new Raw(a[0],a[1],a[2],a[3],a[4],a[5])));}
    }
    private static void permutations(List<Raw> rows,int index){if(index==rows.size()){parity(.5,.5,69,rows);return;}for(int j=index;j<rows.size();j++){Collections.swap(rows,index,j);permutations(rows,index+1);Collections.swap(rows,index,j);}}
    @Test void everyOrderedPermutationIncludesNaNResetAndInfiniteMaximum(){permutations(new ArrayList<>(List.of(new Raw(0,0,0,1,1,1),new Raw(0,0,0,1,.9375,1),new Raw(0,0,0,1,Double.NaN,1),new Raw(0,0,0,1,Double.POSITIVE_INFINITY,1),new Raw(2,0,2,3,2,3))),0);}
    @Test void resetIsIndependentForAllCornersAndSeparateCalls(){
        var a=new GroundSupportCornerAccumulator();var rows=List.of(new Raw(0,0,0,1,1,1));assertEquals(70D,candidate(a,.5,.5,69,rows));assertTrue(Double.isNaN(candidate(a,2,2,69,rows)));assertEquals(1D,candidate(a,0,0,0,rows));assertTrue(Double.isNaN(candidate(a,.5,.5,0,List.of())));parity(.5,.5,Integer.MIN_VALUE,rows);parity(.5,.5,Integer.MAX_VALUE,rows);
    }
    private static double finish(double top,double corner,int ceiling){if(!Double.isFinite(corner)||corner>ceiling+.001||corner<ceiling-1+.001)return Double.NaN;if(Double.isFinite(top)&&Math.abs(top-corner)>.6)return Double.NaN;return Double.isFinite(top)?Math.max(top,corner):corner;}
    @Test void originalCeilingAndPointSixSpreadBoundariesRemainExact(){
        int ceiling=70;for(double corner:new double[]{Math.nextDown(ceiling-1+.001),ceiling-1+.001,Math.nextUp(ceiling-1+.001),Math.nextDown(ceiling+.001),ceiling+.001,Math.nextUp(ceiling+.001)}){
            var rows=List.of(new Raw(0,0,0,1,corner-69,1));double old=original(.5,.5,69,rows),now=candidate(new GroundSupportCornerAccumulator(),.5,.5,69,rows);assertEquals(Double.doubleToLongBits(finish(Double.NaN,old,ceiling)),Double.doubleToLongBits(finish(Double.NaN,now,ceiling)));
            for(double delta:new double[]{Math.nextDown(.6),.6,Math.nextUp(.6)})assertEquals(Double.doubleToLongBits(finish(old-delta,old,ceiling)),Double.doubleToLongBits(finish(now-delta,now,ceiling)));
        }
    }
    @Test void emptyAndNonCoveringShapesCannotCarryPreviousHeight(){var a=new GroundSupportCornerAccumulator();a.reset(.5,.5,69);a.accept(0,0,0,1,1,1);assertEquals(70D,a.height());a.reset(.5,.5,69);assertTrue(Double.isNaN(a.height()));a.accept(2,0,2,3,4,3);assertTrue(Double.isNaN(a.height()));}
}
