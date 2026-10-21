package dev.veltraph.client.service.mc;

import dev.veltraph.client.garden.pests.mc.McPestInventoryRestorationObserver.SelectionHistory;
import dev.veltraph.client.mixin.ScopedCarriedItemSynchronizationInvoker;
import dev.veltraph.client.navigation.NativeCarryPacketOrder;
import dev.veltraph.client.navigation.NativeCarryPublicationWitness;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;

/** Finishing-only genuine vanilla synchronization. Private invocation + exact real packet return;
 * never a raw cache write, a cached-equality success, a server ACK or vanilla-tick suppression. */
public final class McScopedCarriedItemSynchronization {
    private static final ThreadLocal<Request> CURRENT=new ThreadLocal<>();
    private static final ThreadLocal<Send> SENDING=new ThreadLocal<>();
    private static final ThreadLocal<ListenerCall> LISTENERS=new ThreadLocal<>();
    private McScopedCarriedItemSynchronization() {}
    private static final class Refused extends RuntimeException {
        private Refused(){super("Scoped native carry publication refused");}
    }
    public static final class Request {
        private final Minecraft c;private final Object player,world,inventory,menu;
        private final MultiPlayerGameMode mode;private final ClientPacketListener listener;private final Connection transport;
        private final int slot;private final BooleanSupplier leaf;private final SelectionHistory history;
        private final NativeCarryPublicationWitness witness;private boolean closed,nativeStarted,cacheCommitted;
        private int priorCache,pendingSlot;private Runnable originalCacheWrite;
        private IntSupplier nativeCache;private Supplier<NativeCarryPublicationWitness.CacheHistory.Snapshot> nativeHistory;
        private NativeCarryPublicationWitness.CacheHistory.Snapshot priorHistory,committedHistory;
        private Request(Minecraft c,int slot,BooleanSupplier leaf,SelectionHistory history) {
            this.c=c;this.slot=slot;this.leaf=leaf;this.history=history;
            player=c.player;world=c.level;inventory=c.player.getInventory();menu=c.player.inventoryMenu;
            mode=c.gameMode;listener=c.getConnection();transport=listener.getConnection();
            witness=new NativeCarryPublicationWitness(mode,c,listener,transport,slot);
        }
        private boolean current(){return context()&&leaf.getAsBoolean()&&context();}
        private boolean cacheCurrent(){
            return nativeCache==null||nativeCache.getAsInt()==(cacheCommitted?slot:priorCache)
                    &&(cacheCommitted?committedHistory!=null&&committedHistory.followsExactlyOne(priorHistory):priorHistory!=null&&priorHistory.current());
        }
        /** Native bookkeeping follows exact real submission; later local selection is gameplay provenance. */
        private boolean bookkeepingContext(){
            var p=c.player;
            return !closed&&CURRENT.get()==this&&c.isSameThread()&&p!=null&&p==player&&c.level==world&&p.level()==world
                    &&c.gameMode==mode&&c.getConnection()==listener&&listener.getConnection()==transport
                    &&p.getInventory()==inventory&&cacheCurrent();
        }
        private boolean context(){
            var p=c.player;
            return bookkeepingContext()&&p.inventoryMenu==menu&&p.containerMenu==menu&&p.inventoryMenu.getCarried().isEmpty()
                    &&p.getInventory().getSelectedSlot()==slot&&history!=null&&history.current();
        }
    }

    public static final class Send {
        private final Request request;private final Packet<?> packet;private final Send previous;private boolean listenerStarted;
        private Send(Request request,Packet<?> packet,Send previous){this.request=request;this.packet=packet;this.previous=previous;}
    }
    /** Actual submission survives a later body failure without certifying that invocation. */
    static final class Publication {
        final boolean submitted,confirmed;
        private Publication(boolean submitted,boolean confirmed){this.submitted=submitted;this.confirmed=confirmed;}
    }
    /** Only the actual scoped setter's adapter supplies this captured history and native leaf. */
    static Publication publish(Minecraft c,int slot,BooleanSupplier beforeNative,BooleanSupplier finalLeaf,SelectionHistory history) {
        if(!c.isSameThread()||CURRENT.get()!=null||c.player==null||c.level==null||c.gameMode==null||c.getConnection()==null
                ||slot<0||slot>8||history==null||!beforeNative.getAsBoolean())throw new Refused();
        var request=new Request(c,slot,finalLeaf,history);CURRENT.set(request);boolean normal=false,current=false;
        try {
            try {
                if(!request.current())throw new Refused();
                ((ScopedCarriedItemSynchronizationInvoker)request.mode).veltraph$ensureHasSentCarriedItem();normal=true;
            }catch(RuntimeException unavailable){
                // An actual normal packet return survives a later native-body failure.
            }finally{request.witness.bodyReturned(normal);}
            current=request.current();
            return new Publication(request.witness.submitted(),request.witness.confirmed(current));
        }catch(RuntimeException unavailable){
            return new Publication(request.witness.submitted(),false);
        }finally {
            request.closed=true;request.originalCacheWrite=null;
            if(CURRENT.get()==request)CURRENT.remove();
        }
    }
    public static Request enterNative(MultiPlayerGameMode mode,Minecraft nativeClient,ClientPacketListener nativeListener,
            IntSupplier nativeCache,Supplier<NativeCarryPublicationWitness.CacheHistory.Snapshot> nativeHistory) {
        var request=CURRENT.get();if(request==null||request.mode!=mode||request.nativeStarted)return null;
        request.nativeStarted=true;request.nativeCache=nativeCache;request.nativeHistory=nativeHistory;
        request.priorCache=nativeCache.getAsInt();request.priorHistory=nativeHistory.get();
        if(!request.witness.enter(mode,nativeClient,nativeListener,request.current()))throw new Refused();return request;
    }
    /** Captures only the original owned native opcode operation. There is no arbitrary cache value API. */
    public static void stageCache(Request request,MultiPlayerGameMode mode,int slot,Runnable originalNativeOperation) {
        if(request==null)throw new Refused();
        if(request.originalCacheWrite!=null||!request.witness.beforeCache(mode,slot,request.current()))throw new Refused();
        request.pendingSlot=slot;request.originalCacheWrite=originalNativeOperation;request.witness.assignmentStaged();
    }
    /** Only the second native cached read, used as its original packet constructor operand, is adapted. */
    public static int constructorSlot(Request request,MultiPlayerGameMode mode) {
        if(request==null||request.mode!=mode||request.originalCacheWrite==null||!request.current())throw new Refused();
        return request.pendingSlot;
    }
    public static Send beforeSend(Request request,ClientPacketListener listener,Packet<?> packet) {
        if(request==null)return null;
        if(!(packet instanceof ServerboundSetCarriedItemPacket carry)
                ||!request.witness.offer(listener,packet,carry.getSlot(),request.current()))throw new Refused();
        var send=new Send(request,packet,SENDING.get());SENDING.set(send);return send;
    }
    public static void closeSend(Send send){if(send!=null&&SENDING.get()==send){if(send.previous==null)SENDING.remove();else SENDING.set(send.previous);}}
    /** Private actual listener-call identity, including an unclaimed entry masking nested/manual calls. */
    public static final class ListenerCall {
        private final Object listener;private final Packet<?> packet;private final Send send;private final ListenerCall previous;
        private ListenerCall(Object listener,Packet<?> packet,Send send,ListenerCall previous){
            this.listener=listener;this.packet=packet;this.send=send;this.previous=previous;
        }
    }
    /** Claim the exact inherited listener.send body once; nested/manual calls get an unclaimed capsule. */
    public static ListenerCall enterListener(Object listener,Packet<?> packet) {
        var previous=LISTENERS.get();var candidate=SENDING.get();Send claimed=null;
        if(previous==null&&candidate!=null&&!candidate.listenerStarted&&candidate.request.listener==listener&&candidate.packet==packet){
            candidate.listenerStarted=true;claimed=candidate;
        }
        if(claimed==null&&previous==null&&candidate==null)return null;
        var call=new ListenerCall(listener,packet,claimed,previous);LISTENERS.set(call);return call;
    }
    public static void closeListener(ListenerCall call){
        if(call!=null&&LISTENERS.get()==call){if(call.previous==null)LISTENERS.remove();else LISTENERS.set(call.previous);}
    }
    /** Only the existing Fig redirect calls this, after its own external permit/refusal checks,
     * immediately around its genuine Connection.send. The exact listener capsule supplies ownership. */
    public static Send beforeTransport(Object listener,Connection transport,Packet<?> packet) {
        var call=LISTENERS.get();
        if(call==null||call.send==null)return null;
        var send=call.send;
        if(call.listener!=listener||call.packet!=packet||SENDING.get()!=send
                ||!send.request.witness.beforeTransport(listener,transport,packet,send.request.current()))throw new Refused();
        return send;
    }
    /** A latest exact actual native submission is ordering evidence only; it never supplies owned success. */
    private static boolean latestNativePacket(Send send){
        try{
            if(!(send.request.transport instanceof NativeCarryPacketOrder order))return false;
            var snapshot=order.veltraph$nativeCarryPacketOrder();
            return snapshot!=null&&snapshot.latestExact(send.packet,send.request.slot);
        }catch(Throwable unknown){return false;}
    }
    /** The actual normally returned Connection.send commits only its original native cache operation.
     * Later helper/config failure denies gameplay authority without losing real-send bookkeeping. */
    public static void transportReturned(Send send,boolean normal){
        if(send==null)return;var request=send.request;
        request.witness.transportReturned(normal);
        if(normal&&request.originalCacheWrite!=null&&request.witness.beforeCommit(request.bookkeepingContext()&&latestNativePacket(send))){
            request.originalCacheWrite.run();
            request.committedHistory=request.nativeHistory.get();request.cacheCommitted=true;
            request.witness.cacheReturned();
            request.originalCacheWrite=null;
        }
    }
}
