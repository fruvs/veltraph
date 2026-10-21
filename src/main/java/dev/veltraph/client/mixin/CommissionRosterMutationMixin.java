package dev.veltraph.client.mixin;
import dev.veltraph.client.mining.commission.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Before/after actual incoming roster changes retain remove/readd task ABA as observation only. */
@Mixin(ClientPacketListener.class)
public abstract class CommissionRosterMutationMixin implements CommissionMutationCarrier {
    @Unique private long veltraph$commissionRosterVersion;
    @Override public long commissionNativeMutationVersion(){return veltraph$commissionRosterVersion;}
    @Unique private void veltraph$observeCommissionRoster(){var c=Minecraft.getInstance();if(!c.isSameThread()||c.getConnection()!=(Object)this)return;if(veltraph$commissionRosterVersion<Long.MAX_VALUE)veltraph$commissionRosterVersion++;McCommissionObservation.nativeMutation(c);}
    @Inject(method="handlePlayerInfoUpdate",at=@At("HEAD"),require=1)
    private void beforeUpdate(ClientboundPlayerInfoUpdatePacket packet,CallbackInfo ci){veltraph$observeCommissionRoster();}
    @Inject(method="handlePlayerInfoUpdate",at=@At("RETURN"),require=1)
    private void afterUpdate(ClientboundPlayerInfoUpdatePacket packet,CallbackInfo ci){veltraph$observeCommissionRoster();}
    @Inject(method="handlePlayerInfoRemove",at=@At("HEAD"),require=1)
    private void beforeRemove(ClientboundPlayerInfoRemovePacket packet,CallbackInfo ci){veltraph$observeCommissionRoster();}
    @Inject(method="handlePlayerInfoRemove",at=@At("RETURN"),require=1)
    private void afterRemove(ClientboundPlayerInfoRemovePacket packet,CallbackInfo ci){veltraph$observeCommissionRoster();}
    @Inject(method="applyPlayerInfoUpdate",at=@At("HEAD"),require=1)
    private void beforeEntry(ClientboundPlayerInfoUpdatePacket.Action action,ClientboundPlayerInfoUpdatePacket.Entry entry,net.minecraft.client.multiplayer.PlayerInfo info,CallbackInfo ci){veltraph$observeCommissionRoster();}
    @Inject(method="applyPlayerInfoUpdate",at=@At("RETURN"),require=1)
    private void afterEntry(ClientboundPlayerInfoUpdatePacket.Action action,ClientboundPlayerInfoUpdatePacket.Entry entry,net.minecraft.client.multiplayer.PlayerInfo info,CallbackInfo ci){veltraph$observeCommissionRoster();}
}
