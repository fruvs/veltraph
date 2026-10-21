package dev.veltraph.client.mixin;
import dev.veltraph.client.mining.vein.VeinWatchNativeFields;
import dev.veltraph.client.mining.vein.VeinWatchedRevisions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.network.syncher.SynchedEntityData;
@Mixin(SynchedEntityData.DataItem.class)
public abstract class VeinWatchedDataItemMixin implements VeinWatchNativeFields.DataItemFields {
    @Shadow private Object value;
    public Object veltraph$watchValue(){return value;}
    @Inject(method="setValue(Ljava/lang/Object;)V",at=@At("HEAD"),require=1)
    private void veltraph$watchRawData(Object value,CallbackInfo ci){VeinWatchedRevisions.mutation(this);}
}
