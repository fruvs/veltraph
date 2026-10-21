package dev.veltraph.client.builder;

import dev.veltraph.client.world.BlockPos;
import java.util.Objects;

/** Exact dimension-aware request rotation from recovered FarmBuilderRequest/rotation bytecode. */
public record SourceBuilderTransform(int width,int height,int depth,BlockPos origin,int quarterTurns) {
    public SourceBuilderTransform {
        Objects.requireNonNull(origin,"origin");
        if(width<1||depth<1||height<1||width>481||depth>481||height>256)
            throw new IllegalArgumentException("Invalid source builder dimensions");
        quarterTurns=Math.floorMod(quarterTurns,4);
    }
    public static SourceBuilderTransform centered(int width,int height,int depth,BlockPos player,int turns) {
        return new SourceBuilderTransform(width,height,depth,player.add(-width/2,-height/2,-depth/2),turns);
    }
    public BlockPos apply(BlockPos relative) {
        int x=relative.x(),z=relative.z();
        BlockPos rotated=switch(quarterTurns){
            case 0->relative;
            case 1->new BlockPos(depth-z-1,relative.y(),x);
            case 2->new BlockPos(width-x-1,relative.y(),depth-z-1);
            default->new BlockPos(z,relative.y(),width-x-1);
        };
        long wx=(long)origin.x()+rotated.x(),wy=(long)origin.y()+rotated.y(),wz=(long)origin.z()+rotated.z();
        if(wx< -240||wx>239||wz< -240||wz>239||wy<0||wy>255)
            throw new IllegalArgumentException("Source builder entry outside Garden bounds");
        return new BlockPos((int)wx,(int)wy,(int)wz);
    }
    public BuildLayout apply(BuildLayout layout) {
        return new BuildLayout(layout.name(),layout.entries().stream()
                .map(e->new BuildLayout.Entry(e.operation(),apply(e.pos()),e.block())).toList());
    }
}
