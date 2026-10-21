package dev.veltraph.client.mixin;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import dev.veltraph.client.rewarp.mc.IssuedFarmTransportOrigin;
import dev.veltraph.client.scheduling.mc.NativeRestTransport;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
@Mixin(NativeRestTransport.class)
public abstract class IssuedFarmTransportOriginMixin {
 @WrapOperation(method="disconnect()Z",at=@At(value="INVOKE",target="Lnet/minecraft/client/Minecraft;disconnect(Lnet/minecraft/client/gui/screens/Screen;Z)V"),require=1)
 private void veltraph$issuedFarmDisconnect(Minecraft c,Screen screen,boolean transfer,Operation<Void> original){var i=IssuedFarmTransportOrigin.disconnectEntered((NativeRestTransport)(Object)this,c,screen,transfer);boolean normal=false;try{original.call(c,screen,transfer);normal=true;}finally{IssuedFarmTransportOrigin.disconnectReturned(i,c,screen,normal);}}
 @WrapOperation(method="rejoin()Z",at=@At(value="INVOKE",target="Lnet/minecraft/client/gui/screens/ConnectScreen;startConnecting(Lnet/minecraft/client/gui/screens/Screen;Lnet/minecraft/client/Minecraft;Lnet/minecraft/client/multiplayer/resolver/ServerAddress;Lnet/minecraft/client/multiplayer/ServerData;ZLnet/minecraft/client/multiplayer/TransferState;)V"),require=1)
 private void veltraph$issuedFarmConnect(Screen parent,Minecraft c,ServerAddress address,ServerData server,boolean quick,net.minecraft.client.multiplayer.TransferState state,Operation<Void> original){var i=IssuedFarmTransportOrigin.connectEntered((NativeRestTransport)(Object)this,c,parent,server);boolean normal=false;try{original.call(parent,c,address,server,quick,state);normal=true;}finally{IssuedFarmTransportOrigin.connectReturned(i,c,normal);}}
 @WrapMethod(method="rejoin()Z",require=1)
 private boolean veltraph$issuedFarmConnectOuter(Operation<Boolean> original){boolean normal=false;boolean result=false;try{result=original.call();normal=true;return result;}finally{IssuedFarmTransportOrigin.connectOuterReturned((NativeRestTransport)(Object)this,normal&&result);}}
}
