package dev.veltraph.client.mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.veltraph.client.observation.nativeinput.McNativeManualInputLineage;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
@Mixin(MouseHandler.class)
public abstract class NativeManualQueuedMouseMoveMixin {
 @WrapMethod(method="onMove(JDDDD)V")
 private void veltraph$manualQueuedMove(long window,double x,double y,double dx,double dy,Operation<Void> original){McNativeManualInputLineage.MouseMoveEpisode observed=null;try{observed=McNativeManualInputLineage.beginNativeMouseMove(this,window,dx,dy);}catch(RuntimeException|Error unavailable){}boolean normal=false;try{original.call(window,x,y,dx,dy);normal=true;}finally{try{McNativeManualInputLineage.finishNativeMouseMove(observed,normal);}catch(RuntimeException|Error unavailable){}}}
}
