package dev.veltraph.client.mixin;
import dev.veltraph.client.garden.loadout.mc.McNamedLoadoutPort;
import dev.veltraph.client.garden.visitor.history.VisitorHistoryNativeCallFrame;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
@Mixin(ClientPacketListener.class)
public abstract class RetainedNamedLoadoutReceiveMixin implements McNamedLoadoutPort.NativeCallCarrier {
    @Unique private final ThreadLocal<VisitorHistoryNativeCallFrame> named$receive=new ThreadLocal<>();
    public Object namedLoadoutClaim(Object packet,int kind){var f=named$receive.get();return f==null?null:f.claim(packet,this,null,kind);}
    public boolean namedLoadoutCurrent(Object token,Object packet,int kind,boolean normal){var f=named$receive.get();return f!=null&&f.current(token,packet,this,null,kind,normal);}
    @WrapMethod(method="handleContainerContent(Lnet/minecraft/network/protocol/game/ClientboundContainerSetContentPacket;)V",require=1)
    private void named$handleContainerContent(ClientboundContainerSetContentPacket packet,Operation<Void> original){
        var previous=named$receive.get();var frame=new VisitorHistoryNativeCallFrame(packet,this,null,1);named$receive.set(frame);
        McNamedLoadoutPort.ReceiveInvocation call=null;try{call=McNamedLoadoutPort.receiveEntry(this,packet);}catch(Throwable unknown){McNamedLoadoutPort.nativeObservationFailure();}
        boolean normal=false;try{original.call(packet);normal=true;}
        finally{try{frame.originalReturned(normal);McNamedLoadoutPort.receiveExit(call,normal);}catch(Throwable unknown){McNamedLoadoutPort.nativeObservationFailure();}
            finally{if(previous==null)named$receive.remove();else named$receive.set(previous);}}
    }
    @WrapMethod(method="handleOpenScreen(Lnet/minecraft/network/protocol/game/ClientboundOpenScreenPacket;)V",require=1)
    private void named$handleOpenScreen(ClientboundOpenScreenPacket packet,Operation<Void> original){
        var previous=named$receive.get();var frame=new VisitorHistoryNativeCallFrame(packet,this,null,2);named$receive.set(frame);
        McNamedLoadoutPort.ReceiveInvocation call=null;try{call=McNamedLoadoutPort.receiveEntry(this,packet);}catch(Throwable unknown){McNamedLoadoutPort.nativeObservationFailure();}
        boolean normal=false;try{original.call(packet);normal=true;}
        finally{try{frame.originalReturned(normal);McNamedLoadoutPort.receiveExit(call,normal);}catch(Throwable unknown){McNamedLoadoutPort.nativeObservationFailure();}
            finally{if(previous==null)named$receive.remove();else named$receive.set(previous);}}
    }
    @WrapMethod(method="handleSystemChat(Lnet/minecraft/network/protocol/game/ClientboundSystemChatPacket;)V",require=1)
    private void named$handleSystemChat(ClientboundSystemChatPacket packet,Operation<Void> original){
        var previous=named$receive.get();var frame=new VisitorHistoryNativeCallFrame(packet,this,null,3);named$receive.set(frame);
        McNamedLoadoutPort.ReceiveInvocation call=null;try{call=McNamedLoadoutPort.receiveEntry(this,packet);}catch(Throwable unknown){McNamedLoadoutPort.nativeObservationFailure();}
        boolean normal=false;try{original.call(packet);normal=true;}
        finally{try{frame.originalReturned(normal);McNamedLoadoutPort.receiveExit(call,normal);}catch(Throwable unknown){McNamedLoadoutPort.nativeObservationFailure();}
            finally{if(previous==null)named$receive.remove();else named$receive.set(previous);}}
    }
    @WrapMethod(method="handleContainerSetSlot(Lnet/minecraft/network/protocol/game/ClientboundContainerSetSlotPacket;)V",require=1)
    private void named$handleContainerSetSlot(ClientboundContainerSetSlotPacket packet,Operation<Void> original){
        var previous=named$receive.get();var frame=new VisitorHistoryNativeCallFrame(packet,this,null,4);named$receive.set(frame);
        McNamedLoadoutPort.ReceiveInvocation call=null;try{call=McNamedLoadoutPort.receiveEntry(this,packet);}catch(Throwable unknown){McNamedLoadoutPort.nativeObservationFailure();}
        boolean normal=false;try{original.call(packet);normal=true;}
        finally{try{frame.originalReturned(normal);McNamedLoadoutPort.receiveExit(call,normal);}catch(Throwable unknown){McNamedLoadoutPort.nativeObservationFailure();}
            finally{if(previous==null)named$receive.remove();else named$receive.set(previous);}}
    }
    @WrapMethod(method="handleSetPlayerInventory(Lnet/minecraft/network/protocol/game/ClientboundSetPlayerInventoryPacket;)V",require=1)
    private void named$handleSetPlayerInventory(ClientboundSetPlayerInventoryPacket packet,Operation<Void> original){
        var previous=named$receive.get();var frame=new VisitorHistoryNativeCallFrame(packet,this,null,5);named$receive.set(frame);
        McNamedLoadoutPort.ReceiveInvocation call=null;try{call=McNamedLoadoutPort.receiveEntry(this,packet);}catch(Throwable unknown){McNamedLoadoutPort.nativeObservationFailure();}
        boolean normal=false;try{original.call(packet);normal=true;}
        finally{try{frame.originalReturned(normal);McNamedLoadoutPort.receiveExit(call,normal);}catch(Throwable unknown){McNamedLoadoutPort.nativeObservationFailure();}
            finally{if(previous==null)named$receive.remove();else named$receive.set(previous);}}
    }
    @WrapMethod(method="handleSetCursorItem(Lnet/minecraft/network/protocol/game/ClientboundSetCursorItemPacket;)V",require=1)
    private void named$handleSetCursorItem(ClientboundSetCursorItemPacket packet,Operation<Void> original){
        var previous=named$receive.get();var frame=new VisitorHistoryNativeCallFrame(packet,this,null,6);named$receive.set(frame);
        McNamedLoadoutPort.ReceiveInvocation call=null;try{call=McNamedLoadoutPort.receiveEntry(this,packet);}catch(Throwable unknown){McNamedLoadoutPort.nativeObservationFailure();}
        boolean normal=false;try{original.call(packet);normal=true;}
        finally{try{frame.originalReturned(normal);McNamedLoadoutPort.receiveExit(call,normal);}catch(Throwable unknown){McNamedLoadoutPort.nativeObservationFailure();}
            finally{if(previous==null)named$receive.remove();else named$receive.set(previous);}}
    }
}
