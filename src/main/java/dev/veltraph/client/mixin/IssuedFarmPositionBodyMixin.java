package dev.veltraph.client.mixin;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import dev.veltraph.client.rewarp.mc.IssuedFarmAppliedLanding;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
@Mixin(ClientPacketListener.class)
public abstract class IssuedFarmPositionBodyMixin {
 @WrapOperation(method="setValuesFromPositionPacket(Lnet/minecraft/world/entity/PositionMoveRotation;Ljava/util/Set;Lnet/minecraft/world/entity/Entity;Z)Z",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/Entity;setPos(Lnet/minecraft/world/phys/Vec3;)V"),require=1)
 private static void veltraph$issuedFarmActualSetPosition(Entity player,Vec3 position,Operation<Void> original){boolean normal=false;try{original.call(player,position);normal=true;}finally{IssuedFarmAppliedLanding.nativePositionReturned(player,normal);}}
}
