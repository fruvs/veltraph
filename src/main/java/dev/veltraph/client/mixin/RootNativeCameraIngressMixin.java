package dev.veltraph.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.veltraph.client.service.mc.RootNativeCameraPhase;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Original runTick/renderFrame camera operations only; direct render/update calls carry no source. */
@Mixin(value=Minecraft.class,priority=400)
public abstract class RootNativeCameraIngressMixin {
    @WrapOperation(method="runTick(Z)V",at=@At(value="INVOKE",target="Lnet/minecraft/client/Minecraft;renderFrame(Z)V"),require=1)
    private void veltraph$originalRenderFrameOpcode(Minecraft client,boolean render,Operation<Void> original){
        RootNativeCameraPhase.RenderOpcodeSource source=null;
        try{source=RootNativeCameraPhase.enterRenderOpcode(client);}
        catch(Throwable unavailable){try{RootNativeCameraPhase.observerFailure();}catch(Throwable ignored){}}
        boolean normal=false;
        try{original.call(client,render);normal=true;}
        finally{try{RootNativeCameraPhase.closeRenderOpcode(source,normal);}
            catch(Throwable unavailable){try{RootNativeCameraPhase.observerFailure();}catch(Throwable ignored){}}}
    }
    @WrapOperation(method="renderFrame(Z)V",at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/GameRenderer;update(Lnet/minecraft/client/DeltaTracker;)V"),require=1)
    private void veltraph$originalRendererUpdateOpcode(GameRenderer receiver,DeltaTracker delta,Operation<Void> original){
        RootNativeCameraPhase.FrameOpcodeSource source=null;
        try{source=RootNativeCameraPhase.enterFrameOpcode(receiver,delta);}
        catch(Throwable unavailable){try{RootNativeCameraPhase.observerFailure();}catch(Throwable ignored){}}
        boolean normal=false;
        try{original.call(receiver,delta);normal=true;}
        finally{try{RootNativeCameraPhase.closeFrameOpcode(source,normal);}
            catch(Throwable unavailable){try{RootNativeCameraPhase.observerFailure();}catch(Throwable ignored){}}}
    }
}
