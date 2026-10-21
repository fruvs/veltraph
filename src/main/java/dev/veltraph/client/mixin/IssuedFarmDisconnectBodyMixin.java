package dev.veltraph.client.mixin;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import dev.veltraph.client.rewarp.mc.IssuedFarmTransportOrigin;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
@Mixin(Minecraft.class)
public abstract class IssuedFarmDisconnectBodyMixin {
 @WrapOperation(method="disconnect(Lnet/minecraft/client/gui/screens/Screen;ZZ)V",at=@At(value="FIELD",target="Lnet/minecraft/client/Minecraft;level:Lnet/minecraft/client/multiplayer/ClientLevel;",opcode=181),require=1)
 private void veltraph$issuedFarmLevelClear(Minecraft c,ClientLevel level,Operation<Void> original){original.call(c,level);IssuedFarmTransportOrigin.disconnectFieldWritten(c,level,false);}
 @WrapOperation(method="disconnect(Lnet/minecraft/client/gui/screens/Screen;ZZ)V",at=@At(value="FIELD",target="Lnet/minecraft/client/Minecraft;player:Lnet/minecraft/client/player/LocalPlayer;",opcode=181),require=1)
 private void veltraph$issuedFarmPlayerClear(Minecraft c,LocalPlayer player,Operation<Void> original){original.call(c,player);IssuedFarmTransportOrigin.disconnectFieldWritten(c,player,true);}
}
