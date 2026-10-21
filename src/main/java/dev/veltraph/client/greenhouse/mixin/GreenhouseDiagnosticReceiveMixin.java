package dev.veltraph.client.greenhouse.mixin;

import dev.veltraph.client.greenhouse.McGreenhouseDiagnosticAdapter;
import dev.veltraph.client.greenhouse.GreenhouseObserverBoundary;
import dev.veltraph.client.garden.visitor.history.VisitorHistoryNativeCallFrame;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

/** Observation may fail; each unchanged original native packet handler still executes exactly once. */
@Mixin(ClientPacketListener.class)
public abstract class GreenhouseDiagnosticReceiveMixin implements McGreenhouseDiagnosticAdapter.NativeReceiveCarrier {
    // Lazy observer allocation is inside the protected handler boundary, not the native listener constructor.
    @Unique private ThreadLocal<VisitorHistoryNativeCallFrame> greenhouse$receive;
    @Unique private volatile boolean greenhouse$observerFailed;
    public boolean greenhouseObserverHealthy(){return !greenhouse$observerFailed;}
    public Object greenhouseClaim(Object packet,int kind){var store=greenhouse$receive;var f=store==null?null:store.get();return f==null?null:f.claim(packet,this,null,kind);}
    public boolean greenhouseCurrent(Object claim,Object packet,int kind,boolean normal){var store=greenhouse$receive;var f=store==null?null:store.get();return !greenhouse$observerFailed&&f!=null&&f.current(claim,packet,this,null,kind,normal);}
    @Unique private void greenhouse$failed(){
        try{greenhouse$observerFailed=true;}catch(Throwable latchUnavailable){/* Do not replace the original native operation. */} // Listener lifetime: adapter replacement cannot erase this refusal.
        try{McGreenhouseDiagnosticAdapter.observerFailed(this);}catch(Throwable observerUnavailable){/* The listener latch still denies publication and fresh observations. */}
    }
    @WrapMethod(method="handleOpenScreen(Lnet/minecraft/network/protocol/game/ClientboundOpenScreenPacket;)V",require=1)
    private void greenhouse$open(ClientboundOpenScreenPacket packet,Operation<Void> original){
        ThreadLocal<VisitorHistoryNativeCallFrame> store=null;
        VisitorHistoryNativeCallFrame previous=null,frame=null;
        McGreenhouseDiagnosticAdapter.ReceiveInvocation call=null;boolean restore=false;
        try{
            if(!greenhouse$observerFailed){
                if(greenhouse$receive==null)greenhouse$receive=new ThreadLocal<>();
                store=greenhouse$receive;previous=store.get();frame=new VisitorHistoryNativeCallFrame(packet,this,null,1);
                restore=true;store.set(frame);call=McGreenhouseDiagnosticAdapter.receiveEntry(this,packet);
            }
        }catch(Throwable observerFailure){try{greenhouse$failed();}catch(Throwable reportingFailure){}}
        boolean normal=false;
        try{original.call(packet);normal=true;} // Deliberately outside every observer helper: no retry or fallback can execute it again.
        finally{
            try{var captured=frame;boolean returned=normal;
                if(captured!=null&&!GreenhouseObserverBoundary.run(()->captured.originalReturned(returned)))greenhouse$failed();
            }catch(Throwable observerLinkageFailure){try{greenhouse$failed();}catch(Throwable reportingFailure){}}
            try{var captured=call;boolean returned=normal;
                if(captured!=null&&!greenhouse$observerFailed&&!GreenhouseObserverBoundary.run(()->McGreenhouseDiagnosticAdapter.receiveExit(captured,returned)))greenhouse$failed();
            }catch(Throwable observerLinkageFailure){try{greenhouse$failed();}catch(Throwable reportingFailure){}}
            try{var capturedStore=store;var capturedPrevious=previous;
                if(restore&&!GreenhouseObserverBoundary.run(()->{if(capturedPrevious==null)capturedStore.remove();else capturedStore.set(capturedPrevious);}))greenhouse$failed();
            }catch(Throwable observerLinkageFailure){try{greenhouse$failed();}catch(Throwable reportingFailure){}}
        }
    }
    @WrapMethod(method="handleContainerContent(Lnet/minecraft/network/protocol/game/ClientboundContainerSetContentPacket;)V",require=1)
    private void greenhouse$content(ClientboundContainerSetContentPacket packet,Operation<Void> original){
        ThreadLocal<VisitorHistoryNativeCallFrame> store=null;
        VisitorHistoryNativeCallFrame previous=null,frame=null;
        McGreenhouseDiagnosticAdapter.ReceiveInvocation call=null;boolean restore=false;
        try{
            if(!greenhouse$observerFailed){
                if(greenhouse$receive==null)greenhouse$receive=new ThreadLocal<>();
                store=greenhouse$receive;previous=store.get();frame=new VisitorHistoryNativeCallFrame(packet,this,null,2);
                restore=true;store.set(frame);call=McGreenhouseDiagnosticAdapter.receiveEntry(this,packet);
            }
        }catch(Throwable observerFailure){try{greenhouse$failed();}catch(Throwable reportingFailure){}}
        boolean normal=false;
        try{original.call(packet);normal=true;} // Deliberately outside every observer helper: no retry or fallback can execute it again.
        finally{
            try{var captured=frame;boolean returned=normal;
                if(captured!=null&&!GreenhouseObserverBoundary.run(()->captured.originalReturned(returned)))greenhouse$failed();
            }catch(Throwable observerLinkageFailure){try{greenhouse$failed();}catch(Throwable reportingFailure){}}
            try{var captured=call;boolean returned=normal;
                if(captured!=null&&!greenhouse$observerFailed&&!GreenhouseObserverBoundary.run(()->McGreenhouseDiagnosticAdapter.receiveExit(captured,returned)))greenhouse$failed();
            }catch(Throwable observerLinkageFailure){try{greenhouse$failed();}catch(Throwable reportingFailure){}}
            try{var capturedStore=store;var capturedPrevious=previous;
                if(restore&&!GreenhouseObserverBoundary.run(()->{if(capturedPrevious==null)capturedStore.remove();else capturedStore.set(capturedPrevious);}))greenhouse$failed();
            }catch(Throwable observerLinkageFailure){try{greenhouse$failed();}catch(Throwable reportingFailure){}}
        }
    }
    @WrapMethod(method="handleContainerClose(Lnet/minecraft/network/protocol/game/ClientboundContainerClosePacket;)V",require=1)
    private void greenhouse$close(ClientboundContainerClosePacket packet,Operation<Void> original){
        ThreadLocal<VisitorHistoryNativeCallFrame> store=null;
        VisitorHistoryNativeCallFrame previous=null,frame=null;
        McGreenhouseDiagnosticAdapter.ReceiveInvocation call=null;boolean restore=false;
        try{
            if(!greenhouse$observerFailed){
                if(greenhouse$receive==null)greenhouse$receive=new ThreadLocal<>();
                store=greenhouse$receive;previous=store.get();frame=new VisitorHistoryNativeCallFrame(packet,this,null,3);
                restore=true;store.set(frame);call=McGreenhouseDiagnosticAdapter.receiveEntry(this,packet);
            }
        }catch(Throwable observerFailure){try{greenhouse$failed();}catch(Throwable reportingFailure){}}
        boolean normal=false;
        try{original.call(packet);normal=true;} // Deliberately outside every observer helper: no retry or fallback can execute it again.
        finally{
            try{var captured=frame;boolean returned=normal;
                if(captured!=null&&!GreenhouseObserverBoundary.run(()->captured.originalReturned(returned)))greenhouse$failed();
            }catch(Throwable observerLinkageFailure){try{greenhouse$failed();}catch(Throwable reportingFailure){}}
            try{var captured=call;boolean returned=normal;
                if(captured!=null&&!greenhouse$observerFailed&&!GreenhouseObserverBoundary.run(()->McGreenhouseDiagnosticAdapter.receiveExit(captured,returned)))greenhouse$failed();
            }catch(Throwable observerLinkageFailure){try{greenhouse$failed();}catch(Throwable reportingFailure){}}
            try{var capturedStore=store;var capturedPrevious=previous;
                if(restore&&!GreenhouseObserverBoundary.run(()->{if(capturedPrevious==null)capturedStore.remove();else capturedStore.set(capturedPrevious);}))greenhouse$failed();
            }catch(Throwable observerLinkageFailure){try{greenhouse$failed();}catch(Throwable reportingFailure){}}
        }
    }
}
