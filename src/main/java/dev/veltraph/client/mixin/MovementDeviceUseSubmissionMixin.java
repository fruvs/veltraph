package dev.veltraph.client.mixin;
import dev.veltraph.client.movement.device.NativeDeviceTeleportReceipt;
import dev.veltraph.client.movement.device.NativeOwnedDeviceUseBoundary;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
@Mixin(Connection.class)
public abstract class MovementDeviceUseSubmissionMixin {
 @WrapMethod(method="send(Lnet/minecraft/network/protocol/Packet;)V",require=1)
 private void veltraph$movementDeviceUseSubmission(Packet<?> packet,Operation<Void> original){
  var owned=NativeOwnedDeviceUseBoundary.captureActualSend((Connection)(Object)this,packet);
  NativeDeviceTeleportReceipt.SendInvocation call=null;
  try{call=NativeDeviceTeleportReceipt.beginSend((Connection)(Object)this,packet);}catch(Throwable observationFailure){/* No global session may be revoked. */}
  boolean returned=false;
  try{if(NativeOwnedDeviceUseBoundary.afterObservation(owned,call)){original.call(packet);returned=true;}}
  finally{try{NativeDeviceTeleportReceipt.finishSend(call,returned);}catch(Throwable observationFailure){/* Never mask native sending or its original exception. */}}
 }
}
