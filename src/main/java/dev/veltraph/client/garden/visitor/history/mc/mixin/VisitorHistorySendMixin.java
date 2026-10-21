package dev.veltraph.client.garden.visitor.history.mc.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.veltraph.client.garden.visitor.history.VisitorHistoryNativeCallCarrier;
import dev.veltraph.client.garden.visitor.history.VisitorHistoryNativeCallFrame;
import dev.veltraph.client.garden.visitor.history.mc.McVisitorHistoryPacketObserver;
import io.netty.channel.ChannelFutureListener;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Actual native-call carrier lives privately on transformed Connection; helper classes are outside mixin reservation. */
@Mixin(Connection.class)
public abstract class VisitorHistorySendMixin implements VisitorHistoryNativeCallCarrier {
    @Unique private final ThreadLocal<VisitorHistoryNativeCallFrame> veltraph$historySendFrames=new ThreadLocal<>();
    @Override public Object veltraph$claimHistoryNativeCall(Object p,Object a1,Object a2,int kind){var f=veltraph$historySendFrames.get();return f==null?null:f.claim(p,a1,a2,kind);}
    @Override public boolean veltraph$historyNativeCallCurrent(Object t,Object p,Object a1,Object a2,int kind,boolean normal){var f=veltraph$historySendFrames.get();return f!=null&&f.current(t,p,a1,a2,kind,normal);}
    @WrapMethod(method="send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;Z)V",require=1)
    private void history$sent(Packet<?> packet,ChannelFutureListener listener,boolean flush,Operation<Void> original){
        VisitorHistoryNativeCallFrame previous=null,frame=null;boolean installed=false;
        McVisitorHistoryPacketObserver.SendInvocation call=null;
        try{previous=veltraph$historySendFrames.get();frame=new VisitorHistoryNativeCallFrame(packet,listener,Boolean.valueOf(flush),0);veltraph$historySendFrames.set(frame);installed=true;
            call=McVisitorHistoryPacketObserver.beginSend((Connection)(Object)this,packet,listener,flush);}catch(Throwable observationFailure){/* Refuse observation only. Original must still run. */}
        boolean normal=false;
        try{original.call(packet,listener,flush);normal=true;}
        finally{try{if(frame!=null)frame.originalReturned(normal);McVisitorHistoryPacketObserver.finishSend(call,normal);}catch(Throwable observationFailure){/* Never mask native exceptions. */}
            finally{try{if(installed){if(previous==null)veltraph$historySendFrames.remove();else veltraph$historySendFrames.set(previous);}}catch(Throwable restorationFailure){/* Observation only; preserve native result/exception. */}}}
    }
}
