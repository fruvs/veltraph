package dev.veltraph.client.navigation;

import dev.veltraph.client.world.BlockPos;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Actual owned value record/pure source fragments only; no native position or world is mocked. */
final class OwnedCornerNodeReuseParityTest {
    private static BlockPos same(BlockPos prior,int x,int ceiling,int z){
        var original=FrozenANOwnedCornerNode.select(prior,x,ceiling,z);var actual=ActualANOwnedCornerNode.select(prior,x,ceiling,z);
        assertEquals(original,actual);assertNotSame(prior,original);
        if(prior!=null&&prior.x()==x&&prior.y()==ceiling-1&&prior.z()==z)assertSame(prior,actual);else assertNotSame(prior,actual);
        return actual;
    }
    @Test void fourSameCoordinatesReuseOnlyOneOwnedCornerValue(){
        BlockPos prior=null;var first=same(prior,8,12,-4);prior=first;
        for(int i=0;i<3;i++)assertSame(first,prior=same(prior,8,12,-4));
    }
    @Test void anyChangedCoordinateRetainsOriginalAllocation(){
        var prior=new BlockPos(8,11,-4);same(prior,9,12,-4);same(prior,8,13,-4);same(prior,8,12,-3);same(null,8,12,-4);
    }
    @Test void ceilingSubtractionKeepsOriginalSignedIntegerWrap(){
        for(int x:new int[]{Integer.MIN_VALUE,-1,0,1,Integer.MAX_VALUE})for(int ceiling:new int[]{Integer.MIN_VALUE,-1,0,1,Integer.MAX_VALUE})for(int z:new int[]{Integer.MIN_VALUE,0,Integer.MAX_VALUE}){
            var expected=new BlockPos(x,ceiling-1,z);assertSame(expected,same(expected,x,ceiling,z));same(new BlockPos(x,ceiling,z),x,ceiling,z);
        }
    }
    @Test void sourceCoordinateSequenceMatchesAtFloorChunkAndBlockBoundaries(){
        for(double x:new double[]{-16.5,-16,-.5,-0D,0D,.5,15.5,16,Double.MAX_VALUE,Double.NaN})for(double z:new double[]{-16.5,-.5,.5,16,Double.POSITIVE_INFINITY}){
            BlockPos prior=null;for(int cx=0;cx<2;cx++)for(int cz=0;cz<2;cz++){
                double dx=cx==0?-.3:.3,dz=cz==0?-.3:.3;int bx=(int)Math.floor(x+dx),bz=(int)Math.floor(z+dz);prior=same(prior,bx,4,bz);
                assertEquals(bx>>4,prior.x()>>4);assertEquals(bz>>4,prior.z()>>4);
            }
        }
    }
    @Test void everyIndependentCallStartsWithAnUnsharedOwnedValue(){
        var first=same(null,3,7,9);var second=same(null,3,7,9);assertEquals(first,second);assertNotSame(first,second);
    }
    private static void consumePrimitiveOperands(BlockPos node,List<Integer> trace,RuntimeException original){
        // Bounded scalar operand journal, not an SDK callback: chunk X/Z then conversion X/Y/Z.
        trace.add(node.x()>>4);trace.add(node.z()>>4);trace.add(node.x());trace.add(node.y());trace.add(node.z());throw original;
    }
    @Test void scalarConsumerKeepsOperandOrderAndOriginalUncheckedErrorIdentity(){
        var prior=new BlockPos(-17,Integer.MAX_VALUE,16);var original=new IllegalStateException("original scalar consumer");var before=new ArrayList<Integer>();var after=new ArrayList<Integer>();
        assertSame(original,assertThrows(IllegalStateException.class,()->consumePrimitiveOperands(FrozenANOwnedCornerNode.select(prior,-17,Integer.MIN_VALUE,16),before,original)));
        assertSame(original,assertThrows(IllegalStateException.class,()->consumePrimitiveOperands(ActualANOwnedCornerNode.select(prior,-17,Integer.MIN_VALUE,16),after,original)));
        assertEquals(before,after);assertEquals(List.of(-2,1,-17,Integer.MAX_VALUE,16),after);
    }
    @Test void deterministicCoordinatesAndRepeatedRunsPreserveAllOriginalValues(){
        Random random=new Random(0xA1438L);
        BlockPos prior=null;for(int i=0;i<10000;i++){
            if(i%3==0&&prior!=null)prior=same(prior,prior.x(),prior.y()+1,prior.z());else prior=same(prior,random.nextInt(),random.nextInt(),random.nextInt());
        }
    }
}
