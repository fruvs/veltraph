package dev.veltraph.client.mixin;
import dev.veltraph.client.galatea.McFigToolWearBridge;
import dev.veltraph.client.service.mc.McBlockBreakingService;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Records the real existing predicate return. No predicate reevaluation or cancellation. */
@Mixin(value=McBlockBreakingService.class,remap=false)
public abstract class FigToolWearGuardObserverMixin {
    @Inject(method="allowsDestroyBlock",at=@At("RETURN"),require=1)
    private static void wear$guard(MultiPlayerGameMode mode,BlockPos target,CallbackInfoReturnable<Boolean> ci){McFigToolWearBridge.observeSafely(()->McFigToolWearBridge.guardReturned(mode,target,ci.getReturnValue()));}
}
