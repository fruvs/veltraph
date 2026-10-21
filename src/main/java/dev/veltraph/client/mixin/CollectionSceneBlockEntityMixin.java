package dev.veltraph.client.mixin;
import dev.veltraph.client.navigation.mc.CollectionSceneHistory;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(BlockEntity.class)
public abstract class CollectionSceneBlockEntityMixin {
    @Shadow protected Level level;
    @Shadow @Final protected BlockPos worldPosition;
    @Inject(method={"loadWithComponents(Lnet/minecraft/world/level/storage/ValueInput;)V",
        "loadCustomOnly(Lnet/minecraft/world/level/storage/ValueInput;)V","setChanged()V"},at=@At("HEAD"),require=1)
    private void veltraph$sceneBlockEntityData(CallbackInfo ci) {
        CollectionSceneHistory.nativeBlockEntityWrite(level,worldPosition);
    }
}
