package dev.veltraph.client.mixin;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import dev.veltraph.client.observation.nativeinput.NativeGuiKeyResetLineage;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
/** Only the SDK grabMouse INVOKE can pair its one inner-null GUI operation. */
@Mixin(MouseHandler.class)
public abstract class NativeManualGuiGrabInnerMixin {
 @WrapOperation(method="grabMouse()V",at=@At(value="INVOKE",target="Lnet/minecraft/client/gui/Gui;setScreen(Lnet/minecraft/client/gui/screens/Screen;)V"),require=1)
 private void veltraph$manualGuiGrabbedInner(Gui gui,Screen next,Operation<Void> original){
  NativeGuiKeyResetLineage.InnerCall observed=null;try{observed=NativeGuiKeyResetLineage.beforeGrabbedInner((MouseHandler)(Object)this,gui,next);}catch(RuntimeException|Error unavailable){}
  boolean normal=false;try{original.call(gui,next);normal=true;}finally{try{NativeGuiKeyResetLineage.afterGrabbedInner(observed,normal);}catch(RuntimeException|Error unavailable){}}
 }
}
