package dev.veltraph.client.mixin;

import dev.veltraph.client.galatea.McFigOwnedUseBridge;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

/** Does not clear queues. Only the exact owned first event can change its consume result. */
@Mixin(KeyMapping.class)
public abstract class FigOwnedUseKeyMappingMixin {
    @Unique private int veltraph$figBeforeConsume;
    @Inject(method="consumeClick",at=@At("HEAD"),require=1)
    private void veltraph$figBeforeConsume(CallbackInfoReturnable<Boolean> ci){
        veltraph$figBeforeConsume=((FigScaffoldKeyMappingAccessor)(Object)this).veltraph$figScaffoldClickCount();
    }
    @Inject(method="consumeClick",at=@At("RETURN"),cancellable=true,require=1)
    private void veltraph$figConsumed(CallbackInfoReturnable<Boolean> ci){
        int after=((FigScaffoldKeyMappingAccessor)(Object)this).veltraph$figScaffoldClickCount();
        boolean result=McFigOwnedUseBridge.nativeConsumed((KeyMapping)(Object)this,veltraph$figBeforeConsume,after,ci.getReturnValue());
        if(result!=ci.getReturnValue())ci.setReturnValue(result);
    }
    @Inject(method="release",at=@At("HEAD"),require=1)
    private void veltraph$figReleased(CallbackInfo ci){McFigOwnedUseBridge.queueReleased((KeyMapping)(Object)this);}
}
