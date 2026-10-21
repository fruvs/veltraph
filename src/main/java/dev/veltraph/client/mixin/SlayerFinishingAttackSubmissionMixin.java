package dev.veltraph.client.mixin;
import dev.veltraph.client.combat.grind.McSlayerFinishingSwap;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
/** Ordinary native attack submission observation only; no additional attacks or packet replacement. */
@Mixin(Connection.class)
public abstract class SlayerFinishingAttackSubmissionMixin {
    @WrapMethod(method="send(Lnet/minecraft/network/protocol/Packet;)V",require=1)
    private void finishingSubmission(Packet<?> packet,Operation<Void> original){
        McSlayerFinishingSwap.SendInvocation captured=null;
        try{captured=McSlayerFinishingSwap.beginSend((Connection)(Object)this,packet);}catch(Throwable ignored){}
        boolean returned=false;
        try{original.call(packet);returned=true;}
        finally{try{McSlayerFinishingSwap.finishSend(captured,returned);}catch(Throwable ignored){}}
    }
}
