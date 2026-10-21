package dev.veltraph.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.*;
import dev.veltraph.client.service.mc.RootNativeSdlBoundaryPhase;
import net.minecraft.client.Minecraft;
import net.minecraft.client.main.Main;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value=Main.class,priority=500)
public abstract class RootNativeSdlMainMixin {
    @WrapOperation(method="main([Ljava/lang/String;)V",at=@At(value="INVOKE",target="Lnet/minecraft/client/Minecraft;run()V"),require=1)
    private static void veltraph$originalMainRun(Minecraft client,Operation<Void> original) {
        RootNativeSdlBoundaryPhase.OriginalMainRunCall observed=null;
        try{observed=RootNativeSdlBoundaryPhase.enterOriginalMainRunOpcode(client);}catch(Throwable unavailable){try{RootNativeSdlBoundaryPhase.observerFailure();}catch(Throwable ignored){}}
        boolean normal=false;
        try{original.call(client);normal=true;}
        finally{try{RootNativeSdlBoundaryPhase.leaveOriginalMainRunOpcode(observed,normal);}catch(Throwable unavailable){try{RootNativeSdlBoundaryPhase.observerFailure();}catch(Throwable ignored){}}}
    }
}
