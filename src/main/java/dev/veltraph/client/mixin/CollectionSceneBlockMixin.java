package dev.veltraph.client.mixin;
import dev.veltraph.client.navigation.mc.CollectionSceneNativeAccess;
import net.minecraft.core.Holder;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Block.class)
public abstract class CollectionSceneBlockMixin implements CollectionSceneNativeAccess.BlockFields {
    @Unique private Object veltraph$sceneBlockRevision=new Object();
    public Object veltraph$sceneBlockRevision() { return veltraph$sceneBlockRevision; }
    public Holder.Reference<Block> veltraph$sceneBlockHolder() {
        return (Object)this instanceof CollectionSceneBlockAccessor raw ? raw.veltraph$sceneRawBlockHolder() : null;
    }
    @Inject(method="registerDefaultState(Lnet/minecraft/world/level/block/state/BlockState;)V",at=@At("HEAD"),require=1)
    private void veltraph$sceneDefaultState(CallbackInfo ci) { veltraph$sceneBlockRevision=new Object(); }
}
