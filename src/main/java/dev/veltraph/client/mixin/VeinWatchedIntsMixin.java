package dev.veltraph.client.mixin;
import dev.veltraph.client.mining.vein.VeinWatchGraphFields;
import dev.veltraph.client.mining.vein.VeinWatchedRevisions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(targets="net.minecraft.nbt.IntArrayTag")
public abstract class VeinWatchedIntsMixin implements VeinWatchGraphFields.Ints {
    @Shadow private int[] data;
    public int[] veltraph$watchInts(){return data;}
    @Inject(method={"setTag(ILnet/minecraft/nbt/Tag;)Z"},at=@At("HEAD"),require=1)
    private void veltraph$watchGraphMutation0(CallbackInfoReturnable<?> ci){VeinWatchedRevisions.mutation(this);}
    @Inject(method={"addTag(ILnet/minecraft/nbt/Tag;)Z"},at=@At("HEAD"),require=1)
    private void veltraph$watchGraphMutation1(CallbackInfoReturnable<?> ci){VeinWatchedRevisions.mutation(this);}
    @Inject(method={"remove(I)Lnet/minecraft/nbt/IntTag;"},at=@At("HEAD"),require=1)
    private void veltraph$watchGraphMutation2(CallbackInfoReturnable<?> ci){VeinWatchedRevisions.mutation(this);}
    @Inject(method={"clear()V"},at=@At("HEAD"),require=1)
    private void veltraph$watchGraphMutation3(CallbackInfo ci){VeinWatchedRevisions.mutation(this);}
}
