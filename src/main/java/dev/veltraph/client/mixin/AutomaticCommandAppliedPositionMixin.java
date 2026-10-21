package dev.veltraph.client.mixin;
import dev.veltraph.client.navigation.mc.NativeAutomaticCommandReceipt;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import org.spongepowered.asm.mixin.Mixin;
/** Journal the actual after-vanilla position, associating only the session eligible at this exact entry. */
@Mixin(ClientPacketListener.class)
public abstract class AutomaticCommandAppliedPositionMixin {
    @WrapMethod(method="handleMovePlayer(Lnet/minecraft/network/protocol/game/ClientboundPlayerPositionPacket;)V",require=1)
    private void veltraph$automaticAppliedInvocation(ClientboundPlayerPositionPacket packet,Operation<Void> original){
        NativeAutomaticCommandReceipt.PositionInvocation entry=null;
        try{entry=NativeAutomaticCommandReceipt.positionEntry(Minecraft.getInstance(),this,packet);}catch(Throwable ignored){/* Observation cannot block native application. */}
        boolean normal=false;
        try{original.call(packet);normal=true;}
        finally{try{NativeAutomaticCommandReceipt.positionExited(entry,this,packet,normal);}catch(Throwable failure){try{NativeAutomaticCommandReceipt.positionObservationFailed(entry);}catch(Throwable ignored){/* Preserve the original native exit. */}}}
    }
}
