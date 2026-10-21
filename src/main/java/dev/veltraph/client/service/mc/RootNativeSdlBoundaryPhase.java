package dev.veltraph.client.service.mc;

import com.mojang.blaze3d.platform.SDLEventHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;

/** Exact Main/run and native SDL/mouse call custody. No claim of human origin below SDL's native queue. */
public final class RootNativeSdlBoundaryPhase {
    private RootNativeSdlBoundaryPhase() {}
    private static volatile boolean observerFailed;
    private static final java.util.concurrent.atomic.AtomicBoolean originalMainIssued=new java.util.concurrent.atomic.AtomicBoolean();
    private static final ThreadLocal<OriginalMainRunCall> MAIN=new ThreadLocal<>();
    private static final ThreadLocal<OriginalClientRunSource> WHOLE=new ThreadLocal<>();
    private static final ThreadLocal<OriginalSdlOuterCall> OUTER=new ThreadLocal<>();
    private static final ThreadLocal<SdlInnerFrame> INNER=new ThreadLocal<>();
    private static final ThreadLocal<MouseFrame> MOUSE=new ThreadLocal<>();

    public static final class OriginalMainRunCall {
        private final Minecraft client;private final RootNativeSdlRunState state;private boolean closed;
        private OriginalMainRunCall(Minecraft client){this.client=client;state=new RootNativeSdlRunState(client);}
    }
    public static final class OriginalClientRunSource {
        private final OriginalMainRunCall main;private boolean closed;
        private OriginalClientRunSource(OriginalMainRunCall main){this.main=main;}
        public Minecraft clientIdentity(){return main.client;}
        public boolean current(){try{return !observerFailed&&!closed&&!main.closed&&MAIN.get()==main&&WHOLE.get()==this
            &&main.state.current(main.client,this);}catch(Throwable unavailable){observerFailure();return false;}}
    }
    public static final class OriginalSdlOuterCall {
        private final OriginalClientRunSource run;private final SDLEventHandler handler;private boolean consumed,revoked,closed;
        private OriginalSdlOuterCall(OriginalClientRunSource run,SDLEventHandler handler){this.run=run;this.handler=handler;}
        private boolean current(){return !closed&&!revoked&&OUTER.get()==this&&run.current();}
    }
    public static final class SdlInnerFrame {
        private final OriginalSdlOuterCall outer;private OriginalSdlPollCall claim;private boolean consumed,revoked,closed;
        private SdlInnerFrame(OriginalSdlOuterCall outer){this.outer=outer;}
        private boolean current(){return !closed&&!revoked&&INNER.get()==this&&outer.current();}
    }
    public static final class OriginalSdlPollCall {
        private final SdlInnerFrame frame;
        private OriginalSdlPollCall(SdlInnerFrame frame){this.frame=frame;}
        public Minecraft clientIdentity(){return frame.outer.run.clientIdentity();}
        public SDLEventHandler handlerIdentity(){return frame.outer.handler;}
        public boolean current(){try{return !observerFailed&&frame.claim==this&&frame.current();}
            catch(Throwable unavailable){observerFailure();return false;}}
    }
    public static final class MouseFrame {
        private final OriginalClientRunSource run;private final RootNativeGameplayTickPhase.RunFrame nativeRun;
        private final MouseHandler handler;private OriginalMouseAccumulationCall claim;private boolean consumed,accumulationEntered,revoked,closed;
        private MouseFrame(OriginalClientRunSource run,RootNativeGameplayTickPhase.RunFrame nativeRun,MouseHandler handler){
            this.run=run;this.nativeRun=nativeRun;this.handler=handler;}
        private boolean current(){return !closed&&!revoked&&MOUSE.get()==this&&run.current()
            &&nativeRun.clientIdentity()==run.clientIdentity()&&nativeRun.current();}
        public boolean originalInvocationCurrent(Minecraft client,MouseHandler actualHandler){
            try{return !observerFailed&&clientIdentity()==client&&handler==actualHandler&&current();}
            catch(Throwable unavailable){observerFailure();return false;}}
        private Minecraft clientIdentity(){return run.clientIdentity();}
    }
    public static final class OriginalMouseAccumulationCall {
        private final MouseFrame frame;
        private final dev.veltraph.client.observation.nativeinput.NativeMouseInvocationPhase.EntityTurn originalInvocation;
        private OriginalMouseAccumulationCall(MouseFrame frame,dev.veltraph.client.observation.nativeinput.NativeMouseInvocationPhase.EntityTurn originalInvocation){
            this.frame=frame;this.originalInvocation=originalInvocation;}
        public Minecraft clientIdentity(){return frame.run.clientIdentity();}
        public MouseHandler handlerIdentity(){return frame.handler;}
        public boolean current(){try{return !observerFailed&&frame.claim==this&&frame.current()&&originalInvocation!=null
            &&originalInvocation.current(clientIdentity(),handlerIdentity());}
            catch(Throwable unavailable){observerFailure();return false;}}
    }
    private static boolean opcodeCaller(String kind){var binding=RootNativeSdlOpcodeBindings.get(kind);
        return binding!=null&&StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(s->{var f=s.skip(2).limit(2).toList();
            return f.size()==2&&binding.exact(f.get(0),f.get(1));});}
    private static boolean claimCaller(String facade,String facadeDescriptor,Class<?> actor,String method,String descriptor){
        return StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(s->{var f=s.skip(2).limit(2).toList();
            return f.size()==2&&f.get(0).getDeclaringClass()==RootNativeGameplayTickPhase.class
                &&f.get(0).getMethodName().equals(facade)&&f.get(0).getDescriptor().equals(facadeDescriptor)
                &&f.get(1).getDeclaringClass()==actor&&f.get(1).getMethodName().equals(method)&&f.get(1).getDescriptor().equals(descriptor);});
    }
    private static boolean originalRunClaimCaller(){return StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(s->{var f=s.skip(2).findFirst().orElse(null);
        return f!=null&&f.getDeclaringClass()==RootNativeGameplayTickPhase.class&&f.getMethodName().equals("enterRunTickOpcode")
            &&f.getDescriptor().equals("(Lnet/minecraft/client/Minecraft;)Ldev/veltraph/client/service/mc/RootNativeGameplayTickPhase$RunFrame;");});}
    /** Invalidate only the retained input boundary; a refused public handler call is not a new Main/run invocation. */
    private static void revokeBoundary(){var inner=INNER.get();if(inner!=null)inner.revoked=true;var outer=OUTER.get();if(outer!=null)outer.revoked=true;
        var mouse=MOUSE.get();if(mouse!=null)mouse.revoked=true;}
    private static void revokeRun(){revokeBoundary();var whole=WHOLE.get();if(whole!=null)whole.main.state.revoke();
        var main=MAIN.get();if(main!=null)main.state.revoke();}
    public static void observerFailure(){observerFailed=true;try{revokeRun();}catch(Throwable unavailable){}}

    public static OriginalMainRunCall enterOriginalMainRunOpcode(Minecraft client){try{
        if(MAIN.get()!=null||WHOLE.get()!=null){revokeRun();return null;}
        if(observerFailed||client==null||!opcodeCaller("MAIN_RUN")){revokeRun();return null;}
        if(!originalMainIssued.compareAndSet(false,true)){revokeRun();return null;}var actual=new OriginalMainRunCall(client);MAIN.set(actual);return actual;
    }catch(Throwable unavailable){observerFailure();return null;}}
    public static void leaveOriginalMainRunOpcode(OriginalMainRunCall actual,boolean normal){try{
        if(actual==null)return;if(MAIN.get()!=actual||WHOLE.get()!=null||!normal)actual.state.revoke();actual.closed=true;
        if(MAIN.get()==actual)MAIN.remove();else revokeRun();
    }catch(Throwable unavailable){observerFailure();}}
    /** Whole-method wrapper is essential: a nested public Minecraft.run cannot reuse an outer Main source. */
    public static OriginalClientRunSource enterOriginalClientRun(Minecraft client){try{
        if(WHOLE.get()!=null){revokeRun();return null;}var main=MAIN.get();
        if(observerFailed||main==null||main.closed||main.client!=client||!opcodeCaller("WHOLE_RUN")){revokeRun();return null;}
        var actual=new OriginalClientRunSource(main);if(!main.state.claim(client,actual))return null;WHOLE.set(actual);return actual;
    }catch(Throwable unavailable){observerFailure();return null;}}
    public static void leaveOriginalClientRun(OriginalClientRunSource actual,boolean normal){try{
        if(actual==null)return;boolean valid=actual.current()&&OUTER.get()==null&&INNER.get()==null&&MOUSE.get()==null;
        actual.closed=true;actual.main.state.close(actual,normal&&valid);if(WHOLE.get()==actual)WHOLE.remove();else revokeRun();
    }catch(Throwable unavailable){observerFailure();}}
    /** Read-only exact Main/run source captured only by the genuine existing native runTick opcode issuer. */
    public static OriginalClientRunSource originalClientRun(Minecraft client){try{var actual=WHOLE.get();
        return originalRunClaimCaller()&&actual!=null&&actual.clientIdentity()==client&&actual.current()?actual:null;
    }catch(Throwable unavailable){observerFailure();return null;}}
    public static OriginalSdlOuterCall enterOriginalSdlOuterOpcode(Minecraft client,SDLEventHandler handler){try{
        if(OUTER.get()!=null||INNER.get()!=null){revokeBoundary();return null;}var run=WHOLE.get();
        if(observerFailed||run==null||!run.current()||run.clientIdentity()!=client||handler==null||!opcodeCaller("SDL_OUTER")){revokeBoundary();return null;}
        var actual=new OriginalSdlOuterCall(run,handler);OUTER.set(actual);return actual;
    }catch(Throwable unavailable){observerFailure();return null;}}
    public static void leaveOriginalSdlOuterOpcode(OriginalSdlOuterCall actual,boolean normal){try{
        if(actual==null)return;if(!actual.current()||INNER.get()!=null||!normal){actual.revoked=true;revokeBoundary();}actual.closed=true;
        if(OUTER.get()==actual)OUTER.remove();else revokeBoundary();
    }catch(Throwable unavailable){observerFailure();}}
    public static SdlInnerFrame enterOriginalSdlInnerOpcode(SDLEventHandler handler){try{
        if(INNER.get()!=null){revokeBoundary();return null;}var outer=OUTER.get();
        if(observerFailed||outer==null||!outer.current()||outer.handler!=handler||outer.consumed||!opcodeCaller("SDL_INNER")){revokeBoundary();return null;}
        outer.consumed=true;var actual=new SdlInnerFrame(outer);INNER.set(actual);return actual;
    }catch(Throwable unavailable){observerFailure();return null;}}
    public static void leaveOriginalSdlInnerOpcode(SdlInnerFrame actual,boolean normal){try{
        if(actual==null)return;if(!actual.current()||!normal){actual.revoked=true;revokeBoundary();}actual.closed=true;
        if(INNER.get()==actual)INNER.remove();else revokeBoundary();
    }catch(Throwable unavailable){observerFailure();}}
    public static OriginalSdlPollCall claimOriginalSdlPoll(SDLEventHandler handler,Minecraft client){try{var actual=INNER.get();
        if(!claimCaller("claimOriginalSdlPoll","(Lcom/mojang/blaze3d/platform/SDLEventHandler;Lnet/minecraft/client/Minecraft;)Ldev/veltraph/client/service/mc/RootNativeSdlBoundaryPhase$OriginalSdlPollCall;",
                dev.veltraph.client.observation.nativeinput.NativeSdlInputLineage.class,"beginPoll","(Lnet/minecraft/client/Minecraft;Lcom/mojang/blaze3d/platform/SDLEventHandler;Lcom/mojang/blaze3d/platform/Window;)Ldev/veltraph/client/observation/nativeinput/NativeSdlInputLineage$Poll;")
            ||actual==null||!actual.current()||actual.consumed||actual.outer.handler!=handler||actual.outer.run.clientIdentity()!=client){revokeBoundary();return null;}
        actual.consumed=true;var claim=new OriginalSdlPollCall(actual);actual.claim=claim;return claim;
    }catch(Throwable unavailable){observerFailure();return null;}}
    public static MouseFrame enterMouseAccumulationOpcode(Minecraft client,MouseHandler handler){try{
        if(MOUSE.get()!=null){revokeBoundary();return null;}var run=WHOLE.get();
        if(observerFailed||run==null||!run.current()||run.clientIdentity()!=client||handler==null||client.mouseHandler!=handler||!opcodeCaller("MOUSE_ACCUMULATE")){revokeBoundary();return null;}
        var nativeRun=RootNativeGameplayTickPhase.originalRunTickForMouse(client);
        if(nativeRun==null||nativeRun.clientIdentity()!=client||!nativeRun.current()){revokeBoundary();return null;}
        var actual=new MouseFrame(run,nativeRun,handler);MOUSE.set(actual);return actual;
    }catch(Throwable unavailable){observerFailure();return null;}}
    public static void leaveMouseAccumulationOpcode(MouseFrame actual,boolean normal){try{
        if(actual==null)return;if(!actual.current()||!normal){actual.revoked=true;revokeBoundary();}actual.closed=true;
        if(MOUSE.get()==actual)MOUSE.remove();else revokeBoundary();
    }catch(Throwable unavailable){observerFailure();}}
    /** Original whole accumulation entry consumes this exact root opcode before any callback reads. */
    public static MouseFrame captureOriginalMouseInvocationWindow(Minecraft client,MouseHandler handler){try{
        boolean original=StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(stream->{var f=stream.skip(1).findFirst().orElse(null);
            return f!=null&&f.getDeclaringClass()==dev.veltraph.client.observation.nativeinput.NativeMouseInvocationPhase.class
                &&f.getMethodName().equals("beginAccumulation")
                &&f.getDescriptor().equals("(Lnet/minecraft/client/MouseHandler;)Ldev/veltraph/client/observation/nativeinput/NativeMouseInvocationPhase$Accumulation;");});
        var actual=MOUSE.get();
        if(!original||actual==null||!actual.originalInvocationCurrent(client,handler)||actual.accumulationEntered){refuseOriginalMouseInvocation();return null;}
        actual.accumulationEntered=true;return actual;
    }catch(Throwable unavailable){observerFailure();return null;}}
    /** Refusal only: cannot create a root/native mouse source or alter authentic startup custody. */
    public static void refuseOriginalMouseInvocation(){try{var actual=MOUSE.get();if(actual!=null)actual.revoked=true;}
        catch(Throwable unavailable){observerFailure();}}
    public static OriginalMouseAccumulationCall claimOriginalMouseAccumulation(Minecraft client,MouseHandler handler){try{var actual=MOUSE.get();
        if(!claimCaller("claimOriginalMouseAccumulation","(Lnet/minecraft/client/Minecraft;Lnet/minecraft/client/MouseHandler;)Ldev/veltraph/client/service/mc/RootNativeSdlBoundaryPhase$OriginalMouseAccumulationCall;",
                dev.veltraph.client.observation.nativeinput.McNativeManualInputLineage.class,"beginNativeTurn","(Lnet/minecraft/world/entity/Entity;DD)Ldev/veltraph/client/observation/nativeinput/McNativeManualInputLineage$PoseEpisode;")
            ||actual==null||!actual.current()||actual.consumed||actual.handler!=handler||actual.run.clientIdentity()!=client){revokeBoundary();return null;}
        var invocation=dev.veltraph.client.observation.nativeinput.NativeMouseInvocationPhase.claimedOriginalMouseInvocation(client,handler);
        if(invocation==null||!invocation.current(client,handler)){refuseOriginalMouseInvocation();return null;}
        actual.consumed=true;var claim=new OriginalMouseAccumulationCall(actual,invocation);actual.claim=claim;return claim;
    }catch(Throwable unavailable){observerFailure();return null;}}
}
