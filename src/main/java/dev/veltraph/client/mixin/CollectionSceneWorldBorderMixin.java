package dev.veltraph.client.mixin;
import dev.veltraph.client.navigation.mc.CollectionSceneNativeAccess;
import net.minecraft.world.level.border.BorderStatus;
import net.minecraft.world.level.border.WorldBorder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(WorldBorder.class)
public abstract class CollectionSceneWorldBorderMixin implements CollectionSceneNativeAccess.Border {
    @Unique private Object veltraph$sceneBorderRevision;
    @Unique private boolean veltraph$sceneStaticBorder;
    public Object veltraph$sceneBorderRevision() { return veltraph$sceneBorderRevision; }
    public boolean veltraph$sceneStaticBorder() { return veltraph$sceneStaticBorder; }
    @Inject(method="<init>(Lnet/minecraft/world/level/border/WorldBorder$Settings;)V",
        at=@At("RETURN"),require=1)
    private void veltraph$sceneInitialStaticExtent(CallbackInfo ci) {
        veltraph$sceneBorderRevision=new Object(); veltraph$sceneStaticBorder=true;
    }
    @Inject(method={"setCenter(DD)V","setSize(D)V","setAbsoluteMaxSize(I)V","lerpSizeBetween(DDJJ)V",
        "applyInitialSettings(J)V"},at=@At("HEAD"),require=1)
    private void veltraph$sceneBorderMutation(CallbackInfo ci) { veltraph$sceneBorderRevision=new Object(); }
    @Inject(method="setSize(D)V",at=@At(value="FIELD",
        target="Lnet/minecraft/world/level/border/WorldBorder;extent:Lnet/minecraft/world/level/border/WorldBorder$BorderExtent;",
        opcode=181,shift=At.Shift.AFTER),require=1)
    private void veltraph$sceneStaticExtent(double size,CallbackInfo ci) { veltraph$sceneStaticBorder=true; }
    @Inject(method="lerpSizeBetween(DDJJ)V",at=@At(value="FIELD",
        target="Lnet/minecraft/world/level/border/WorldBorder;extent:Lnet/minecraft/world/level/border/WorldBorder$BorderExtent;",
        opcode=181,shift=At.Shift.AFTER),require=1)
    private void veltraph$sceneExtentFactory(double from,double to,long begin,long end,CallbackInfo ci) {
        // Exact native branch: equal endpoints construct StaticBorderExtent; others construct MovingBorderExtent.
        veltraph$sceneStaticBorder=from == to;
    }
    @Inject(method="tick()V",at=@At("RETURN"),require=1)
    private void veltraph$sceneSettledExtent(CallbackInfo ci) {
        // Native MovingBorderExtent.update constructs StaticBorderExtent before this return.
        if (!veltraph$sceneStaticBorder && ((WorldBorder)(Object)this).getStatus() == BorderStatus.STATIONARY) {
            veltraph$sceneStaticBorder=true; veltraph$sceneBorderRevision=new Object();
        }
    }
}
