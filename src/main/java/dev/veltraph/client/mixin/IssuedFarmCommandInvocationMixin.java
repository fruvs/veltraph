package dev.veltraph.client.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.veltraph.client.rewarp.mc.IssuedFarmCommandFence;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ClientPacketListener.class)
public abstract class IssuedFarmCommandInvocationMixin {
    @WrapMethod(method="sendCommand(Ljava/lang/String;)V", require=1)
    private void veltraph$farmReturnCommand(String command, Operation<Void> original) {
        var self=(ClientPacketListener)(Object)this;
        var invocation=IssuedFarmCommandFence.commandEntered(self,command);
        boolean normal=false;
        try { original.call(command); normal=true; }
        finally { IssuedFarmCommandFence.exited(invocation,self,null,normal); }
    }
    @WrapOperation(method="sendCommand(Ljava/lang/String;)V", at=@At(value="INVOKE",
            target="Lnet/minecraft/client/multiplayer/ClientPacketListener;send(Lnet/minecraft/network/protocol/Packet;)V"), require=2, allow=2)
    private void veltraph$farmReturnConstructedPacket(ClientPacketListener listener, Packet<?> packet, Operation<Void> original) {
        IssuedFarmCommandFence.commandPacket(listener,packet);
        original.call(listener,packet);
    }
}
