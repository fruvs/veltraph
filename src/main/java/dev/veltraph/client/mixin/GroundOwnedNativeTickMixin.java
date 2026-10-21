package dev.veltraph.client.mixin;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.veltraph.client.navigation.mc.GroundOwnedNativeTickCarrier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
@Mixin(LivingEntity.class)
public abstract class GroundOwnedNativeTickMixin implements GroundOwnedNativeTickCarrier {
    @Unique private Object veltraph$nativeAiIdentity;
    public Object nativeOwnedAiStepIdentity(){return veltraph$nativeAiIdentity;}
    @WrapOperation(method="tick()V",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/LivingEntity;aiStep()V"),require=1)
    private void veltraph$actualTickAi(LivingEntity receiver,Operation<Void> original){var c=Minecraft.getInstance();
        boolean normal=c!=null&&c.isSameThread()&&c.player==receiver&&receiver.getClass()==LocalPlayer.class&&veltraph$nativeAiIdentity==null;
        Object prior=veltraph$nativeAiIdentity;veltraph$nativeAiIdentity=normal?new Object():null;
        try{original.call(receiver);}finally{veltraph$nativeAiIdentity=prior;}
    }
}
