package dev.veltraph.client.navigation.mc;

import dev.veltraph.client.world.BlockPos;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.shapes.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Source-domain original four-corner query order model, with actual SDK shapes. Not a Minecraft
 * world/guard/accessor positive proof. Each before/after port has independent identical history. */
class GroundLiveUnitCubeQueryOrderTest {
    private static final class Port {
        final List<String> calls=new ArrayList<>();final VoxelShape shape;final int refuseAt,failAt;final Error failure;final boolean dangerous;
        Port(VoxelShape shape,int refuseAt,int failAt,Error failure,boolean dangerous){this.shape=shape;this.refuseAt=refuseAt;this.failAt=failAt;this.failure=failure;this.dangerous=dangerous;}
        boolean event(String name){calls.add(name);if(calls.size()==failAt)throw failure;return calls.size()!=refuseAt;}
    }
    private static double query(Port p,double x,int ceiling,double z,boolean optimized){
        double top=Double.NaN;
        for(double dx:new double[]{-.3,.3})for(double dz:new double[]{-.3,.3}){
            int bx=(int)Math.floor(x+dx),bz=(int)Math.floor(z+dz);var node=new BlockPos(bx,ceiling-1,bz);
            if(!p.event("loaded:"+node))return Double.NaN;
            p.event("state:"+node);
            if(!p.event("hazard:"+node)||p.dangerous)return Double.NaN;
            p.event("shape:"+node);
            double localX=x+dx-bx,localZ=z+dz-bz;
            double corner=optimized?GroundLiveUnitCubeSdkParityTest.candidate(p.shape,localX,localZ,ceiling-1):GroundLiveUnitCubeSdkParityTest.original(p.shape,localX,localZ,ceiling-1);
            if(!Double.isFinite(corner)||corner>ceiling+.001||corner<ceiling-1+.001)return Double.NaN;
            if(Double.isFinite(top)&&Math.abs(top-corner)>.6)return Double.NaN;
            top=Double.isFinite(top)?Math.max(top,corner):corner;
        }
        return top;
    }
    private static VoxelShape unit(boolean occupied){var grid=new BitSetDiscreteVoxelShape(1,1,1);if(occupied)grid.fill(0,0,0);return new CubeVoxelShape(grid);}
    @Test void fullEmptyAndPartialShapesPreserveFourCornerOrderAndHeightBits(){
        for(VoxelShape shape:List.of(unit(true),unit(false),Shapes.box(0,0,0,1,.5,1)))for(double x:new double[]{-.3,0,.3,.5,1.3,Double.NaN,Double.POSITIVE_INFINITY}){
            var a=new Port(shape,-1,-1,null,false);var b=new Port(shape,-1,-1,null,false);
            assertEquals(Double.doubleToLongBits(query(a,x,70,.5,false)),Double.doubleToLongBits(query(b,x,70,.5,true)));assertEquals(a.calls,b.calls);
        }
    }
    @Test void everyLoadedHazardRefusalAndDangerRetainsExactOriginalPrefix(){
        VoxelShape shape=unit(true);var all=new Port(shape,-1,-1,null,false);query(all,.5,70,.5,false);
        for(int stop=1;stop<=all.calls.size();stop++){
            var a=new Port(shape,stop,-1,null,false);var b=new Port(shape,stop,-1,null,false);
            assertEquals(Double.doubleToLongBits(query(a,.5,70,.5,false)),Double.doubleToLongBits(query(b,.5,70,.5,true)));assertEquals(a.calls,b.calls);
        }
        var a=new Port(shape,-1,-1,null,true);var b=new Port(shape,-1,-1,null,true);
        assertTrue(Double.isNaN(query(a,.5,70,.5,false)));assertTrue(Double.isNaN(query(b,.5,70,.5,true)));assertEquals(a.calls,b.calls);assertEquals(3,b.calls.size());
    }
    @Test void everyOriginalQueryExceptionRetainsExactErrorAndNoLaterCalls(){
        VoxelShape shape=unit(true);var all=new Port(shape,-1,-1,null,false);query(all,.5,70,.5,false);
        for(int stop=1;stop<=all.calls.size();stop++){
            Error failure=new AssertionError("original query "+stop);var a=new Port(shape,-1,stop,failure,false);var b=new Port(shape,-1,stop,failure,false);
            assertSame(failure,assertThrows(Error.class,()->query(a,.5,70,.5,false)));assertSame(failure,assertThrows(Error.class,()->query(b,.5,70,.5,true)));assertEquals(a.calls,b.calls);assertEquals(stop,b.calls.size());
        }
    }
    @Test void liveChangedSupportAndForeignCallbackFailureCannotBecomeKnownUnitPermission(){
        var grid=new BitSetDiscreteVoxelShape(1,1,1);grid.fill(0,0,0);var shape=new CubeVoxelShape(grid);
        var first=new Port(shape,-1,-1,null,false);assertEquals(70D,query(first,.5,70,.5,true));
        GroundLiveUnitCubeSdkParityTest.clearLocal(grid);var after=new Port(shape,-1,-1,null,false);assertTrue(Double.isNaN(query(after,.5,70,.5,true)));assertEquals(4,after.calls.size());
        Error failure=new AssertionError("custom boxes");var foreign=new GroundLiveUnitCubeSdkParityTest.ForeignShape(grid,failure);var a=new Port(foreign,-1,-1,null,false);var b=new Port(foreign,-1,-1,null,false);
        assertSame(failure,assertThrows(Error.class,()->query(a,.5,70,.5,false)));assertSame(failure,assertThrows(Error.class,()->query(b,.5,70,.5,true)));assertEquals(a.calls,b.calls);
    }
}
