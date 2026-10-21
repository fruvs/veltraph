package dev.veltraph.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.veltraph.client.service.mc.RootNativeGameplayTickPhase;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Original native opcode scopes, separate from public END_CLIENT_TICK invocations. */
@Mixin(value = Minecraft.class, priority = 500)
public abstract class RootNativeGameplayTickMixin {
    @WrapOperation(method = "run()V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;runTick(Z)V"), require = 1)
    private void veltraph$rootRunTickOpcode(Minecraft client, boolean render, Operation<Void> original) {
        RootNativeGameplayTickPhase.RunFrame frame = null;
        try { frame = RootNativeGameplayTickPhase.enterRunTickOpcode(client); }
        catch (Throwable ignored) { try { RootNativeGameplayTickPhase.observerFailure(); } catch (Throwable unavailable) {} }
        boolean normal = false;
        try { original.call(client, render); normal = true; }
        finally {
            try { RootNativeGameplayTickPhase.leaveRunTickOpcode(frame, normal); }
            catch (Throwable ignored) { try { RootNativeGameplayTickPhase.observerFailure(); } catch (Throwable unavailable) {} }
        }
    }
    @WrapOperation(method = "runTick(Z)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;tick()V"), require = 1)
    private void veltraph$rootTickOpcode(Minecraft client, Operation<Void> original) {
        RootNativeGameplayTickPhase.TickFrame frame = null;
        try { frame = RootNativeGameplayTickPhase.enterTickOpcode(client); }
        catch (Throwable ignored) { try { RootNativeGameplayTickPhase.observerFailure(); } catch (Throwable unavailable) {} }
        boolean normal = false;
        try { original.call(client); normal = true; }
        finally {
            try { RootNativeGameplayTickPhase.leaveTickOpcode(frame, normal); }
            catch (Throwable ignored) { try { RootNativeGameplayTickPhase.observerFailure(); } catch (Throwable unavailable) {} }
        }
    }
}
