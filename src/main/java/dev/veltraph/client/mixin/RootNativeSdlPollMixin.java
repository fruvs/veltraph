package dev.veltraph.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.mojang.blaze3d.platform.SDLEventHandler;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.veltraph.client.service.mc.RootNativeSdlBoundaryPhase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value=RenderSystem.class,priority=500)
public abstract class RootNativeSdlPollMixin {
    @WrapOperation(method="pollEvents(Lcom/mojang/blaze3d/platform/SDLEventHandler;)V",at=@At(value="INVOKE",target="Lcom/mojang/blaze3d/platform/SDLEventHandler;pollEvents()V"),require=1)
    private static void veltraph$originalSdlInner(SDLEventHandler handler,Operation<Void> original) {
        RootNativeSdlBoundaryPhase.SdlInnerFrame observed=null;
        try{observed=RootNativeSdlBoundaryPhase.enterOriginalSdlInnerOpcode(handler);}catch(Throwable unavailable){try{RootNativeSdlBoundaryPhase.observerFailure();}catch(Throwable ignored){}}
        boolean normal=false;
        try{original.call(handler);normal=true;}
        finally{try{RootNativeSdlBoundaryPhase.leaveOriginalSdlInnerOpcode(observed,normal);}catch(Throwable unavailable){try{RootNativeSdlBoundaryPhase.observerFailure();}catch(Throwable ignored){}}}
    }
}
