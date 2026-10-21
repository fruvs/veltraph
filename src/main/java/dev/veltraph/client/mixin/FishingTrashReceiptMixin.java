package dev.veltraph.client.mixin;

import dev.veltraph.client.fishing.trash.mc.FishingTrashInventoryReceipts;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.*;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.*;

/** TAIL copies payload values after vanilla applied the actual packet on its client thread. */
@Mixin(ClientPacketListener.class)
public class FishingTrashReceiptMixin {
    @Inject(method="handleContainerSetSlot",at=@At("TAIL"))
    private void veltraph$trashSlot(ClientboundContainerSetSlotPacket packet,CallbackInfo ci) {
        var client=Minecraft.getInstance();
        FishingTrashInventoryReceipts.applied(client,this,packet.getContainerId(),packet.getStateId(),
                Map.of(packet.getSlot(),packet.getItem()),client.player!=null&&client.player.containerMenu.getCarried().isEmpty());
    }
    @Inject(method="handleContainerContent",at=@At("TAIL"))
    private void veltraph$trashContents(ClientboundContainerSetContentPacket packet,CallbackInfo ci) {
        var rows=new TreeMap<Integer,ItemStack>();for(int i=0;i<packet.items().size();i++)rows.put(i,packet.items().get(i));
        FishingTrashInventoryReceipts.applied(Minecraft.getInstance(),this,packet.containerId(),packet.stateId(),rows,packet.carriedItem().isEmpty());
    }
}
