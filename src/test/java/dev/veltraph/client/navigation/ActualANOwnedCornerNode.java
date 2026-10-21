package dev.veltraph.client.navigation;
import dev.veltraph.client.world.BlockPos;
/** Source-pinned owned coordinate selection only; no SDK/world query or admission. */
final class ActualANOwnedCornerNode {
    static BlockPos select(BlockPos previousCornerNode,int bx,int ceiling,int bz){return previousCornerNode!=null&&previousCornerNode.x()==bx&&previousCornerNode.y()==ceiling-1&&previousCornerNode.z()==bz?previousCornerNode:new BlockPos(bx,ceiling-1,bz);}
}
