package dev.veltraph.client.mixin;

import dev.veltraph.client.service.mc.McOwnedRotation;
import dev.veltraph.client.service.mc.RootNativeCameraPhase;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Original tick-HEAD endpoint restore; observation never changes its one beforeTick result/exception. */
@Mixin(Minecraft.class)
public abstract class OwnedRotationTickMixin {
    @Inject(method="tick()V",at=@At("HEAD"))
    private void veltraph$restoreOwnedPose(CallbackInfo ci){
        RootNativeCameraPhase.Phase phase=null;
        try{phase=RootNativeCameraPhase.enterTick((Minecraft)(Object)this);}
        catch(Throwable unavailable){try{RootNativeCameraPhase.observerFailure();}catch(Throwable ignored){}}
        try{
            McOwnedRotation.beforeTick((Minecraft)(Object)this);
            try{RootNativeCameraPhase.normalReturned(phase);}
            catch(Throwable unavailable){try{RootNativeCameraPhase.observerFailure();}catch(Throwable ignored){}}
        }finally{
            try{RootNativeCameraPhase.close(phase);}
            catch(Throwable unavailable){try{RootNativeCameraPhase.observerFailure();}catch(Throwable ignored){}}
        }
    }
}
