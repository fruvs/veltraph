package dev.veltraph.client.mixin;
import dev.veltraph.client.VeltraphBootstrap;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Original event boundary: incoming native state and current pre-application world state. */
@Mixin(ClientLevel.class)
public class SourceFloorVerifiedBlockMixin {
    @Inject(method="setServerVerifiedBlockState(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)V",at=@At("HEAD"),require=1)
    private void veltraph$verifiedFloor(BlockPos position,BlockState incoming,int flags,CallbackInfo ci){try{VeltraphBootstrap.onSourceFloorVerifiedBlock((ClientLevel)(Object)this,position,incoming);}catch(RuntimeException unavailable){/* Native packet application remains authoritative; unavailable observer grants no immunity. */}}
}
