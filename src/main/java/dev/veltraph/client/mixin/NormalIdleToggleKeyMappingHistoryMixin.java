package dev.veltraph.client.mixin;
import dev.veltraph.client.service.mc.*;
import net.minecraft.client.*;
import java.util.function.BooleanSupplier;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Mixin(ToggleKeyMapping.class)
public abstract class NormalIdleToggleKeyMappingHistoryMixin {
 @Inject(method="setDown(Z)V",at=@At("HEAD"),require=1)
 private void veltraph$normalIdleToggleBegin(boolean down,CallbackInfo ci){McHeldKeys.canonicalToggleOuterBegin((KeyMapping)(Object)this,down);((NormalIdleKeyCarrier)this).normalIdleNativeBegin(down,false);}
 @Inject(method="setDown(Z)V",at=@At("RETURN"),require=1)
 private void veltraph$normalIdleToggleReturned(boolean down,CallbackInfo ci){((NormalIdleKeyCarrier)this).normalIdleNativeReturned();McHeldKeys.canonicalToggleOuterReturned((KeyMapping)(Object)this,down);}
 @WrapOperation(method="setDown(Z)V",at=@At(value="INVOKE",target="Ljava/util/function/BooleanSupplier;getAsBoolean()Z"),require=1)
 private boolean veltraph$canonicalMode(BooleanSupplier source,Operation<Boolean> original){Object token=McHeldKeys.canonicalToggleModeBegin((KeyMapping)(Object)this);try{boolean result=original.call(source);McHeldKeys.canonicalToggleModeReturned(token,result,true);return result;}catch(RuntimeException|Error failure){McHeldKeys.canonicalToggleModeReturned(token,false,false);throw failure;}}
 @WrapOperation(method="setDown(Z)V",at=@At(value="INVOKE",target="Lnet/minecraft/client/KeyMapping;setDown(Z)V"),require=2)
 private void veltraph$canonicalSuper(ToggleKeyMapping receiver,boolean down,Operation<Void> original){Object token=McHeldKeys.canonicalToggleSuperBegin(receiver,down);try{original.call(receiver,down);McHeldKeys.canonicalToggleSuperReturned(token,true);}catch(RuntimeException|Error failure){McHeldKeys.canonicalToggleSuperReturned(token,false);throw failure;}}
 @Inject(method="shouldRestoreStateOnScreenClosed()Z",at=@At("HEAD"),require=1)
 private void veltraph$normalIdleRestoreBegin(CallbackInfoReturnable<Boolean> ci){((NormalIdleKeyCarrier)this).normalIdleNativeBegin(false,true);}
 @Inject(method="shouldRestoreStateOnScreenClosed()Z",at=@At("RETURN"),require=1)
 private void veltraph$normalIdleRestoreReturned(CallbackInfoReturnable<Boolean> ci){((NormalIdleKeyCarrier)this).normalIdleNativeReturned();}
}
