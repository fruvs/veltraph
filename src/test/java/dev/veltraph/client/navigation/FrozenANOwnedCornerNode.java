package dev.veltraph.client.navigation;
import dev.veltraph.client.world.BlockPos;
/** Source-pinned owned coordinate selection only; no SDK/world query or admission. */
final class FrozenANOwnedCornerNode {
    static BlockPos select(BlockPos previousCornerNode,int bx,int ceiling,int bz){return new BlockPos(bx,ceiling-1,bz);}
}
