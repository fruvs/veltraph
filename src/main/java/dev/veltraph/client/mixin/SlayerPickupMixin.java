package dev.veltraph.client.mixin;

import dev.veltraph.client.combat.slayer.SlayerPickupSignal;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundTakeItemEntityPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Observe after vanilla's main-thread guard, before its first entity lookup and item shrink/removal. */
@Mixin(ClientPacketListener.class)
public abstract class SlayerPickupMixin {
    @Shadow private ClientLevel level;
    @Inject(method="handleTakeItemEntity",at=@At(value="INVOKE",target="Lnet/minecraft/client/multiplayer/ClientLevel;getEntity(I)Lnet/minecraft/world/entity/Entity;",ordinal=0))
    private void veltraph$observeNativePickup(ClientboundTakeItemEntityPacket packet,CallbackInfo ci){
        SlayerPickupSignal.capture(Minecraft.getInstance(),level,this,packet.getItemId(),packet.getPlayerId(),packet.getAmount());
    }
}
