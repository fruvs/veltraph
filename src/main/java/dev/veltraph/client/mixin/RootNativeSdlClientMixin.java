package dev.veltraph.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.mojang.blaze3d.platform.SDLEventHandler;
import dev.veltraph.client.service.mc.RootNativeSdlBoundaryPhase;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value=Minecraft.class,priority=500)
public abstract class RootNativeSdlClientMixin {
    @WrapMethod(method="run()V")
    private void veltraph$originalWholeRun(Operation<Void> original) {
        RootNativeSdlBoundaryPhase.OriginalClientRunSource observed=null;
        try{observed=RootNativeSdlBoundaryPhase.enterOriginalClientRun((Minecraft)(Object)this);}catch(Throwable unavailable){try{RootNativeSdlBoundaryPhase.observerFailure();}catch(Throwable ignored){}}
        boolean normal=false;
        try{original.call();normal=true;}
        finally{try{RootNativeSdlBoundaryPhase.leaveOriginalClientRun(observed,normal);}catch(Throwable unavailable){try{RootNativeSdlBoundaryPhase.observerFailure();}catch(Throwable ignored){}}}
    }
    @WrapOperation(method="run()V",at=@At(value="INVOKE",target="Lcom/mojang/blaze3d/systems/RenderSystem;pollEvents(Lcom/mojang/blaze3d/platform/SDLEventHandler;)V"),require=1)
    private void veltraph$originalSdlOuter(SDLEventHandler handler,Operation<Void> original) {
        RootNativeSdlBoundaryPhase.OriginalSdlOuterCall observed=null;
        try{observed=RootNativeSdlBoundaryPhase.enterOriginalSdlOuterOpcode((Minecraft)(Object)this,handler);}catch(Throwable unavailable){try{RootNativeSdlBoundaryPhase.observerFailure();}catch(Throwable ignored){}}
        boolean normal=false;
        try{original.call(handler);normal=true;}
        finally{try{RootNativeSdlBoundaryPhase.leaveOriginalSdlOuterOpcode(observed,normal);}catch(Throwable unavailable){try{RootNativeSdlBoundaryPhase.observerFailure();}catch(Throwable ignored){}}}
    }
    @WrapOperation(method="runTick(Z)V",at=@At(value="INVOKE",target="Lnet/minecraft/client/MouseHandler;handleAccumulatedMovement()V"),require=1)
    private void veltraph$originalMouseAccumulation(MouseHandler handler,Operation<Void> original) {
        RootNativeSdlBoundaryPhase.MouseFrame observed=null;
        try{observed=RootNativeSdlBoundaryPhase.enterMouseAccumulationOpcode((Minecraft)(Object)this,handler);}catch(Throwable unavailable){try{RootNativeSdlBoundaryPhase.observerFailure();}catch(Throwable ignored){}}
        boolean normal=false;
        try{original.call(handler);normal=true;}
        finally{try{RootNativeSdlBoundaryPhase.leaveMouseAccumulationOpcode(observed,normal);}catch(Throwable unavailable){try{RootNativeSdlBoundaryPhase.observerFailure();}catch(Throwable ignored){}}}
    }
}
