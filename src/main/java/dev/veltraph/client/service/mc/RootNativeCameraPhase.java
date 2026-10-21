package dev.veltraph.client.service.mc;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;

/** One-use source observation from exact original native opcodes. Public camera effects remain unchanged. */
public final class RootNativeCameraPhase {
    private RootNativeCameraPhase(){}
    private enum Kind {TICK,FRAME}
    private static volatile boolean observerFailed;
    private static final ThreadLocal<Phase> ACTIVE=new ThreadLocal<>();
    private static final ThreadLocal<RenderOpcodeSource> RENDER=new ThreadLocal<>();
    private static final ThreadLocal<FrameOpcodeSource> UPDATE=new ThreadLocal<>();
    public static final class Phase {
        private final Minecraft client;private final Object receiver;private final DeltaTracker delta;private final Kind kind;
        private final Thread thread=Thread.currentThread();private McOwnedRotation.NativeMetadataInvocation actual;
        private RootNativeGameplayTickPhase.TickCameraSource tickSource;private FrameOpcodeSource frameSource;
        private boolean revoked,normal,closed;
        private Phase(Minecraft client,Object receiver,DeltaTracker delta,Kind kind){this.client=client;this.receiver=receiver;this.delta=delta;this.kind=kind;}
        private boolean nativeCurrent(){return !observerFailed&&!revoked&&Thread.currentThread()==thread&&client==Minecraft.getInstance()&&client.isSameThread()
            &&(kind==Kind.TICK?receiver==client&&tickSource!=null&&tickSource.originalCurrent(client,this)
                :receiver==client.gameRenderer&&delta==client.getDeltaTracker()&&frameSource!=null&&frameSource.originalCurrent(this));}
        private boolean entryWindowCurrent(){return kind==Kind.TICK?tickSource!=null&&tickSource.entryCurrent(client,this):frameSource!=null&&frameSource.entryCurrent(this);}
        /** Exact private SDK source entry before its one original native operation; identity binding only. */
        public boolean originalEntryCurrent(Minecraft exact){return exact==client&&ACTIVE.get()==this&&!closed&&!normal&&actual==null&&nativeCurrent()&&entryWindowCurrent();}
        boolean claim(McOwnedRotation.NativeMetadataInvocation observed){
            if(actual!=null||observed==null||closed||ACTIVE.get()!=this||!nativeCurrent()||!entryWindowCurrent()){revoked=true;return false;}
            actual=observed;return true;
        }
        boolean matches(McOwnedRotation.NativeMetadataInvocation observed){return actual==observed&&observed!=null;}
        // Completed original before*/cancel observation survives scope retirement; it never reopens admission.
        boolean normalCurrent(Minecraft exact){return exact==client&&normal&&!revoked&&nativeCurrent();}
    }
    /** Original runTick -> renderFrame operation only, before the real renderer update. */
    public static final class RenderOpcodeSource {
        private final Minecraft client;private final RootNativeGameplayTickPhase.RunFrame run;
        private final Thread thread=Thread.currentThread();private FrameOpcodeSource update;private boolean revoked,closed;
        private RenderOpcodeSource(Minecraft client,RootNativeGameplayTickPhase.RunFrame run){this.client=client;this.run=run;}
        private boolean current(){return !observerFailed&&!revoked&&!closed&&RENDER.get()==this&&Thread.currentThread()==thread
            &&RootNativeGameplayTickPhase.originalCameraRunCurrent(run,client);}
    }
    /** Actual renderFrame -> GameRenderer.update receiver/argument, consumed by its original HEAD once. */
    public static final class FrameOpcodeSource {
        private final RenderOpcodeSource render;private final GameRenderer receiver;private final DeltaTracker delta;
        private final Thread thread=Thread.currentThread();private Phase phase;private boolean revoked,closed;
        private FrameOpcodeSource(RenderOpcodeSource render,GameRenderer receiver,DeltaTracker delta){this.render=render;this.receiver=receiver;this.delta=delta;}
        private boolean originalCurrent(Phase exact){return !observerFailed&&!revoked&&!render.revoked&&phase==exact&&exact!=null
            &&Thread.currentThread()==thread&&render.update==this&&exact.client==render.client&&exact.receiver==receiver&&exact.delta==delta;}
        private boolean entryCurrent(Phase exact){return originalCurrent(exact)&&!closed&&UPDATE.get()==this&&render.current();}
        private boolean claim(Phase exact){if(phase!=null||exact==null||closed||UPDATE.get()!=this||!render.current()){revoked=true;return false;}phase=exact;return true;}
    }
    /** Negative-only fault latch; neither a phase setter nor a source grant. */
    public static void observerFailure(){observerFailed=true;try{var phase=ACTIVE.get();if(phase!=null)phase.revoked=true;
        var render=RENDER.get();if(render!=null)render.revoked=true;var update=UPDATE.get();if(update!=null)update.revoked=true;}catch(Throwable ignored){}}
    private static boolean actualNativeCaller(Kind kind,int skip){
        var binding=RootNativeCameraOpcodeBindings.get(kind==Kind.TICK?"TICK":"FRAME");
        return binding!=null&&StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(stream->{var f=stream.skip(skip).limit(2).toList();
            return f.size()==2&&binding.exact(f.get(0),f.get(1));});
    }
    private static boolean actualOpcodeCaller(String kind){
        var binding=RootNativeCameraOpcodeBindings.get(kind);
        return binding!=null&&StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(stream->{var f=stream.skip(2).limit(2).toList();
            return f.size()==2&&binding.exact(f.get(0),f.get(1));});
    }
    public static RenderOpcodeSource enterRenderOpcode(Minecraft client){
        try{var previous=RENDER.get();if(previous!=null){previous.revoked=true;return null;}
            if(observerFailed||client==null||!actualOpcodeCaller("RENDER"))return null;
            var run=RootNativeGameplayTickPhase.originalCameraRun(client);if(run==null)return null;
            var source=new RenderOpcodeSource(client,run);RENDER.set(source);return source;
        }catch(Throwable unavailable){observerFailure();return null;}
    }
    public static void closeRenderOpcode(RenderOpcodeSource source,boolean normal){
        try{if(source==null)return;source.closed=true;if(!normal||RENDER.get()!=source||UPDATE.get()!=null)source.revoked=true;
            if(RENDER.get()==source)RENDER.remove();}catch(Throwable unavailable){observerFailure();}
    }
    public static FrameOpcodeSource enterFrameOpcode(GameRenderer receiver,DeltaTracker delta){
        try{var previous=UPDATE.get();if(previous!=null){previous.revoked=true;return null;}
            var render=RENDER.get();if(observerFailed||render==null||!render.current()||!actualOpcodeCaller("UPDATE")
                ||receiver!=render.client.gameRenderer||delta!=render.client.getDeltaTracker())return null;
            if(render.update!=null){render.update.revoked=true;return null;}
            var source=new FrameOpcodeSource(render,receiver,delta);render.update=source;UPDATE.set(source);return source;
        }catch(Throwable unavailable){observerFailure();return null;}
    }
    public static void closeFrameOpcode(FrameOpcodeSource source,boolean normal){
        try{if(source==null)return;source.closed=true;if(!normal||UPDATE.get()!=source)source.revoked=true;
            if(UPDATE.get()==source)UPDATE.remove();}catch(Throwable unavailable){observerFailure();}
    }
    public static Phase enterTick(Minecraft client){return enter(client,client,null,Kind.TICK);}
    public static Phase enterFrame(Minecraft client,GameRenderer receiver,DeltaTracker delta){return enter(client,receiver,delta,Kind.FRAME);}
    private static Phase enter(Minecraft client,Object receiver,DeltaTracker delta,Kind kind){
        Phase created=null;
        try{
            var previous=ACTIVE.get();if(previous!=null){previous.revoked=true;return null;}
            if(observerFailed||client==null||!client.isSameThread()||!actualNativeCaller(kind,3))return null;
            var next=new Phase(client,receiver,delta,kind);
            if(kind==Kind.TICK){next.tickSource=RootNativeGameplayTickPhase.claimOriginalCameraTick(client,next);if(next.tickSource==null)return null;}
            else{var source=UPDATE.get();if(source==null||!source.claim(next))return null;next.frameSource=source;}
            if(!next.nativeCurrent()||!next.entryWindowCurrent())return null;
            ACTIVE.set(next);created=next;dev.veltraph.client.VeltraphBootstrap.observeOriginalNativeCameraPhase(next);return next;
        }catch(Throwable unavailable){observerFailure();if(created!=null){created.revoked=true;try{if(ACTIVE.get()==created)ACTIVE.remove();}catch(Throwable ignored){}}return null;}
    }
    static Phase peekTick(Minecraft client){var next=ACTIVE.get();return next!=null&&!next.closed&&next.kind==Kind.TICK&&next.client==client&&next.nativeCurrent()&&next.entryWindowCurrent()?next:null;}
    static Phase peekFrame(Minecraft client,DeltaTracker delta){var next=ACTIVE.get();return next!=null&&!next.closed&&next.kind==Kind.FRAME&&next.client==client&&next.delta==delta&&next.nativeCurrent()&&next.entryWindowCurrent()?next:null;}
    /** Only the original handler after its one existing before-camera API normally returns. */
    public static void normalReturned(Phase exact){
        if(exact==null)return;
        try{if(ACTIVE.get()!=exact||exact.closed||exact.normal||!exact.nativeCurrent()||!exact.entryWindowCurrent()||!actualNativeCaller(exact.kind,2)){exact.revoked=true;return;}
            exact.normal=true;McOwnedRotation.nativePhaseReturned(exact);
        }catch(Throwable unavailable){observerFailure();exact.revoked=true;}
    }
    public static void close(Phase exact){
        if(exact==null)return;
        try{exact.closed=true;if(!exact.normal)exact.revoked=true;if(ACTIVE.get()==exact)ACTIVE.remove();else exact.revoked=true;}
        catch(Throwable unavailable){observerFailure();exact.revoked=true;}
    }
}
