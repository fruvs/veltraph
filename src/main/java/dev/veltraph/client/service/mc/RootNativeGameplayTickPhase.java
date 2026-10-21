package dev.veltraph.client.service.mc;

import dev.veltraph.client.VeltraphBootstrap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;

/** Tickets from the actual run/runTick/tick and Fabric subscriber opcodes, never a public event pump. */
public final class RootNativeGameplayTickPhase {
    private RootNativeGameplayTickPhase() {}
    private static volatile boolean observerFailed;
    private static final ThreadLocal<RunFrame> RUN = new ThreadLocal<>();
    private static final ThreadLocal<TickFrame> TICK = new ThreadLocal<>();
    private static final ThreadLocal<EventFrame> EVENT = new ThreadLocal<>();
    private static final ThreadLocal<LoopFrame> LOOP = new ThreadLocal<>();
    private static final ThreadLocal<SubscriberFrame> SUBSCRIBER = new ThreadLocal<>();

    public static final class RunFrame {
        private final Minecraft client;
        private final RootNativeSdlBoundaryPhase.OriginalClientRunSource originalClientRun;
        private final Thread thread = Thread.currentThread();
        private boolean revoked;
        private RunFrame(Minecraft client,RootNativeSdlBoundaryPhase.OriginalClientRunSource originalClientRun) { this.client = client;this.originalClientRun=originalClientRun; }
        public Minecraft clientIdentity(){return client;}
        public boolean current(){try{return !observerFailed&&!revoked&&RUN.get()==this&&thread==Thread.currentThread()
            &&originalClientRun!=null&&originalClientRun.clientIdentity()==client&&originalClientRun.current();}
            catch(Throwable unavailable){observerFailure();return false;}}

    }
    public static final class TickFrame {
        private final RunFrame run;
        private final RootTickIngressState state;
        private TickCameraSource cameraSource;
        private TickFrame(RunFrame run) { this.run = run; this.state = new RootTickIngressState(run.client); }
    }
    /** Retained original one-use tick camera source; expiry of the live opcode is not a new source. */
    public static final class TickCameraSource {
        private final TickFrame tick;private final RootNativeCameraPhase.Phase phase;
        private final Thread thread=Thread.currentThread();private boolean revoked;
        private TickCameraSource(TickFrame tick,RootNativeCameraPhase.Phase phase){this.tick=tick;this.phase=phase;}
        boolean originalCurrent(Minecraft client,RootNativeCameraPhase.Phase exact){
            return !observerFailed&&!revoked&&Thread.currentThread()==thread&&tick.cameraSource==this&&phase==exact&&tick.run.client==client;
        }
        boolean entryCurrent(Minecraft client,RootNativeCameraPhase.Phase exact){return originalCurrent(client,exact)&&nativeCurrent(tick);}
    }
    /** Only the concrete camera phase entry may consume its actual native tick opcode once. */
    public static TickCameraSource claimOriginalCameraTick(Minecraft client,RootNativeCameraPhase.Phase phase){
        try{
            boolean caller=StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(stream->{var f=stream.skip(1).findFirst().orElse(null);
                return f!=null&&f.getDeclaringClass()==RootNativeCameraPhase.class&&f.getMethodName().equals("enter");});
            var tick=TICK.get();if(!caller||phase==null||!nativeCurrent(tick)||tick.run.client!=client)return null;
            if(!originalCameraRunCurrent(tick.run,client)||!singleOriginalTickBody()){revokeCurrent();return null;}
            if(tick.cameraSource!=null){tick.cameraSource.revoked=true;revokeCurrent();return null;}
            var source=new TickCameraSource(tick,phase);tick.cameraSource=source;return source;
        }catch(Throwable unavailable){observerFailure();return null;}
    }
    /** Exact already-open run opcode, consumed only by the concrete renderFrame opcode phase. */
    public static RunFrame originalCameraRun(Minecraft client){
        try{
            boolean caller=StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(stream->{var f=stream.skip(1).findFirst().orElse(null);
                return f!=null&&f.getDeclaringClass()==RootNativeCameraPhase.class&&f.getMethodName().equals("enterRenderOpcode");});
            var run=RUN.get();if(!caller||!originalCameraRunCurrent(run,client))return null;
            return run;
        }catch(Throwable unavailable){observerFailure();return null;}
    }
    static boolean originalCameraRunCurrent(RunFrame run,Minecraft client){
        if(observerFailed||run==null||!run.current()||run.client!=client||run.thread!=Thread.currentThread())return false;
        // Negative recursion conservation: a nested public runTick cannot borrow the already-issued original run opcode.
        var binding=RootNativeTickOpcodeBindings.get("TICK");
        return binding!=null&&StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(stream->stream.filter(f->
            f.getDeclaringClass()==Minecraft.class&&f.getMethodName().equals(binding.sourceName())&&f.getDescriptor().equals(binding.sourceDescriptor())).limit(2).count()==1);
    }
    private static boolean singleOriginalTickBody(){
        var binding=RootNativeTickOpcodeBindings.get("END");
        return binding!=null&&StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(stream->stream.filter(f->
            f.getDeclaringClass()==Minecraft.class&&f.getMethodName().equals(binding.sourceName())&&f.getDescriptor().equals(binding.sourceDescriptor())).limit(2).count()==1);
    }
    /** Typed original runTick source for the one concrete mouse opcode issuer only. */
    public static RunFrame originalRunTickForMouse(Minecraft client){
        try{
            boolean caller=StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(stream->{var f=stream.skip(1).findFirst().orElse(null);
                return f!=null&&f.getDeclaringClass()==RootNativeSdlBoundaryPhase.class&&f.getMethodName().equals("enterMouseAccumulationOpcode");});
            var run=RUN.get();return caller&&originalCameraRunCurrent(run,client)?run:null;
        }catch(Throwable unavailable){observerFailure();return null;}
    }
    public static RootNativeSdlBoundaryPhase.OriginalSdlPollCall claimOriginalSdlPoll(com.mojang.blaze3d.platform.SDLEventHandler handler,Minecraft client){
        return RootNativeSdlBoundaryPhase.claimOriginalSdlPoll(handler,client);
    }
    public static RootNativeSdlBoundaryPhase.OriginalMouseAccumulationCall claimOriginalMouseAccumulation(Minecraft client,net.minecraft.client.MouseHandler handler){
        return RootNativeSdlBoundaryPhase.claimOriginalMouseAccumulation(client,handler);
    }
    public static final class EventFrame {
        private final TickFrame tick;
        private final ClientTickEvents.EndTick invoker;
        private boolean entered;
        private EventFrame(TickFrame tick, ClientTickEvents.EndTick invoker) { this.tick = tick; this.invoker = invoker; }
    }
    public static final class LoopFrame {
        private final EventFrame event;
        private final Minecraft client;
        private LoopFrame(EventFrame event, Minecraft client) { this.event = event; this.client = client; }
    }
    public static final class SubscriberFrame {
        private final LoopFrame loop;
        private final ClientTickEvents.EndTick callback;
        private RootTickTicket ticket;
        private SubscriberFrame(LoopFrame loop, ClientTickEvents.EndTick callback) { this.loop = loop; this.callback = callback; }
    }
    public static final class RootTickTicket {
        private final SubscriberFrame subscriber;
        private final Thread thread = Thread.currentThread();
        private boolean closed, normalReturned;
        private RootTickTicket(SubscriberFrame subscriber) { this.subscriber = subscriber; }
        public boolean current() {
            try {
            return !closed && thread == Thread.currentThread() && SUBSCRIBER.get() == subscriber
                    && subscriber.ticket == this && nativeCurrent(subscriber.loop.event.tick)
                    && EVENT.get() == subscriber.loop.event && LOOP.get() == subscriber.loop
                    && subscriber.loop.event.tick.state.current(subscriber.loop.client, subscriber, this);

            } catch (Throwable ignored) { observerFailure(); return false; }
        }
        /** Read-only historical exact Root claim closure; never creates an active native window. */
        public boolean normalReturned() {
            try { return closed && normalReturned && !observerFailed && thread == Thread.currentThread()
                    && subscriber.loop.event.tick.state.normalClaimReturned(subscriber.loop.client,this)
                    && !subscriber.loop.event.tick.run.revoked; }
            catch (Throwable ignored) { observerFailure(); return false; }
        }
    }

    /** Exact final-transformed hook and original opcode body; no suffix or ancestor match. */
    private static boolean directOpcodeCaller(String kind) {
        var binding = RootNativeTickOpcodeBindings.get(kind);
        return binding != null && StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(s -> {
            var f = s.skip(2).limit(2).toList();
            return f.size() == 2 && binding.exact(f.get(0), f.get(1));
        });
    }
    /** Negative sticky revocation only. Observation errors never alter the original operation's outcome. */
    public static void observerFailure() {
        observerFailed = true;
        try { revokeCurrent(); } catch (Throwable ignored) { /* The sticky bit already refuses every ticket. */ }
    }
    private static boolean directRootEntry() {
        return StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(s -> {
            var f = s.skip(2).findFirst().orElse(null);
            return f != null && f.getDeclaringClass() == VeltraphBootstrap.class && f.getMethodName().equals("onClientTick");
        });
    }
    private static boolean nativeCurrent(TickFrame tick) {
        var run = RUN.get();
        return !observerFailed && tick != null && TICK.get() == tick && run == tick.run && run.current()
                && run.thread == Thread.currentThread() && tick.state.live(run.client);
    }
    private static void revokeCurrent() {
        var tick = TICK.get(); if (tick != null) {tick.state.revoke();if(tick.cameraSource!=null)tick.cameraSource.revoked=true;}
        var run = RUN.get(); if (run != null) run.revoked = true;
    }

    /** Called solely by the installed Minecraft.run invocation wrapper. */
    public static RunFrame enterRunTickOpcode(Minecraft client) {
        try {
        if (!directOpcodeCaller("RUN")
                || client == null || !client.isSameThread() || RUN.get() != null) { revokeCurrent(); return null; }
        var original=RootNativeSdlBoundaryPhase.originalClientRun(client);if(original==null||!original.current())return null;
        var frame = new RunFrame(client,original); RUN.set(frame); return frame;

        } catch (Throwable ignored) { observerFailure(); return null; }
    }
    public static void leaveRunTickOpcode(RunFrame frame, boolean normal) {
        try {
        if (frame == null) return;
        if (RUN.get() != frame || !normal || TICK.get() != null) frame.revoked = true;
        if (RUN.get() == frame) RUN.remove();

        } catch (Throwable ignored) { observerFailure(); return; }
    }
    /** Called solely by the installed Minecraft.runTick invocation wrapper. */
    public static TickFrame enterTickOpcode(Minecraft client) {
        try {
        var run = RUN.get();
        if (!directOpcodeCaller("TICK") || run == null
                || !originalCameraRunCurrent(run,client) || run.revoked || run.client != client || run.thread != Thread.currentThread() || TICK.get() != null) {
            revokeCurrent(); return null;
        }
        var frame = new TickFrame(run); TICK.set(frame); return frame;

        } catch (Throwable ignored) { observerFailure(); return null; }
    }
    public static void leaveTickOpcode(TickFrame frame, boolean normal) {
        try {
        if (frame == null) return;
        boolean originalNormal = TICK.get() == frame && normal && EVENT.get() == null && LOOP.get() == null && SUBSCRIBER.get() == null;
        if (!originalNormal && frame.cameraSource!=null) frame.cameraSource.revoked=true;
        frame.state.closeTick(originalNormal);
        if (TICK.get() == frame) TICK.remove();

        } catch (Throwable ignored) { observerFailure(); return; }
    }

    /** Setup only, from the generated concrete Minecraft native opcode bridge. */
    public static EventFrame enterOriginalEndTickOpcode(ClientTickEvents.EndTick invoker, Minecraft client) {
        try {
            var tick = TICK.get();
            var binding = RootNativeTickOpcodeBindings.get("END");
            boolean direct = binding != null && StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(s -> {
                var f = s.skip(1).limit(3).toList();
                return f.size() == 3 && f.get(0).getDeclaringClass() == Minecraft.class
                        && f.get(0).getMethodName().equals("veltraph$nativeEndTickOpcode")
                        && binding.exact(f.get(1), f.get(2));
            });
            if (!direct || !nativeCurrent(tick) || tick.run.client != client || !originalCameraRunCurrent(tick.run,client)
                    || !singleOriginalTickBody() || invoker == null || EVENT.get() != null) {
                revokeCurrent(); return null;
            }
            var event = new EventFrame(tick, invoker);
            if (!tick.state.enterEvent(client, event)) return null;
            EVENT.set(event); return event;
        } catch (Throwable ignored) { observerFailure(); return null; }
    }
    public static void leaveOriginalEndTickOpcode(EventFrame event, boolean normal) {
        try {
            if (event == null) return;
            event.tick.state.leaveEvent(event, normal);
            if (EVENT.get() == event) EVENT.remove(); else event.tick.state.revoke();
        } catch (Throwable ignored) { observerFailure(); }
    }

    /** Entered at the original pinned Fabric array invoker method, before any listener. */
    public static LoopFrame enterOriginalEndTickLoop(Minecraft client) {
        try {
        var event = EVENT.get();
        if (!directOriginalLoopEntry()
                || event == null || !nativeCurrent(event.tick) || event.entered || LOOP.get() != null
                || event.tick.run.client != client || !directNativeEventBridge()) { revokeCurrent(); return null; }
        event.entered = true;
        var loop = new LoopFrame(event, client);
        if (!event.tick.state.enterLoop(client, event, loop)) return null;
        LOOP.set(loop); return loop;

        } catch (Throwable ignored) { observerFailure(); return null; }
    }
    private static boolean directOriginalLoopEntry() {
        return StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(s -> {
            var f = s.skip(2).findFirst().orElse(null);
            return f != null && f.getDeclaringClass() == ClientTickEvents.class && f.getMethodName().equals("lambda$static$3");
        });
    }
    private static boolean directNativeEventBridge() {
        return StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(s -> {
            var f = s.skip(2).limit(2).toList();
            return f.size() == 2 && f.get(0).getDeclaringClass() == ClientTickEvents.class
                    && f.get(0).getMethodName().equals("lambda$static$3")
                    && f.get(1).getDeclaringClass() == Minecraft.class
                    && f.get(1).getMethodName().equals("veltraph$nativeEndTickOpcode");
        });
    }
    private static boolean directOriginalSubscriberOpcode() {
        return StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(s -> {
            var f = s.skip(2).limit(2).toList();
            return f.size() == 2 && f.get(0).getDeclaringClass() == ClientTickEvents.class
                    && f.get(0).getMethodName().equals("veltraph$nativeEndTickSubscriberOpcode")
                    && f.get(1).getDeclaringClass() == ClientTickEvents.class
                    && f.get(1).getMethodName().equals("veltraph$originalEndTickLoopBody");
        });
    }
    public static void leaveOriginalEndTickLoop(LoopFrame loop, boolean normal) {
        try {
        if (loop == null) return;
        loop.event.tick.state.leaveLoop(loop, normal);
        if (LOOP.get() == loop) LOOP.remove(); else loop.event.tick.state.revoke();

        } catch (Throwable ignored) { observerFailure(); return; }
    }
    /** Exact array element receiver of Fabric's one original EndTick subscriber opcode. */
    public static SubscriberFrame enterOriginalSubscriber(ClientTickEvents.EndTick callback, Minecraft client) {
        try {
        var loop = LOOP.get();
        if (!directOriginalSubscriberOpcode()
                || loop == null || loop.client != client || !nativeCurrent(loop.event.tick) || SUBSCRIBER.get() != null) {
            revokeCurrent(); return null;
        }
        var subscriber = new SubscriberFrame(loop, callback);
        if (!loop.event.tick.state.enterSubscriber(client, loop, subscriber)) return null;
        SUBSCRIBER.set(subscriber); return subscriber;

        } catch (Throwable ignored) { observerFailure(); return null; }
    }
    public static void leaveOriginalSubscriber(SubscriberFrame subscriber, boolean normal) {
        try {
        if (subscriber == null) return;
        subscriber.loop.event.tick.state.leaveSubscriber(subscriber, normal);
        if (SUBSCRIBER.get() == subscriber) SUBSCRIBER.remove(); else subscriber.loop.event.tick.state.revoke();

        } catch (Throwable ignored) { observerFailure(); return; }
    }
    /** Must be the first operation of the actual private registered Root onClientTick. */
    public static RootTickTicket claimOriginalRootTick(Minecraft client, Object callback) {
        try {
        var subscriber = SUBSCRIBER.get();
        if (!directRootEntry() || subscriber == null || subscriber.loop.client != client
                || callback == null || callback != subscriber.callback
                || callback != VeltraphBootstrap.originalRootEndTickCallbackIdentity()
                || subscriber.ticket != null || !nativeCurrent(subscriber.loop.event.tick)) { revokeCurrent(); return null; }
        var ticket = new RootTickTicket(subscriber);
        if (!subscriber.loop.event.tick.state.claim(client, subscriber, ticket)) return null;
        subscriber.ticket = ticket; return ticket;

        } catch (Throwable ignored) { observerFailure(); return null; }
    }
    public static void finishRootTick(RootTickTicket ticket, boolean normal) {
        try {
        if (ticket == null) return;
        boolean original = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(s -> {
            var f = s.skip(1).findFirst().orElse(null);
            return f != null && f.getDeclaringClass() == VeltraphBootstrap.class && f.getMethodName().equals("onClientTick");
        });
        boolean current = ticket.current();
        ticket.closed = true;
        ticket.normalReturned = ticket.subscriber.loop.event.tick.state.leaveClaim(ticket, original && current && normal);
        if (ticket.subscriber.ticket == ticket) ticket.subscriber.ticket = null;

        } catch (Throwable ignored) { observerFailure(); return; }
    }
}
