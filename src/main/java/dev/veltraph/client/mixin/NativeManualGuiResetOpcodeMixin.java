package dev.veltraph.client.mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import dev.veltraph.client.observation.nativeinput.NativeGuiKeyResetLineage;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import dev.veltraph.client.service.mc.RootNativeHistory;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
@Mixin(Gui.class)
public abstract class NativeManualGuiResetOpcodeMixin {
 @WrapMethod(method="setScreen(Lnet/minecraft/client/gui/screens/Screen;)V")
 private void veltraph$manualGuiTransition(Screen next,Operation<Void> original){
  NativeGuiKeyResetLineage.Episode observed=null;NativeGuiKeyResetLineage.ActualGuiEntry entry=null;RootNativeHistory.GuiTicket ticket=null;Minecraft client=Minecraft.getInstance();
  try{observed=NativeGuiKeyResetLineage.begin(next);entry=NativeGuiKeyResetLineage.actualGuiEntry(observed,client);if(entry!=null)ticket=RootNativeHistory.beginActualGui(entry,client);}catch(RuntimeException|Error unavailable){}
  boolean normal=false;try{original.call(next);normal=true;}finally{
   try{NativeGuiKeyResetLineage.finish(observed,normal);}catch(RuntimeException|Error unavailable){}
   NativeGuiKeyResetLineage.ActualGuiOutcome outcome=null;try{outcome=NativeGuiKeyResetLineage.actualGuiOutcome(observed,entry,normal,client);}catch(RuntimeException|Error unavailable){}
   if(ticket!=null)try{if(normal)RootNativeHistory.finishActualGui(ticket,outcome,client);else RootNativeHistory.failedActualGui(ticket,outcome,client);}catch(RuntimeException|Error unavailable){}
  }
 }
 @WrapOperation(method="setScreen(Lnet/minecraft/client/gui/screens/Screen;)V",at=@At(value="INVOKE",target="Lnet/minecraft/client/KeyMapping;releaseAll()V"),require=1)
 private void veltraph$manualGuiReleaseAll(Operation<Void> original){try{NativeGuiKeyResetLineage.beginReleaseAll();}catch(RuntimeException|Error unavailable){}original.call();}
 @WrapOperation(method="setScreen(Lnet/minecraft/client/gui/screens/Screen;)V",at=@At(value="INVOKE",target="Lnet/minecraft/client/KeyMapping;restoreToggleStatesOnScreenClosed()V"),require=1)
 private void veltraph$manualGuiRestoreAll(Operation<Void> original){try{NativeGuiKeyResetLineage.beginRestoreAll();}catch(RuntimeException|Error unavailable){}original.call();}
 @WrapOperation(method="setScreen(Lnet/minecraft/client/gui/screens/Screen;)V",at=@At(value="INVOKE",target="Lnet/minecraft/client/MouseHandler;grabMouse()V"),require=1)
 private void veltraph$manualGuiGrabMouse(MouseHandler mouse,Operation<Void> original){
  NativeGuiKeyResetLineage.GrabCall observed=null;try{observed=NativeGuiKeyResetLineage.beforeGuiGrab((Gui)(Object)this,mouse);}catch(RuntimeException|Error unavailable){}
  boolean normal=false;try{original.call(mouse);normal=true;}finally{try{NativeGuiKeyResetLineage.afterGuiGrab(observed,normal);}catch(RuntimeException|Error unavailable){}}
 }
}

