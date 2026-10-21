package dev.veltraph.client.mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.platform.InputConstants;
import dev.veltraph.client.observation.nativeinput.McNativeManualInputLineage;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
/** Owns only an independent passive native opcode episode, preserving original handlers and R68 hooks. */
@Mixin(KeyboardHandler.class)
public abstract class NativeManualKeyboardOpcodeMixin {
 @WrapMethod(method="keyPress(JILnet/minecraft/client/input/KeyEvent;)V")
 private void veltraph$manualKeyboard(long window,int action,KeyEvent event,Operation<Void> original){
  McNativeManualInputLineage.KeyboardEpisode observed=null;try{observed=McNativeManualInputLineage.beginKeyboard(window,action,event);}catch(RuntimeException|Error unavailable){}boolean normal=false;
  try{original.call(window,action,event);normal=true;}finally{try{McNativeManualInputLineage.finishKeyboard(observed,normal);}catch(RuntimeException|Error unavailable){}}
 }
 @WrapOperation(method="keyPress(JILnet/minecraft/client/input/KeyEvent;)V",at=@At(value="INVOKE",target="Lnet/minecraft/client/KeyMapping;set(Lcom/mojang/blaze3d/platform/InputConstants$Key;Z)V"),require=4)
 private void veltraph$manualStaticKey(InputConstants.Key key,boolean down,Operation<Void> original){
  McNativeManualInputLineage.StaticKeyEpisode observed=null;try{observed=McNativeManualInputLineage.beginKeyboardSet(key,down);}catch(RuntimeException|Error unavailable){}boolean normal=false;
  try{original.call(key,down);normal=true;}finally{try{McNativeManualInputLineage.finishKeyboardSet(observed,normal);}catch(RuntimeException|Error unavailable){}}
 }
}
