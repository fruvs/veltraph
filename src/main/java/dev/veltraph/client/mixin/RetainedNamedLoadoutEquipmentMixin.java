package dev.veltraph.client.mixin;
import dev.veltraph.client.garden.loadout.mc.McNamedLoadoutPort;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
@Mixin(EntityEquipment.class)
public abstract class RetainedNamedLoadoutEquipmentMixin {
    @WrapMethod(method="set(Lnet/minecraft/world/entity/EquipmentSlot;Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/item/ItemStack;",require=1)
    private ItemStack named$equipment(EquipmentSlot slot,ItemStack next,Operation<ItemStack> original){
        McNamedLoadoutPort.EquipmentWrite call=null;try{call=McNamedLoadoutPort.equipmentWriteEntry(this,slot,next);}catch(Throwable failure){McNamedLoadoutPort.nativeObservationFailure();}
        boolean normal=false;try{var result=original.call(slot,next);normal=true;return result;}
        finally{try{McNamedLoadoutPort.equipmentWriteExit(call,normal);}catch(Throwable failure){McNamedLoadoutPort.nativeObservationFailure();}}
    }
    @org.spongepowered.asm.mixin.injection.Inject(method={"setAll(Lnet/minecraft/world/entity/EntityEquipment;)V","clear()V","dropAll(Lnet/minecraft/world/entity/LivingEntity;)V"},at=@org.spongepowered.asm.mixin.injection.At("HEAD"),require=1)
    private void named$equipmentMutation(org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci){McNamedLoadoutPort.equipmentMutation(this);}
}
