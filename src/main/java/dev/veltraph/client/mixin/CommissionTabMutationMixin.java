package dev.veltraph.client.mixin;
import dev.veltraph.client.mining.commission.McCommissionObservation;
import dev.veltraph.client.mining.commission.CommissionMutationCarrier;
import org.spongepowered.asm.mixin.Unique;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Passive before/after actual native visible-text mutation, not an action or permission producer. */
@Mixin(PlayerTabOverlay.class)
public abstract class CommissionTabMutationMixin implements CommissionMutationCarrier {
    @Unique private long veltraph$commissionMutationVersion;
    @Override public long commissionNativeMutationVersion(){return veltraph$commissionMutationVersion;}
    @Inject(method={"setHeader","setFooter"},at=@At("HEAD"),require=1)
    private void before(Component text,CallbackInfo ci){if(veltraph$commissionMutationVersion<Long.MAX_VALUE)veltraph$commissionMutationVersion++;McCommissionObservation.nativeMutation(Minecraft.getInstance());}
    @Inject(method={"setHeader","setFooter"},at=@At("RETURN"),require=1)
    private void after(Component text,CallbackInfo ci){McCommissionObservation.nativeMutation(Minecraft.getInstance());}
    @Inject(method="reset",at=@At("HEAD"),require=1)
    private void beforeReset(CallbackInfo ci){if(veltraph$commissionMutationVersion<Long.MAX_VALUE)veltraph$commissionMutationVersion++;McCommissionObservation.nativeMutation(Minecraft.getInstance());}
    @Inject(method="reset",at=@At("RETURN"),require=1)
    private void afterReset(CallbackInfo ci){McCommissionObservation.nativeMutation(Minecraft.getInstance());}
}
