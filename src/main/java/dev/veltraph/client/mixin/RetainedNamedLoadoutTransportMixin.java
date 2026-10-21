package dev.veltraph.client.mixin;
import dev.veltraph.client.garden.loadout.mc.McNamedLoadoutPort;
import dev.veltraph.client.garden.visitor.history.VisitorHistoryNativeCallFrame;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import io.netty.channel.ChannelFutureListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
@Mixin(Connection.class)
public abstract class RetainedNamedLoadoutTransportMixin implements McNamedLoadoutPort.NativeTransportCarrier {
    @Unique private final ThreadLocal<VisitorHistoryNativeCallFrame> named$transport=new ThreadLocal<>();
    public Object namedLoadoutTransportClaim(Object packet,Object callback,Object flush){var f=named$transport.get();return f==null?null:f.claim(packet,callback,flush,7);}
    public boolean namedLoadoutTransportCurrent(Object token,Object packet,Object callback,Object flush,boolean normal){var f=named$transport.get();return f!=null&&f.current(token,packet,callback,flush,7,normal);}
    @WrapMethod(method="send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;Z)V",require=1)
    private void named$transport(Packet<?> packet,ChannelFutureListener callback,boolean flush,Operation<Void> original){
        var previous=named$transport.get();var frame=new VisitorHistoryNativeCallFrame(packet,callback,Boolean.valueOf(flush),7);named$transport.set(frame);
        McNamedLoadoutPort.TransportInvocation call=null;try{call=McNamedLoadoutPort.transportEntry(this,packet,callback,flush);}catch(Throwable failure){McNamedLoadoutPort.nativeObservationFailure();}
        boolean normal=false;try{original.call(packet,callback,flush);normal=true;}
        finally{try{frame.originalReturned(normal);McNamedLoadoutPort.transportExit(call,normal);}catch(Throwable failure){McNamedLoadoutPort.nativeObservationFailure();}
            finally{if(previous==null)named$transport.remove();else named$transport.set(previous);}}
    }
}
