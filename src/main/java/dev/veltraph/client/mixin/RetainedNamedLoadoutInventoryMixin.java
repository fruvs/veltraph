package dev.veltraph.client.mixin;
import dev.veltraph.client.garden.loadout.mc.McNamedLoadoutPort;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(Inventory.class)
public abstract class RetainedNamedLoadoutInventoryMixin {
    @com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod(method="setItem(ILnet/minecraft/world/item/ItemStack;)V",require=1)
    private void named$setItem(int slot,ItemStack next,com.llamalad7.mixinextras.injector.wrapoperation.Operation<Void> original){
        McNamedLoadoutPort.InventoryWrite call=null;try{call=McNamedLoadoutPort.inventoryWriteEntry((Inventory)(Object)this,slot,next);}catch(Throwable failure){McNamedLoadoutPort.nativeObservationFailure();}
        boolean normal=false;try{original.call(slot,next);normal=true;}
        finally{try{McNamedLoadoutPort.inventoryWriteExit(call,normal);}catch(Throwable failure){McNamedLoadoutPort.nativeObservationFailure();}}
    }
    @Inject(method={"clearContent()V","replaceWith(Lnet/minecraft/world/entity/player/Inventory;)V","load(Lnet/minecraft/world/level/storage/ValueInput$TypedInputList;)V","dropAll()V"},at=@At("HEAD"),require=1)
    private void named$inventoryVoid(CallbackInfo ci){McNamedLoadoutPort.wholeInventoryMutation(this);}
    @Inject(method={"removeItem(II)Lnet/minecraft/world/item/ItemStack;","removeItemNoUpdate(I)Lnet/minecraft/world/item/ItemStack;","setSelectedItem(Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/item/ItemStack;","removeFromSelected(Z)Lnet/minecraft/world/item/ItemStack;"},at=@At("HEAD"),require=1)
    private void named$inventoryResult(CallbackInfoReturnable<ItemStack> ci){McNamedLoadoutPort.protectedInventoryMutation(this);}
    @Inject(method="removeItem(Lnet/minecraft/world/item/ItemStack;)V",at=@At("HEAD"),require=1)
    private void named$removeExactStack(CallbackInfo ci){McNamedLoadoutPort.protectedInventoryMutation(this);}
}
