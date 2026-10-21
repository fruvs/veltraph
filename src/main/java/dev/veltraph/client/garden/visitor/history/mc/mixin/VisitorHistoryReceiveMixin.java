package dev.veltraph.client.garden.visitor.history.mc.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.veltraph.client.garden.visitor.history.VisitorHistoryNativeCallCarrier;
import dev.veltraph.client.garden.visitor.history.VisitorHistoryNativeCallFrame;
import dev.veltraph.client.garden.visitor.history.mc.McVisitorHistoryPacketObserver;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Private exact invocation frames on the actual listener preserve normal/error/reentrant provenance. */
@Mixin(ClientPacketListener.class)
public abstract class VisitorHistoryReceiveMixin implements VisitorHistoryNativeCallCarrier {
    @Unique private final ThreadLocal<VisitorHistoryNativeCallFrame> veltraph$historyReceiveFrames=new ThreadLocal<>();
    @Override public Object veltraph$claimHistoryNativeCall(Object p,Object a1,Object a2,int kind){var f=veltraph$historyReceiveFrames.get();return f==null?null:f.claim(p,a1,a2,kind);}
    @Override public boolean veltraph$historyNativeCallCurrent(Object t,Object p,Object a1,Object a2,int kind,boolean normal){var f=veltraph$historyReceiveFrames.get();return f!=null&&f.current(t,p,a1,a2,kind,normal);}
    @WrapMethod(method="handleContainerContent(Lnet/minecraft/network/protocol/game/ClientboundContainerSetContentPacket;)V",require=1)
    private void history$content(ClientboundContainerSetContentPacket packet,Operation<Void> original){
        VisitorHistoryNativeCallFrame previous=null,frame=null;boolean installed=false;
        McVisitorHistoryPacketObserver.ReceiveInvocation call=null;
        try{previous=veltraph$historyReceiveFrames.get();frame=new VisitorHistoryNativeCallFrame(packet,this,null,1);veltraph$historyReceiveFrames.set(frame);installed=true;
            call=McVisitorHistoryPacketObserver.beginReceive(this,packet);}catch(Throwable failure){/* No authority; original still runs. */}
        boolean normal=false;try{original.call(packet);normal=true;}
        finally{try{if(frame!=null)frame.originalReturned(normal);McVisitorHistoryPacketObserver.finishReceive(call,normal);}catch(Throwable failure){/* Keep native exception. */}
            finally{try{if(installed){if(previous==null)veltraph$historyReceiveFrames.remove();else veltraph$historyReceiveFrames.set(previous);}}catch(Throwable failure){/* Preserve original exception. */}}}
    }
    @WrapMethod(method="handleContainerSetSlot(Lnet/minecraft/network/protocol/game/ClientboundContainerSetSlotPacket;)V",require=1)
    private void history$slot(ClientboundContainerSetSlotPacket packet,Operation<Void> original){
        VisitorHistoryNativeCallFrame previous=null,frame=null;boolean installed=false;
        McVisitorHistoryPacketObserver.ReceiveInvocation call=null;
        try{previous=veltraph$historyReceiveFrames.get();frame=new VisitorHistoryNativeCallFrame(packet,this,null,2);veltraph$historyReceiveFrames.set(frame);installed=true;
            call=McVisitorHistoryPacketObserver.beginReceive(this,packet);}catch(Throwable failure){/* No authority; original still runs. */}
        boolean normal=false;try{original.call(packet);normal=true;}
        finally{try{if(frame!=null)frame.originalReturned(normal);McVisitorHistoryPacketObserver.finishReceive(call,normal);}catch(Throwable failure){/* Keep native exception. */}
            finally{try{if(installed){if(previous==null)veltraph$historyReceiveFrames.remove();else veltraph$historyReceiveFrames.set(previous);}}catch(Throwable failure){/* Preserve original exception. */}}}
    }
    @WrapMethod(method="handleSetPlayerInventory(Lnet/minecraft/network/protocol/game/ClientboundSetPlayerInventoryPacket;)V",require=1)
    private void history$inventory(ClientboundSetPlayerInventoryPacket packet,Operation<Void> original){
        VisitorHistoryNativeCallFrame previous=null,frame=null;boolean installed=false;
        McVisitorHistoryPacketObserver.ReceiveInvocation call=null;
        try{previous=veltraph$historyReceiveFrames.get();frame=new VisitorHistoryNativeCallFrame(packet,this,null,3);veltraph$historyReceiveFrames.set(frame);installed=true;
            call=McVisitorHistoryPacketObserver.beginReceive(this,packet);}catch(Throwable failure){/* No authority; original still runs. */}
        boolean normal=false;try{original.call(packet);normal=true;}
        finally{try{if(frame!=null)frame.originalReturned(normal);McVisitorHistoryPacketObserver.finishReceive(call,normal);}catch(Throwable failure){/* Keep native exception. */}
            finally{try{if(installed){if(previous==null)veltraph$historyReceiveFrames.remove();else veltraph$historyReceiveFrames.set(previous);}}catch(Throwable failure){/* Preserve original exception. */}}}
    }
    @WrapMethod(method="handleOpenScreen(Lnet/minecraft/network/protocol/game/ClientboundOpenScreenPacket;)V",require=1)
    private void history$open(ClientboundOpenScreenPacket packet,Operation<Void> original){
        VisitorHistoryNativeCallFrame previous=null,frame=null;boolean installed=false;
        McVisitorHistoryPacketObserver.ReceiveInvocation call=null;
        try{previous=veltraph$historyReceiveFrames.get();frame=new VisitorHistoryNativeCallFrame(packet,this,null,4);veltraph$historyReceiveFrames.set(frame);installed=true;
            call=McVisitorHistoryPacketObserver.beginReceive(this,packet);}catch(Throwable failure){/* No authority; original still runs. */}
        boolean normal=false;try{original.call(packet);normal=true;}
        finally{try{if(frame!=null)frame.originalReturned(normal);McVisitorHistoryPacketObserver.finishReceive(call,normal);}catch(Throwable failure){/* Keep native exception. */}
            finally{try{if(installed){if(previous==null)veltraph$historyReceiveFrames.remove();else veltraph$historyReceiveFrames.set(previous);}}catch(Throwable failure){/* Preserve original exception. */}}}
    }
    @WrapMethod(method="handleContainerClose(Lnet/minecraft/network/protocol/game/ClientboundContainerClosePacket;)V",require=1)
    private void history$close(ClientboundContainerClosePacket packet,Operation<Void> original){
        VisitorHistoryNativeCallFrame previous=null,frame=null;boolean installed=false;
        McVisitorHistoryPacketObserver.ReceiveInvocation call=null;
        try{previous=veltraph$historyReceiveFrames.get();frame=new VisitorHistoryNativeCallFrame(packet,this,null,5);veltraph$historyReceiveFrames.set(frame);installed=true;
            call=McVisitorHistoryPacketObserver.beginReceive(this,packet);}catch(Throwable failure){/* No authority; original still runs. */}
        boolean normal=false;try{original.call(packet);normal=true;}
        finally{try{if(frame!=null)frame.originalReturned(normal);McVisitorHistoryPacketObserver.finishReceive(call,normal);}catch(Throwable failure){/* Keep native exception. */}
            finally{try{if(installed){if(previous==null)veltraph$historyReceiveFrames.remove();else veltraph$historyReceiveFrames.set(previous);}}catch(Throwable failure){/* Preserve original exception. */}}}
    }
    @WrapMethod(method="handleSetCursorItem(Lnet/minecraft/network/protocol/game/ClientboundSetCursorItemPacket;)V",require=1)
    private void history$cursor(ClientboundSetCursorItemPacket packet,Operation<Void> original){
        VisitorHistoryNativeCallFrame previous=null,frame=null;boolean installed=false;
        McVisitorHistoryPacketObserver.ReceiveInvocation call=null;
        try{previous=veltraph$historyReceiveFrames.get();frame=new VisitorHistoryNativeCallFrame(packet,this,null,6);veltraph$historyReceiveFrames.set(frame);installed=true;
            call=McVisitorHistoryPacketObserver.beginReceive(this,packet);}catch(Throwable failure){/* No authority; original still runs. */}
        boolean normal=false;try{original.call(packet);normal=true;}
        finally{try{if(frame!=null)frame.originalReturned(normal);McVisitorHistoryPacketObserver.finishReceive(call,normal);}catch(Throwable failure){/* Preserve original exception. */}
            finally{try{if(installed){if(previous==null)veltraph$historyReceiveFrames.remove();else veltraph$historyReceiveFrames.set(previous);}}catch(Throwable failure){/* Preserve original exception. */}}}
    }

}
