package dev.veltraph.client.navigation.mc;

import dev.veltraph.client.mixin.GroundSupportVoxelShapeAccessor;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.BitSetDiscreteVoxelShape;
import net.minecraft.world.phys.shapes.CubeVoxelShape;
import net.minecraft.world.phys.shapes.DiscreteVoxelShape;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Fresh native unit-grid height projection only. No shape, world, actor, permission or result
 * is cached. Unsupported uses -Infinity solely to execute the original enumeration path;
 * supported empty/outside/NaN retains the original NaN support result. Loaded/state/hazard/shape
 * queries and the surrounding native custody fences belong to the original caller unchanged. */
final class McGroundUnitCubeSupport {
    private McGroundUnitCubeSupport() {}
    static double tryCornerHeight(VoxelShape shape,double localX,double localZ,int blockY){
        if(shape==null||shape.getClass()!=CubeVoxelShape.class
                ||!((Object)shape instanceof GroundSupportVoxelShapeAccessor accessor))return Double.NEGATIVE_INFINITY;
        // Exact accessor exposes only this original shape's protected final native grid field.
        return nativeUnitGridHeight(shape,accessor.veltraph$groundSupportDiscreteShape(),localX,localZ,blockY);
    }
    /** Package-private data/native-domain projection. The real caller supplies only its field
     * read above; no detached caller grid or scalar can acquire a gameplay capability. */
    static double nativeUnitGridHeight(VoxelShape shape,DiscreteVoxelShape grid,double localX,double localZ,int blockY){
        if(shape==null||shape.getClass()!=CubeVoxelShape.class||grid==null||grid.getClass()!=BitSetDiscreteVoxelShape.class)
            return Double.NEGATIVE_INFINITY;
        // These are fresh closed native dimension reads, in original X/Y/Z coordinate order.
        int x=grid.getSize(Direction.Axis.X),y=grid.getSize(Direction.Axis.Y),z=grid.getSize(Direction.Axis.Z);
        if(x!=1||y!=1||z!=1)return Double.NEGATIVE_INFINITY;
        // A 1x1x1 CubePointRange has exactly [0,1] on each axis. Its sole live occupancy bit
        // emits either no box or exactly the unit box in the original forAllBoxes algorithm.
        boolean occupied=grid.isFull(0,0,0);
        return occupied&&localX>=0D&&localX<=1D&&localZ>=0D&&localZ<=1D?blockY+1D:Double.NaN;
    }
}
