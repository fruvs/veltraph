package dev.veltraph.client.mixin;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import dev.veltraph.client.rewarp.mc.IssuedFarmTransportOrigin;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.*;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
@Mixin(ClientPacketListener.class)
public abstract class IssuedFarmTransportContextBodyMixin {
 @WrapOperation(method={"handleLogin(Lnet/minecraft/network/protocol/game/ClientboundLoginPacket;)V","handleRespawn(Lnet/minecraft/network/protocol/game/ClientboundRespawnPacket;)V"},at=@At(value="FIELD",target="Lnet/minecraft/client/Minecraft;player:Lnet/minecraft/client/player/LocalPlayer;",opcode=181),require=2)
 private void veltraph$issuedFarmPlayerField(Minecraft c,LocalPlayer player,Operation<Void> original){original.call(c,player);IssuedFarmTransportOrigin.playerWritten(c,player);}
 @WrapOperation(method={"handleLogin(Lnet/minecraft/network/protocol/game/ClientboundLoginPacket;)V","handleRespawn(Lnet/minecraft/network/protocol/game/ClientboundRespawnPacket;)V"},at=@At(value="INVOKE",target="Lnet/minecraft/client/multiplayer/ClientLevel;addEntity(Lnet/minecraft/world/entity/Entity;)V"),require=2)
 private void veltraph$issuedFarmAddedPlayer(ClientLevel level,Entity player,Operation<Void> original){boolean normal=false;try{original.call(level,player);normal=true;}finally{IssuedFarmTransportOrigin.entityAdded(level,player,normal);}}
}
