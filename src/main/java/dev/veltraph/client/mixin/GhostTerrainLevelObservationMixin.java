package dev.veltraph.client.mixin;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import dev.veltraph.client.combat.ghost.GhostSourceWalkTerrainEpochs;
/** Read-only mutation version observation; does not cancel, modify or inspect packet/account data. */
@Mixin(Level.class)
public abstract class GhostTerrainLevelObservationMixin {
    @Inject(method="<init>",at=@At("RETURN"))
    private void veltraph$levelObserved(CallbackInfo ci){if((Object)this instanceof ClientLevel)GhostSourceWalkTerrainEpochs.observedLevelHook(this);}
    @Inject(method="setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",at=@At("HEAD"))
    private void veltraph$blockMutation(net.minecraft.core.BlockPos pos,net.minecraft.world.level.block.state.BlockState state,int flags,int recursion,CallbackInfoReturnable<Boolean> cir){if((Object)this instanceof ClientLevel)GhostSourceWalkTerrainEpochs.changedCell(this,new dev.veltraph.client.combat.ghost.GhostSourceWalkFrontier.Cell(pos.getX(),pos.getY(),pos.getZ()));}
    @Inject(method="setBlockEntity",at=@At("HEAD"))
    private void veltraph$blockEntityMutation(net.minecraft.world.level.block.entity.BlockEntity entity,CallbackInfo ci){if((Object)this instanceof ClientLevel){var pos=entity.getBlockPos();GhostSourceWalkTerrainEpochs.changedCell(this,new dev.veltraph.client.combat.ghost.GhostSourceWalkFrontier.Cell(pos.getX(),pos.getY(),pos.getZ()));}}
    @Inject(method="removeBlockEntity",at=@At("HEAD"))
    private void veltraph$blockEntityRemoval(net.minecraft.core.BlockPos pos,CallbackInfo ci){if((Object)this instanceof ClientLevel)GhostSourceWalkTerrainEpochs.changedCell(this,new dev.veltraph.client.combat.ghost.GhostSourceWalkFrontier.Cell(pos.getX(),pos.getY(),pos.getZ()));}
}
