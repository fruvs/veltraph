package dev.veltraph.client.observation.unexpected;

import net.minecraft.client.*;
import net.minecraft.client.input.KeyEvent;
import dev.veltraph.client.world.Vec3;
import dev.veltraph.client.observation.unexpected.UnexpectedBehaviorMonitor.*;

/** Explicit pre-dispatch hooks. Disabled safely until Bootstrap attaches its local observer. */
public final class UnexpectedActions {
    private static McUnexpectedBehaviorMonitor observer;
    private UnexpectedActions() {}
    static McUnexpectedBehaviorMonitor attach(McUnexpectedBehaviorMonitor next) {var prior=observer;observer=next;return prior;}
    static void detach(McUnexpectedBehaviorMonitor prior,McUnexpectedBehaviorMonitor restore) {if(observer==prior)observer=restore;}
    private static long now() {return System.currentTimeMillis();}
    public static void rotation(Minecraft client,float yaw,float pitch) {if(observer!=null&&client.isSameThread()&&client.player!=null&&(Math.abs(Math.IEEEremainder(client.player.getYRot()-yaw,360))>.01||Math.abs(client.player.getXRot()-Math.max(-90,Math.min(90,pitch)))>.01))observer.intent(Kind.ROTATION,Value.rotation(yaw,Math.max(-90,Math.min(90,pitch))),.01,now(),250);}
    public static void held(Minecraft client,KeyMapping key,boolean down) {if(observer!=null&&client.isSameThread())observer.held(key,down,now());}
    /** Passive pre/post association for an actual McHeldKeys method; this token grants no input authority. */
    public static final class HeldPublicationObservation {
        private final McUnexpectedBehaviorMonitor original;private final Object frame;
        private HeldPublicationObservation(McUnexpectedBehaviorMonitor original,Object frame){this.original=original;this.frame=frame;}
    }
    public static HeldPublicationObservation beginHeldPublication(Minecraft client,KeyMapping key,boolean down){
        if(observer==null||!client.isSameThread())return null;var original=observer;var frame=original.beginHeldPublication(key,down,now());
        return frame==null?null:new HeldPublicationObservation(original,frame);
    }
    public static void completeHeldPublication(HeldPublicationObservation observation,dev.veltraph.client.service.mc.McHeldKeys.HeldPublicationReceipt actual){
        if(observation!=null&&actual!=null&&actual.observedBy(observation)&&observer==observation.original)observation.original.completeHeldPublication(observation.frame,actual,now());
    }
    public static void selectedSlot(Minecraft client,int slot) {if(slot<0||slot>8)throw new IllegalArgumentException("Invalid hotbar slot");if(observer!=null&&client.isSameThread()&&client.player!=null&&client.player.getInventory().getSelectedSlot()!=slot)observer.intent(Kind.SELECTED_SLOT,Value.number(slot),0,now(),500);}
    /** Only call after the existing recovery owner has a specific landing, before dispatching it. */
    public static void expectTeleport(Minecraft client,Vec3 target,double radius,long ttlMillis) {if(observer!=null&&client.isSameThread())observer.intent(Kind.POSITION,Value.position(target.x(),target.y(),target.z()),radius,now(),ttlMillis);}
    /** Exact issued landing; cancellation affects only this receipt on its captured observer. */
    public static Runnable expectOwnedTeleport(Minecraft client,Vec3 target,double radius,long ttlMillis,java.util.function.BooleanSupplier valid){
        if(observer==null||!client.isSameThread())return ()->{};
        return observer.scopedTeleportIntent(Value.position(target.x(),target.y(),target.z()),radius,now(),ttlMillis,valid);
    }
    public static void ownedLandingPacketApplied(){if(observer!=null)observer.ownedLandingPacket(now());}
    public static void keyboard(long window,int action,KeyEvent event) {if(observer!=null)observer.keyboard(window,action,event,now());}
    public static void nativeNavigationCommand(String submitted) {if(observer!=null)observer.nativeNavigationCommand(submitted,now());}
    public static void keyboardApplied(long window,int action,KeyEvent event) {if(observer!=null)observer.keyboardApplied(window,action,event,now());}
    public static void mouse(long window,double deltaX,double deltaY) {if(observer!=null)observer.mouse(window,deltaX,deltaY,now());}
    public static void mouseApplied() {if(observer!=null)observer.mouseApplied(now());}
    public static void scroll(long window) {if(observer!=null)observer.scroll(window,now());}
    public static void mouseButton(long window,int action) {if(observer!=null)observer.mouseButton(window,action,now());}
    public static void mouseButton(long window,int action,net.minecraft.client.input.MouseButtonInfo button){if(observer!=null)observer.mouseButton(window,action,button,now());}
    public static void mouseButtonApplied(long window,int action,net.minecraft.client.input.MouseButtonInfo button){if(observer!=null)observer.mouseButtonApplied(window,action,button,now());}
    public static void nativeSignal(Kind kind,Cause cause,long ttlMillis) {if(observer!=null)observer.signal(kind,cause,now(),ttlMillis);}
}
