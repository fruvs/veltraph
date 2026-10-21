package dev.veltraph.client.mixin;
import dev.veltraph.client.mining.vein.VeinNativePickupAdapter;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Independent mining observers; existing foraging hooks and signals remain unchanged. */
@Mixin(ClientPacketListener.class)
public abstract class VeinPickupPacketObserverMixin {
    private static final String DISPATCH="Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V";
    @Inject(method="handleAddEntity",at=@At("HEAD"),require=1)
    private void vein$addBefore(ClientboundAddEntityPacket packet,CallbackInfo ci){if(!net.minecraft.client.Minecraft.getInstance().isSameThread())return;VeinNativePickupAdapter.observeSafely(()->VeinNativePickupAdapter.addBefore(this,packet));}
    @Inject(method="handleAddEntity",at=@At("TAIL"),require=1)
    private void vein$addAfter(ClientboundAddEntityPacket packet,CallbackInfo ci){VeinNativePickupAdapter.observeSafely(()->VeinNativePickupAdapter.addAfter(this,packet));}
    @Inject(method="handleSetEntityData",at=@At("TAIL"),require=1)
    private void vein$dataAfter(ClientboundSetEntityDataPacket packet,CallbackInfo ci){VeinNativePickupAdapter.observeSafely(()->{if(packet.packedItems().stream().anyMatch(v->v.id()==VeinPickupItemDataAccessor.vein$stackData().id()))VeinNativePickupAdapter.dataAfter(this,packet.id());});}
    @Inject(method="handleTakeItemEntity",at=@At(value="INVOKE",target="Lnet/minecraft/world/item/ItemStack;shrink(I)V",shift=At.Shift.BEFORE),require=1)
    private void vein$takeBefore(ClientboundTakeItemEntityPacket packet,CallbackInfo ci){VeinNativePickupAdapter.observeSafely(()->VeinNativePickupAdapter.takeBefore(this,packet));}
    @Inject(method="handleTakeItemEntity",at=@At("TAIL"),require=1)
    private void vein$takeAfter(ClientboundTakeItemEntityPacket packet,CallbackInfo ci){VeinNativePickupAdapter.observeSafely(()->VeinNativePickupAdapter.takeAfter(this,packet));}
    @Inject(method="handleSetPlayerInventory",at=@At("HEAD"),require=1)
    private void vein$playerBefore(ClientboundSetPlayerInventoryPacket packet,CallbackInfo ci){if(!net.minecraft.client.Minecraft.getInstance().isSameThread())return;VeinNativePickupAdapter.observeSafely(()->VeinNativePickupAdapter.inventoryBefore(this,packet));}
    @Inject(method="handleSetPlayerInventory",at=@At("TAIL"),require=1)
    private void vein$playerAfter(ClientboundSetPlayerInventoryPacket packet,CallbackInfo ci){VeinNativePickupAdapter.observeSafely(()->VeinNativePickupAdapter.inventoryAfter(this,packet));}
    @Inject(method="handleContainerSetSlot",at=@At("HEAD"),require=1)
    private void vein$slotBefore(ClientboundContainerSetSlotPacket packet,CallbackInfo ci){if(!net.minecraft.client.Minecraft.getInstance().isSameThread())return;VeinNativePickupAdapter.observeSafely(()->VeinNativePickupAdapter.inventoryBefore(this,packet));}
    @Inject(method="handleContainerSetSlot",at=@At("TAIL"),require=1)
    private void vein$slotAfter(ClientboundContainerSetSlotPacket packet,CallbackInfo ci){VeinNativePickupAdapter.observeSafely(()->VeinNativePickupAdapter.inventoryAfter(this,packet));}
    @Inject(method="handleContainerContent",at=@At("HEAD"),require=1)
    private void vein$contentBefore(ClientboundContainerSetContentPacket packet,CallbackInfo ci){if(!net.minecraft.client.Minecraft.getInstance().isSameThread())return;VeinNativePickupAdapter.observeSafely(()->VeinNativePickupAdapter.inventoryBefore(this,packet));}
    @Inject(method="handleContainerContent",at=@At("TAIL"),require=1)
    private void vein$contentAfter(ClientboundContainerSetContentPacket packet,CallbackInfo ci){VeinNativePickupAdapter.observeSafely(()->VeinNativePickupAdapter.inventoryAfter(this,packet));}
}
