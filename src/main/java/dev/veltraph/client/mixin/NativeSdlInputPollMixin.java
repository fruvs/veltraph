package dev.veltraph.client.mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.mojang.blaze3d.platform.SDLEventHandler;
import com.mojang.blaze3d.platform.Window;
import dev.veltraph.client.observation.nativeinput.NativeSdlInputLineage;
import net.minecraft.client.Minecraft;
import org.lwjgl.sdl.SDL_Event;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
@Mixin(SDLEventHandler.class)
public abstract class NativeSdlInputPollMixin {
 @Shadow @Final private Minecraft minecraft;
 @Shadow @Final private Window window;
 @WrapMethod(method="pollEvents()V")
 private void veltraph$sdlPollMethod(Operation<Void> original){NativeSdlInputLineage.Poll observed=null;
  try{observed=NativeSdlInputLineage.beginPoll(minecraft,(SDLEventHandler)(Object)this,window);}catch(RuntimeException|Error unavailable){}
  try{original.call();}finally{try{NativeSdlInputLineage.closePoll(observed);}catch(RuntimeException|Error unavailable){}}
 }
 @WrapOperation(method="pollEvents()V",at=@At(value="INVOKE",target="Lorg/lwjgl/sdl/SDLEvents;SDL_PollEvent(Lorg/lwjgl/sdl/SDL_Event;)Z"),require=1)
 private boolean veltraph$sdlNativePoll(SDL_Event buffer,Operation<Boolean> original){NativeSdlInputLineage.NativePoll observed=null;
  try{observed=NativeSdlInputLineage.beginNativePoll(buffer);}catch(RuntimeException|Error unavailable){}
  try{boolean returned=original.call(buffer);try{NativeSdlInputLineage.nativePollReturned(observed,returned);}catch(RuntimeException|Error unavailable){}return returned;}
  finally{try{NativeSdlInputLineage.closeNativePoll(observed);}catch(RuntimeException|Error unavailable){}}
 }
 @WrapOperation(method="pollEvents()V",at=@At(value="INVOKE",target="Lorg/lwjgl/sdl/SDL_Event;close()V"),require=2)
 private void veltraph$sdlBeforeBufferClose(SDL_Event buffer,Operation<Void> original){
  try{NativeSdlInputLineage.beforeNativeBufferClose(buffer);}catch(RuntimeException|Error unavailable){}
  original.call(buffer);
 }
 @WrapOperation(method="pollEvents()V",at=@At(value="INVOKE",target="Lcom/mojang/blaze3d/platform/SDLEventHandler;handleKeyEvent(Lorg/lwjgl/sdl/SDL_Event;)V"),require=1)
 private void veltraph$sdlKeyDispatch(SDLEventHandler handler,SDL_Event event,Operation<Void> original){NativeSdlInputLineage.Dispatch observed=null;
  try{observed=NativeSdlInputLineage.beginDispatch(handler,event,NativeSdlInputLineage.Kind.KEYBOARD);}catch(RuntimeException|Error unavailable){}boolean normal=false;
  try{original.call(handler,event);normal=true;}finally{try{NativeSdlInputLineage.finishDispatch(observed,normal);}catch(RuntimeException|Error unavailable){}}
 }
 @WrapOperation(method="pollEvents()V",at=@At(value="INVOKE",target="Lcom/mojang/blaze3d/platform/SDLEventHandler;handleMouseMotionEvent(Lorg/lwjgl/sdl/SDL_Event;)V"),require=1)
 private void veltraph$sdlMotionDispatch(SDLEventHandler handler,SDL_Event event,Operation<Void> original){NativeSdlInputLineage.Dispatch observed=null;
  try{observed=NativeSdlInputLineage.beginDispatch(handler,event,NativeSdlInputLineage.Kind.MOTION);}catch(RuntimeException|Error unavailable){}boolean normal=false;
  try{original.call(handler,event);normal=true;}finally{try{NativeSdlInputLineage.finishDispatch(observed,normal);}catch(RuntimeException|Error unavailable){}}
 }
 @WrapOperation(method="pollEvents()V",at=@At(value="INVOKE",target="Lcom/mojang/blaze3d/platform/SDLEventHandler;handleMouseButtonEvent(Lorg/lwjgl/sdl/SDL_Event;)V"),require=1)
 private void veltraph$sdlButtonDispatch(SDLEventHandler handler,SDL_Event event,Operation<Void> original){NativeSdlInputLineage.Dispatch observed=null;
  try{observed=NativeSdlInputLineage.beginDispatch(handler,event,NativeSdlInputLineage.Kind.BUTTON);}catch(RuntimeException|Error unavailable){}boolean normal=false;
  try{original.call(handler,event);normal=true;}finally{try{NativeSdlInputLineage.finishDispatch(observed,normal);}catch(RuntimeException|Error unavailable){}}
 }
 @WrapOperation(method="handleKeyEvent(Lorg/lwjgl/sdl/SDL_Event;)V",at=@At(value="INVOKE",target="Lnet/minecraft/client/Minecraft;execute(Ljava/lang/Runnable;)V"),require=1)
 private void veltraph$sdlKeyQueue(Minecraft client,Runnable originalTask,Operation<Void> original){NativeSdlInputLineage.Delivery observed=null;
  try{observed=NativeSdlInputLineage.captureQueue(client,originalTask);}catch(RuntimeException|Error unavailable){}boolean normal=false;
  try{original.call(client,observed==null?originalTask:observed);normal=true;}finally{try{NativeSdlInputLineage.finishQueue(observed,normal);}catch(RuntimeException|Error unavailable){}}
 }
 @WrapOperation(method="handleMouseMotionEvent(Lorg/lwjgl/sdl/SDL_Event;)V",at=@At(value="INVOKE",target="Lnet/minecraft/client/Minecraft;execute(Ljava/lang/Runnable;)V"),require=1)
 private void veltraph$sdlMotionQueue(Minecraft client,Runnable originalTask,Operation<Void> original){NativeSdlInputLineage.Delivery observed=null;
  try{observed=NativeSdlInputLineage.captureQueue(client,originalTask);}catch(RuntimeException|Error unavailable){}boolean normal=false;
  try{original.call(client,observed==null?originalTask:observed);normal=true;}finally{try{NativeSdlInputLineage.finishQueue(observed,normal);}catch(RuntimeException|Error unavailable){}}
 }
 @WrapOperation(method="handleMouseButtonEvent(Lorg/lwjgl/sdl/SDL_Event;)V",at=@At(value="INVOKE",target="Lnet/minecraft/client/Minecraft;execute(Ljava/lang/Runnable;)V"),require=1)
 private void veltraph$sdlButtonQueue(Minecraft client,Runnable originalTask,Operation<Void> original){NativeSdlInputLineage.Delivery observed=null;
  try{observed=NativeSdlInputLineage.captureQueue(client,originalTask);}catch(RuntimeException|Error unavailable){}boolean normal=false;
  try{original.call(client,observed==null?originalTask:observed);normal=true;}finally{try{NativeSdlInputLineage.finishQueue(observed,normal);}catch(RuntimeException|Error unavailable){}}
 }
}
