package dev.veltraph.client.mixin;
import dev.veltraph.client.mining.vein.VeinWatchNativeFields;
import dev.veltraph.client.mining.vein.VeinWatchedRevisions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.network.syncher.SynchedEntityData;
@Mixin(SynchedEntityData.class)
public abstract class VeinWatchedDataMixin implements VeinWatchNativeFields.DataFields {
    @Shadow @Final private SynchedEntityData.DataItem<?>[] itemsById;
    public SynchedEntityData.DataItem<?>[] veltraph$watchItems(){return itemsById;}
    @Inject(method={"set(Lnet/minecraft/network/syncher/EntityDataAccessor;Ljava/lang/Object;)V",
            "set(Lnet/minecraft/network/syncher/EntityDataAccessor;Ljava/lang/Object;Z)V",
            "assignValues(Ljava/util/List;)V"},at=@At("HEAD"),require=1)
    private void veltraph$watchData(CallbackInfo ci){VeinWatchedRevisions.mutation(this);}
}
