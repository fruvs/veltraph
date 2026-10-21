package dev.veltraph.client.mixin;

import dev.veltraph.client.combat.heal.mc.McReceivedHealth;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundSetHealthPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Passive exact26.3 tail hooks. No metadata index, hurt animation or predicted health is interpreted. */
@Mixin(ClientPacketListener.class)
public final class HealHealthObservationMixin {
    @Inject(method="handleSetHealth(Lnet/minecraft/network/protocol/game/ClientboundSetHealthPacket;)V",at=@At("TAIL"))
    private void veltraph$health(ClientboundSetHealthPacket packet,CallbackInfo ci){McReceivedHealth.observeNative(Minecraft.getInstance(),this);}
    @Inject(method="handleSetEntityData(Lnet/minecraft/network/protocol/game/ClientboundSetEntityDataPacket;)V",at=@At("TAIL"))
    private void veltraph$localHealthMetadata(ClientboundSetEntityDataPacket packet,CallbackInfo ci){var client=Minecraft.getInstance();if(client.player!=null&&client.level!=null&&packet.id()==client.player.getId()&&client.level.getEntity(packet.id())==client.player)McReceivedHealth.observeNative(client,this);}
}
