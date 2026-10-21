package dev.veltraph.client.observation.nativeinput;

import com.mojang.blaze3d.platform.SDLEventHandler;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import dev.veltraph.client.service.mc.RootNativeGameplayTickPhase;
import dev.veltraph.client.service.mc.RootNativeSdlBoundaryPhase;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonInfo;
import org.lwjgl.sdl.*;
import org.lwjgl.system.MemoryUtil;
import java.util.ArrayList;
import java.util.Arrays;

/** Actual native SDL poll/SDK queued-task origin only. This does not identify a human or rule out SDL_PushEvent. */
public final class NativeSdlInputLineage {
    private NativeSdlInputLineage(){}
    public enum Kind { KEYBOARD, BUTTON, MOTION }
    /** Installed final SDK fields, supplied by direct-field mixins rather than caller-populated registrations. */
    public interface InstalledCarrier {SDLEventHandler veltraph$originalSdlHandler();Window veltraph$originalSdlWindow();}
    public interface WindowCarrier {long veltraph$originalSdlHandle();}
    private static final ThreadLocal<Poll> POLL=new ThreadLocal<>();
    private static final ThreadLocal<Dispatch> DISPATCH=new ThreadLocal<>();
    private static final ThreadLocal<Delivery> DELIVERY=new ThreadLocal<>();
    private static final ThreadLocal<Lambda> LAMBDA=new ThreadLocal<>();
    private static final ThreadLocal<Handler> HANDLER=new ThreadLocal<>();
    private static final ThreadLocal<TaskRun> TASK_RUN=new ThreadLocal<>();
    public static final class TaskRun {
        private final Delivery delivery;private final TaskRun prior;private boolean claimed,closed;
        private TaskRun(Delivery delivery,TaskRun prior){this.delivery=delivery;this.prior=prior;}
    }
    /** Cookie from the original BlockableEventLoop.doRunTask Runnable.run opcode, not a run ancestor. */
    public static TaskRun beginTaskRun(Object actualLoop,Runnable actualTask){
        if(!NativeSdlInputOpcodeBindings.actual("TASK_RUN")||!(actualTask instanceof Delivery d)||actualLoop!=d.event.poll.client
                ||d.revoked||d.started||!d.event.payloadCurrent())return null;
        var prior=TASK_RUN.get();if(prior!=null){prior.delivery.revoked=true;d.revoked=true;return null;}
        var next=new TaskRun(d,prior);TASK_RUN.set(next);return next;
    }
    public static void closeTaskRun(TaskRun exact){if(exact==null)return;exact.closed=true;if(TASK_RUN.get()==exact){if(exact.prior==null)TASK_RUN.remove();else TASK_RUN.set(exact.prior);}}

    private static long serial;
    private static Receipt lastKeyboard,lastButton,lastMotion;
    private static boolean installed(Minecraft c,SDLEventHandler h,Window w){return c!=null&&c instanceof InstalledCarrier fields
        &&fields.veltraph$originalSdlHandler()==h&&fields.veltraph$originalSdlWindow()==w&&(Object)w instanceof WindowCarrier;}
    private static boolean issuer(Class<?> exact){return StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(frames->{
        var caller=frames.filter(f->f.getDeclaringClass()!=NativeSdlInputLineage.class).findFirst().orElse(null);
        return caller!=null&&caller.getDeclaringClass()==exact;});}
    public static final class Poll {
        private final Minecraft client;private final SDLEventHandler handler;private final Window window;
        private final Thread thread=Thread.currentThread();private final Poll prior;
        private final RootNativeSdlBoundaryPhase.OriginalSdlPollCall root;
        private Event event;private boolean nativePending,closed,revoked;
        private Poll(Minecraft client,SDLEventHandler handler,Window window,Poll prior,RootNativeSdlBoundaryPhase.OriginalSdlPollCall root){this.client=client;this.handler=handler;this.window=window;this.prior=prior;this.root=root;}
        private boolean current(){return !closed&&!revoked&&POLL.get()==this&&Thread.currentThread()==thread&&client.isSameThread()&&root.clientIdentity()==client&&root.handlerIdentity()==handler&&root.current()&&installed(client,handler,window);}
    }
    public static Poll beginPoll(Minecraft client,SDLEventHandler handler,Window window){
        if(!NativeSdlInputOpcodeBindings.actual("POLL_METHOD"))return null;
        var root=RootNativeGameplayTickPhase.claimOriginalSdlPoll(handler,client);
        if(root==null||root.clientIdentity()!=client||root.handlerIdentity()!=handler||!root.current()
            ||!installed(client,handler,window)||!client.isSameThread())return null;
        var prior=POLL.get();if(prior!=null)prior.revoked=true;
        var next=new Poll(client,handler,window,prior,root);next.revoked=prior!=null;POLL.set(next);return next;
    }
    public static void closePoll(Poll exact){if(exact==null)return;try{exact.closed=true;if(exact.event!=null&&!exact.event.completed)exact.event.revoked=true;}
        finally{if(POLL.get()==exact){if(exact.prior==null)POLL.remove();else POLL.set(exact.prior);}}}
    public static final class NativePoll {
        private final Poll poll;private final SDL_Event buffer;private final long address;
        private boolean returned,closed,storageAlive=true;private NativePoll(Poll poll,SDL_Event buffer){this.poll=poll;this.buffer=buffer;address=buffer.address();}
    }
    public static NativePoll beginNativePoll(SDL_Event actualBuffer){var p=POLL.get();
        if(!NativeSdlInputOpcodeBindings.actual("POLL_NATIVE")||p==null||!p.current()||p.nativePending||actualBuffer==null||actualBuffer.getClass()!=SDL_Event.class)return null;
        if(p.event!=null&&!p.event.completed)p.event.revoked=true;p.event=null;p.nativePending=true;return new NativePoll(p,actualBuffer);
    }
    /** Positive native result is retained only for the exact original selected native call/buffer. */
    public static void nativePollReturned(NativePoll exact,boolean actualResult){if(!NativeSdlInputOpcodeBindings.actual("POLL_NATIVE")||exact==null||exact.closed||exact.returned)return;
        exact.returned=true;var p=exact.poll;
        if(!actualResult||!p.current()||!p.nativePending||exact.address!=exact.buffer.address())return;
        var type=exact.buffer.type();Kind kind=type==SDLEvents.SDL_EVENT_KEY_DOWN||type==SDLEvents.SDL_EVENT_KEY_UP?Kind.KEYBOARD:
            type==SDLEvents.SDL_EVENT_MOUSE_BUTTON_DOWN||type==SDLEvents.SDL_EVENT_MOUSE_BUTTON_UP?Kind.BUTTON:
            type==SDLEvents.SDL_EVENT_MOUSE_MOTION?Kind.MOTION:null;
        if(kind==null)return;var bytes=bytes(exact.buffer);if(bytes==null)return;
        p.event=new Event(exact,kind,type,bytes);
    }
    public static void closeNativePoll(NativePoll exact){if(exact==null)return;exact.closed=true;exact.poll.nativePending=false;}
    /** Exact actual try-with-resources close opcode revokes observation BEFORE native memory is freed. */
    public static void beforeNativeBufferClose(SDL_Event exactBuffer){if(!NativeSdlInputOpcodeBindings.actual("BUFFER_CLOSE"))return;var p=POLL.get();if(p==null)return;var e=p.event;
        if(e!=null&&e.buffer==exactBuffer){e.nativeCall.storageAlive=false;if(!e.completed)e.revoked=true;}}
    private static byte[] bytes(SDL_Event event){if(event==null||event.getClass()!=SDL_Event.class||event.address()==0)return null;
        var result=new byte[SDL_Event.SIZEOF];MemoryUtil.memByteBuffer(event.address(),SDL_Event.SIZEOF).get(result);return result;}
    private static final class Event {
        private final NativePoll nativeCall;private final Poll poll;private final SDL_Event buffer;private final Kind kind;private final int type;
        private final byte[] payload;private final long nativeAddress,window;
        private Dispatch dispatch;private Delivery delivery;private boolean dispatchClaimed,completed,revoked;
        private Event(NativePoll actual,Kind kind,int type,byte[] payload){nativeCall=actual;poll=actual.poll;buffer=actual.buffer;nativeAddress=actual.address;
            this.kind=kind;this.type=type;this.payload=payload;window=((WindowCarrier)(Object)poll.window).veltraph$originalSdlHandle();}
        private boolean payloadCurrent(){return !revoked&&nativeCall.storageAlive&&!poll.closed&&poll.root.current()&&installed(poll.client,poll.handler,poll.window)
            &&buffer.address()==nativeAddress&&Arrays.equals(payload,bytes(buffer));}
    }
    public static final class Dispatch {
        private final Event event;private final Dispatch prior;private boolean returned,closed,revoked;
        private Dispatch(Event event,Dispatch prior){this.event=event;this.prior=prior;}
    }
    public static Dispatch beginDispatch(SDLEventHandler exactHandler,SDL_Event exactBuffer,Kind kind){var p=POLL.get();var e=p==null?null:p.event;
        String role=switch(kind){case KEYBOARD->"KEY_DISPATCH";case BUTTON->"BUTTON_DISPATCH";case MOTION->"MOTION_DISPATCH";};
        if(!NativeSdlInputOpcodeBindings.actual(role)||p==null||!p.current()||e==null||e.kind!=kind||e.buffer!=exactBuffer||p.handler!=exactHandler
            ||e.dispatchClaimed||!e.nativeCall.returned||!e.nativeCall.closed||!e.payloadCurrent())return null;
        e.dispatchClaimed=true;var prior=DISPATCH.get();if(prior!=null)prior.revoked=true;
        var next=new Dispatch(e,prior);next.revoked=prior!=null;e.dispatch=next;DISPATCH.set(next);return next;
    }
    public static void finishDispatch(Dispatch exact,boolean normal){if(exact==null)return;
        try{exact.returned=normal&&!exact.revoked&&DISPATCH.get()==exact&&exact.event.payloadCurrent();if(!exact.returned)exact.event.revoked=true;
            complete(exact.event.delivery);}
        finally{exact.closed=true;if(DISPATCH.get()==exact){if(exact.prior==null)DISPATCH.remove();else DISPATCH.set(exact.prior);}}}
    public static final class Delivery implements Runnable {
        private final Event event;private final Dispatch dispatch;private final Runnable original;
        private final Thread thread=Thread.currentThread();private final ArrayList<Runnable> completions=new ArrayList<>();
        private boolean enqueueReturned,enqueueClosed,started,normal,closed,revoked,publishing,claimed;
        private int action,key,scan,mods,button;private double x,y,dx,dy;
        private Delivery(Event event,Dispatch dispatch,Runnable original){this.event=event;this.dispatch=dispatch;this.original=original;}
        @Override public void run(){
            var prior=DELIVERY.get();boolean selected=false;
            try{var task=TASK_RUN.get();selected=task!=null&&task.delivery==this&&!task.claimed&&!task.closed
                &&!started&&!revoked&&Thread.currentThread()==thread&&event.delivery==this;
                if(selected)task.claimed=true; // Consume exact actual native task opcode before any data getter.
                selected=selected&&event.payloadCurrent()&&dispatch.event==event&&!dispatch.revoked&&installed(event.poll.client,event.poll.handler,event.poll.window);
                if(prior!=null){prior.revoked=true;selected=false;}if(!selected)revoked=true;
                started=true;if(selected)DELIVERY.set(this);
            }catch(RuntimeException|Error unavailable){revoked=true;}
            boolean actualNormal=false;
            try{original.run();actualNormal=true;}
            finally{try{normal=actualNormal&&!revoked&&selected;closed=true;complete(this);}
                catch(RuntimeException|Error unavailable){revoked=true;}
                finally{if(selected&&DELIVERY.get()==this){if(prior==null)DELIVERY.remove();else DELIVERY.set(prior);}}}
        }
    }
    /** Original Runnable identity is captured before execute can synchronously deliver it. */
    public static Delivery captureQueue(Minecraft client,Runnable original){var d=DISPATCH.get();var e=d==null?null:d.event;
        String role=e==null?null:switch(e.kind){case KEYBOARD->"KEY_QUEUE";case BUTTON->"BUTTON_QUEUE";case MOTION->"MOTION_QUEUE";};
        if(role==null||!NativeSdlInputOpcodeBindings.actual(role)||d==null||d.closed||d.revoked||e==null||e.poll.client!=client||original==null
            ||e.delivery!=null||!e.payloadCurrent())return null;
        var next=new Delivery(e,d,original);e.delivery=next;return next;
    }
    public static void finishQueue(Delivery exact,boolean actualNormal){if(exact==null)return;
        exact.enqueueReturned=actualNormal;exact.enqueueClosed=true;if(!actualNormal)exact.revoked=true;complete(exact);
    }
    private static void complete(Delivery exact){if(exact==null||exact.publishing||exact.event.completed||!exact.enqueueClosed||!exact.closed||!exact.dispatch.returned)return;
        if(!exact.enqueueReturned||!exact.normal||exact.revoked||exact.event.revoked||!exact.claimed||!exact.event.payloadCurrent())return;
        exact.publishing=true;exact.event.completed=true;
        try{for(var completion:exact.completions)try{completion.run();}catch(RuntimeException|Error unavailable){}
            if(serial==Long.MAX_VALUE)return;var receipt=new Receipt(++serial,exact);
            switch(exact.event.kind){case KEYBOARD->lastKeyboard=receipt;case BUTTON->lastButton=receipt;case MOTION->lastMotion=receipt;}
        }finally{exact.completions.clear();exact.publishing=false;}
    }
    public static final class Lambda {
        private final Delivery delivery;private final Lambda prior;private boolean closed,revoked;
        private Lambda(Delivery delivery,Lambda prior){this.delivery=delivery;this.prior=prior;}
    }
    public static Lambda beginLambda(SDLEventHandler handler,Kind kind,SDL_Event event,KeyEvent key,int action,MouseButtonInfo button,long window,SDL_MouseMotionEvent motion){
        var d=DELIVERY.get();String role=switch(kind){case KEYBOARD->"KEY_LAMBDA";case BUTTON->"BUTTON_LAMBDA";case MOTION->"MOTION_LAMBDA";};
        if(!NativeSdlInputOpcodeBindings.actual(role)||d==null||d.revoked||d.closed||d.event.kind!=kind||d.event.poll.handler!=handler
            ||!d.event.payloadCurrent()||kind==Kind.KEYBOARD&&event!=d.event.buffer||kind!=Kind.KEYBOARD&&window!=d.event.window)return null;
        var prior=LAMBDA.get();if(prior!=null){prior.revoked=true;d.revoked=true;return null;}
        if(kind==Kind.KEYBOARD){var nativeKey=d.event.buffer.key();int expected=d.event.type==SDLEvents.SDL_EVENT_KEY_UP?0:nativeKey.repeat()?2:1;
            if(key==null||action!=expected||key.key()!=nativeKey.scancode()||key.keycode()!=nativeKey.key()||key.modifiers()!=nativeKey.mod()) {d.revoked=true;return null;}}
        if(kind==Kind.MOTION&&(motion==null||motion.address()!=d.event.buffer.motion().address())){d.revoked=true;return null;}
        if(kind==Kind.BUTTON&&(button==null||action!=(d.event.type==SDLEvents.SDL_EVENT_MOUSE_BUTTON_DOWN?1:0)
            ||button.button()!=(Byte.toUnsignedInt(d.event.buffer.button().button())-1))){d.revoked=true;return null;}
        var next=new Lambda(d,prior);LAMBDA.set(next);return next;
    }
    public static void closeLambda(Lambda exact,boolean normal){if(exact==null)return;
        if(!normal||exact.revoked)exact.delivery.revoked=true;exact.closed=true;
        if(LAMBDA.get()==exact){if(exact.prior==null)LAMBDA.remove();else LAMBDA.set(exact.prior);}}
    public static final class Handler {
        private final Delivery delivery;private final Lambda lambda;private final Object handler,argument;private final long window;
        private final int action;private final double x,y,dx,dy;private boolean claimed,closed;
        private Handler(Delivery delivery,Lambda lambda,Object handler,Object argument,long window,int action,double x,double y,double dx,double dy){
            this.delivery=delivery;this.lambda=lambda;this.handler=handler;this.argument=argument;this.window=window;this.action=action;this.x=x;this.y=y;this.dx=dx;this.dy=dy;}
    }
    public static Handler beginHandler(Object exactHandler,Kind kind,long window,Object event,int action,double x,double y,double dx,double dy){var l=LAMBDA.get();var d=DELIVERY.get();
        String role=switch(kind){case KEYBOARD->"KEY_HANDLER";case BUTTON->"BUTTON_HANDLER";case MOTION->"MOTION_HANDLER";};
        if(!NativeSdlInputOpcodeBindings.actual(role)||l==null||l.closed||l.revoked||d==null||l.delivery!=d||d.revoked||d.claimed||d.event.kind!=kind
            ||window!=d.event.window||!d.event.payloadCurrent())return null;
        Object expected=kind==Kind.KEYBOARD?d.event.poll.client.keyboardHandler:d.event.poll.client.mouseHandler;
        if(exactHandler!=expected||HANDLER.get()!=null){d.revoked=true;return null;}
        var next=new Handler(d,l,exactHandler,event,window,action,x,y,dx,dy);HANDLER.set(next);return next;
    }
    public static void closeHandler(Handler exact,boolean normal){if(exact==null)return;if(!normal)exact.delivery.revoked=true;exact.closed=true;if(HANDLER.get()==exact)HANDLER.remove();}
    private static Handler claim(Kind kind,Object handler,long window,Object argument,int action,double dx,double dy){var h=HANDLER.get();
        if(h==null||h.closed||h.claimed||h.delivery.revoked||h.delivery.event.kind!=kind||h.handler!=handler||h.window!=window
            ||h.argument!=argument||h.action!=action||Double.doubleToRawLongBits(h.dx)!=Double.doubleToRawLongBits(dx)
            ||Double.doubleToRawLongBits(h.dy)!=Double.doubleToRawLongBits(dy))return null;
        // Consume before all native reads: a callback cannot borrow the original one-use handler entry.
        h.claimed=true;
        if(!h.delivery.event.payloadCurrent()){h.delivery.revoked=true;return null;}
        h.delivery.claimed=true;return h;
    }
    public static boolean claimKeyboard(long window,int action,KeyEvent event){var selected=HANDLER.get();var h=claim(Kind.KEYBOARD,selected==null?null:selected.handler,window,event,action,0,0);
        if(h==null)return false;h.delivery.action=action;h.delivery.key=event.key();h.delivery.scan=event.keycode();h.delivery.mods=event.modifiers();return true;}
    public static boolean claimButton(long window,MouseButtonInfo info,int action){var selected=HANDLER.get();var h=claim(Kind.BUTTON,selected==null?null:selected.handler,window,info,action,0,0);
        if(h==null)return false;h.delivery.action=action;h.delivery.button=info.button();h.delivery.mods=info.modifiers();return true;}
    public static boolean claimMotion(Object handler,long window,double dx,double dy){var h=HANDLER.get();var claimed=claim(Kind.MOTION,handler,window,null,0,dx,dy);
        if(claimed==null)return false;h.delivery.x=h.x;h.delivery.y=h.y;h.delivery.dx=dx;h.delivery.dy=dy;return true;}
    /** Only genuine existing manual opcode completion can register a deferred data publication. */
    public static boolean deferManualCompletion(Runnable exactPublication){var h=HANDLER.get();
        if(!issuer(McNativeManualInputLineage.class)||h==null||!h.claimed||h.closed||h.delivery.revoked||exactPublication==null)return false;
        h.delivery.completions.add(exactPublication);return true;
    }
    /** Immutable normal native-backend delivery observation only, never a source or manual admission token. */
    public static final class Receipt {
        private final long serial;private final Minecraft client;private final Kind kind;private final long window;
        private final int action,key,scan,mods,button;private final double x,y,dx,dy;
        private Receipt(long serial,Delivery actual){this.serial=serial;client=actual.event.poll.client;kind=actual.event.kind;window=actual.event.window;
            action=actual.action;key=actual.key;scan=actual.scan;mods=actual.mods;button=actual.button;x=actual.x;y=actual.y;dx=actual.dx;dy=actual.dy;}
        public long serial(){return serial;}public Kind kind(){return kind;}public long window(){return window;}
        public int action(){return action;}public int key(){return key;}public int scan(){return scan;}public int modifiers(){return mods;}public int button(){return button;}
        public double x(){return x;}public double y(){return y;}public double dx(){return dx;}public double dy(){return dy;}
    }
    public static Receipt peekLastDelivery(Minecraft client,Kind kind){var value=switch(kind){case KEYBOARD->lastKeyboard;case BUTTON->lastButton;case MOTION->lastMotion;};return value!=null&&value.client==client?value:null;}
}
