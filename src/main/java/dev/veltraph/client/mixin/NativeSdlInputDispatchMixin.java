package dev.veltraph.client.mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.mojang.blaze3d.platform.SDLEventHandler;
import dev.veltraph.client.observation.nativeinput.NativeSdlInputLineage;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.*;
import org.lwjgl.sdl.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
@Mixin(SDLEventHandler.class)
public abstract class NativeSdlInputDispatchMixin {
 @WrapMethod(method="lambda$handleKeyEvent$0(Lorg/lwjgl/sdl/SDL_Event;ILnet/minecraft/client/input/KeyEvent;)V")
 private void veltraph$sdlKeyLambda(SDL_Event raw,int action,KeyEvent event,Operation<Void> original){NativeSdlInputLineage.Lambda observed=null;
  try{observed=NativeSdlInputLineage.beginLambda((SDLEventHandler)(Object)this,NativeSdlInputLineage.Kind.KEYBOARD,raw,event,action,null,0,null);}catch(RuntimeException|Error unavailable){}boolean normal=false;
  try{original.call(raw,action,event);normal=true;}finally{try{NativeSdlInputLineage.closeLambda(observed,normal);}catch(RuntimeException|Error unavailable){}}
 }
 @WrapMethod(method="lambda$handleMouseButtonEvent$0(JLnet/minecraft/client/input/MouseButtonInfo;I)V")
 private void veltraph$sdlButtonLambda(long window,MouseButtonInfo event,int action,Operation<Void> original){NativeSdlInputLineage.Lambda observed=null;
  try{observed=NativeSdlInputLineage.beginLambda((SDLEventHandler)(Object)this,NativeSdlInputLineage.Kind.BUTTON,null,null,action,event,window,null);}catch(RuntimeException|Error unavailable){}boolean normal=false;
  try{original.call(window,event,action);normal=true;}finally{try{NativeSdlInputLineage.closeLambda(observed,normal);}catch(RuntimeException|Error unavailable){}}
 }
 @WrapMethod(method="lambda$handleMouseMotionEvent$0(JLorg/lwjgl/sdl/SDL_MouseMotionEvent;)V")
 private void veltraph$sdlMotionLambda(long window,SDL_MouseMotionEvent raw,Operation<Void> original){NativeSdlInputLineage.Lambda observed=null;
  try{observed=NativeSdlInputLineage.beginLambda((SDLEventHandler)(Object)this,NativeSdlInputLineage.Kind.MOTION,null,null,0,null,window,raw);}catch(RuntimeException|Error unavailable){}boolean normal=false;
  try{original.call(window,raw);normal=true;}finally{try{NativeSdlInputLineage.closeLambda(observed,normal);}catch(RuntimeException|Error unavailable){}}
 }
 @WrapOperation(method="lambda$handleKeyEvent$0(Lorg/lwjgl/sdl/SDL_Event;ILnet/minecraft/client/input/KeyEvent;)V",at=@At(value="INVOKE",target="Lnet/minecraft/client/KeyboardHandler;keyPress(JILnet/minecraft/client/input/KeyEvent;)V"),require=1)
 private void veltraph$sdlKeyboard(KeyboardHandler handler,long window,int action,KeyEvent event,Operation<Void> original){NativeSdlInputLineage.Handler observed=null;
  try{observed=NativeSdlInputLineage.beginHandler(handler,NativeSdlInputLineage.Kind.KEYBOARD,window,event,action,0,0,0,0);}catch(RuntimeException|Error unavailable){}boolean normal=false;
  try{original.call(handler,window,action,event);normal=true;}finally{try{NativeSdlInputLineage.closeHandler(observed,normal);}catch(RuntimeException|Error unavailable){}}
 }
 @WrapOperation(method="lambda$handleMouseButtonEvent$0(JLnet/minecraft/client/input/MouseButtonInfo;I)V",at=@At(value="INVOKE",target="Lnet/minecraft/client/MouseHandler;onButton(JLnet/minecraft/client/input/MouseButtonInfo;I)V"),require=1)
 private void veltraph$sdlButton(MouseHandler handler,long window,MouseButtonInfo event,int action,Operation<Void> original){NativeSdlInputLineage.Handler observed=null;
  try{observed=NativeSdlInputLineage.beginHandler(handler,NativeSdlInputLineage.Kind.BUTTON,window,event,action,0,0,0,0);}catch(RuntimeException|Error unavailable){}boolean normal=false;
  try{original.call(handler,window,event,action);normal=true;}finally{try{NativeSdlInputLineage.closeHandler(observed,normal);}catch(RuntimeException|Error unavailable){}}
 }
 @WrapOperation(method="lambda$handleMouseMotionEvent$0(JLorg/lwjgl/sdl/SDL_MouseMotionEvent;)V",at=@At(value="INVOKE",target="Lnet/minecraft/client/MouseHandler;onMove(JDDDD)V"),require=1)
 private void veltraph$sdlMotion(MouseHandler handler,long window,double x,double y,double dx,double dy,Operation<Void> original){NativeSdlInputLineage.Handler observed=null;
  try{observed=NativeSdlInputLineage.beginHandler(handler,NativeSdlInputLineage.Kind.MOTION,window,null,0,x,y,dx,dy);}catch(RuntimeException|Error unavailable){}boolean normal=false;
  try{original.call(handler,window,x,y,dx,dy);normal=true;}finally{try{NativeSdlInputLineage.closeHandler(observed,normal);}catch(RuntimeException|Error unavailable){}}
 }
}
