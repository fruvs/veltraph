package dev.veltraph.client.mixin;
import dev.veltraph.client.navigation.mc.CollectionSceneHistory;
import dev.veltraph.client.navigation.mc.CollectionSceneNativeAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.chunk.PalettedContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(PalettedContainer.class)
public abstract class CollectionScenePaletteMixin implements CollectionSceneNativeAccess.PaletteObserver {
    @Inject(method="getAndSet(ILjava/lang/Object;)Ljava/lang/Object;",at=@At("HEAD"),require=1)
    private void veltraph$sceneExchange(int index,Object value,CallbackInfoReturnable<Object> cir) {
        CollectionSceneHistory.nativeCellWrite(this,index);
    }
    @Inject(method="set(ILjava/lang/Object;)V",at=@At("HEAD"),require=1)
    private void veltraph$sceneSet(int index,Object value,CallbackInfo ci) {
        CollectionSceneHistory.nativeCellWrite(this,index);
    }
    @Inject(method="read(Lnet/minecraft/network/FriendlyByteBuf;)V",at=@At("HEAD"),require=1)
    private void veltraph$sceneRead(FriendlyByteBuf buf,CallbackInfo ci) { CollectionSceneHistory.nativeCellWrite(this,-1); }
}
