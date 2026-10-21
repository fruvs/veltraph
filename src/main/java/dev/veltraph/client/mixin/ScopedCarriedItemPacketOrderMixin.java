package dev.veltraph.client.mixin;
import dev.veltraph.client.navigation.NativeCarryPublicationWitness;
import dev.veltraph.client.navigation.NativeCarryPacketOrder;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import io.netty.channel.ChannelFutureListener;
import java.util.Queue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
/** Passive true native submission/queue opcode returns, before outer method postcallbacks.
 * No admission, cancellation, packet mutation, cache write or observer exception escapes. */
@Mixin(Connection.class)
public abstract class ScopedCarriedItemPacketOrderMixin implements NativeCarryPacketOrder {
    @Unique private final NativeCarryPublicationWitness.PacketHistory carry$packetHistory=new NativeCarryPublicationWitness.PacketHistory();
    public NativeCarryPublicationWitness.PacketHistory.Snapshot veltraph$nativeCarryPacketOrder(){
        try{return carry$packetHistory.snapshot();}catch(Throwable unknown){carry$unknown();return null;}
    }
    @Unique private void carry$unknown(){try{carry$packetHistory.unknown();}catch(Throwable ignored){/* Preserve original native exit. */}}
    @Unique private void carry$observe(Packet<?> packet,boolean normal){
        try{if(packet instanceof ServerboundSetCarriedItemPacket carry){if(packet.getClass()!=ServerboundSetCarriedItemPacket.class){carry$unknown();return;}if(normal)carry$packetHistory.nativeSubmissionReturned(packet,carry.getSlot());else carry$packetHistory.unknown();}}
        catch(Throwable unknown){carry$unknown();}
    }
    @WrapOperation(method="send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;Z)V",at=@At(value="INVOKE",target="Lnet/minecraft/network/Connection;sendPacket(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;Z)V"),require=1)
    private void carry$actualSubmission(Connection transport,Packet<?> packet,ChannelFutureListener listener,boolean flush,Operation<Void> original){
        boolean normal=false;
        try{original.call(transport,packet,listener,flush);normal=true;}finally{carry$observe(packet,normal);}
    }
    @WrapOperation(method="send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;Z)V",at=@At(value="INVOKE",target="Ljava/util/Queue;add(Ljava/lang/Object;)Z"),require=1)
    private boolean carry$actualQueue(Queue<?> queue,Object action,Operation<Boolean> original,Packet<?> packet,ChannelFutureListener listener,boolean flush){
        boolean normal=false;
        try{boolean accepted=original.call(queue,action);normal=accepted;return accepted;}finally{carry$observe(packet,normal);}
    }
}
