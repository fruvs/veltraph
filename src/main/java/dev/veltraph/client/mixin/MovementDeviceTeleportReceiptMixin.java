package dev.veltraph.client.mixin;
import dev.veltraph.client.movement.device.NativeDeviceTeleportReceipt;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import org.spongepowered.asm.mixin.Mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
@Mixin(ClientPacketListener.class)
public final class MovementDeviceTeleportReceiptMixin {
 @WrapMethod(method="handleMovePlayer",require=1)
 private void veltraph$movementDeviceAppliedPosition(ClientboundPlayerPositionPacket packet,Operation<Void> original){
  NativeDeviceTeleportReceipt.ReceiveInvocation call=null;
  try{call=NativeDeviceTeleportReceipt.beginReceive(Minecraft.getInstance(),this,packet);}catch(Throwable observationFailure){/* No global session may be revoked. */}
  boolean returned=false;
  try{original.call(packet);returned=true;}
  finally{try{NativeDeviceTeleportReceipt.finishReceive(call,returned);}catch(Throwable observationFailure){/* Never mask native application or its original exception. */}}
 }
}
