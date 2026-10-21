package dev.veltraph.client.mixin;
import dev.veltraph.client.navigation.mc.CollectionSceneHistory;
import dev.veltraph.client.navigation.mc.CollectionSceneNativeAccess;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class CollectionSceneStateMixin implements CollectionSceneNativeAccess.State {
    @Unique private Object veltraph$sceneCacheIdentity;
    public Object veltraph$sceneCacheIdentity() { return veltraph$sceneCacheIdentity; }
    @Inject(method="initCache()V",at=@At("HEAD"),require=1)
    private void veltraph$sceneInit(CallbackInfo ci) {
        CollectionSceneHistory.nativeStateCacheWrite(this); veltraph$sceneCacheIdentity=null;
    }
    @Inject(method="initCache()V",at=@At(value="FIELD",
        target="Lnet/minecraft/world/level/block/state/BlockBehaviour$BlockStateBase;cache:Lnet/minecraft/world/level/block/state/BlockBehaviour$BlockStateBase$Cache;",
        opcode=181,shift=At.Shift.AFTER),require=1)
    private void veltraph$sceneCacheAssignment(CallbackInfo ci) { veltraph$sceneCacheIdentity=new Object(); }
}
