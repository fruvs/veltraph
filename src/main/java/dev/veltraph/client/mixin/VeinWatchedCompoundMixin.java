package dev.veltraph.client.mixin;
import dev.veltraph.client.mining.vein.VeinWatchGraphFields;
import dev.veltraph.client.mining.vein.VeinWatchedRevisions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(targets="net.minecraft.nbt.CompoundTag")
public abstract class VeinWatchedCompoundMixin implements VeinWatchGraphFields.Compound {
    @Shadow @Final private java.util.Map<?,?> tags;
    public java.util.Map<?,?> veltraph$watchTags(){return tags;}
    @Inject(method={"put(Ljava/lang/String;Lnet/minecraft/nbt/Tag;)Lnet/minecraft/nbt/Tag;"},at=@At("HEAD"),require=1)
    private void veltraph$watchGraphMutation0(CallbackInfoReturnable<?> ci){VeinWatchedRevisions.mutation(this);}
    @Inject(method={"putByte(Ljava/lang/String;B)V"},at=@At("HEAD"),require=1)
    private void veltraph$watchGraphMutation1(CallbackInfo ci){VeinWatchedRevisions.mutation(this);}
    @Inject(method={"putShort(Ljava/lang/String;S)V"},at=@At("HEAD"),require=1)
    private void veltraph$watchGraphMutation2(CallbackInfo ci){VeinWatchedRevisions.mutation(this);}
    @Inject(method={"putInt(Ljava/lang/String;I)V"},at=@At("HEAD"),require=1)
    private void veltraph$watchGraphMutation3(CallbackInfo ci){VeinWatchedRevisions.mutation(this);}
    @Inject(method={"putLong(Ljava/lang/String;J)V"},at=@At("HEAD"),require=1)
    private void veltraph$watchGraphMutation4(CallbackInfo ci){VeinWatchedRevisions.mutation(this);}
    @Inject(method={"putFloat(Ljava/lang/String;F)V"},at=@At("HEAD"),require=1)
    private void veltraph$watchGraphMutation5(CallbackInfo ci){VeinWatchedRevisions.mutation(this);}
    @Inject(method={"putDouble(Ljava/lang/String;D)V"},at=@At("HEAD"),require=1)
    private void veltraph$watchGraphMutation6(CallbackInfo ci){VeinWatchedRevisions.mutation(this);}
    @Inject(method={"putString(Ljava/lang/String;Ljava/lang/String;)V"},at=@At("HEAD"),require=1)
    private void veltraph$watchGraphMutation7(CallbackInfo ci){VeinWatchedRevisions.mutation(this);}
    @Inject(method={"putByteArray(Ljava/lang/String;[B)V"},at=@At("HEAD"),require=1)
    private void veltraph$watchGraphMutation8(CallbackInfo ci){VeinWatchedRevisions.mutation(this);}
    @Inject(method={"putIntArray(Ljava/lang/String;[I)V"},at=@At("HEAD"),require=1)
    private void veltraph$watchGraphMutation9(CallbackInfo ci){VeinWatchedRevisions.mutation(this);}
    @Inject(method={"putLongArray(Ljava/lang/String;[J)V"},at=@At("HEAD"),require=1)
    private void veltraph$watchGraphMutation10(CallbackInfo ci){VeinWatchedRevisions.mutation(this);}
    @Inject(method={"putBoolean(Ljava/lang/String;Z)V"},at=@At("HEAD"),require=1)
    private void veltraph$watchGraphMutation11(CallbackInfo ci){VeinWatchedRevisions.mutation(this);}
    @Inject(method={"remove(Ljava/lang/String;)Lnet/minecraft/nbt/Tag;"},at=@At("HEAD"),require=1)
    private void veltraph$watchGraphMutation12(CallbackInfoReturnable<?> ci){VeinWatchedRevisions.mutation(this);}
    @Inject(method={"merge(Lnet/minecraft/nbt/CompoundTag;)Lnet/minecraft/nbt/CompoundTag;"},at=@At("HEAD"),require=1)
    private void veltraph$watchGraphMutation13(CallbackInfoReturnable<?> ci){VeinWatchedRevisions.mutation(this);}
    @Inject(method={"store(Ljava/lang/String;Lcom/mojang/serialization/Codec;Ljava/lang/Object;)V"},at=@At("HEAD"),require=1)
    private void veltraph$watchGraphMutation14(CallbackInfo ci){VeinWatchedRevisions.mutation(this);}
    @Inject(method={"storeNullable(Ljava/lang/String;Lcom/mojang/serialization/Codec;Ljava/lang/Object;)V"},at=@At("HEAD"),require=1)
    private void veltraph$watchGraphMutation15(CallbackInfo ci){VeinWatchedRevisions.mutation(this);}
    @Inject(method={"store(Ljava/lang/String;Lcom/mojang/serialization/Codec;Lcom/mojang/serialization/DynamicOps;Ljava/lang/Object;)V"},at=@At("HEAD"),require=1)
    private void veltraph$watchGraphMutation16(CallbackInfo ci){VeinWatchedRevisions.mutation(this);}
    @Inject(method={"storeNullable(Ljava/lang/String;Lcom/mojang/serialization/Codec;Lcom/mojang/serialization/DynamicOps;Ljava/lang/Object;)V"},at=@At("HEAD"),require=1)
    private void veltraph$watchGraphMutation17(CallbackInfo ci){VeinWatchedRevisions.mutation(this);}
    @Inject(method={"store(Lcom/mojang/serialization/MapCodec;Ljava/lang/Object;)V"},at=@At("HEAD"),require=1)
    private void veltraph$watchGraphMutation18(CallbackInfo ci){VeinWatchedRevisions.mutation(this);}
    @Inject(method={"store(Lcom/mojang/serialization/MapCodec;Lcom/mojang/serialization/DynamicOps;Ljava/lang/Object;)V"},at=@At("HEAD"),require=1)
    private void veltraph$watchGraphMutation19(CallbackInfo ci){VeinWatchedRevisions.mutation(this);}
}
