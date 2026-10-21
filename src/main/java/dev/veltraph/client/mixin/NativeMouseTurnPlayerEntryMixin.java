package dev.veltraph.client.mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.veltraph.client.observation.nativeinput.NativeMouseInvocationPhase;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
@Mixin(MouseHandler.class)
public abstract class NativeMouseTurnPlayerEntryMixin {
 @WrapMethod(method="turnPlayer(D)V")
 private void veltraph$originalWholeTurnPlayer(double partial,Operation<Void> original){NativeMouseInvocationPhase.TurnPlayer entered=null;
  try{entered=NativeMouseInvocationPhase.beginTurnPlayer((MouseHandler)(Object)this);}catch(RuntimeException|Error unavailable){}boolean normal=false;
  try{original.call(partial);normal=true;}finally{try{NativeMouseInvocationPhase.closeTurnPlayer(entered,normal);}catch(RuntimeException|Error unavailable){}}
 }
}
