package dev.veltraph.client.navigation.mc;

import dev.veltraph.client.navigation.GroundSupportCornerAccumulator;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Actual SDK box enumeration is the independent reference. Reflection below only reads/changes
 * locally constructed test shapes/grids; never global Shapes.block, world or native permission.
 * Positive unit tests exercise closed projection; production accessor weaving remains native gate. */
class GroundLiveUnitCubeSdkParityTest {
    static DiscreteVoxelShape grid(VoxelShape shape){try{Field f=VoxelShape.class.getDeclaredField("shape");f.setAccessible(true);return (DiscreteVoxelShape)f.get(shape);}catch(ReflectiveOperationException e){throw new AssertionError(e);}}
    static void clearLocal(BitSetDiscreteVoxelShape grid){try{Field f=BitSetDiscreteVoxelShape.class.getDeclaredField("storage");f.setAccessible(true);((BitSet)f.get(grid)).clear();}catch(ReflectiveOperationException e){throw new AssertionError(e);}}
    static double original(VoxelShape shape,double x,double z,int y){
        if(shape.getClass()==CubeVoxelShape.class||shape.getClass()==ArrayVoxelShape.class||shape.getClass()==SliceShape.class){
            var accumulator=new GroundSupportCornerAccumulator();accumulator.reset(x,z,y);shape.forAllBoxes(accumulator::accept);return accumulator.height();
        }
        double corner=Double.NaN;for(var box:shape.toAabbs())if(x>=box.minX&&x<=box.maxX&&z>=box.minZ&&z<=box.maxZ)
            corner=Double.isNaN(corner)?y+box.maxY:Math.max(corner,y+box.maxY);return corner;
    }
    static double candidate(VoxelShape shape,double x,double z,int y){
        double unit=McGroundUnitCubeSupport.nativeUnitGridHeight(shape,grid(shape),x,z,y);
        return unit!=Double.NEGATIVE_INFINITY?unit:original(shape,x,z,y);
    }
    static void same(VoxelShape shape,double x,double z,int y){assertEquals(Double.doubleToLongBits(original(shape,x,z,y)),Double.doubleToLongBits(candidate(shape,x,z,y)));}
    @Test void fullAndEmptyActualUnitSdkEnumerationKeepsAllInclusiveAndNonfiniteCoordinates(){
        for(boolean occupied:new boolean[]{false,true}){
            var grid=new BitSetDiscreteVoxelShape(1,1,1);if(occupied)grid.fill(0,0,0);var shape=new CubeVoxelShape(grid);
            for(double x:new double[]{-Double.MIN_VALUE,-0D,0D,Double.MIN_VALUE,.3,.5,Math.nextDown(1D),1D,Math.nextUp(1D),Double.NaN,Double.NEGATIVE_INFINITY,Double.POSITIVE_INFINITY})
                for(double z:new double[]{-Double.MIN_VALUE,-0D,0D,.5,1D,Math.nextUp(1D),Double.NaN,Double.POSITIVE_INFINITY})
                    for(int y:new int[]{-1,0,69,Integer.MIN_VALUE,Integer.MAX_VALUE})same(shape,x,z,y);
        }
    }
    @Test void liveUnitBitChangesNeverReuseEarlierFullOrEmptySupport(){
        var grid=new BitSetDiscreteVoxelShape(1,1,1);var shape=new CubeVoxelShape(grid);
        for(int i=0;i<8;i++){clearLocal(grid);assertTrue(Double.isNaN(candidate(shape,.5,.5,69)));same(shape,.5,.5,69);
            grid.fill(0,0,0);assertEquals(70D,candidate(shape,.5,.5,69));same(shape,.5,.5,69);}
    }
    @Test void eachMixedDimensionRejectsFastProjectionAndRetainsActualSdkOriginalFallback(){
        for(int[] size:new int[][]{{2,1,1},{1,2,1},{1,1,2},{2,2,2},{3,4,2}}){
            var grid=new BitSetDiscreteVoxelShape(size[0],size[1],size[2]);grid.fill(0,0,0);var shape=new CubeVoxelShape(grid);
            assertEquals(Double.NEGATIVE_INFINITY,McGroundUnitCubeSupport.nativeUnitGridHeight(shape,grid,.2,.2,69));
            for(double x:new double[]{0,.2,.5,1})for(double z:new double[]{0,.2,.5,1})same(shape,x,z,69);
        }
    }
    @Test void partialOffsetAndUnionShapesKeepActualSdkEnumeration(){
        for(var shape:List.of(Shapes.box(0,0,0,1,.5,1),Shapes.box(.2,.1,.3,.8,.9,.7),Shapes.or(Shapes.box(0,0,0,.5,.5,1),Shapes.box(.5,0,0,1,1,1)))){
            for(double x:new double[]{0,.2,.3,.5,.7,.8,1})for(double z:new double[]{0,.2,.3,.5,.7,.8,1})same(shape,x,z,69);
        }
    }
    static final class ForeignGrid extends DiscreteVoxelShape {
        final List<String> calls=new ArrayList<>();final BitSetDiscreteVoxelShape delegate=new BitSetDiscreteVoxelShape(1,1,1);
        ForeignGrid(){super(1,1,1);delegate.fill(0,0,0);}
        @Override public int getSize(Direction.Axis axis){calls.add("size:"+axis);return super.getSize(axis);}
        @Override public boolean isFull(int x,int y,int z){calls.add("full:"+x+":"+y+":"+z);return delegate.isFull(x,y,z);}
        @Override public void fill(int x,int y,int z){calls.add("fill");delegate.fill(x,y,z);}
        @Override public int firstFull(Direction.Axis axis){calls.add("first:"+axis);return delegate.firstFull(axis);}
        @Override public int lastFull(Direction.Axis axis){calls.add("last:"+axis);return delegate.lastFull(axis);}
    }
    @Test void foreignGridClassNeverRunsItsGettersInsideFastPathAndPreservesFallbackCallbacks(){
        var a=new ForeignGrid();var b=new ForeignGrid();var before=new CubeVoxelShape(a);var after=new CubeVoxelShape(b);
        assertEquals(Double.NEGATIVE_INFINITY,McGroundUnitCubeSupport.nativeUnitGridHeight(after,b,.5,.5,69));assertTrue(b.calls.isEmpty());
        assertEquals(original(before,.5,.5,69),candidate(after,.5,.5,69));assertEquals(a.calls,b.calls);
    }
    static final class ForeignShape extends VoxelShape {
        final List<String> calls=new ArrayList<>();final Error failure;
        ForeignShape(DiscreteVoxelShape grid,Error failure){super(grid);this.failure=failure;}
        @Override public it.unimi.dsi.fastutil.doubles.DoubleList getCoords(Direction.Axis axis){calls.add("coords:"+axis);return new it.unimi.dsi.fastutil.doubles.DoubleArrayList(new double[]{0D,1D});}
        @Override public void forAllBoxes(Shapes.DoubleLineConsumer consumer){calls.add("forAllBoxes");if(failure!=null)throw failure;super.forAllBoxes(consumer);}
    }
    @Test void foreignShapeSubclassRetainsOriginalCustomEnumerationAndExactFailure(){
        var a=new BitSetDiscreteVoxelShape(1,1,1);a.fill(0,0,0);var x=new ForeignShape(a,null);var z=new ForeignShape(a,null);
        assertEquals(Double.NEGATIVE_INFINITY,McGroundUnitCubeSupport.nativeUnitGridHeight(z,a,.5,.5,69));assertTrue(z.calls.isEmpty());
        assertEquals(original(x,.5,.5,69),candidate(z,.5,.5,69));assertEquals(x.calls,z.calls);
        Error failure=new AssertionError("original foreign enumeration");var bad=new ForeignShape(a,failure);
        assertSame(failure,assertThrows(Error.class,()->original(bad,.5,.5,69)));assertSame(failure,assertThrows(Error.class,()->candidate(bad,.5,.5,69)));
    }
    @Test void nullGridAndAbsentProductionAccessorCannotSupplyUnitSupport(){
        var shape=new CubeVoxelShape(new BitSetDiscreteVoxelShape(1,1,1));
        assertEquals(Double.NEGATIVE_INFINITY,McGroundUnitCubeSupport.nativeUnitGridHeight(shape,null,.5,.5,69));
        assertEquals(Double.NEGATIVE_INFINITY,McGroundUnitCubeSupport.nativeUnitGridHeight(null,grid(shape),.5,.5,69));
        assertEquals(Double.NEGATIVE_INFINITY,McGroundUnitCubeSupport.tryCornerHeight(null,.5,.5,69));
        // Ordinary JUnit is deliberately not a positive Mixin/accessor transformation proof.
        if(!((Object)shape instanceof dev.veltraph.client.mixin.GroundSupportVoxelShapeAccessor))assertEquals(Double.NEGATIVE_INFINITY,McGroundUnitCubeSupport.tryCornerHeight(shape,.5,.5,69));
    }
    static it.unimi.dsi.fastutil.doubles.DoubleList unitCoords(VoxelShape shape,Direction.Axis axis){try{var method=CubeVoxelShape.class.getDeclaredMethod("getCoords",Direction.Axis.class);method.setAccessible(true);return (it.unimi.dsi.fastutil.doubles.DoubleList)method.invoke(shape,axis);}catch(ReflectiveOperationException e){throw new AssertionError(e);}}
    @Test void independentActualSdkUnitCoordinatesAreZeroAndOneOnEveryAxis(){
        var shape=new CubeVoxelShape(new BitSetDiscreteVoxelShape(1,1,1));
        for(var axis:Direction.Axis.values()){var coords=unitCoords(shape,axis);assertEquals(2,coords.size());assertEquals(0D,coords.getDouble(0));assertEquals(1D,coords.getDouble(1));}
    }
}
