package dev.veltraph.client.navigation;

import dev.veltraph.client.world.Vec3;
import java.util.ArrayList;
import java.util.Random;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Pure private operand parity; no SDK class, world, query or runtime grant is represented. */
final class ProjectedBodyPointReuseParityTest {
    private static long bits(double d){return Double.doubleToRawLongBits(d);}
    private static void sameBits(Vec3 a,Vec3 b){
        assertEquals(bits(a.x()),bits(b.x()));assertEquals(bits(a.y()),bits(b.y()));assertEquals(bits(a.z()),bits(b.z()));
    }
    private static void same(Vec3 p,double height){
        Vec3 old=FrozenAIProjectedBodyPoint.project(p,height),actual=ActualAIProjectedBodyPoint.project(p,height);
        sameBits(old,actual);assertNotSame(p,old);
        if(bits(p.y())==bits(height))assertSame(p,actual);else assertNotSame(p,actual);
    }
    @Test void exactFiniteHeightReusesOnlyPrivatePointWithIdenticalCoordinateBits(){
        for(double h:new double[]{0D,-0D,.001,.6,1D,-1D,Math.nextUp(1D),Double.MAX_VALUE,Double.MIN_VALUE})same(new Vec3(-0D,h,3.14159),h);
    }
    @Test void changedHeightRetainsOriginalProjectedAllocationAndArithmetic(){
        for(double h:new double[]{0D,.001,.6,1D,-1D,Math.nextUp(1D),Double.MAX_VALUE}){
            same(new Vec3(2D,h,-3D),Math.nextDown(h));same(new Vec3(2D,h,-3D),h+.5);
        }
    }
    @Test void signedZerosRequireExactRawBitsBeforeReuse(){
        Vec3 positive=new Vec3(-0D,0D,-0D),negative=new Vec3(0D,-0D,0D);
        same(positive,0D);same(positive,-0D);same(negative,0D);same(negative,-0D);
        assertNotSame(positive,ActualAIProjectedBodyPoint.project(positive,-0D));assertNotSame(negative,ActualAIProjectedBodyPoint.project(negative,0D));
    }
    @Test void nonfiniteValuesAndDistinctNaNPayloadsKeepExactBits(){
        double nanA=Double.longBitsToDouble(0x7ff8000000000001L),nanB=Double.longBitsToDouble(0x7ff8000000000042L),nanC=Double.longBitsToDouble(0xfff8000000000001L);
        for(double y:new double[]{nanA,nanB,nanC,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY})
            for(double height:new double[]{y,nanA,nanB,nanC,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY,0D})same(new Vec3(nanB,y,-0D),height);
    }
    @Test void deterministicPrimitiveRecordsRetainFrozenProjectionWithoutNativeCalls(){
        Random random=new Random(0xA1438);for(int i=0;i<10000;i++){
            Vec3 p=new Vec3(Double.longBitsToDouble(random.nextLong()),Double.longBitsToDouble(random.nextLong()),Double.longBitsToDouble(random.nextLong()));
            same(p,i%2==0?p.y():Double.longBitsToDouble(random.nextLong()));
        }
    }
    private static void consumeOperands(Vec3 projected,ArrayList<Long> trace,RuntimeException original){
        trace.add(bits(projected.x()));trace.add(bits(projected.y()));trace.add(bits(projected.z()));throw original;
    }
    @Test void pureCoordinateConsumerKeepsOperandOrderAndOriginalErrorIdentity(){
        for(double height:new double[]{1D,1.5D,-0D,Double.longBitsToDouble(0x7ff8000000000042L)}){
            Vec3 p=new Vec3(-0D,1D,2D);var before=new ArrayList<Long>();var after=new ArrayList<Long>();var original=new IllegalStateException("original coordinate consumer");
            assertSame(original,assertThrows(IllegalStateException.class,()->consumeOperands(FrozenAIProjectedBodyPoint.project(p,height),before,original)));
            assertSame(original,assertThrows(IllegalStateException.class,()->consumeOperands(ActualAIProjectedBodyPoint.project(p,height),after,original)));
            assertEquals(before,after);assertEquals(java.util.List.of(bits(p.x()),bits(height),bits(p.z())),after);
        }
    }
}
