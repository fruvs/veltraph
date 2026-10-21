package dev.veltraph.client.mixin;

import dev.veltraph.client.service.mc.McOwnedRotation;
import dev.veltraph.client.service.mc.RootNativeCameraPhase;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Original before-camera pose application, with exact native opcode source and outcome conservation. */
@Mixin(GameRenderer.class)
public abstract class OwnedRotationFrameMixin {
    @Inject(method="update(Lnet/minecraft/client/DeltaTracker;)V",at=@At("HEAD"))
    private void veltraph$applyOwnedFrame(DeltaTracker delta,CallbackInfo ci){
        RootNativeCameraPhase.Phase phase=null;
        try{phase=RootNativeCameraPhase.enterFrame(Minecraft.getInstance(),(GameRenderer)(Object)this,delta);}
        catch(Throwable unavailable){try{RootNativeCameraPhase.observerFailure();}catch(Throwable ignored){}}
        try{
            McOwnedRotation.beforeFrame(Minecraft.getInstance(),delta);
            try{RootNativeCameraPhase.normalReturned(phase);}
            catch(Throwable unavailable){try{RootNativeCameraPhase.observerFailure();}catch(Throwable ignored){}}
        }finally{
            try{RootNativeCameraPhase.close(phase);}
            catch(Throwable unavailable){try{RootNativeCameraPhase.observerFailure();}catch(Throwable ignored){}}
        }
    }
}
