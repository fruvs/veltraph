package dev.veltraph.client.mixin;

import dev.veltraph.client.VeltraphBootstrap;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Read-only common helper input clock. This callback never dispatches or cancels an action. */
@Mixin(MultiPlayerGameMode.class)
public abstract class NativeContainerInputObservationMixin {
    @Inject(method="handleContainerInput",at=@At("HEAD"))
    private void veltraph$observeContainerInput(int syncId,int slot,int button,ContainerInput input,Player player,CallbackInfo callback){
        VeltraphBootstrap.onNativeContainerInput();
    }
}
