package dev.veltraph.client.mixin;
import dev.veltraph.client.rewarp.mc.IssuedReturnNativeFields;
import dev.veltraph.client.rewarp.mc.IssuedReturnMutationJournal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(targets="net.minecraft.world.entity.player.Player")
public abstract class IssuedReturnPlayerFieldsMixin implements IssuedReturnNativeFields.Player {
    @Shadow @Final private net.minecraft.world.entity.player.Inventory inventory;
    public net.minecraft.world.entity.player.Inventory veltraph$returnInventory(){return inventory;}
}
