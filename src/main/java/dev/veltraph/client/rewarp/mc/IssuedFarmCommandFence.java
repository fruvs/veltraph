package dev.veltraph.client.rewarp.mc;

import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.Connection;
import io.netty.channel.Channel;
import io.netty.channel.EventLoop;
import dev.veltraph.client.mixin.IssuedFarmConnectionAccessor;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;
import net.minecraft.network.protocol.game.ServerboundChatCommandSignedPacket;

/** Exactly one private configured-command submission. A submission is never a server landing ACK. */
public final class IssuedFarmCommandFence {
    private static final ThreadLocal<Scope> ACTIVE = new ThreadLocal<>();
    private static final StackWalker STACK = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
    private static final class Scope {
        final McSuspendedFarmReturnCoordinator.CommandCall call;
        final ClientPacketListener listener;
        final Connection connection;
        final Thread thread = Thread.currentThread();
        final IssuedFarmCommandOrder order = new IssuedFarmCommandOrder();
        final String command;
        final long serial;private long nativeIngressBaseline=-1;
        Scope(McSuspendedFarmReturnCoordinator.CommandCall call) {
            this.call = call; listener = call.listener(); connection = call.connection(); command = call.command(); serial = call.serial();
        }
        boolean current() {
            try {
                boolean linked=listener.getConnection()==connection;
                return linked && ACTIVE.get() == this && Thread.currentThread() == thread
                        && call.serial() == serial && call.listener() == listener && call.connection() == connection && command.equals(call.command())
                        && call.currentFinalNative();
            } catch (RuntimeException unavailable) { order.refuse(); return false; }
        }
    }
    /** Private construction prevents unscoped callers from declaring successful submission. */
    static final class Submitted {
        private final Scope scope;
        private Submitted(Scope scope) { this.scope = scope; }
        boolean belongs(McSuspendedFarmReturnCoordinator.CommandCall call) { return scope.call == call; }
        boolean normallyReturned() { return scope.order.complete(); }
        /** Captured only at the actual validated Connection body opcode, not command intent or a wrapper return. */
        long nativeIncomingBaseline(){return scope.order.complete()?scope.nativeIngressBaseline:-1;}
        boolean current() {
            try { return normallyReturned() && Thread.currentThread() == scope.thread && scope.call.currentFinalNative(); }
            catch (RuntimeException unavailable) { return false; }
        }
    }
    /** Native wrapper handle, minted only while the actual transformed native method is on the stack. */
    public static final class Invocation {
        private final Scope scope;
        private final Object receiver, packet;
        private final Kind kind;
        private Invocation(Scope s, Object receiver, Object packet, Kind kind) { scope=s; this.receiver=receiver; this.packet=packet; this.kind=kind; }
    }
    public static final class BodyInvocation {
        private final Scope scope;private final Connection connection;private final Packet<?> packet;private final boolean scheduled;private final Channel channel;private final EventLoop loop;private boolean exited;
        private BodyInvocation(Scope scope,Connection connection,Packet<?> packet,boolean scheduled,Channel channel,EventLoop loop){this.scope=scope;this.connection=connection;this.packet=packet;this.scheduled=scheduled;this.channel=channel;this.loop=loop;}
    }
    private static final ThreadLocal<BodyInvocation> BODY=new ThreadLocal<>();
    private enum Kind { COMMAND, PACKET, TRANSPORT }
    private IssuedFarmCommandFence() {}
    static Submitted send(McSuspendedFarmReturnCoordinator.CommandCall call) {
        if (call == null || ACTIVE.get() != null || !call.currentFinalNative() || call.listener() == null
                || call.connection() == null || !IssuedFarmCommandOrder.validCommand(call.command())) return null;
        var scope = new Scope(call); ACTIVE.set(scope);
        try {
            if (!scope.current()) return null;
            scope.listener.sendCommand(scope.command);
            return scope.current() && scope.order.complete() ? new Submitted(scope) : null;
        } finally { BODY.remove(); ACTIVE.remove(); }
    }
    private static boolean nativeCaller(Class<?> owner) {
        return STACK.walk(frames -> frames.filter(f -> f.getDeclaringClass() != IssuedFarmCommandFence.class)
                .findFirst().map(f -> f.getDeclaringClass() == owner).orElse(false));
    }
    public static Invocation commandEntered(ClientPacketListener listener, String command) {
        var s=ACTIVE.get(); if (s==null) return null;
        if (!nativeCaller(ClientPacketListener.class)) { s.order.refuse(); return null; }
        s.order.commandEntered();
        if (listener!=s.listener || !s.command.equals(command) || !s.current()) s.order.refuse();
        return new Invocation(s,listener,null,Kind.COMMAND);
    }
    /** Called only at the actual sendCommand packet-send opcode, never packet constructor or a free-standing send. */
    public static void commandPacket(ClientPacketListener listener, Packet<?> packet) {
        var s=ACTIVE.get(); if (s==null) return;
        if (!nativeCaller(ClientPacketListener.class)) { s.order.refuse(); return; }
        String text=packet instanceof ServerboundChatCommandPacket p?p.command()
                :packet instanceof ServerboundChatCommandSignedPacket p?p.command():null;
        if (listener!=s.listener || !s.command.equals(text) || !s.current()) s.order.refuse();
        s.order.bind(packet);
    }
    public static Invocation packetEntered(Object listener, Packet<?> packet) {
        var s=ACTIVE.get(); if (s==null) return null;
        if (!nativeCaller(ClientCommonPacketListenerImpl.class)) { s.order.refuse(); return null; }
        s.order.packetEntered(packet);
        if (listener!=s.listener || !s.current()) s.order.refuse();
        return new Invocation(s,listener,packet,Kind.PACKET);
    }
    public static Invocation transportEntered(Connection connection, Packet<?> packet) {
        var s=ACTIVE.get(); if (s==null) return null;
        if (!nativeCaller(Connection.class)) { s.order.refuse(); return null; }
        s.order.transportEntered(packet);
        if (connection!=s.connection || !s.current()) s.order.refuse();
        return new Invocation(s,connection,packet,Kind.TRANSPORT);
    }
    /** Connected leaf follows the native queue flush. A refusal cannot be retried or adopted later. */
    public static boolean allowLeaf(Connection connection, Packet<?> packet) {
        var s=ACTIVE.get(); if (s==null) return true;
        if (!nativeCaller(Connection.class)) { s.order.refuse(); return false; }
        if (!s.order.matches(packet)) { s.order.refuse(); return true; }
        if (connection!=s.connection || !connection.isConnected() || !s.current()) { s.order.refuse(); return false; }
        return s.order.leaf(packet);
    }
    public static boolean scopedBodyRefused(BodyInvocation body){var s=ACTIVE.get();return s!=null&&(body==null||BODY.get()!=body||body.scope!=s);}
    /** Privately bind the exact actual native branch opcode; ordinary wrapper return alone is insufficient. */
    public static BodyInvocation bodyEntered(Connection connection,Packet<?> packet,boolean scheduled,EventLoop actualLoop){
        var s=ACTIVE.get();if(s==null)return null;
        var channel=((IssuedFarmConnectionAccessor)(Object)connection).veltraph$issuedFarmChannel();var loop=channel==null?null:channel.eventLoop();boolean connected=connection.isConnected();
        if(!nativeCaller(Connection.class)||channel==null||loop==null||(scheduled&&actualLoop!=loop)||BODY.get()!=null||connection!=s.connection||!s.order.matches(packet)||!connected||!s.current()||!s.order.bodyEntered(packet)){s.order.refuse();return null;}
        s.nativeIngressBaseline=IssuedFarmIncomingOrigin.nativeIngressSerial();
        var body=new BodyInvocation(s,connection,packet,scheduled,channel,loop);BODY.set(body);return body;
    }
    /** The direct branch must reach a real channel write opcode and normally return that operation. */
    public static boolean allowDirectWrite(Connection connection,Channel channel,Object packet){
        var s=ACTIVE.get();if(s==null)return true;var body=BODY.get();boolean connected=connection.isConnected();
        if(!nativeCaller(Connection.class)||body==null||body.scope!=s||body.scheduled||body.exited||body.connection!=connection||body.channel!=channel||((IssuedFarmConnectionAccessor)(Object)connection).veltraph$issuedFarmChannel()!=channel||body.packet!=packet||connection!=s.connection||!connected||!s.current()){s.order.refuse();return false;}return true;
    }
    public static void directWriteReturned(Connection connection,Channel channel,Object packet,boolean normal){
        var s=ACTIVE.get();if(s==null)return;var body=BODY.get();
        if(!nativeCaller(Connection.class)||body==null||body.scope!=s||body.scheduled||body.connection!=connection||body.channel!=channel||((IssuedFarmConnectionAccessor)(Object)connection).veltraph$issuedFarmChannel()!=channel||body.packet!=packet||body.exited||!s.current())s.order.refuse();
        s.order.directWriteReturned(packet,normal);
    }
    public static void bodyExited(BodyInvocation body,Connection connection,Packet<?> packet,boolean normal){
        if(body==null)return;var s=body.scope;
        try{if(!nativeCaller(Connection.class)||BODY.get()!=body||ACTIVE.get()!=s||body.exited||body.connection!=connection||body.packet!=packet||((IssuedFarmConnectionAccessor)(Object)connection).veltraph$issuedFarmChannel()!=body.channel||!s.current())s.order.refuse();body.exited=true;s.order.bodyExited(packet,normal,body.scheduled);}
        finally{if(BODY.get()==body)BODY.remove();}
    }
    /** Observes the real sendPacket method normal exit; no wire or server receipt is inferred. */
    public static void dispatchExited(Connection connection, Packet<?> packet, boolean normal) {
        var s=ACTIVE.get(); if (s==null) return;
        if (!nativeCaller(Connection.class) || connection!=s.connection || !s.current()) s.order.refuse();
        s.order.dispatchExited(packet,normal);
    }
    public static boolean allowQueue(Connection connection, Packet<?> packet) {
        var s=ACTIVE.get(); if (s==null) return true;
        if (!nativeCaller(Connection.class)) { s.order.refuse(); return false; }
        boolean exact=s.connection==connection && s.order.matches(packet);
        s.order.refuse(); return !exact;
    }
    public static void exited(Invocation invocation, Object receiver, Object packet, boolean normal) {
        if (invocation==null) return;
        var s=invocation.scope;
        Class<?> owner=switch(invocation.kind) { case COMMAND -> ClientPacketListener.class; case PACKET -> ClientCommonPacketListenerImpl.class; case TRANSPORT -> Connection.class; };
        if (!nativeCaller(owner) || ACTIVE.get()!=s || invocation.receiver!=receiver || invocation.packet!=packet || !s.current()) s.order.refuse();
        switch(invocation.kind) {
            case COMMAND -> s.order.commandExited(normal);
            case PACKET -> s.order.packetExited(packet,normal);
            case TRANSPORT -> s.order.transportExited(packet,normal);
        }
    }
}
