package dev.veltraph.client.mixin;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import dev.veltraph.client.observation.nativeinput.NativeSdlInputLineage;
import net.minecraft.util.thread.BlockableEventLoop;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
@Mixin(BlockableEventLoop.class)
public abstract class NativeSdlTaskDeliveryMixin {
 @WrapOperation(method="doRunTask(Ljava/lang/Runnable;)V",at=@At(value="INVOKE",target="Ljava/lang/Runnable;run()V"),require=1)
 private void veltraph$sdlOriginalTask(Runnable task,Operation<Void> original){
  NativeSdlInputLineage.TaskRun observed=null;try{observed=NativeSdlInputLineage.beginTaskRun(this,task);}catch(RuntimeException|Error unavailable){}
  try{original.call(task);}finally{try{NativeSdlInputLineage.closeTaskRun(observed);}catch(RuntimeException|Error unavailable){}}
 }
}
