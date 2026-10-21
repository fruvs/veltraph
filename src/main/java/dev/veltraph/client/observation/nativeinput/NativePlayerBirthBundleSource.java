package dev.veltraph.client.observation.nativeinput;

import io.netty.channel.ChannelHandlerContext;
import java.util.ArrayDeque;
import static dev.veltraph.client.observation.nativeinput.NativePlayerBirthWireOrder.Kind;
import static dev.veltraph.client.observation.nativeinput.NativePlayerBirthWireOrder.Node;

/** Exact native bundler construction, native List.add membership, and original constructor result. */
public final class NativePlayerBirthBundleSource {
    private NativePlayerBirthBundleSource() {}
    private static final StackWalker WALKER = walker();
    private static StackWalker walker() {
        try { return StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE); }
        catch (RuntimeException | Error unavailable) { return null; }
    }
    private static final ThreadLocal<Decode> DECODE = new ThreadLocal<>();
    private static final ThreadLocal<Start> START = new ThreadLocal<>();
    private static final ThreadLocal<Member> MEMBER = new ThreadLocal<>();
    private static final ThreadLocal<Create> CREATE = new ThreadLocal<>();
    private static final ArrayDeque<Record> RECORDS = new ArrayDeque<>();
    private record Record(Object owner, Object channel, NativePlayerBirthBundleOrder order) {}

    public static final class Decode {
        private final Object owner, context, packet, list, channel;
        private final NativePlayerBirthWireSource.Certificate source;
        private final Node node;
        private Object produced;
        private Node producedSource;
        private NativePlayerBirthBundleOrder producedBundle;
        private Decode(Object owner, Object context, Object packet, Object list, Object channel,
                       NativePlayerBirthWireSource.Certificate source) {
            this.owner = owner; this.context = context; this.packet = packet; this.list = list;
            this.channel = channel; this.source = source;
            this.node = new Node(Kind.DECODE, owner, channel, packet, list, source.nodeForNativeConsumer());
        }
    }
    public static final class Start {
        private final Decode decode;
        private final Object info;
        private final Node node;
        private Object constructed, originalList;
        private Start(Decode decode, Object info) {
            this.decode = decode; this.info = info;
            node = new Node(Kind.BUNDLE, decode.owner, decode.channel, decode.packet, decode.list, decode.node);
        }
    }
    public static final class Member {
        private final Decode decode;
        private final NativePlayerBirthBundleOrder order;
        private final Node node;
        private Member(Decode decode, NativePlayerBirthBundleOrder order) {
            this.decode = decode; this.order = order;
            node = new Node(Kind.BUNDLE, order.owner, decode.channel, decode.packet, order.originalList,
                    decode.node, order.start);
        }
    }
    public static final class Create {
        private final Member member;
        private final Object list;
        private final Node node;
        private Object constructed;
        private Create(Member member, Object list) {
            this.member = member; this.list = list;
            node = new Node(Kind.BUNDLE, member.order.owner, member.decode.channel, member.decode.packet,
                    list, member.node);
        }
    }

    private static boolean caller(String nativeClass, String suffix) {
        return WALKER != null && WALKER.walk(frames -> frames
                .filter(frame -> frame.getDeclaringClass() != NativePlayerBirthBundleSource.class)
                .findFirst().map(frame -> frame.getDeclaringClass().getName().equals(nativeClass)
                        && frame.getDeclaringClass().getClassLoader()==net.minecraft.network.protocol.Packet.class.getClassLoader()
                        && frame.getMethodName().endsWith(suffix)).orElse(false));
    }

    public static Decode beginDecode(Object owner, ChannelHandlerContext context, Object packet, Object list) {
        if (!caller("net.minecraft.network.PacketBundlePacker", "veltraph$birthWireBundleDecode")) return null;
        var old = DECODE.get();
        if (old != null) { old.node.refuse(); return null; }
        var source = NativePlayerBirthWireSource.decodeSource(owner, context, packet, list);
        if (source == null || !source.viable()) return null;
        var d = new Decode(owner, context, packet, list, context.channel(), source);
        DECODE.set(d);
        return d;
    }

    public static void finishDecode(Decode decode, boolean normal) {
        if (!caller("net.minecraft.network.PacketBundlePacker", "veltraph$birthWireBundleDecode") || decode == null) return;
        if (DECODE.get() != decode) decode.node.refuse();
        decode.node.returned(normal);
        if (DECODE.get() == decode) DECODE.remove();
    }

    public static Start beginStart(Object originalInfo, Object originalPacket) {
        if (!caller("net.minecraft.network.PacketBundlePacker", "veltraph$birthWireBundleStart")) return null;
        var d = DECODE.get();
        if (d == null || originalPacket != d.packet) return null;
        if (START.get() != null) { d.node.refuse(); return null; }
        var result = new Start(d, originalInfo);
        START.set(result);
        return result;
    }

    public static void bundlerConstructed(Object originalInfo, Object actualBundler, Object nativeList) {
        if (!caller("net.minecraft.network.protocol.BundlerInfo$1$1", "veltraph$birthWireBundlerCtor")) return;
        var s = START.get();
        if (s == null) return;
        if (s.info != originalInfo || actualBundler == null || nativeList == null
                || nativeList.getClass() != java.util.ArrayList.class || s.constructed != null) {
            s.node.refuse(); return;
        }
        s.constructed = actualBundler;
        s.originalList = nativeList;
    }

    public static synchronized void finishStart(Start start, Object originalResult, boolean normal) {
        if (!caller("net.minecraft.network.PacketBundlePacker", "veltraph$birthWireBundleStart") || start == null) return;
        if (START.get() != start || !normal || originalResult != null && originalResult != start.constructed) start.node.refuse();
        start.node.returned(normal);
        if (originalResult != null && start.node.viable() && start.originalList != null) {
            if (RECORDS.size() >= 128) start.node.refuse();
            else RECORDS.addLast(new Record(originalResult, start.decode.channel,
                    new NativePlayerBirthBundleOrder(originalResult, start.decode.channel, start.originalList, start.node)));
        }
        if (START.get() == start) START.remove();
    }

    public static synchronized Member beginMember(Object originalBundler, Object originalPacket) {
        if (!caller("net.minecraft.network.PacketBundlePacker", "veltraph$birthWireBundleAdd")) return null;
        var d = DECODE.get();
        if (d == null || d.packet != originalPacket) return null;
        if (MEMBER.get() != null) { d.node.refuse(); return null; }
        for (var record : RECORDS) if (record.owner == originalBundler && record.channel == d.channel) {
            var result = new Member(d, record.order);
            MEMBER.set(result);
            return result;
        }
        d.node.refuse();
        return null;
    }

    public static void memberAdded(Object originalList, Object originalPacket, boolean result) {
        if (!caller("net.minecraft.network.protocol.BundlerInfo$1$1", "veltraph$birthWireBundlerMember")) return;
        var m = MEMBER.get();
        if (m == null) return;
        if (m.decode.packet != originalPacket || !m.order.memberAdded(originalList, originalPacket, m.node, result)) m.node.refuse();
    }

    public static Create beginCreate(Object originalList) {
        if (!caller("net.minecraft.network.protocol.BundlerInfo$1$1", "veltraph$birthWireBundlerCreate")) return null;
        var m = MEMBER.get();
        if (m == null || originalList != m.order.originalList) return null;
        if (CREATE.get() != null) { m.node.refuse(); return null; }
        var result = new Create(m, originalList);
        CREATE.set(result);
        return result;
    }

    public static void bundleConstructed(Object actualBundle, Object originalIterable) {
        if (!caller("net.minecraft.network.protocol.BundlePacket", "veltraph$birthWireBundleCtor")) return;
        var c = CREATE.get();
        if (c == null) return;
        if (c.list != originalIterable || actualBundle == null || c.constructed != null) { c.node.refuse(); return; }
        c.constructed = actualBundle;
    }

    public static void finishCreate(Create create, Object originalResult, boolean normal) {
        if (!caller("net.minecraft.network.protocol.BundlerInfo$1$1", "veltraph$birthWireBundlerCreate") || create == null) return;
        if (CREATE.get() != create || !normal || originalResult != create.constructed) create.node.refuse();
        create.node.returned(normal);
        if (create.node.viable()) create.member.order.constructed(create.list, originalResult, create.node, create.member.node);
        if (CREATE.get() == create) CREATE.remove();
    }

    public static void finishMember(Member member, Object originalResult, boolean normal) {
        if (!caller("net.minecraft.network.PacketBundlePacker", "veltraph$birthWireBundleAdd") || member == null) return;
        if (MEMBER.get() != member || !normal) member.node.refuse();
        member.node.returned(normal);
        if (originalResult != null) {
            var source = member.order.source(originalResult);
            if (source == null || !source.viable()) member.node.refuse();
            else {
                member.decode.produced = originalResult;
                member.decode.producedSource = source;
                member.decode.producedBundle = member.order;
                synchronized(NativePlayerBirthBundleSource.class){RECORDS.removeIf(record -> record.order == member.order);}
            }
        }
        if (MEMBER.get() == member) MEMBER.remove();
    }

    public static void outputAdded(Object originalList, Object originalPacket, boolean result) {
        if (!caller("net.minecraft.network.PacketBundlePacker", "veltraph$birthWireBundleAdded")) return;
        var d = DECODE.get();
        if (d == null) return;
        if (!result || originalList != d.list) { d.node.refuse(); return; }
        Node source;
        if (originalPacket == d.packet && d.produced == null) {
            source = new Node(Kind.OUTPUT, d.owner, d.channel, originalPacket, d.list, d.node);
            source.returned(true);
        } else if (originalPacket == d.produced && d.producedSource != null) {
            source = new Node(Kind.OUTPUT, d.owner, d.channel, originalPacket, d.list, d.node, d.producedSource);
            source.returned(true);
            NativePlayerBirthDecodedOutput.recordBundle(originalPacket, d.channel, d.producedBundle,
                    new NativePlayerBirthWireSource.Certificate(source));
        } else { d.node.refuse(); return; }
        NativePlayerBirthWireSource.bundleOutput(originalPacket, d.owner, d.channel, source);
    }
    static synchronized void retireChannel(Object originalChannel) {
        RECORDS.removeIf(record -> { if(record.channel != originalChannel) return false; record.order.refuse(); return true; });
        var d=DECODE.get();if(d!=null&&d.channel==originalChannel)d.node.refuse();
    }
}
