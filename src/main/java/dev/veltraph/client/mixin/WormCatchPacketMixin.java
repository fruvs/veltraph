package dev.veltraph.client.mixin;
import dev.veltraph.client.fishing.mc.WormCatchPacketBridge;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ClientPacketListener.class)
public abstract class WormCatchPacketMixin {
 @Inject(method="handleAddEntity",at=@At("TAIL"),require=1)private void spawn(ClientboundAddEntityPacket p,CallbackInfo ci){WormCatchPacketBridge.spawnApplied(this,p);}
 @Inject(method="handleTakeItemEntity",at=@At("HEAD"),require=1)private void takeBefore(ClientboundTakeItemEntityPacket p,CallbackInfo ci){WormCatchPacketBridge.takeStarted(this,p);}
 @Inject(method="handleTakeItemEntity",at=@At("TAIL"),require=1)private void takeAfter(ClientboundTakeItemEntityPacket p,CallbackInfo ci){WormCatchPacketBridge.takeApplied(this,p);}
 @Inject(method="handleSetPlayerInventory",at=@At("HEAD"),require=1)private void invBefore(ClientboundSetPlayerInventoryPacket p,CallbackInfo ci){WormCatchPacketBridge.inventoryStarted(this,p);}
 @Inject(method="handleSetPlayerInventory",at=@At("TAIL"),require=1)private void invAfter(ClientboundSetPlayerInventoryPacket p,CallbackInfo ci){WormCatchPacketBridge.inventoryApplied(this,p);}
 @Inject(method="handleContainerSetSlot",at=@At("HEAD"),require=1)private void slotBefore(ClientboundContainerSetSlotPacket p,CallbackInfo ci){if(p.getContainerId()==0)WormCatchPacketBridge.inventoryStarted(this,p);}
 @Inject(method="handleContainerSetSlot",at=@At("TAIL"),require=1)private void slotAfter(ClientboundContainerSetSlotPacket p,CallbackInfo ci){WormCatchPacketBridge.inventoryApplied(this,p);}
}
