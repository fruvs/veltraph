package dev.veltraph.client.navigation;

import dev.veltraph.client.world.Vec3;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Exact source-fragment loop parity and the real production discarded-node Y projection. */
final class BodyBoundProjectionParityTest {
    private record Cell(int x,int y,int z) {}
    private static final class Trace {
        final List<Cell> cells=new ArrayList<>();final int cut;final Throwable failure;
        Trace(int cut,Throwable failure){this.cut=cut;this.failure=failure;}
        boolean visit(int x,int y,int z){
            cells.add(new Cell(x,y,z));
            if(cells.size()==cut){
                if(failure instanceof RuntimeException runtime)throw runtime;
                if(failure instanceof Error error)throw error;
                return false;
            }
            return true;
        }
    }
    private static FrozenZBodyCellLoop.Bounds old(double[] b){return new FrozenZBodyCellLoop.Bounds(b[0],b[1],b[2],b[3],b[4],b[5]);}
    private static ActualZBodyCellLoop.Bounds next(double[] b){return new ActualZBodyCellLoop.Bounds(b[0],b[1],b[2],b[3],b[4],b[5]);}
    private static List<Cell> compare(double[] bounds,int cut){
        var original=new Trace(cut,null);var actual=new Trace(cut,null);
        assertEquals(FrozenZBodyCellLoop.scan(old(bounds),original::visit),ActualZBodyCellLoop.scan(next(bounds),actual::visit));
        assertEquals(original.cells,actual.cells);return actual.cells;
    }
    @Test void originalNodeProjectionMatchesProductionForDoubleBoundariesAndRawValues(){
        double[] values={-0.0,0.0,-.001,.001,Math.nextDown(.001),Math.nextUp(.001),
                -1,Math.nextDown(-1.0),Math.nextUp(-1.0),1,Math.nextDown(1.001),Math.nextUp(1.001),
                Integer.MIN_VALUE,Integer.MAX_VALUE,(double)Integer.MAX_VALUE+1,Double.MIN_VALUE,
                -Double.MIN_VALUE,Double.MAX_VALUE,-Double.MAX_VALUE,Double.NaN,Double.NEGATIVE_INFINITY,Double.POSITIVE_INFINITY};
        for(double y:values)for(double x:new double[]{-0.0,Double.NaN,Double.MAX_VALUE})
            for(double z:new double[]{0,Double.NEGATIVE_INFINITY,-Double.MAX_VALUE})
                assertEquals(GroundPathfinder.node(new Vec3(x,y,z)).y(),GroundPathfinder.nodeCeiling(x,y,z));
        Random random=new Random(0x6713);
        for(int i=0;i<2000;i++){
            double x=Double.longBitsToDouble(random.nextLong()),y=Double.longBitsToDouble(random.nextLong()),z=Double.longBitsToDouble(random.nextLong());
            assertEquals(GroundPathfinder.node(new Vec3(x,y,z)).y(),GroundPathfinder.nodeCeiling(x,y,z));
        }
    }
    @Test void finiteBodyRangesKeepExactOrderedCells(){
        for(double x:new double[]{-.5,-.3,-0.0,0,.5,1,20.5})for(double z:new double[]{-.5,0,.5,20.5}){
            double[] bounds={x-.3,70.001,z-.3,x+.3,71.799999952316284,z+.3};
            var cells=compare(bounds,Integer.MAX_VALUE);assertFalse(cells.isEmpty());
            assertEquals(new Cell((int)Math.floor(bounds[0]),70,(int)Math.floor(bounds[2])),cells.getFirst());
        }
    }
    @Test void fractionalBoundariesSignedZeroAndEmptyRangesRemainExact(){
        for(double[] bounds:List.of(new double[]{-.0,-.0,-.0,.0,.0,.0},
                new double[]{Math.nextDown(1.0),-.001,-.3,Math.nextUp(1.0),1.799999952316284,.3},
                new double[]{3,0,0,2,1,1},new double[]{0,3,0,1,2,1},new double[]{0,0,3,1,1,2}))compare(bounds,100);
    }
    @Test void everyFiniteCutoffPreservesFalseAndVisitedPrefix(){
        double[] bounds={-2.3,69.001,-1.3,2.3,71.799999952316284,1.3};
        int count=compare(bounds,Integer.MAX_VALUE).size();
        for(int stop=1;stop<=count+1;stop++)compare(bounds,stop);
    }
    @Test void originalRuntimeAndErrorIdentityAndCellPrefixArePreserved(){
        double[] bounds={-2,70,-2,2,71,2};
        for(Throwable failure:List.of(new IllegalStateException("original"),new AssertionError("original")))
            for(int stop:new int[]{1,2,7,17}){
                var original=new Trace(stop,failure);var actual=new Trace(stop,failure);
                assertSame(failure,assertThrows(failure.getClass(),()->FrozenZBodyCellLoop.scan(old(bounds),original::visit)));
                assertSame(failure,assertThrows(failure.getClass(),()->ActualZBodyCellLoop.scan(next(bounds),actual::visit)));
                assertEquals(original.cells,actual.cells);
            }
    }
    @Test void NaNAndNegativeInfinityUpperBoundsStillSkipOriginalComparisons(){
        for(double[] bounds:List.of(new double[]{Double.NaN,0,0,Double.NaN,1,1},
                new double[]{0,0,0,1,Double.NaN,1},new double[]{0,0,0,1,1,Double.NaN},
                new double[]{Double.NEGATIVE_INFINITY,0,0,Double.NEGATIVE_INFINITY,1,1},
                new double[]{0,Double.NEGATIVE_INFINITY,0,1,Double.NEGATIVE_INFINITY,1},
                new double[]{0,0,Double.NEGATIVE_INFINITY,1,1,Double.NEGATIVE_INFINITY}))assertTrue(compare(bounds,20).isEmpty());
    }
    @Test void SaturatingMinimumCastAndIntWrapPreserveOriginalFinitePrefix(){
        for(double[] bounds:List.of(new double[]{Integer.MAX_VALUE,0,0,(double)Integer.MAX_VALUE+1,0,0},
                new double[]{0,Integer.MAX_VALUE,0,0,Double.POSITIVE_INFINITY,0},
                new double[]{0,0,Integer.MAX_VALUE,0,0,Double.POSITIVE_INFINITY},
                new double[]{Double.NEGATIVE_INFINITY,0,0,(double)Integer.MIN_VALUE+3,0,0},
                new double[]{Double.NaN,0,0,2,0,0}))compare(bounds,20);
        var wrap=compare(new double[]{Integer.MAX_VALUE,0,0,Double.POSITIVE_INFINITY,0,0},3);
        assertEquals(Integer.MAX_VALUE,wrap.get(0).x());assertEquals(Integer.MIN_VALUE,wrap.get(1).x());
    }
}
