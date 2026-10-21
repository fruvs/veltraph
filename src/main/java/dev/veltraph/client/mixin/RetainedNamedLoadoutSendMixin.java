package dev.veltraph.client.mixin;
import dev.veltraph.client.garden.loadout.mc.McNamedLoadoutPort;
import dev.veltraph.client.garden.visitor.history.VisitorHistoryNativeCallFrame;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
@Mixin(ClientCommonPacketListenerImpl.class)
public abstract class RetainedNamedLoadoutSendMixin implements McNamedLoadoutPort.NativeSendCarrier {
    @Unique private final ThreadLocal<VisitorHistoryNativeCallFrame> named$send=new ThreadLocal<>();
    public Object namedLoadoutSendClaim(Object packet){var f=named$send.get();return f==null?null:f.claim(packet,this,null,0);}
    public boolean namedLoadoutSendCurrent(Object token,Object packet,boolean normal){var f=named$send.get();return f!=null&&f.current(token,packet,this,null,0,normal);}
    @WrapMethod(method="send(Lnet/minecraft/network/protocol/Packet;)V",require=1)
    private void named$send(Packet<?> packet,Operation<Void> original){
        var previous=named$send.get();var frame=new VisitorHistoryNativeCallFrame(packet,this,null,0);named$send.set(frame);
        McNamedLoadoutPort.SendInvocation call=null;try{call=McNamedLoadoutPort.sendEntry(this,packet);}catch(Throwable unknown){McNamedLoadoutPort.nativeObservationFailure();}
        boolean normal=false;try{original.call(packet);normal=true;}
        finally{try{frame.originalReturned(normal);McNamedLoadoutPort.sendExit(call,this,packet,normal);}catch(Throwable unknown){McNamedLoadoutPort.nativeObservationFailure();}
            finally{if(previous==null)named$send.remove();else named$send.set(previous);}}
    }
}
