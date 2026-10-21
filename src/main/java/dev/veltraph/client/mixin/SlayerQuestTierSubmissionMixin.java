package dev.veltraph.client.mixin;
import dev.veltraph.client.combat.slayer.McSlayerQuestTierObserver;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
@Mixin(Connection.class)
public abstract class SlayerQuestTierSubmissionMixin {
    @WrapMethod(method="send(Lnet/minecraft/network/protocol/Packet;)V",require=1)
    private void veltraph$questTierSend(Packet<?> packet,Operation<Void> original){
        McSlayerQuestTierObserver.SendInvocation call=null;
        try{call=McSlayerQuestTierObserver.beginSend((Connection)(Object)this,packet);}catch(Throwable ignored){}
        boolean returned=false;
        try{original.call(packet);returned=true;}
        finally{try{McSlayerQuestTierObserver.finishSend(call,returned);}catch(Throwable ignored){}}
    }
}
