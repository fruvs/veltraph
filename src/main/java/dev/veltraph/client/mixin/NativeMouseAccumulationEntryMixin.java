package dev.veltraph.client.mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.veltraph.client.observation.nativeinput.NativeMouseInvocationPhase;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
@Mixin(MouseHandler.class)
public abstract class NativeMouseAccumulationEntryMixin {
 @WrapMethod(method="handleAccumulatedMovement()V")
 private void veltraph$originalWholeAccumulation(Operation<Void> original){NativeMouseInvocationPhase.Accumulation entered=null;
  try{entered=NativeMouseInvocationPhase.beginAccumulation((MouseHandler)(Object)this);}catch(RuntimeException|Error unavailable){}boolean normal=false;
  try{original.call();normal=true;}finally{try{NativeMouseInvocationPhase.closeAccumulation(entered,normal);}catch(RuntimeException|Error unavailable){}}
 }
}
