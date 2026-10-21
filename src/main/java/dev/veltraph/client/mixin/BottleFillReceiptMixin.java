package dev.veltraph.client.mixin;
import dev.veltraph.client.inventory.bottle.BottleFillPacketObserver;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.*;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.*;

/** Observe payloads only after vanilla applies them; never send/cancel/change a packet. */
@Mixin(ClientPacketListener.class)
public class BottleFillReceiptMixin {
    @Inject(method="handleSetPlayerInventory",at=@At("TAIL")) private void bottlePlayer(ClientboundSetPlayerInventoryPacket p,CallbackInfo ci){BottleFillPacketObserver.player(Minecraft.getInstance(),this,p.slot(),p.contents());}
    @Inject(method="handleContainerSetSlot",at=@At("TAIL")) private void bottleSlot(ClientboundContainerSetSlotPacket p,CallbackInfo ci){BottleFillPacketObserver.container(Minecraft.getInstance(),this,p.getContainerId(),p.getStateId(),dev.veltraph.client.inventory.bottle.BottleFillReceipts.Kind.CONTAINER_SLOT,Map.of(p.getSlot(),p.getItem()));}
    @Inject(method="handleContainerContent",at=@At("TAIL")) private void bottleContent(ClientboundContainerSetContentPacket p,CallbackInfo ci){var rows=new HashMap<Integer,ItemStack>();for(int i=0;i<p.items().size();i++)rows.put(i,p.items().get(i));BottleFillPacketObserver.container(Minecraft.getInstance(),this,p.containerId(),p.stateId(),dev.veltraph.client.inventory.bottle.BottleFillReceipts.Kind.CONTAINER_CONTENT,rows);}
}
