package dev.veltraph.client.mixin;
import dev.veltraph.client.combat.slayer.McSlayerQuestTierObserver;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import org.spongepowered.asm.mixin.Mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
@Mixin(ClientPacketListener.class)
public abstract class SlayerQuestTierReceiveMixin {
    @WrapMethod(method="handleContainerContent",require=1)
    private void veltraph$questTierContents(ClientboundContainerSetContentPacket packet,Operation<Void> original){
        McSlayerQuestTierObserver.ReceiveInvocation call=null;
        try{call=McSlayerQuestTierObserver.beginReceive((ClientPacketListener)(Object)this,packet);}catch(Throwable ignored){}
        boolean returned=false;
        try{original.call(packet);returned=true;}
        finally{try{McSlayerQuestTierObserver.finishReceive(call,returned);}catch(Throwable ignored){}}
    }
    @WrapMethod(method="handleSystemChat",require=1)
    private void veltraph$questTierStarted(ClientboundSystemChatPacket packet,Operation<Void> original){
        McSlayerQuestTierObserver.ReceiveInvocation call=null;
        try{call=McSlayerQuestTierObserver.beginReceive((ClientPacketListener)(Object)this,packet);}catch(Throwable ignored){}
        boolean returned=false;
        try{original.call(packet);returned=true;}
        finally{try{McSlayerQuestTierObserver.finishReceive(call,returned);}catch(Throwable ignored){}}
    }
    @WrapMethod(method="handleRespawn",require=1)
    private void veltraph$questTierWorldBoundary(ClientboundRespawnPacket packet,Operation<Void> original){
        McSlayerQuestTierObserver.BoundaryInvocation call=null;
        try{call=McSlayerQuestTierObserver.beginWorldBoundary((ClientPacketListener)(Object)this);}catch(Throwable ignored){}
        try{original.call(packet);}
        finally{try{McSlayerQuestTierObserver.finishWorldBoundary(call);}catch(Throwable ignored){}}
    }

}
