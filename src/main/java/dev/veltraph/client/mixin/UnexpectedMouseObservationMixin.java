package dev.veltraph.client.mixin;
import dev.veltraph.client.observation.unexpected.UnexpectedActions;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(MouseHandler.class)
public class UnexpectedMouseObservationMixin implements dev.veltraph.client.service.mc.NormalIdleMouseCarrier,dev.veltraph.client.observation.nativeinput.NativeManualMouseHistoryCarrier {
    @org.spongepowered.asm.mixin.Unique private final dev.veltraph.client.rotation.NormalIdleInputVersion veltraph$normalIdleMouse=new dev.veltraph.client.rotation.NormalIdleInputVersion();
    @org.spongepowered.asm.mixin.Unique private final java.util.ArrayDeque<Boolean> veltraph$normalIdleMoves=new java.util.ArrayDeque<>();
    public String cachedNormalIdleMouseDiagnostic(){return veltraph$normalIdleMouse.cachedCurrentDiagnostic();}
    public boolean nativeManualMouseAdvancedOnce(Object before,Object after){return before instanceof dev.veltraph.client.rotation.NormalIdleInputVersion.Snapshot a&&after instanceof dev.veltraph.client.rotation.NormalIdleInputVersion.Snapshot b&&veltraph$normalIdleMouse.unscopedAdvancedOnce(a,b);}
    public Object normalIdleMouseSnapshot(){return veltraph$normalIdleMouse.snapshot();}
    public boolean normalIdleMouseCurrent(Object exact){return exact instanceof dev.veltraph.client.rotation.NormalIdleInputVersion.Snapshot s&&veltraph$normalIdleMouse.current(s);}
    @org.spongepowered.asm.mixin.Unique private boolean veltraph$nativeWindow(long window){var c=net.minecraft.client.Minecraft.getInstance();return c!=null&&c.isSameThread()&&c.getWindow().handle()==window;}
    @Shadow private boolean ignoreFirstMove;
    @Inject(method="onMove(JDDDD)V",at=@At("HEAD"))
    private void veltraph$nativeMouse(long window,double x,double y,double deltaX,double deltaY,CallbackInfo ci) {var c=net.minecraft.client.Minecraft.getInstance();boolean thread=c!=null&&c.isSameThread();boolean entered=!ignoreFirstMove&&veltraph$nativeWindow(window);if(thread){veltraph$normalIdleMoves.push(entered);if(entered)veltraph$normalIdleMouse.begin(null,true);else if(!ignoreFirstMove)veltraph$normalIdleMouse.invalidate();}else veltraph$normalIdleMouse.invalidate();if(!ignoreFirstMove)UnexpectedActions.mouse(window,deltaX,deltaY);}
    @Inject(method="turnPlayer(D)V",at=@At("TAIL"))
    private void veltraph$appliedNativeMouse(double timeDelta,CallbackInfo ci) {UnexpectedActions.mouseApplied();}
    @Inject(method="onScroll(JDD)V",at=@At("TAIL"))
    private void veltraph$nativeScroll(long window,double horizontal,double vertical,CallbackInfo ci) {if(vertical!=0)UnexpectedActions.scroll(window);}
    @Inject(method="onButton(JLnet/minecraft/client/input/MouseButtonInfo;I)V",at=@At("HEAD"))
    private void veltraph$nativeMouseButton(long window,MouseButtonInfo button,int action,CallbackInfo ci) {if(veltraph$nativeWindow(window))veltraph$normalIdleMouse.begin(null,true);else veltraph$normalIdleMouse.invalidate();UnexpectedActions.mouseButton(window,action,button);}
    @Inject(method="onMove(JDDDD)V",at=@At("RETURN"),require=1)
    private void veltraph$normalIdleMouseReturned(long window,double x,double y,double dx,double dy,CallbackInfo ci){var c=net.minecraft.client.Minecraft.getInstance();if(c==null||!c.isSameThread()){veltraph$normalIdleMouse.invalidate();return;}if(veltraph$normalIdleMoves.isEmpty())veltraph$normalIdleMouse.invalidate();else if(veltraph$normalIdleMoves.pop())veltraph$normalIdleMouse.end(true);}
    @Inject(method="onScroll(JDD)V",at=@At("HEAD"),require=1)
    private void veltraph$normalIdleScrollBegin(long window,double horizontal,double vertical,CallbackInfo ci){if(vertical!=0){if(veltraph$nativeWindow(window))veltraph$normalIdleMouse.begin(null,true);else veltraph$normalIdleMouse.invalidate();}}
    @Inject(method="onScroll(JDD)V",at=@At("RETURN"),require=1)
    private void veltraph$normalIdleScrollReturned(long window,double horizontal,double vertical,CallbackInfo ci){var c=net.minecraft.client.Minecraft.getInstance();if(c==null||!c.isSameThread()){veltraph$normalIdleMouse.invalidate();return;}if(vertical!=0)veltraph$normalIdleMouse.end(true);}
    @Inject(method="onButton(JLnet/minecraft/client/input/MouseButtonInfo;I)V",at=@At("RETURN"),require=1)
    private void veltraph$normalIdleMouseButtonReturned(long window,MouseButtonInfo button,int action,CallbackInfo ci){UnexpectedActions.mouseButtonApplied(window,action,button);var c=net.minecraft.client.Minecraft.getInstance();if(c==null||!c.isSameThread()){veltraph$normalIdleMouse.invalidate();return;}veltraph$normalIdleMouse.end(true);}
}
