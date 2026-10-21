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
@Mixin(targets="net.minecraft.nbt.ListTag")
public abstract class VeinWatchedListMixin implements VeinWatchGraphFields.NbtList {
    @Shadow @Final private java.util.List<?> list;
    public java.util.List<?> veltraph$watchList(){return list;}
    @Inject(method={"addAndUnwrap(Lnet/minecraft/nbt/Tag;)V"},at=@At("HEAD"),require=1)
    private void veltraph$watchGraphMutation0(CallbackInfo ci){VeinWatchedRevisions.mutation(this);}
    @Inject(method={"remove(I)Lnet/minecraft/nbt/Tag;"},at=@At("HEAD"),require=1)
    private void veltraph$watchGraphMutation1(CallbackInfoReturnable<?> ci){VeinWatchedRevisions.mutation(this);}
    @Inject(method={"set(ILnet/minecraft/nbt/Tag;)Lnet/minecraft/nbt/Tag;"},at=@At("HEAD"),require=1)
    private void veltraph$watchGraphMutation2(CallbackInfoReturnable<?> ci){VeinWatchedRevisions.mutation(this);}
    @Inject(method={"add(ILnet/minecraft/nbt/Tag;)V"},at=@At("HEAD"),require=1)
    private void veltraph$watchGraphMutation3(CallbackInfo ci){VeinWatchedRevisions.mutation(this);}
    @Inject(method={"setTag(ILnet/minecraft/nbt/Tag;)Z"},at=@At("HEAD"),require=1)
    private void veltraph$watchGraphMutation4(CallbackInfoReturnable<?> ci){VeinWatchedRevisions.mutation(this);}
    @Inject(method={"addTag(ILnet/minecraft/nbt/Tag;)Z"},at=@At("HEAD"),require=1)
    private void veltraph$watchGraphMutation5(CallbackInfoReturnable<?> ci){VeinWatchedRevisions.mutation(this);}
    @Inject(method={"clear()V"},at=@At("HEAD"),require=1)
    private void veltraph$watchGraphMutation6(CallbackInfo ci){VeinWatchedRevisions.mutation(this);}
}
