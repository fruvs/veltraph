package dev.veltraph.client.mixin;
import dev.veltraph.client.mining.vein.VeinNativeWearBridge;
import dev.veltraph.client.service.mc.McBlockBreakingService;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(value=McBlockBreakingService.class,remap=false)
public abstract class VeinWearGuardMixin {
 @Inject(method="allowsDestroyBlock",at=@At("RETURN"),require=1)
 private static void vein$guard(MultiPlayerGameMode mode,BlockPos target,CallbackInfoReturnable<Boolean> ci){VeinNativeWearBridge.observeSafely(()->VeinNativeWearBridge.guardReturned(mode,target,ci.getReturnValue()));}
}
