package dev.veltraph.client.observation.nativeinput;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import static dev.veltraph.client.observation.nativeinput.NativePlayerBirthWireOrder.Kind;
import static dev.veltraph.client.observation.nativeinput.NativePlayerBirthWireOrder.Node;

/** Actual native transport and transform custody. Public methods are observers, never enrollment APIs. */
public final class NativePlayerBirthWireSource {
    private NativePlayerBirthWireSource() {}
    private static final StackWalker WALKER = walker();
    private static StackWalker walker() {
        try { return StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE); }
        catch (RuntimeException | Error unavailable) { return null; }
    }
    private static final ThreadLocal<Phase> ACTIVE = new ThreadLocal<>();
    private static final ThreadLocal<Incoming> INCOMING = new ThreadLocal<>();
    private static final ThreadLocal<LocalReadBody> LOCAL_READ_BODY = new ThreadLocal<>();
    private static final ThreadLocal<LocalReadEdge> LOCAL_READ_EDGE = new ThreadLocal<>();
    private static final ArrayDeque<Alias> ALIASES = new ArrayDeque<>();
    private static final ArrayDeque<Queued> QUEUED = new ArrayDeque<>();
    private static final ArrayDeque<ChannelNodes> CHANNEL_NODES = new ArrayDeque<>();
    private static final ArrayDeque<WeakReference<Object>> CLOSED_CHANNELS = new ArrayDeque<>();
    private record ChannelNodes(WeakReference<Object> channel, java.util.ArrayList<WeakReference<Node>> nodes) {}

    private enum Role { DELIVERY, CUMULATION, OUTPUT }
    private record Alias(WeakReference<Object> object, Object owner, Object channel, Role role,
                         Node node, NativePlayerBirthWireOrder.Delivery delivery, NativePlayerBirthWireBytes bytes, Object bytesTarget) {}
    private record Queued(Object owner, Object queue, Object object, Object channel,
                          NativePlayerBirthWireOrder.Queued custody, NativePlayerBirthWireBytes bytes, Object bytesTarget) {}

    public static final class Phase {
        private final Phase previous;
        private final String stage;
        private final Object owner, context, input, channel, list;
        private Object currentInput;
        private final Node node;
        private Node inputSource;
        private Alias claimedInputDelivery;
        private FabricPassThrough fabricPassThrough;
        private LocalReadBody localReadBody;
        private RawRead pendingRawRead;
        private NativeRawPrimitiveOrder.Received rawReceived;
        private NativePlayerBirthWireBytes expected, producedBytes;
        private Object expectedTarget, producedBytesTarget;
        private Object readObject, readOwner, produced;
        private Node readSource, producedSource;
        private boolean outputAdded;
        private int decodeStart=-1;
        private byte[] transformedBytes;
        private Object transformer;
        private java.nio.ByteBuffer inflateInput;
        private int inflateInputStart;
        private byte[] inflateExpected;
        private NativeCryptoPrimitiveOrder.CipherUpdate cryptoCipher;
        private NativeCryptoPrimitiveOrder.Inflate cryptoInflate;
        private Phase(String stage, Object owner, Object context, Object input, Object channel,
                      Object list, Node inputSource, Kind kind) {
            this.previous = ACTIVE.get();
            this.stage = stage;
            this.owner = owner;
            this.context = context;
            this.input = input;
            this.currentInput = input;
            this.channel = channel;
            this.list = list;
            this.inputSource = inputSource;
            this.node = inputSource == null && kind == Kind.TRANSPORT
                    ? new Node(kind, owner, channel, input, list)
                    : new Node(kind, owner, channel, input, list, inputSource);
        }
    }

    static final class Certificate {
        private final Node node;
        Certificate(Node node) { this.node = node; }
        Node nodeForNativeConsumer() { return node; }
        boolean viable() { return node != null && node.viable(); }
        boolean complete() { return node != null && node.complete(); }
    }

    private static boolean caller(String suffix, String... classes) {
        return WALKER != null && WALKER.walk(frames -> frames
                .filter(frame -> frame.getDeclaringClass() != NativePlayerBirthWireSource.class)
                .findFirst().map(frame -> {
                    if (!frame.getMethodName().endsWith(suffix)) return false;
                    for (String name : classes) if (frame.getDeclaringClass().getName().equals(name)
                            && frame.getDeclaringClass().getClassLoader() == (name.startsWith("io.netty.")
                            ? io.netty.buffer.ByteBuf.class.getClassLoader() : net.minecraft.network.Connection.class.getClassLoader())) return true;
                    return false;
                }).orElse(false));
    }

    private static boolean transportCaller(String suffix) {
        return caller(suffix, "io.netty.channel.nio.AbstractNioByteChannel$NioByteUnsafe",
                "io.netty.channel.kqueue.AbstractKQueueStreamChannel$KQueueStreamUnsafe",
                "io.netty.channel.local.LocalChannel");
    }

    /** Native readInbound lifetime only; this record does not certify bytes or packet origins. */
    public static final class LocalReadBody {
        private final LocalReadBody previous;
        private final Object owner;
        private final Thread thread;
        private final io.netty.channel.EventLoop loop;
        private final Phase entryActive;
        private Phase transport;
        private boolean closed, nativeFinishObserved, nativeNormal;
        private LocalReadBody(Object owner, io.netty.channel.EventLoop loop) {
            this.previous = LOCAL_READ_BODY.get(); this.owner = owner; this.loop = loop;
            this.thread = Thread.currentThread(); this.entryActive = ACTIVE.get();
        }
        private boolean live() {
            return !closed && thread == Thread.currentThread() && owner instanceof io.netty.channel.local.LocalChannel channel
                    && channel.eventLoop() == loop && loop.inEventLoop();
        }
        private void observeNativeFinish(Phase actual, boolean normal, boolean wasOriginalCurrent) {
            if (transport != actual || nativeFinishObserved || closed || LOCAL_READ_BODY.get() != this || !wasOriginalCurrent
                    || ACTIVE.get() != actual.previous) { actual.node.refuse(); return; }
            nativeFinishObserved = true; nativeNormal = normal;
        }
    }

    /** Admission exists only inside the original doBeginRead private readInbound opcode (PC51). */
    public static final class LocalReadEdge {
        private final LocalReadEdge previous;
        private final Object owner;
        private final Phase parent;
        private final LocalReadBody parentBody;
        private final NativeLocalReadOrder order;
        private final int admittedPc51Count;
        private LocalReadBody childBody;
        private Phase child;
        private boolean closed;
        private LocalReadEdge(Object owner, Phase parent, LocalReadBody parentBody) {
            this.previous = LOCAL_READ_EDGE.get(); this.owner = owner; this.parent = parent; this.parentBody = parentBody;
            this.order = new NativeLocalReadOrder(owner, parent, parentBody);
            this.admittedPc51Count = previous == null ? 1 : previous.admittedPc51Count + 1;
        }
        private boolean boundImmediateChild(Phase actualChild, LocalReadBody actualBody) {
            return !closed && child == actualChild && childBody == actualBody && actualChild != null && actualBody != null
                    && owner == actualBody.owner && actualBody.previous == parentBody && actualChild.previous == parent
                    && actualChild.localReadBody == actualBody && actualBody.transport == actualChild
                    && !actualBody.closed && !actualBody.nativeFinishObserved && order.activeChild(actualChild, actualBody);
        }
        private boolean parentCurrent() {
            return !closed && LOCAL_READ_EDGE.get() == this && ACTIVE.get() == parent && parentBody.live()
                    && !parentBody.nativeFinishObserved && parentBody.transport == parent && parent.localReadBody == parentBody
                    && parent.owner == owner && parent.stage.equals("transport")
                    && parent.readObject == null && parent.readOwner == null && parent.readSource == null
                    && parent.pendingRawRead == null && INCOMING.get() == null && parent.node.viable()
                    && admittedPc51Count >= 1 && admittedPc51Count <= 8
                    && (previous == null || previous.boundImmediateChild(parent, parentBody));
        }
        private Phase claim(Object actualOwner, Phase originalActive, Phase originalMatchingAncestor) {
            var body = LOCAL_READ_BODY.get();
            if (!order.claim(actualOwner, originalActive, parentBody, body)) return null;
            if (originalActive != parent || originalMatchingAncestor != parent || actualOwner != owner || !parentCurrent()
                    || body == null || body == parentBody || body.previous != parentBody || body.owner != owner
                    || body.entryActive != parent || body.transport != null || !body.live()) { order.refuse(); return null; }
            var phase = new Phase("transport", owner, null, owner, owner, null, parent.node, Kind.TRANSPORT);
            phase.localReadBody = body; body.transport = phase; childBody = body; child = phase;
            if (!order.bind(phase)) { phase.node.refuse(); return null; }
            ACTIVE.set(phase);
            return phase;
        }
        private void reject() { order.refuse(); if (child != null) child.node.refuse(); parent.node.refuse(); }
    }

    public static LocalReadBody enterLocalReadBody(Object owner) {
        if (!caller("veltraph$birthWireTransport", "io.netty.channel.local.LocalChannel")
                || !(owner instanceof io.netty.channel.local.LocalChannel channel)
                || owner.getClass() != io.netty.channel.local.LocalChannel.class) return null;
        var loop = channel.eventLoop(); if (loop == null || !loop.inEventLoop()) return null;
        var body = new LocalReadBody(owner, loop); LOCAL_READ_BODY.set(body); return body;
    }

    public static void exitLocalReadBody(LocalReadBody body) {
        if (!caller("veltraph$birthWireTransport", "io.netty.channel.local.LocalChannel") || body == null) return;
        if (body.closed || LOCAL_READ_BODY.get() != body) { if (body.transport != null) body.transport.node.refuse(); return; }
        body.closed = true;
        if (body.previous == null) LOCAL_READ_BODY.remove(); else LOCAL_READ_BODY.set(body.previous);
    }

    public static LocalReadEdge enterLocalReadEdge(Object handler, Object originalReceiver) {
        if (!caller("veltraph$birthWireNestedRead", "io.netty.channel.local.LocalChannel") || handler != originalReceiver) return null;
        var previous = LOCAL_READ_EDGE.get();
        var parent = ACTIVE.get(); var body = LOCAL_READ_BODY.get();
        if (parent == null || body == null || body.nativeFinishObserved || !body.live() || body.owner != originalReceiver || body.transport != parent
                || parent.localReadBody != body || parent.owner != originalReceiver || !parent.stage.equals("transport")
                || parent.readObject != null || parent.readOwner != null || parent.readSource != null
                || parent.pendingRawRead != null || INCOMING.get() != null || !parent.node.viable()) return null;
        if (previous != null && (previous.admittedPc51Count >= 8 || previous.owner != originalReceiver
                || !previous.boundImmediateChild(parent, body))) return null;
        var edge = new LocalReadEdge(originalReceiver, parent, body); LOCAL_READ_EDGE.set(edge); return edge;
    }

    public static void exitLocalReadEdge(LocalReadEdge edge, boolean originalNormal) {
        if (!caller("veltraph$birthWireNestedRead", "io.netty.channel.local.LocalChannel") || edge == null) return;
        if (edge.closed || LOCAL_READ_EDGE.get() != edge) { edge.reject(); return; }
        try {
            if (edge.child == null || edge.childBody == null || !edge.childBody.closed || !edge.childBody.nativeFinishObserved
                    || edge.childBody.nativeNormal != originalNormal || LOCAL_READ_BODY.get() != edge.parentBody
                    || !edge.parentCurrent() || !edge.order.returned(edge.child, ACTIVE.get(), LOCAL_READ_BODY.get(), originalNormal)) edge.reject();
        } catch (RuntimeException | Error unavailable) { edge.reject(); throw unavailable; }
        finally {
            edge.closed = true;
            if (LOCAL_READ_EDGE.get() == edge) {
                if (edge.previous == null) LOCAL_READ_EDGE.remove(); else LOCAL_READ_EDGE.set(edge.previous);
            } else edge.reject();
        }
    }

    public static Phase beginTransport(Object owner) {
        if (!transportCaller("veltraph$birthWireTransport") || owner == null) return null;
        var previous = ACTIVE.get();
        for (var p = previous; p != null; p = p.previous) if (p.owner == owner && p.stage.equals("transport")) {
            var edge = LOCAL_READ_EDGE.get();
            if (edge != null) {
                try { var child = edge.claim(owner, previous, p); if (child != null) return child; }
                catch (RuntimeException | Error authorityFailure) { p.node.refuse(); edge.reject(); throw authorityFailure; }
            }
            p.node.refuse();
            return null;
        }
        var result = new Phase("transport", owner, null, owner, owner, null, null, Kind.TRANSPORT);
        var body = LOCAL_READ_BODY.get();
        try {
            if (body != null && body.owner == owner && body.transport == null && body.entryActive == previous && body.live()) {
                result.localReadBody = body; body.transport = result;
            }
        } catch (RuntimeException | Error unavailableBody) { /* Ordinary original transport remains unchanged; no edge affiliation is installed. */ }
        ACTIVE.set(result);
        return result;
    }

    public static void readReturned(Object nativeChannel, Object buffer, int bytes) {
        if (!transportCaller("veltraph$birthWireRead")) return;
        var p = ACTIVE.get();
        if (p == null || !p.stage.equals("transport")) return;
        if (bytes <= 0) return;
        if (nativeChannel == null || buffer == null || p.readObject != null) {
            p.node.refuse();
            return;
        }
        var raw = p.pendingRawRead;
        p.pendingRawRead = null;
        var received = raw == null || raw.order == null ? null : raw.order.take(nativeChannel, buffer, bytes);
        if (received == null || !raw.node.viable()) { p.node.refuse(); return; }
        var read = new Node(Kind.TRANSPORT, p.owner, nativeChannel, buffer, null, p.node, raw.node);
        read.returned(true);
        if (!bindChannel(nativeChannel, p.node) || !bindChannel(nativeChannel, read)) { p.node.refuse(); return; }
        p.readObject = buffer; p.readOwner = nativeChannel; p.readSource = read; p.rawReceived = received;
        p.expected = new NativePlayerBirthWireBytes(received.start, received.bytes());
        p.expectedTarget = received.target;
    }

    public static void localPolled(Object queue, Object message) {
        if (!caller("veltraph$birthWirePoll", "io.netty.channel.local.LocalChannel")) return;
        var p = ACTIVE.get();
        if (p == null || !p.stage.equals("transport") || message == null) return;
        if (p.readObject != null) { p.node.refuse(); return; }
        NativeRawPrimitiveOrder.Received bytes = null;
        Node insertion = null;
        synchronized (NativePlayerBirthWireSource.class) {
            var it = RAW_INSERTIONS.iterator();
            while (it.hasNext()) {
                var next = it.next();
                if (!next.order.viable()) { it.remove(); continue; }
                if (next.order.wrapper == message) {
                    bytes = next.order.poll(p.owner, queue, message);
                    insertion = next.node;
                    it.remove(); break;
                }
            }
        }
        if (bytes == null || insertion == null) { p.node.refuse(); return; }
        var read = new Node(Kind.TRANSPORT, p.owner, p.owner, message, queue, p.node, insertion);
        read.returned(true);
        if (!bindChannel(p.owner, p.node) || !bindChannel(p.owner, read)) { p.node.refuse(); return; }
        p.readObject = message; p.readOwner = p.owner; p.readSource = read; p.rawReceived = bytes;
        p.expected = new NativePlayerBirthWireBytes(bytes.start, bytes.bytes());
        p.expectedTarget = bytes.target;
    }

    public static Phase beginTransportDelivery(ChannelPipeline pipeline, Object message) {
        if (!transportCaller("veltraph$birthWireFire")) return null;
        var p = ACTIVE.get();
        if (p == null || !p.stage.equals("transport")) return null;
        if (p.readObject != message || p.readSource == null) { p.node.refuse(); return null; }
        Object channel = pipeline.channel();
        if (p.readOwner != channel || !p.node.viable()) { p.node.refuse(); return null; }
        var delivery = new Phase("delivery", pipeline, null, message, channel, null, p.readSource, Kind.DELIVERY);
        p.readObject = null;
        p.readOwner = null;
        p.readSource = null;
        delivery.expected = p.expected; delivery.expectedTarget = p.expectedTarget;
        if (message instanceof io.netty.buffer.ByteBuf && !matchesPhysical(message, delivery.expected)) { p.node.refuse(); return null; }
        publish(message, pipeline, channel, Role.DELIVERY, delivery.node, delivery.expected, delivery.expectedTarget);
        ACTIVE.set(delivery);
        return delivery;
    }

    public static final class Incoming {
        private final Incoming previous;private final Phase delivery;private final Object handler,context,message;
        private final NativePlayerBirthWireOrder.HandlerEdge edge;
        private Incoming(Incoming previous,Phase delivery,Object handler,Object context,Object message){this.previous=previous;this.delivery=delivery;this.handler=handler;this.context=context;this.message=message;edge=new NativePlayerBirthWireOrder.HandlerEdge(handler,context,message,delivery.node);}
    }
    public static Incoming actualHandlerEntered(Object firingContext,Object handler,Object recipientContext,Object message){
        if(!caller("veltraph$birthUpstream","io.netty.channel.AbstractChannelHandlerContext")
                &&!caller("veltraph$birthUpstreamDuplex","io.netty.channel.AbstractChannelHandlerContext"))return null;
        var p=ACTIVE.get();var previous=INCOMING.get();
        if(p==null||!p.stage.equals("delivery")||p.input!=message||handler==null||recipientContext==null)return null;
        boolean exact;
        if(previous!=null&&previous.delivery==p)exact=firingContext==previous.context&&message==previous.message;
        else if(p.context!=null)exact=firingContext==p.context;
        else exact=firingContext instanceof ChannelHandlerContext context
                &&firingContext.getClass().getName().equals("io.netty.channel.DefaultChannelPipeline$HeadContext")
                &&firingContext.getClass().getClassLoader()==io.netty.buffer.ByteBuf.class.getClassLoader()
                &&context.pipeline()==p.owner;
        if(!exact||!p.node.viable()){p.node.refuse();return null;}
        var result=new Incoming(previous,p,handler,recipientContext,message);INCOMING.set(result);return result;
    }
    public static void actualHandlerExited(Incoming incoming,boolean normal){
        if(!caller("veltraph$birthUpstream","io.netty.channel.AbstractChannelHandlerContext")
                &&!caller("veltraph$birthUpstreamDuplex","io.netty.channel.AbstractChannelHandlerContext")||incoming==null)return;
        if(INCOMING.get()!=incoming||!normal)incoming.delivery.node.refuse();
        if(INCOMING.get()==incoming){if(incoming.previous==null)INCOMING.remove();else INCOMING.set(incoming.previous);}
    }

    public static Phase beginRead(Object owner, ChannelHandlerContext context, Object input) {
        if (!caller("veltraph$birthWireChannelRead", "io.netty.handler.codec.ByteToMessageDecoder",
                "io.netty.handler.codec.MessageToMessageDecoder", "io.netty.handler.flow.FlowControlHandler",
                "net.minecraft.network.LocalFrameDecoder", "net.minecraft.network.MonitoredLocalFrameDecoder")) return null;
        if (owner == null || context == null || input == null) return null;
        var incoming=INCOMING.get();
        if(incoming==null||incoming.handler!=owner||incoming.context!=context||incoming.message!=input||ACTIVE.get()!=incoming.delivery){
            if(incoming!=null)incoming.delivery.node.refuse();return null;
        }
        if(!incoming.edge.claim(owner,context,input,incoming.delivery.node))return null;
        Object channel = incoming.delivery.channel;
        var receipt = claimDelivery(input, channel);
        Node source = receipt == null ? null : receipt.node;
        var read = new Phase("read", owner, context, input, channel, null, source, Kind.READ);
        read.claimedInputDelivery = receipt;
        read.expected = receipt == null ? null : receipt.bytes; read.expectedTarget = receipt == null ? null : receipt.bytesTarget;
        if (input instanceof io.netty.buffer.ByteBuf && (read.expectedTarget != input || !matchesPhysical(input, read.expected))) read.node.refuse();
        ACTIVE.set(read);
        return read;
    }

    public static final class Cumulation {
        private final Phase read;
        private final Object old, input;
        private final Node oldSource;
        private final boolean originalEmpty;
        private final NativePlayerBirthWireBytes combinedBytes;
        private Cumulation(Phase read, Object old, Object input, Node oldSource, boolean originalEmpty, NativePlayerBirthWireBytes combinedBytes) {
            this.read = read; this.old = old; this.input = input; this.oldSource = oldSource; this.originalEmpty = originalEmpty; this.combinedBytes = combinedBytes;
        }
    }

    public static Cumulation beforeCumulate(Object actualCumulator, Object old, Object input) {
        if (!caller("veltraph$birthWireCumulate", "io.netty.handler.codec.ByteToMessageDecoder")) return null;
        var p = ACTIVE.get();
        if (p == null || !p.stage.equals("read")) return null;
        Object originalField = ((dev.veltraph.client.mixin.NativePlayerBirthCumulationAccessor)p.owner).veltraph$birthWireCumulation();
        boolean empty = originalField == null;
        boolean supported = actualCumulator == io.netty.handler.codec.ByteToMessageDecoder.MERGE_CUMULATOR
                || actualCumulator == io.netty.handler.codec.ByteToMessageDecoder.COMPOSITE_CUMULATOR;
        if (!supported || input != p.currentInput || old != (empty ? Unpooled.EMPTY_BUFFER : originalField)) {
            p.node.refuse();
            return null;
        }
        Node previous = empty ? null : lookup(old, p.owner, p.channel, Role.CUMULATION);
        var oldReceipt = empty ? null : alias(old, p.owner, p.channel, Role.CUMULATION);
        if (!matchesPhysical(input, p.expected) || !empty && (oldReceipt == null || !matchesPhysical(old, oldReceipt.bytes))) { p.node.refuse(); return null; }
        var oldBuffer = empty ? null : (io.netty.buffer.ByteBuf) old;
        var combined = NativePlayerBirthWireBytes.cumulated(oldReceipt == null ? null : oldReceipt.bytes,
                oldBuffer == null ? 0 : oldBuffer.readerIndex(), empty, p.expected,
                ((io.netty.buffer.ByteBuf) input).readerIndex(), 0);
        if (combined == null) { p.node.refuse(); return null; }
        return new Cumulation(p, old, input, previous, empty, combined);
    }

    public static void cumulated(Cumulation before, Object result) {
        if (!caller("veltraph$birthWireCumulate", "io.netty.handler.codec.ByteToMessageDecoder")) return;
        if (before == null) return;
        var p = ACTIVE.get();
        if (p != before.read || result == null || p.currentInput != before.input) {
            before.read.node.refuse();
            return;
        }
        Node merged = NativePlayerBirthWireOrder.cumulated(p.owner, p.channel, result, p.node,
                before.oldSource, before.originalEmpty, p.inputSource);
        if (merged == null) {
            synchronized (NativePlayerBirthWireSource.class) {
                ALIASES.removeIf(a -> a.role == Role.CUMULATION && a.owner == p.owner && a.channel == p.channel);
            }
        } else {
            var expected = before.combinedBytes.rebasedUnread(0, ((io.netty.buffer.ByteBuf) result).readerIndex());
            if (!matchesPhysical(result, expected)) { p.node.refuse(); merged.refuse(); return; }
            publish(result, p.owner, p.channel, Role.CUMULATION, merged, expected, result);
        }
    }

    public static final class Compaction {
        private final Phase read; private final Object buffer; private final Alias source; private final NativePlayerBirthWireBytes unread;
        private Compaction(Phase read,Object buffer,Alias source,NativePlayerBirthWireBytes unread){this.read=read;this.buffer=buffer;this.source=source;this.unread=unread;}
    }
    public static Compaction beforeCompact(Object decoder){
        if(!caller("veltraph$birthWireCompact","io.netty.handler.codec.ByteToMessageDecoder"))return null;
        var p=ACTIVE.get();if(p==null||!p.stage.equals("read")||p.owner!=decoder)return null;
        var buffer=((dev.veltraph.client.mixin.NativePlayerBirthCumulationAccessor)decoder).veltraph$birthWireCumulation();
        var original=alias(buffer,decoder,p.channel,Role.CUMULATION);
        if(buffer==null||original==null||!matchesPhysical(buffer,original.bytes)){p.node.refuse();return null;}
        var unread=original.bytes.rebasedUnread(buffer.readerIndex(),0);
        if(unread==null){p.node.refuse();return null;}return new Compaction(p,buffer,original,unread);
    }
    public static void compacted(Compaction before){
        if(!caller("veltraph$birthWireCompact","io.netty.handler.codec.ByteToMessageDecoder")||before==null)return;
        var p=ACTIVE.get();var buffer=((dev.veltraph.client.mixin.NativePlayerBirthCumulationAccessor)before.read.owner).veltraph$birthWireCumulation();
        if(p!=before.read||buffer!=before.buffer){before.read.node.refuse();return;}
        var expected=before.unread.rebasedUnread(0,buffer.readerIndex());
        if(!matchesPhysical(buffer,expected)){p.node.refuse();return;}
        var node=new Node(Kind.CUMULATION,p.owner,p.channel,buffer,null,p.node,before.source.node);node.returned(true);
        publish(buffer,p.owner,p.channel,Role.CUMULATION,node,expected,buffer);
    }

    public static Phase beginDecode(Object owner, ChannelHandlerContext context, Object buffer, Object list) {
        if (!caller("veltraph$birthWireDecode", "io.netty.handler.codec.ByteToMessageDecoder",
                "io.netty.handler.codec.MessageToMessageDecoder")) return null;
        var read = ACTIVE.get();
        if (read == null || !read.stage.equals("read") || read.owner != owner || read.context != context || list == null) return null;
        Alias cumulative = owner instanceof io.netty.handler.codec.ByteToMessageDecoder ? alias(buffer, owner, read.channel, Role.CUMULATION) : null;
        Node source = owner instanceof io.netty.handler.codec.ByteToMessageDecoder ? cumulative == null ? null : cumulative.node : buffer == read.currentInput ? read.inputSource : null;
        var joined = new Node(Kind.DECODE, owner, read.channel, buffer, list, source, read.node); joined.returned(true);
        var decode = new Phase("decode", owner, context, buffer, read.channel, list, joined, Kind.DECODE);
        decode.expected = cumulative == null ? read.expected : cumulative.bytes;
        decode.expectedTarget = cumulative == null ? read.expectedTarget : cumulative.bytesTarget;
        if (buffer instanceof io.netty.buffer.ByteBuf b) {
            decode.decodeStart = b.readerIndex();
            if (decode.expectedTarget != buffer || !matchesPhysical(buffer, decode.expected)) decode.node.refuse();
        }
        ACTIVE.set(decode);
        return decode;
    }

    /** Private one-use custody for the actual Fabric plain same-packet List.add opcode. */
    public static final class FabricPassThrough {
        private final Phase decode, read;
        private final Incoming incoming;
        private final Alias claimedInput;
        private final Object owner, packet, list, channel;
        private final ChannelHandlerContext context;
        private final io.netty.util.concurrent.EventExecutor loop;
        private boolean completed, refused;
        private FabricPassThrough(Phase decode, Phase read, Incoming incoming, Alias claimedInput,
                                  Object owner, ChannelHandlerContext context, Object packet, Object list,
                                  Object channel, io.netty.util.concurrent.EventExecutor loop) {
            this.decode = decode; this.read = read; this.incoming = incoming; this.claimedInput = claimedInput;
            this.owner = owner; this.context = context; this.packet = packet; this.list = list;
            this.channel = channel; this.loop = loop;
        }
        private void refuse() { refused = true; decode.node.refuse(); }
        private boolean originalSourceCurrent() {
            return !refused && ACTIVE.get() == decode && decode.fabricPassThrough == this
                    && decode.previous == read && read.previous == incoming.delivery
                    && INCOMING.get() == incoming && incoming.handler == owner && incoming.context == context
                    && incoming.message == packet && decode.stage.equals("decode") && read.stage.equals("read")
                    && decode.owner == owner && read.owner == owner && decode.context == context && read.context == context
                    && decode.input == packet && read.input == packet && read.currentInput == packet && decode.list == list
                    && decode.channel == channel && read.channel == channel && claimedInput.channel == channel
                    && read.claimedInputDelivery == claimedInput && read.inputSource == claimedInput.node
                    && claimedInput.node == incoming.delivery.node && claimedInput.role == Role.DELIVERY
                    && claimedInput.delivery != null && claimedInput.object.get() == packet
                    && context.channel() == channel && context.executor() == loop && loop.inEventLoop()
                    && read.node.viable() && decode.node.viable();
        }
    }

    public static FabricPassThrough beginFabricPassThrough(Object owner, ChannelHandlerContext context,
                                                           Object input, Object originalList, Object addedObject, Object addedList) {
        if (!caller("veltraph$birthFabricPassThroughAdd", "net.fabricmc.fabric.impl.networking.splitter.FabricPacketMerger")) return null;
        var decode = ACTIVE.get();
        if (decode == null || !decode.stage.equals("decode") || decode.owner != owner || decode.context != context) return null;
        if (decode.fabricPassThrough != null) { decode.fabricPassThrough.refuse(); return null; }
        if (owner == null || !owner.getClass().getName().equals("net.fabricmc.fabric.impl.networking.splitter.FabricPacketMerger")
                || owner.getClass().getClassLoader() != net.minecraft.network.Connection.class.getClassLoader()
                || context == null || input == null || !(input instanceof net.minecraft.network.protocol.Packet<?>)
                || input != addedObject || originalList == null || originalList != addedList || decode.input != input
                || decode.list != originalList || decode.produced != null || decode.outputAdded) { decode.node.refuse(); return null; }
        var read = decode.previous;
        var incoming = INCOMING.get();
        if (read == null || !read.stage.equals("read") || incoming == null || read.claimedInputDelivery == null) { decode.node.refuse(); return null; }
        Object channel = context.channel(); var loop = context.executor();
        if (channel == null || loop == null || !loop.inEventLoop()) { decode.node.refuse(); return null; }
        var token = new FabricPassThrough(decode, read, incoming, read.claimedInputDelivery,
                owner, context, input, originalList, channel, loop);
        decode.fabricPassThrough = token;
        try { if (!token.originalSourceCurrent()) { token.refuse(); return null; } }
        catch (RuntimeException | Error unavailable) { token.refuse(); throw unavailable; }
        return token;
    }

    public static void finishFabricPassThrough(FabricPassThrough token, Object owner, ChannelHandlerContext context,
                                              Object input, Object originalList, Object addedObject, Object addedList,
                                              boolean normal, boolean added) {
        if (!caller("veltraph$birthFabricPassThroughAdd", "net.fabricmc.fabric.impl.networking.splitter.FabricPacketMerger") || token == null) return;
        if (token.completed) { token.refuse(); return; }
        token.completed = true;
        try {
            if (!normal || !added || token.owner != owner || token.context != context || token.packet != input
                    || token.packet != addedObject || token.list != originalList || token.list != addedList
                    || token.decode.produced != null || token.decode.outputAdded || !token.originalSourceCurrent()) { token.refuse(); return; }
            var output = new Node(Kind.OUTPUT, token.owner, token.channel, token.packet, token.list,
                    token.decode.node, token.read.node, token.claimedInput.node);
            output.returned(true);
            token.decode.produced = token.packet; token.decode.producedSource = output; token.decode.outputAdded = true;
            publish(token.packet, token.owner, token.channel, Role.OUTPUT, output);
        } catch (RuntimeException | Error unavailable) { token.refuse(); throw unavailable; }
    }

    /** Original decode opcode record; package-only consumer for the exact PacketDecoder wrapper. */
    static Certificate decodeSource(Object owner, Object context, Object buffer, Object list) {
        var p = ACTIVE.get();
        if (p == null || !p.stage.equals("decode") || p.owner != owner || p.context != context
                || p.input != buffer || p.list != list || !p.node.viable()
                || buffer instanceof io.netty.buffer.ByteBuf && !matchesPhysical(buffer, p.expected)) return null;
        return new Certificate(p.node);
    }

    public static void produced(Object originalInput, Object result) {
        if (!caller("veltraph$birthWireProduced", "net.minecraft.network.Varint21FrameDecoder",
                "net.minecraft.network.CompressionDecoder", "net.minecraft.network.CipherDecoder",
                "net.minecraft.network.PacketDecoder", "net.minecraft.network.LocalFrameDecoder",
                "net.minecraft.network.MonitoredLocalFrameDecoder")
                && !caller("veltraph$birthWireProducedInflated", "net.minecraft.network.CompressionDecoder")
                && !caller("veltraph$birthCodecOutput", "net.minecraft.network.PacketDecoder")) return;
        var p = ACTIVE.get();
        if (p == null || !(p.stage.equals("decode") || p.stage.equals("read"))) return;
        if (originalInput != p.input || result == null || p.produced != null) { p.node.refuse(); return; }
        var output = new Node(Kind.OUTPUT, p.owner, p.channel, result, p.list, p.node);
        output.returned(true);
        p.produced = result;
        p.producedSource = output;
    }

    public static void outputAdded(Object list, Object object, boolean added) {
        if (!caller("veltraph$birthWireAdded", "net.minecraft.network.Varint21FrameDecoder",
                "net.minecraft.network.CompressionDecoder", "net.minecraft.network.CipherDecoder",
                "net.minecraft.network.PacketDecoder")
                && !caller("veltraph$birthDecodedListAdd", "net.minecraft.network.PacketDecoder")) return;
        var p = ACTIVE.get();
        if (p == null || !p.stage.equals("decode")) return;
        if (!added || p.list != list || p.produced != object || p.producedSource == null || p.outputAdded) {
            p.node.refuse();
            return;
        }
        p.outputAdded = true;
        publish(object, p.owner, p.channel, Role.OUTPUT, p.producedSource, p.producedBytes, p.producedBytesTarget);
    }

    public static void localUnpacked(Object original, Object result) {
        if (!caller("veltraph$birthWireUnpack", "net.minecraft.network.LocalFrameDecoder",
                "net.minecraft.network.MonitoredLocalFrameDecoder")) return;
        var p = ACTIVE.get();
        if (p == null || !p.stage.equals("read")) return;
        if (p.input != original || !original.getClass().getName().equals("net.minecraft.network.HiddenByteBuf")
                || !(result instanceof io.netty.buffer.ByteBuf) || p.produced != null || result != p.expectedTarget || !matchesPhysical(result, p.expected)) { p.node.refuse(); return; }
        var output = new Node(Kind.LOCAL, p.owner, p.channel, result, null, p.node);
        output.returned(true);
        p.produced = result;
        p.producedSource = output;
        p.producedBytes = p.expected; p.producedBytesTarget = result;
        publish(result, p.owner, p.channel, Role.OUTPUT, output, p.producedBytes, result);
    }

    public static Phase beginOutputDelivery(ChannelHandlerContext context, Object object) {
        if (!caller("veltraph$birthWireOutputFire", "io.netty.handler.codec.ByteToMessageDecoder",
                "io.netty.handler.codec.MessageToMessageDecoder", "net.minecraft.network.LocalFrameDecoder",
                "net.minecraft.network.MonitoredLocalFrameDecoder", "io.netty.handler.flow.FlowControlHandler")) return null;
        var p = ACTIVE.get();
        if (p == null || context == null) return null;
        Object channel = context.channel();
        Alias output = takeOutput(object, p.owner, channel);
        Node source = output == null ? null : output.node;
        if (source == null || !source.viable() || object instanceof io.netty.buffer.ByteBuf && !matchesPhysical(object, output.bytes)) { p.node.refuse(); return null; }
        var joined = new Node(Kind.DELIVERY, p.owner, channel, object, null, source, p.node);
        joined.returned(true);
        var delivery = new Phase("delivery", p.owner, context, object, channel, null, joined, Kind.DELIVERY);
        delivery.expected = output.bytes; delivery.expectedTarget = output.bytesTarget;
        publish(object, p.owner, channel, Role.DELIVERY, delivery.node, delivery.expected, delivery.expectedTarget);
        ACTIVE.set(delivery);
        return delivery;
    }

    public static void offered(Object queue, Object object, boolean result) {
        if (!caller("veltraph$birthWireOffer", "io.netty.handler.flow.FlowControlHandler",
                "io.netty.handler.codec.ByteToMessageDecoder")) return;
        var p = ACTIVE.get();
        if (p == null || !p.stage.equals("read")) return;
        if (!result || object != p.input || queue == null || p.inputSource == null) { p.node.refuse(); return; }
        var source = new Node(Kind.QUEUE, p.owner, p.channel, object, queue, p.node, p.inputSource);
        source.returned(true);
        synchronized (NativePlayerBirthWireSource.class) {
            if (QUEUED.size() >= 4096) { p.node.refuse(); return; }
            QUEUED.addLast(new Queued(p.owner, queue, object, p.channel,
                    new NativePlayerBirthWireOrder.Queued(p.owner, queue, object, p.channel, source), p.expected, p.expectedTarget));
        }
    }

    public static void polled(Object queue, Object actual) {
        if (!caller("veltraph$birthWireQueuePoll", "io.netty.handler.flow.FlowControlHandler",
                "io.netty.handler.codec.ByteToMessageDecoder")) return;
        var p = ACTIVE.get();
        if (p == null || queue == null || actual == null) return;
        Node source = null;
        NativePlayerBirthWireBytes expected = null; Object target = null;
        synchronized (NativePlayerBirthWireSource.class) {
            var it = QUEUED.iterator();
            while (it.hasNext()) {
                var q = it.next();
                if (q.owner == p.owner && q.queue == queue && q.object == actual && q.channel == p.channel) {
                    source = q.custody.poll(p.owner, queue, actual, p.channel);
                    expected = q.bytes; target = q.bytesTarget;
                    it.remove();
                    break;
                }
            }
        }
        if (source == null) { p.node.refuse(); p.inputSource = null; return; }
        if (p.stage.equals("drain")) publish(actual, p.owner, p.channel, Role.OUTPUT, source, expected, target);
        p.inputSource = source;
        p.currentInput = actual; p.expected = expected; p.expectedTarget = target;
    }

    public static Phase beginDrain(Object owner, ChannelHandlerContext context) {
        if (!caller("veltraph$birthWireDrain", "io.netty.handler.flow.FlowControlHandler")) return null;
        var p = new Phase("drain", owner, context, owner, context.channel(), null, null, Kind.TRANSPORT);
        ACTIVE.set(p);
        return p;
    }

    public static void finish(Phase phase, boolean normal) {
        if (!caller("veltraph$birthWireTransport", "io.netty.channel.nio.AbstractNioByteChannel$NioByteUnsafe",
                "io.netty.channel.kqueue.AbstractKQueueStreamChannel$KQueueStreamUnsafe", "io.netty.channel.local.LocalChannel")
                && !caller("veltraph$birthWireFire", "io.netty.channel.nio.AbstractNioByteChannel$NioByteUnsafe",
                "io.netty.channel.kqueue.AbstractKQueueStreamChannel$KQueueStreamUnsafe", "io.netty.channel.local.LocalChannel")
                && !caller("veltraph$birthWireChannelRead", "io.netty.handler.codec.ByteToMessageDecoder",
                "io.netty.handler.codec.MessageToMessageDecoder", "io.netty.handler.flow.FlowControlHandler",
                "net.minecraft.network.LocalFrameDecoder", "net.minecraft.network.MonitoredLocalFrameDecoder")
                && !caller("veltraph$birthWireDecode", "io.netty.handler.codec.ByteToMessageDecoder",
                "io.netty.handler.codec.MessageToMessageDecoder")
                && !caller("veltraph$birthWireOutputFire", "io.netty.handler.codec.ByteToMessageDecoder",
                "io.netty.handler.codec.MessageToMessageDecoder", "net.minecraft.network.LocalFrameDecoder",
                "net.minecraft.network.MonitoredLocalFrameDecoder", "io.netty.handler.flow.FlowControlHandler")
                && !caller("veltraph$birthWireDrain", "io.netty.handler.flow.FlowControlHandler")) return;
        if (phase == null) return;
        boolean originalCurrent = ACTIVE.get() == phase;
        if (!originalCurrent) phase.node.refuse();
        phase.node.returned(normal);
        if (ACTIVE.get() == phase) {
            if (phase.previous == null) ACTIVE.remove();
            else ACTIVE.set(phase.previous);
        }
        if (phase.localReadBody != null) phase.localReadBody.observeNativeFinish(phase, normal, originalCurrent);
    }

    private static synchronized Node lookup(Object object, Object owner, Object channel, Role role) {
        if (object == null) return null;
        var it = ALIASES.descendingIterator();
        while (it.hasNext()) {
            var a = it.next();
            if (a.object.get() == object && a.owner == owner && a.channel == channel && a.role == role) return a.node;
        }
        return null;
    }

    private static synchronized Alias claimDelivery(Object object, Object channel) {
        var it = ALIASES.descendingIterator();
        while (it.hasNext()) {
            var a = it.next();
            if (a.object.get() == object && a.channel == channel && a.role == Role.DELIVERY) {
                it.remove();
                return a.delivery.claim(object, channel) == null ? null : a;
            }
        }
        return null;
    }

    private static synchronized Alias takeOutput(Object object, Object owner, Object channel) {
        var it = ALIASES.descendingIterator();
        while (it.hasNext()) {
            var a = it.next();
            if (a.object.get() == object && a.owner == owner && a.channel == channel && a.role == Role.OUTPUT) {
                it.remove();
                return a;
            }
        }
        return null;
    }

    static void bundleOutput(Object packet, Object owner, Object channel, Node originalBundleSource) {
        if (WALKER == null || !WALKER.walk(frames -> frames.filter(frame -> frame.getDeclaringClass() != NativePlayerBirthWireSource.class).findFirst().map(frame -> frame.getDeclaringClass() == NativePlayerBirthBundleSource.class && frame.getMethodName().equals("outputAdded")).orElse(false))) return;
        publish(packet, owner, channel, Role.OUTPUT, originalBundleSource);
    }

    static Certificate finalDelivery(Object packet, Object channel) {
        var result = claimDelivery(packet, channel);
        return result == null ? null : new Certificate(result.node);
    }

    private static void publish(Object object, Object owner, Object channel, Role role, Node node) {
        publish(object, owner, channel, role, node, null, null);
    }

    private static synchronized void publish(Object object, Object owner, Object channel, Role role, Node node, NativePlayerBirthWireBytes bytes, Object bytesTarget) {
        ALIASES.removeIf(a -> a.object.get() == null || role == Role.CUMULATION
                && a.role == Role.CUMULATION && a.owner == owner && a.channel == channel);
        if (ALIASES.size() >= 8192) { node.refuse(); return; }
        ALIASES.addLast(new Alias(new WeakReference<>(object), owner, channel, role, node,
                role == Role.DELIVERY ? new NativePlayerBirthWireOrder.Delivery(object, channel, owner, node) : null, bytes, bytesTarget));
    }

    private static synchronized Alias alias(Object object, Object owner, Object channel, Role role) {
        var it = ALIASES.descendingIterator();
        while (it.hasNext()) { var a = it.next(); if (a.object.get() == object && a.owner == owner && a.channel == channel && a.role == role) return a; }
        return null;
    }

    private static boolean matchesPhysical(Object object, NativePlayerBirthWireBytes expected) {
        try {
            if (!(object instanceof io.netty.buffer.ByteBuf buffer) || expected == null) return false;
            int count = expected.size();
            if (count > NativeRawPrimitiveOrder.MAX_BYTES || expected.originalIndex > Integer.MAX_VALUE - count
                    || buffer.writerIndex() != expected.originalIndex + count
                    || buffer.readerIndex() < expected.originalIndex || buffer.readerIndex() > expected.originalIndex + count) return false;
            byte[] actual = new byte[count];
            buffer.getBytes(expected.originalIndex, actual);
            return expected.matchesSpan(buffer.readerIndex(), buffer.writerIndex(), actual);
        } catch (RuntimeException | Error unavailable) { return false; }
    }

    public static void verifyCodecInput(Object buffer) {
        if (!caller("veltraph$birthCodecOutput", "net.minecraft.network.PacketDecoder")) return;
        var p = ACTIVE.get();
        if (p != null && (!p.stage.equals("decode") || p.input != buffer || !matchesPhysical(buffer,p.expected))) p.node.refuse();
    }

    public static final class Copy {
        private final Phase decode;
        private final NativePlayerBirthWireBytes bytes;
        private Copy(Phase decode, NativePlayerBirthWireBytes bytes) { this.decode=decode;this.bytes=bytes; }
    }

    public static Copy beforeCopy(io.netty.buffer.ByteBuf input, int count) {
        if (!caller("veltraph$birthWireProduced", "net.minecraft.network.Varint21FrameDecoder", "net.minecraft.network.CompressionDecoder")) return null;
        var p=ACTIVE.get();
        if(p==null||!p.stage.equals("decode")||p.input!=input)return null;
        if(!matchesPhysical(input,p.expected)){p.node.refuse();return null;}
        var bytes=p.expected.copied(input.readerIndex(),count,0);
        if(bytes==null){p.node.refuse();return null;}
        return new Copy(p,bytes);
    }

    public static void copied(Copy copy, io.netty.buffer.ByteBuf result) {
        if (!caller("veltraph$birthWireProduced", "net.minecraft.network.Varint21FrameDecoder", "net.minecraft.network.CompressionDecoder")||copy==null)return;
        var p=ACTIVE.get();
        if(p!=copy.decode||result==null||!matchesPhysical(p.input,p.expected)){copy.decode.node.refuse();return;}
        var expected=copy.bytes.rebasedUnread(0,result.readerIndex());
        if(!matchesPhysical(result,expected)){p.node.refuse();return;}
        produceBytes(p,result,expected);
    }

    private static void produceBytes(Phase p, Object result, NativePlayerBirthWireBytes expected) {
        if(p.produced!=null||result==null||expected==null){p.node.refuse();return;}
        var output=new Node(Kind.OUTPUT,p.owner,p.channel,result,p.list,p.node);output.returned(true);
        p.produced=result;p.producedSource=output;p.producedBytes=expected;p.producedBytesTarget=result;
    }


    private static synchronized boolean bindChannel(Object actualChannel, Node source) {
        CLOSED_CHANNELS.removeIf(reference -> reference.get() == null);
        for (var closed : CLOSED_CHANNELS) if (closed.get() == actualChannel) { source.refuse(); return false; }
        CHANNEL_NODES.removeIf(entry -> entry.channel.get() == null);
        for (var entry : CHANNEL_NODES) if (entry.channel.get() == actualChannel) {
            entry.nodes.removeIf(reference -> reference.get() == null);
            if (entry.nodes.size() >= 8192) { source.refuse(); return false; }
            entry.nodes.add(new WeakReference<>(source)); return true;
        }
        if (CHANNEL_NODES.size() >= 128) { source.refuse(); return false; }
        var nodes = new java.util.ArrayList<WeakReference<Node>>(); nodes.add(new WeakReference<>(source));
        CHANNEL_NODES.addLast(new ChannelNodes(new WeakReference<>(actualChannel), nodes));
        return true;
    }

    public static synchronized void connectionDeliveryFinished(Object handler, ChannelHandlerContext context, Object packet) {
        if (!caller("veltraph$birthUpstream", "io.netty.channel.AbstractChannelHandlerContext")
                || !(handler instanceof net.minecraft.network.Connection) || context == null) return;
        Object channel=((dev.veltraph.client.mixin.NativePlayerBirthConnectionAccessor)handler).veltraph$birthChannel();
        // Dropping the unclaimed registry record cannot change an already retained eligible source node.
        ALIASES.removeIf(alias -> alias.role==Role.DELIVERY && alias.object.get()==packet && alias.channel==channel);
    }

    public static void retireChannel(Object originalChannel) {
        if (!caller("veltraph$birthWireRetire", "io.netty.channel.local.LocalChannel",
                "io.netty.channel.socket.nio.NioSocketChannel", "io.netty.channel.kqueue.AbstractKQueueChannel",
                "net.minecraft.network.Connection") || originalChannel == null) return;
        synchronized (NativePlayerBirthWireSource.class) {
        var channels = CHANNEL_NODES.iterator();
        while (channels.hasNext()) { var entry = channels.next(); if (entry.channel.get() == originalChannel) {
            for (var reference : entry.nodes) { var node = reference.get(); if (node != null) node.refuse(); }
            channels.remove();
        }}
        CLOSED_CHANNELS.removeIf(reference -> reference.get() == null);
        boolean present = false; for (var reference : CLOSED_CHANNELS) if (reference.get() == originalChannel) present = true;
        if (!present) CLOSED_CHANNELS.addLast(new WeakReference<>(originalChannel));
        ALIASES.removeIf(alias -> { boolean owned = alias.channel == originalChannel || alias.owner == originalChannel;
            if (owned) alias.node.refuse(); return owned; });
        QUEUED.removeIf(queued -> { boolean owned = queued.channel == originalChannel;
            if (owned && queued.custody.source != null) queued.custody.source.refuse(); return owned; });
        RAW_INSERTIONS.removeIf(insert -> { boolean owned = insert.node.channel == originalChannel || insert.node.owner == originalChannel;
            if (owned) insert.node.refuse(); return owned; });
        RAW_QUEUE_PENDING.remove(originalChannel); RAW_QUEUES.remove(originalChannel);
        }
        // Cross-component callbacks run after releasing the source registry monitor.
        NativePlayerBirthBundleSource.retireChannel(originalChannel);
        NativePlayerBirthDecodedOutput.retireChannel(originalChannel);
        NativePlayerBirthIngress.retireChannel(originalChannel);
    }

// Compose these members INSIDE current NativePlayerBirthWireSource.
// Add to Phase: private RawRead pendingRawRead; private NativeRawPrimitiveOrder.Received rawReceived;
// All helper callers below are the actual SDK-declaring classes after mixin merge.
private static final ThreadLocal<RawRead> RAW_READ = new ThreadLocal<>();
private static final ThreadLocal<RawLocalWrite> RAW_WRITE = new ThreadLocal<>();
private static final java.util.IdentityHashMap<Object,Object> RAW_QUEUE_PENDING=new java.util.IdentityHashMap<>();
private static final java.util.IdentityHashMap<Object,Object> RAW_QUEUES=new java.util.IdentityHashMap<>();
private static final java.util.ArrayDeque<RawLocalInsertion> RAW_INSERTIONS=new java.util.ArrayDeque<>();
public static final class RawRead {
 private final Phase parent; private final Object owner; private Object socket; private final int writeBase; private final io.netty.buffer.ByteBuf target;
 private NativeRawPrimitiveOrder.Read order; private final Node node;
 private RawRead(Phase parent,Object owner,io.netty.buffer.ByteBuf target,Object socket){this.parent=parent;this.owner=owner;this.target=target;this.socket=socket;this.writeBase=target.writerIndex();this.order=socket==null?null:new NativeRawPrimitiveOrder.Read(owner,target,socket,writeBase);this.node=new Node(Kind.TRANSPORT,owner,owner,target,null,parent.node);}
}
public static final class RawPrimitive {
 private final RawRead read; private final Object token; private final int nioStart;
 private RawPrimitive(RawRead read,Object token,int nioStart){this.read=read;this.token=token;this.nioStart=nioStart;}
}
public static RawRead beginRawRead(Object owner,io.netty.buffer.ByteBuf target,Object socket){
 if(!caller("veltraph$birthRawRead","io.netty.channel.socket.nio.NioSocketChannel","io.netty.channel.kqueue.AbstractKQueueChannel"))return null;
 var p=ACTIVE.get();if(p==null||!p.stage.equals("transport")||owner==null||target==null)return null;
 String bufferClass=target.getClass().getName();
 boolean trustedBuffer=target.getClass().getClassLoader()==io.netty.buffer.ByteBuf.class.getClassLoader()&&java.util.Set.of("io.netty.buffer.PooledDirectByteBuf","io.netty.buffer.PooledUnsafeDirectByteBuf","io.netty.buffer.PooledHeapByteBuf","io.netty.buffer.PooledUnsafeHeapByteBuf","io.netty.buffer.UnpooledDirectByteBuf","io.netty.buffer.UnpooledUnsafeDirectByteBuf","io.netty.buffer.UnpooledHeapByteBuf","io.netty.buffer.UnpooledUnsafeHeapByteBuf").contains(bufferClass);
 if(!trustedBuffer)return null;
 boolean nio=owner.getClass()==io.netty.channel.socket.nio.NioSocketChannel.class;
 boolean kqueue=owner.getClass()==io.netty.channel.kqueue.KQueueSocketChannel.class&&socket==null;
 if(!nio&&!kqueue)return null;
 if(RAW_READ.get()!=null){if(RAW_READ.get().order!=null)RAW_READ.get().order.refuse();p.node.refuse();return null;}
 if(nio&&(socket==null||!socket.getClass().getName().equals("sun.nio.ch.SocketChannelImpl")||socket.getClass().getClassLoader()!=null))return null;
 var result=new RawRead(p,owner,target,socket);RAW_READ.set(result);return result;
}
public static RawPrimitive beginRawNioPrimitive(Object actualBuffer,Object socket,java.nio.ByteBuffer view){
 if(!caller("veltraph$birthRawNioPrimitive","io.netty.buffer.PooledByteBuf","io.netty.buffer.UnpooledDirectByteBuf","io.netty.buffer.UnpooledHeapByteBuf"))return null;
 var r=RAW_READ.get();if(r==null||r.order==null||view==null)return null;
 Object token=r.order.enter(socket,actualBuffer);return token==null?null:new RawPrimitive(r,token,view.position());
}
public static void returnedRawNioPrimitive(RawPrimitive op,java.nio.ByteBuffer view,int result,boolean normal){
 if(!caller("veltraph$birthRawNioPrimitive","io.netty.buffer.PooledByteBuf","io.netty.buffer.UnpooledDirectByteBuf","io.netty.buffer.UnpooledHeapByteBuf")||op==null)return;
 try{
  byte[] expected=null;
  if(normal&&result>0&&result<=NativeRawPrimitiveOrder.MAX_BYTES&&view.position()==op.nioStart+result){var copy=view.duplicate();copy.position(op.nioStart);copy.limit(op.nioStart+result);expected=new byte[result];copy.get(expected);}
  op.read.order.returned(op.token,result,expected,normal);
 }catch(RuntimeException|Error failure){op.read.order.refuse();}
}
public static RawPrimitive beginRawUnixPrimitive(Object actualSocket){
 if(!caller("veltraph$birthRawUnixPrimitive","io.netty.channel.unix.FileDescriptor"))return null;
 var r=RAW_READ.get();if(r==null||r.order==null)return null;
 Object token=r.order.enter(actualSocket,r.target);return token==null?null:new RawPrimitive(r,token,-1);
}
public static void returnedRawUnixPrimitive(RawPrimitive op,int result,boolean normal){
 if(!caller("veltraph$birthRawUnixPrimitive","io.netty.channel.unix.FileDescriptor")||op==null)return;
 try{
  byte[] expected=null;
  if(normal&&result>0&&result<=NativeRawPrimitiveOrder.MAX_BYTES){expected=new byte[result];op.read.target.getBytes(op.read.order.start,expected);}
  op.read.order.returned(op.token,result,expected,normal);
 }catch(RuntimeException|Error failure){op.read.order.refuse();}
}
public static void finishRawRead(RawRead read,int result,boolean normal){
 if(!caller("veltraph$birthRawRead","io.netty.channel.socket.nio.NioSocketChannel","io.netty.channel.kqueue.AbstractKQueueChannel")||read==null)return;
 try{
  if(RAW_READ.get()!=read||ACTIVE.get()!=read.parent){if(read.order!=null)read.order.refuse();read.node.refuse();}
  if(read.order==null){read.node.refuse();read.parent.node.refuse();return;}read.order.finish(result,normal);read.node.returned(normal);
  if(read.parent.pendingRawRead!=null){if(read.parent.pendingRawRead.order!=null)read.parent.pendingRawRead.order.refuse();read.parent.node.refuse();}
  read.parent.pendingRawRead=read;
 }finally{if(RAW_READ.get()==read)RAW_READ.remove();}
}
public static void rawKQueueSocket(Object owner,Object socket){
 if(!caller("veltraph$birthRawSocketCall","io.netty.channel.kqueue.AbstractKQueueChannel"))return;
 var r=RAW_READ.get();if(r==null)return;
 if(r.owner!=owner||r.socket!=null||socket==null||!socket.getClass().getName().equals("io.netty.channel.kqueue.BsdSocket")||socket.getClass().getClassLoader()!=io.netty.channel.unix.FileDescriptor.class.getClassLoader()){r.node.refuse();r.parent.node.refuse();return;}
 r.socket=socket;r.order=new NativeRawPrimitiveOrder.Read(owner,r.target,socket,r.writeBase);
}
// REPLACE positive branch of existing readReturned AFTER caller/phase/bytes checks:
// var raw=p.pendingRawRead; p.pendingRawRead=null;
// var received=raw==null?null:raw.order.take(nativeChannel,buffer,bytes);
// if(received==null||!raw.node.viable()){p.node.refuse();return;}
// var read=new Node(Kind.TRANSPORT,p.owner,nativeChannel,buffer,null,p.node,raw.node);
// read.returned(true);p.readObject=buffer;p.readOwner=nativeChannel;p.readSource=read;p.rawReceived=received;
// Attach received.bytes()/received.target/start/count to parent-authored WireBytes via private code.
// NEVER create a fresh expected-byte snapshot at this outer virtual-return boundary.

public static synchronized void rawQueueCreated(Object owner,Object queue){
 if(!caller("veltraph$birthRawQueueCreated","io.netty.channel.local.LocalChannel")||owner==null||owner.getClass()!=io.netty.channel.local.LocalChannel.class||queue==null)return;
 if(RAW_QUEUE_PENDING.size()>=4096||RAW_QUEUES.containsKey(owner)){return;}
 RAW_QUEUE_PENDING.put(owner,queue);
}
public static synchronized void rawQueueNormal(Object owner,Object actualQueue){
 if(!caller("veltraph$birthRawQueueNormal","io.netty.channel.local.LocalChannel"))return;
 var original=RAW_QUEUE_PENDING.remove(owner);if(original!=null&&original==actualQueue&&RAW_QUEUES.size()<4096)RAW_QUEUES.put(owner,original);
}
public static final class RawLocalWrite {
 private final NativeRawPrimitiveOrder.LocalWrite order; private final Node node;
 private RawLocalWrite(Object owner,Object peer,Object queue,Object outbound){order=new NativeRawPrimitiveOrder.LocalWrite(owner,peer,queue,outbound);node=new Node(Kind.TRANSPORT,owner,peer,outbound,null);}
}
private record RawLocalInsertion(NativeRawPrimitiveOrder.LocalInsert order,Node node) {}
public static RawLocalWrite beginRawLocalWrite(Object owner,Object peer,Object queue,Object outbound){
 if(!caller("veltraph$birthRawLocalWrite","io.netty.channel.local.LocalChannel")||owner==null||peer==null||owner.getClass()!=io.netty.channel.local.LocalChannel.class||peer.getClass()!=io.netty.channel.local.LocalChannel.class)return null;
 synchronized(NativePlayerBirthWireSource.class){if(RAW_QUEUES.get(peer)!=queue)return null;}
 if(RAW_WRITE.get()!=null){RAW_WRITE.get().order.refuse();RAW_WRITE.get().node.refuse();return null;}
 var w=new RawLocalWrite(owner,peer,queue,outbound);if(!bindChannel(owner,w.node)||!bindChannel(peer,w.node)){w.order.refuse();w.node.refuse();return null;}RAW_WRITE.set(w);return w;
}
public static void rawLocalCurrent(Object outbound,Object message){if(!caller("veltraph$birthRawLocalCurrent","io.netty.channel.local.LocalChannel"))return;var w=RAW_WRITE.get();if(w!=null)w.order.current(outbound,message);}
public static void rawLocalRetained(Object original,Object retained){if(!caller("veltraph$birthRawLocalRetained","io.netty.channel.local.LocalChannel"))return;var w=RAW_WRITE.get();if(w!=null)w.order.retained(original,retained);}
public static final class RawLocalAdd {
 private final RawLocalWrite write; private final Object queue,wrapper; private final io.netty.buffer.ByteBuf contents;private final int start;private final byte[] expected;private NativeRawPrimitiveOrder.LocalInsert pending;
 private RawLocalAdd(RawLocalWrite write,Object queue,Object wrapper,io.netty.buffer.ByteBuf contents){this.write=write;this.queue=queue;this.wrapper=wrapper;this.contents=contents;this.start=contents.readerIndex();int count=contents.readableBytes();if(count<=0||count>NativeRawPrimitiveOrder.MAX_BYTES)throw new IllegalArgumentException("unqualified raw size");this.expected=new byte[count];contents.getBytes(start,expected);}
}
public static RawLocalAdd beginRawLocalAdd(Object queue,Object wrapper,io.netty.buffer.ByteBuf contents){
 if(!caller("veltraph$birthRawLocalAdded","io.netty.channel.local.LocalChannel"))return null;
 var w=RAW_WRITE.get();if(w==null)return null;
 if(wrapper==null||wrapper.getClass()!=net.minecraft.network.HiddenByteBuf.class||contents==null){w.order.refuse();w.node.refuse();return null;}
 try{
  var add=new RawLocalAdd(w,queue,wrapper,contents);
  add.pending=w.order.prepare(queue,wrapper,contents,add.start,add.expected);
  if(add.pending==null){w.order.refuse();w.node.refuse();return null;}
  synchronized(NativePlayerBirthWireSource.class){
   if(RAW_INSERTIONS.size()>=4096){w.order.refuse();w.node.refuse();return null;}
   // Publish the pending source before the native queue effect becomes visible to its peer event loop.
   RAW_INSERTIONS.addLast(new RawLocalInsertion(add.pending,w.node));
  }
  return add;
 }catch(RuntimeException|Error failure){w.order.refuse();w.node.refuse();return null;}
}
public static synchronized void returnedRawLocalAdd(RawLocalAdd add,boolean added,boolean normal){
 if(!caller("veltraph$birthRawLocalAdded","io.netty.channel.local.LocalChannel")||add==null)return;
 if(add.pending==null){add.write.order.refuse();add.write.node.refuse();return;}
 add.pending.additionReturned(added,normal);
 if(!normal||!added||!add.pending.viable()){add.write.order.refuse();add.write.node.refuse();}

}
public static void finishRawLocalWrite(RawLocalWrite w,boolean normal){
 if(!caller("veltraph$birthRawLocalWrite","io.netty.channel.local.LocalChannel")||w==null)return;
 try{if(RAW_WRITE.get()!=w){w.order.refuse();w.node.refuse();}w.order.finish(normal);if(!w.order.complete())w.node.refuse();w.node.returned(normal);}finally{if(RAW_WRITE.get()==w)RAW_WRITE.remove();}
}
// REPLACE old localPolled positive mint with exact receipt consumption below inside that method:
// NativeRawPrimitiveOrder.Received bytes=null;Node insertion=null;
// synchronized(NativePlayerBirthWireSource.class){var it=RAW_INSERTIONS.iterator();while(it.hasNext()){
//  var next=it.next();if(!next.order.viable()){it.remove();continue;}
//  if(next.order.wrapper==message){bytes=next.order.poll(p.owner,queue,message);insertion=next.node;it.remove();break;}
// }}
// if(bytes==null||insertion==null){p.node.refuse();return;}
// var read=new Node(Kind.TRANSPORT,p.owner,p.owner,message,queue,p.node,insertion);
// read.returned(true);p.readObject=message;p.readOwner=p.owner;p.readSource=read;p.rawReceived=bytes;
// WireBytes must use receipt contents identity/range/cloned bytes at exact localUnpacked; no re-snapshot.
// Explicit disconnect retirement of these maps is REQUIRED during whole composition; weak keys do not
// suffice because the source nodes retain owners. This checkpoint does not authorize that lifecycle.

// Compose INSIDE current NativePlayerBirthWireSource; add Phase fields:
// private NativeCryptoPrimitiveOrder.CipherUpdate cryptoCipher;
// private NativeCryptoPrimitiveOrder.Inflate cryptoInflate;
public static final class CipherPrimitive {private final Phase phase;private final NativeCryptoPrimitiveOrder.CipherUpdate order;private CipherPrimitive(Phase p,NativeCryptoPrimitiveOrder.CipherUpdate o){phase=p;order=o;}}
public static final class InflatePrimitive {private final Phase phase;private final java.nio.ByteBuffer output;private final int outputStart;private InflatePrimitive(Phase p,java.nio.ByteBuffer out){phase=p;output=out;outputStart=out==null?-1:out.position();}}
private static byte[] nativeViewSlice(java.nio.ByteBuffer view,int start,int count){
 if(view==null||view.getClass().getClassLoader()!=null||!java.util.Set.of("java.nio.HeapByteBuffer","java.nio.DirectByteBuffer","java.nio.HeapByteBufferR","java.nio.DirectByteBufferR").contains(view.getClass().getName())||start<0||count<0||count>NativeCryptoPrimitiveOrder.MAX||start>view.limit()-count)return null;
 var copy=view.duplicate();copy.position(start);copy.limit(start+count);byte[] bytes=new byte[count];copy.get(bytes);return bytes;
}
private static boolean nativeCipher(javax.crypto.Cipher cipher){
 if(cipher==null||cipher.getClass()!=javax.crypto.Cipher.class||cipher.getClass().getClassLoader()!=null||!"AES/CFB8/NoPadding".equalsIgnoreCase(cipher.getAlgorithm()))return false;
 return true;
}
private static boolean nativeCipherProviderAfterNormalUpdate(javax.crypto.Cipher cipher){
 // Called ONLY after original Cipher.update returned normally: do not force lazy provider selection beforehand.
 if(!nativeCipher(cipher))return false;var provider=cipher.getProvider();return provider!=null&&provider.getClass().getClassLoader()==null&&provider.getClass().getName().equals("com.sun.crypto.provider.SunJCE");
}
private static boolean nativeInflater(java.util.zip.Inflater inflater){return inflater!=null&&inflater.getClass()==java.util.zip.Inflater.class&&inflater.getClass().getClassLoader()==null;}
private static void refuseCrypto(Phase p){if(p==null)return;p.node.refuse();if(p.cryptoCipher!=null)p.cryptoCipher.refuse();if(p.cryptoInflate!=null)p.cryptoInflate.refuse();}
public static CipherPrimitive beforeCipherUpdate(Object base,javax.crypto.Cipher cipher,byte[] input,int inputOffset,int count,byte[] output,int outputOffset){
 if(!caller("veltraph$birthCipherUpdate","net.minecraft.network.CipherBase"))return null;
 var p=ACTIVE.get();if(p==null)return null;
 try{
  if(!p.stage.equals("decode")||!p.owner.getClass().getName().equals("net.minecraft.network.CipherDecoder")||p.cryptoCipher!=null||p.cryptoInflate!=null||base==null||!base.getClass().getName().equals("net.minecraft.network.CipherBase")||base.getClass().getClassLoader()!=p.owner.getClass().getClassLoader()||!nativeCipher(cipher)||p.expected==null||p.expectedTarget!=p.input||!matchesPhysical(p.input,p.expected)){refuseCrypto(p);return null;}
  var expected=p.expected.readableFrom(p.decodeStart);
  var order=new NativeCryptoPrimitiveOrder.CipherUpdate(base,cipher,expected,input,inputOffset,count,output,outputOffset);
  p.cryptoCipher=order;p.transformer=base;
  if(!order.viable()){refuseCrypto(p);return null;}
  return new CipherPrimitive(p,order);
 }catch(RuntimeException|Error failure){refuseCrypto(p);return null;}
}
public static void afterCipherUpdate(CipherPrimitive primitive,int count,boolean normal){
 if(!caller("veltraph$birthCipherUpdate","net.minecraft.network.CipherBase")||primitive==null)return;
 var p=primitive.phase;
 try{primitive.order.returned(count,normal);if(!normal||ACTIVE.get()!=p||p.cryptoCipher!=primitive.order||!matchesPhysical(p.input,p.expected)||!nativeCipherProviderAfterNormalUpdate((javax.crypto.Cipher)primitive.order.receiver)){refuseCrypto(p);return;}}
 catch(RuntimeException|Error failure){refuseCrypto(p);}
}
public static InflatePrimitive beforeInflaterSetInput(Object owner,java.util.zip.Inflater inflater,java.nio.ByteBuffer input){
 if(!caller("veltraph$birthInflaterSetInput","net.minecraft.network.CompressionDecoder"))return null;
 var p=ACTIVE.get();if(p==null)return null;
 try{
  if(!p.stage.equals("decode")||owner!=p.owner||owner.getClass()!=net.minecraft.network.CompressionDecoder.class||p.cryptoCipher!=null||p.cryptoInflate!=null||!nativeInflater(inflater)||inflater.getBytesRead()!=0||inflater.getBytesWritten()!=0||inflater.getRemaining()!=0||inflater.finished()||inflater.needsDictionary()||input==null||p.expected==null||p.expectedTarget!=p.input||!matchesPhysical(p.input,p.expected)){refuseCrypto(p);return null;}
  var parsed=NativeCryptoPrimitiveOrder.compressed(p.expected.readableFrom(p.decodeStart));
  if(parsed==null||input.remaining()!=parsed.payload().length){refuseCrypto(p);return null;}
  var order=new NativeCryptoPrimitiveOrder.Inflate(owner,inflater,input,input.position(),parsed);
  p.cryptoInflate=order;p.transformer=owner;p.inflateInput=input;p.inflateInputStart=input.position();p.inflateExpected=parsed.payload();
  if(!order.beforeSet(inflater,input,input.position(),nativeViewSlice(input,p.inflateInputStart,p.inflateExpected.length))){refuseCrypto(p);return null;}
  return new InflatePrimitive(p,null);
 }catch(RuntimeException|Error failure){refuseCrypto(p);return null;}
}
public static void afterInflaterSetInput(InflatePrimitive primitive,java.util.zip.Inflater inflater,java.nio.ByteBuffer input,boolean normal){
 if(!caller("veltraph$birthInflaterSetInput","net.minecraft.network.CompressionDecoder")||primitive==null)return;
 var p=primitive.phase;
 try{if(ACTIVE.get()!=p||p.cryptoInflate==null||!nativeInflater(inflater)||!matchesPhysical(p.input,p.expected)){refuseCrypto(p);return;}p.cryptoInflate.afterSet(inflater,input,input.position(),nativeViewSlice(input,p.inflateInputStart,p.inflateExpected.length),normal);if(!normal)refuseCrypto(p);}
 catch(RuntimeException|Error failure){refuseCrypto(p);}
}
public static InflatePrimitive beforeInflaterInflate(Object owner,java.util.zip.Inflater inflater,java.nio.ByteBuffer output){
 if(!caller("veltraph$birthInflaterInflate","net.minecraft.network.CompressionDecoder"))return null;
 var p=ACTIVE.get();if(p==null)return null;
 try{
  if(p.cryptoInflate==null||!nativeInflater(inflater)||output==null||!matchesPhysical(p.input,p.expected)||!p.cryptoInflate.beforeInflate(owner,inflater,output,output.position(),output.remaining(),nativeViewSlice(p.inflateInput,p.inflateInputStart,p.inflateExpected.length))){refuseCrypto(p);return null;}
  return new InflatePrimitive(p,output);
 }catch(RuntimeException|Error failure){refuseCrypto(p);return null;}
}
public static void afterInflaterInflate(InflatePrimitive primitive,java.nio.ByteBuffer output,int count,boolean normal){
 if(!caller("veltraph$birthInflaterInflate","net.minecraft.network.CompressionDecoder")||primitive==null)return;
 var p=primitive.phase;
 try{var produced=nativeViewSlice(output,primitive.outputStart,count);if(ACTIVE.get()!=p||p.cryptoInflate==null||output!=primitive.output||!matchesPhysical(p.input,p.expected)){refuseCrypto(p);return;}p.cryptoInflate.afterInflate(output,output.position(),count,nativeViewSlice(p.inflateInput,p.inflateInputStart,p.inflateExpected.length),produced,normal);if(produced!=null)p.transformedBytes=produced.clone();if(!normal)refuseCrypto(p);}
 catch(RuntimeException|Error failure){refuseCrypto(p);}
}
public static void producedTransformed(Object originalInput,io.netty.buffer.ByteBuf result,Object transformer,boolean normal){
 if(!caller("veltraph$birthWireProduced","net.minecraft.network.CipherDecoder")&&!caller("veltraph$birthWireProducedInflated","net.minecraft.network.CompressionDecoder"))return;
 var p=ACTIVE.get();if(p==null)return;
 try{
  if(!normal||!p.stage.equals("decode")||p.input!=originalInput||p.transformer!=transformer||result==null||p.produced!=null||!matchesPhysical(p.input,p.expected)){refuseCrypto(p);return;}
  byte[] bytes;
  if(p.cryptoCipher!=null&&p.cryptoInflate==null)bytes=p.cryptoCipher.finish(transformer,normal);
  else if(p.cryptoInflate!=null&&p.cryptoCipher==null)bytes=p.cryptoInflate.finish(transformer,normal,nativeViewSlice(p.inflateInput,p.inflateInputStart,p.inflateExpected.length));
  else bytes=null;
  if(bytes==null||result.readableBytes()!=bytes.length){refuseCrypto(p);return;}
  p.transformedBytes=bytes.clone();var expected=new NativePlayerBirthWireBytes(result.readerIndex(),bytes);
  if(!matchesPhysical(result,expected)){refuseCrypto(p);return;}
  produceBytes(p,result,expected);
 }catch(RuntimeException|Error failure){refuseCrypto(p);}
}

}
