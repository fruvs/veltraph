package dev.veltraph.client.mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.mojang.blaze3d.platform.InputConstants;
import dev.veltraph.client.observation.nativeinput.McNativeManualInputLineage;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
/** Observation-only actual native held button path; existing R68/native handler effects remain once. */
@Mixin(MouseHandler.class)
public abstract class NativeManualMouseButtonOpcodeMixin {
 @WrapMethod(method="onButton(JLnet/minecraft/client/input/MouseButtonInfo;I)V")
 private void veltraph$manualButton(long window,MouseButtonInfo info,int action,Operation<Void> original){McNativeManualInputLineage.MouseButtonEpisode observed=null;try{observed=McNativeManualInputLineage.beginMouseButton(window,info,action);}catch(RuntimeException|Error unavailable){}boolean normal=false;try{original.call(window,info,action);normal=true;}finally{try{McNativeManualInputLineage.finishMouseButton(observed,normal);}catch(RuntimeException|Error unavailable){}}}
 @WrapOperation(method="onButton(JLnet/minecraft/client/input/MouseButtonInfo;I)V",at=@At(value="INVOKE",target="Lnet/minecraft/client/KeyMapping;set(Lcom/mojang/blaze3d/platform/InputConstants$Key;Z)V"),require=1)
 private void veltraph$manualButtonStaticSet(InputConstants.Key key,boolean down,Operation<Void> original){McNativeManualInputLineage.MouseStaticKeyEpisode observed=null;try{observed=McNativeManualInputLineage.beginMouseButtonSet(key,down);}catch(RuntimeException|Error unavailable){}boolean normal=false;try{original.call(key,down);normal=true;}finally{try{McNativeManualInputLineage.finishMouseButtonSet(observed,normal);}catch(RuntimeException|Error unavailable){}}}
}
