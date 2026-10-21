package dev.veltraph.client.mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.veltraph.client.observation.nativeinput.McNativeManualInputLineage;
import dev.veltraph.client.observation.nativeinput.NativeMouseInvocationPhase;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
/** Actual original Entity.turn entry, after delegated mouse invoker callbacks and before its field effects. */
@Mixin(Entity.class)
public abstract class NativeManualMouseTurnOpcodeMixin {
 @WrapMethod(method="turn(DD)V")
 private void veltraph$manualActualEntityTurn(double dx,double dy,Operation<Void> original){
  NativeMouseInvocationPhase.EntityTurn entered=null;McNativeManualInputLineage.PoseEpisode observed=null;
  try{entered=NativeMouseInvocationPhase.beginEntityTurn((Entity)(Object)this,dx,dy);
   if(entered!=null)observed=McNativeManualInputLineage.beginNativeTurn((Entity)(Object)this,dx,dy);
  }catch(RuntimeException|Error unavailable){}boolean normal=false;
  try{original.call(dx,dy);normal=true;}finally{
   try{McNativeManualInputLineage.finishNativeTurn(observed,normal);}catch(RuntimeException|Error unavailable){}
   finally{try{NativeMouseInvocationPhase.closeEntityTurn(entered,normal);}catch(RuntimeException|Error unavailable){}}
  }
 }
}
