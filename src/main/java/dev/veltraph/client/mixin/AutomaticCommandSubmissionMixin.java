package dev.veltraph.client.mixin;
import dev.veltraph.client.navigation.mc.NativeAutomaticCommandReceipt;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
/** Capture this actual native invocation locally, then observe its normal/throw exit without changing it. */
@Mixin(Connection.class)
public abstract class AutomaticCommandSubmissionMixin {
    @WrapMethod(method="send(Lnet/minecraft/network/protocol/Packet;)V",require=1)
    private void veltraph$automaticCommandInvocation(Packet<?> packet,Operation<Void> original){
        NativeAutomaticCommandReceipt.SendInvocation entry=null;
        try{entry=NativeAutomaticCommandReceipt.sendEntry((Connection)(Object)this,packet);}catch(Throwable ignored){/* Observation cannot block native send. */}
        boolean normal=false;
        try{original.call(packet);normal=true;}
        finally{try{NativeAutomaticCommandReceipt.sendExited(entry,(Connection)(Object)this,packet,normal);}catch(Throwable failure){try{NativeAutomaticCommandReceipt.sendObservationFailed(entry);}catch(Throwable ignored){/* Preserve the original native exit. */}}}
    }
}
