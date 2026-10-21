package dev.veltraph.client.farming.movement;

import dev.veltraph.client.world.Vec3;
import java.util.OptionalDouble;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;

/** Actual loaded vanilla collision shapes; never changes world/player/permissions. */
public final class McFarmCollisionProbe implements FarmCollisionProbe {
    private final Minecraft client;
    public McFarmCollisionProbe(Minecraft client){this.client=client;}
    private double half(){return client.player==null?.3:client.player.getBbWidth()/2.0;}
    private AABB box(Vec3 feet){double radius=half()-1e-6,height=client.player==null?1.8:client.player.getBbHeight();
        return new AABB(feet.x()-radius,feet.y()+1e-6,feet.z()-radius,
                feet.x()+radius,feet.y()+height-1e-6,feet.z()+radius);}
    @Override public boolean loaded(Vec3 feet){
        if(client.level==null||client.player==null)return false;
        var box=box(feet);
        for(int x=(int)Math.floor(box.minX);x<=Math.floor(box.maxX);x++)
            for(int z=(int)Math.floor(box.minZ);z<=Math.floor(box.maxZ);z++)
                for(int y=(int)Math.floor(box.minY)-1;y<=Math.floor(box.maxY);y++)
                    if(!client.level.isLoaded(new BlockPos(x,y,z)))return false;
        return true;
    }
    @Override public boolean clear(Vec3 feet,boolean allowWater){
        if(!loaded(feet))return false;
        var box=box(feet);
        for(BlockPos p:BlockPos.betweenClosed((int)Math.floor(box.minX),(int)Math.floor(box.minY),(int)Math.floor(box.minZ),
                (int)Math.floor(box.maxX),(int)Math.floor(box.maxY),(int)Math.floor(box.maxZ))) {
            var state=client.level.getBlockState(p);var fluid=state.getFluidState();
            if(fluid.is(FluidTags.LAVA)||!allowWater&&!fluid.isEmpty()||state.is(Blocks.CACTUS)
                    ||state.is(Blocks.FIRE)||state.is(Blocks.SOUL_FIRE)||state.is(Blocks.COBWEB)
                    ||state.is(Blocks.SWEET_BERRY_BUSH)||state.is(Blocks.POWDER_SNOW))return false;
        }
        return client.level.noCollision(client.player,box);
    }
    @Override public OptionalDouble supportY(double x,double z,double highest,int downBlocks){
        if(client.level==null||!Double.isFinite(highest)||downBlocks<0||downBlocks>8)return OptionalDouble.empty();
        double radius=half()+1e-6;
        double best=Double.NEGATIVE_INFINITY;
        for(int bx=(int)Math.floor(x-radius);bx<=Math.floor(x+radius);bx++)
            for(int bz=(int)Math.floor(z-radius);bz<=Math.floor(z+radius);bz++)
                for(int by=(int)Math.floor(highest);by>=Math.floor(highest)-downBlocks;by--) {
                    var p=new BlockPos(bx,by,bz);
                    if(!client.level.isLoaded(p))return OptionalDouble.empty();
                    for(var shape:client.level.getBlockState(p).getCollisionShape(client.level,p,
                            client.player==null?CollisionContext.empty():CollisionContext.of(client.player)).toAabbs()) {
                        double top=by+shape.maxY;
                        if(top<=highest+.001&&bx+shape.maxX>x-radius&&bx+shape.minX<x+radius
                                &&bz+shape.maxZ>z-radius&&bz+shape.minZ<z+radius)best=Math.max(best,top);
                    }
                }
        return best==Double.NEGATIVE_INFINITY?OptionalDouble.empty():OptionalDouble.of(best);
    }
    @Override public boolean water(Vec3 feet){
        if(!loaded(feet))return false;
        var b=box(feet);
        for(var p:BlockPos.betweenClosed((int)Math.floor(b.minX),(int)Math.floor(b.minY),(int)Math.floor(b.minZ),
                (int)Math.floor(b.maxX),(int)Math.floor(b.maxY),(int)Math.floor(b.maxZ)))
            if(client.level.getFluidState(p).is(FluidTags.WATER))return true;
        return false;
    }
    @Override public boolean matureHarvestHit(String cropId){
        if(!"minecraft:wheat".equals(cropId)&&!"minecraft:carrots".equals(cropId)
                &&!"minecraft:potatoes".equals(cropId)&&!"minecraft:nether_wart".equals(cropId))return false;
        var player=client.player;var level=client.level;var mode=client.gameMode;
        if(player==null||level==null||mode==null||player.level()!=level)return false;
        var listener=client.getConnection();if(listener==null)return false;
        var transport=listener.getConnection();if(transport==null||client.getCameraEntity()!=player)return false;
        var position=player.position();float yaw=player.getYRot(),pitch=player.getXRot();
        if(position==null||!Double.isFinite(position.x)||!Double.isFinite(position.y)||!Double.isFinite(position.z)
            ||!Float.isFinite(yaw)||!Float.isFinite(pitch))return false;
        return FreshHarvestRay.available(new FreshHarvestRay.Read(){
            private boolean rawAliases(){return client.player==player&&client.level==level&&client.gameMode==mode&&player.connection==listener;}
            private boolean aliases(){
                if(!rawAliases())return false;
                var actualPosition=player.position();if(!rawAliases())return false;
                float actualYaw=player.getYRot();if(!rawAliases())return false;
                float actualPitch=player.getXRot();
                return rawAliases()&&actualPosition==position
                    &&Float.floatToRawIntBits(actualYaw)==Float.floatToRawIntBits(yaw)
                    &&Float.floatToRawIntBits(actualPitch)==Float.floatToRawIntBits(pitch);
            }
            public boolean current(){
                if(!aliases()||player.level()!=level||!aliases())return false;
                var actualListener=client.getConnection();if(!aliases())return false;
                var actualTransport=listener.getConnection();if(!aliases())return false;
                var camera=client.getCameraEntity();
                return aliases()&&actualListener==listener&&actualTransport==transport&&camera==player&&rawAliases();
            }
            public double range(){return player.blockInteractionRange();}
            public FreshHarvestRay.Hit pick(double range){
                // Canonical current pose, unchanged latched view. No renderer field or pose is set.
                var picked=player.pick(range,1f,false);
                if(!(picked instanceof net.minecraft.world.phys.BlockHitResult hit)
                    ||hit.getType()!=net.minecraft.world.phys.HitResult.Type.BLOCK)return null;
                var cell=hit.getBlockPos();var location=hit.getLocation();
                return new FreshHarvestRay.Hit(new dev.veltraph.client.world.BlockPos(cell.getX(),cell.getY(),cell.getZ()),
                    new Vec3(location.x,location.y,location.z));
            }
            private BlockPos nativeCell(dev.veltraph.client.world.BlockPos cell){return new BlockPos(cell.x(),cell.y(),cell.z());}
            public boolean loaded(dev.veltraph.client.world.BlockPos cell){return level.isLoaded(nativeCell(cell));}
            public boolean matureWheat(dev.veltraph.client.world.BlockPos cell){
                var state=level.getBlockState(nativeCell(cell));
                return state.is(Blocks.WHEAT)&&state.getBlock() instanceof CropBlock crop&&crop.isMaxAge(state);
            }
            public boolean farmland(dev.veltraph.client.world.BlockPos cell){return level.getBlockState(nativeCell(cell)).is(Blocks.FARMLAND);}
            public boolean matureSelectedCrop(dev.veltraph.client.world.BlockPos cell){
                var state=level.getBlockState(nativeCell(cell));
                return switch(cropId){
                    case "minecraft:wheat"->state.is(Blocks.WHEAT)&&state.getBlock() instanceof CropBlock crop&&crop.isMaxAge(state);
                    case "minecraft:carrots"->state.is(Blocks.CARROTS)&&state.getBlock() instanceof CropBlock crop&&crop.isMaxAge(state);
                    case "minecraft:potatoes"->state.is(Blocks.POTATOES)&&state.getBlock() instanceof CropBlock crop&&crop.isMaxAge(state);
                    case "minecraft:nether_wart"->state.is(Blocks.NETHER_WART)&&state.getValue(NetherWartBlock.AGE)==NetherWartBlock.MAX_AGE;
                    default->false;
                };
            }
            public boolean selectedCropSupport(dev.veltraph.client.world.BlockPos cell){
                return level.getBlockState(nativeCell(cell)).is(
                    "minecraft:nether_wart".equals(cropId)?Blocks.SOUL_SAND:Blocks.FARMLAND);
            }
            public double distance(FreshHarvestRay.Hit hit){return player.getEyePosition().distanceTo(new net.minecraft.world.phys.Vec3(hit.location().x(),hit.location().y(),hit.location().z()));}
        });
    }
    @Override public boolean playerInWater(){
        return client.player!=null&&client.level!=null&&client.player.level()==client.level&&client.player.isInWater();
    }
    @Override public boolean matureCrop(dev.veltraph.client.world.BlockPos crop){
        if(client.level==null)return false;
        var p=new BlockPos(crop.x(),crop.y(),crop.z());if(!client.level.isLoaded(p))return false;
        var state=client.level.getBlockState(p);
        if(state.getBlock() instanceof CropBlock block)return block.isMaxAge(state);
        return state.is(Blocks.NETHER_WART)&&state.getValue(NetherWartBlock.AGE)==NetherWartBlock.MAX_AGE;
    }
    @Override public OptionalDouble matureOutlineHeight(dev.veltraph.client.world.BlockPos crop){
        var player=client.player;var level=client.level;
        if(player==null||level==null||player.level()!=level)return OptionalDouble.empty();
        var cell=new BlockPos(crop.x(),crop.y(),crop.z());
        if(!level.isLoaded(cell))return OptionalDouble.empty();
        var state=level.getBlockState(cell);
        boolean mature=state.getBlock() instanceof CropBlock block&&block.isMaxAge(state)
            ||state.is(Blocks.NETHER_WART)&&state.getValue(NetherWartBlock.AGE)==NetherWartBlock.MAX_AGE;
        if(!mature)return OptionalDouble.empty();
        var shape=state.getShape(level,cell,CollisionContext.of(player));
        if(client.player!=player||client.level!=level||player.level()!=level||shape.isEmpty())return OptionalDouble.empty();
        double height=shape.max(net.minecraft.core.Direction.Axis.Y);
        return Double.isFinite(height)&&height>0&&height<=1?OptionalDouble.of(height):OptionalDouble.empty();
    }
    @Override public float eyeHeight(){return client.player==null?1.62f:client.player.getEyeHeight();}
}
