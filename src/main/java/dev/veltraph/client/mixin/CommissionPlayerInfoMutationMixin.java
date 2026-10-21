package dev.veltraph.client.mixin;
import dev.veltraph.client.mining.commission.McCommissionObservation;
import dev.veltraph.client.mining.commission.CommissionMutationCarrier;
import org.spongepowered.asm.mixin.Unique;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Actual native display-name mutations retain task ABA even if the final text is restored. */
@Mixin(PlayerInfo.class)
public abstract class CommissionPlayerInfoMutationMixin implements CommissionMutationCarrier {
    @Unique private long veltraph$commissionMutationVersion;
    @Override public long commissionNativeMutationVersion(){return veltraph$commissionMutationVersion;}
    @Inject(method="setTabListDisplayName",at=@At("HEAD"),require=1)
    private void before(Component text,CallbackInfo ci){if(veltraph$commissionMutationVersion<Long.MAX_VALUE)veltraph$commissionMutationVersion++;McCommissionObservation.nativeMutation(Minecraft.getInstance());}
    @Inject(method="setTabListDisplayName",at=@At("RETURN"),require=1)
    private void after(Component text,CallbackInfo ci){McCommissionObservation.nativeMutation(Minecraft.getInstance());}
    @Inject(method="setTabListOrder",at=@At("HEAD"),require=1)
    private void beforeOrder(int order,CallbackInfo ci){if(veltraph$commissionMutationVersion<Long.MAX_VALUE)veltraph$commissionMutationVersion++;McCommissionObservation.nativeMutation(Minecraft.getInstance());}
    @Inject(method="setTabListOrder",at=@At("RETURN"),require=1)
    private void afterOrder(int order,CallbackInfo ci){McCommissionObservation.nativeMutation(Minecraft.getInstance());}
    @Inject(method="setGameMode",at=@At("HEAD"),require=1)
    private void beforeMode(net.minecraft.world.level.GameType type,CallbackInfo ci){if(veltraph$commissionMutationVersion<Long.MAX_VALUE)veltraph$commissionMutationVersion++;McCommissionObservation.nativeMutation(Minecraft.getInstance());}
    @Inject(method="setGameMode",at=@At("RETURN"),require=1)
    private void afterMode(net.minecraft.world.level.GameType type,CallbackInfo ci){McCommissionObservation.nativeMutation(Minecraft.getInstance());}
}
