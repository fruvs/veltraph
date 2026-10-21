package dev.veltraph.client.mixin;
import dev.veltraph.client.garden.pests.mc.McPestInventoryRestorationObserver;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.*;
import org.spongepowered.asm.mixin.Mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
@Mixin(ClientPacketListener.class)
public abstract class PestInventoryRestorationPacketMixin {
    @WrapMethod(method="handleContainerContent(Lnet/minecraft/network/protocol/game/ClientboundContainerSetContentPacket;)V",require=1)
    private void pest$restoreContent(ClientboundContainerSetContentPacket packet,Operation<Void> original){
        McPestInventoryRestorationObserver.ReceiveInvocation call=null;
        try{call=McPestInventoryRestorationObserver.beginReceive(this,packet);}catch(Throwable observerFailure){}
        boolean normal=false;
        try{original.call(packet);normal=true;}
        finally{try{McPestInventoryRestorationObserver.finishReceive(call,normal);}catch(Throwable observerFailure){/* Old packet failure cannot revoke a newer window. */}}
    }
    @WrapMethod(method="handleContainerSetSlot(Lnet/minecraft/network/protocol/game/ClientboundContainerSetSlotPacket;)V",require=1)
    private void pest$restoreSlot(ClientboundContainerSetSlotPacket packet,Operation<Void> original){
        McPestInventoryRestorationObserver.ReceiveInvocation call=null;
        try{call=McPestInventoryRestorationObserver.beginReceive(this,packet);}catch(Throwable observerFailure){}
        boolean normal=false;
        try{original.call(packet);normal=true;}
        finally{try{McPestInventoryRestorationObserver.finishReceive(call,normal);}catch(Throwable observerFailure){/* Old packet failure cannot revoke a newer window. */}}
    }
}
