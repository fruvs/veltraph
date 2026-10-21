package dev.veltraph.client.mixin;
import dev.veltraph.client.navigation.mc.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
@Mixin(Entity.class)
public abstract class GroundDirectionalAccelerationVetoMixin {
 @Unique private int veltraph$depth;@Unique private Object veltraph$call;
 @WrapMethod(method="moveRelative(FLnet/minecraft/world/phys/Vec3;)V")
 private void veltraph$relative(float speed,Vec3 input,Operation<Void> original){var c=Minecraft.getInstance();
  var p=c!=null&&c.isSameThread()&&c.player!=null&&c.player.getClass()==LocalPlayer.class&&(Object)c.player==(Object)this?c.player:null;
  var carrier=(Object)p instanceof GroundDirectionalInputCarrier actual?actual:null;veltraph$call=null;
  if(veltraph$depth++!=0){if(carrier!=null)carrier.directionalInvalidateCall();try{original.call(speed,input);}finally{veltraph$depth--;}return;}
  veltraph$call=carrier==null?null:carrier.directionalRelativeCall(input);
  try{original.call(speed,input);}finally{veltraph$call=null;veltraph$depth--;}}
 @WrapOperation(method="moveRelative(FLnet/minecraft/world/phys/Vec3;)V",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/Entity;getInputVector(Lnet/minecraft/world/phys/Vec3;FF)Lnet/minecraft/world/phys/Vec3;"),require=1)
 private Vec3 veltraph$yaw(Vec3 input,float speed,float yaw,Operation<Vec3> original){var c=Minecraft.getInstance();
  var p=c!=null&&c.isSameThread()&&c.player!=null&&c.player.getClass()==LocalPlayer.class&&(Object)c.player==(Object)this?c.player:null;
  var carrier=(Object)p instanceof GroundDirectionalInputCarrier actual?actual:null;
  var frame=veltraph$depth==1&&veltraph$call!=null&&carrier!=null&&carrier.directionalRelativeCurrent(veltraph$call,input)?carrier.directionalTravelFrame(input):null;
  boolean before=frame==null||frame.directionalPoseCurrent()&&Math.abs(dev.veltraph.client.rotation.SourceRotationStepper.wrap(p.getYRot()-yaw))<.001;
  var result=original.call(input,speed,yaw);
  if(frame==null||veltraph$depth!=1||veltraph$call==null||carrier==null||!carrier.directionalRelativeCurrent(veltraph$call,input)||carrier.directionalTravelFrame(input)!=frame||!frame.directionalInputCurrent())return result;
  return before&&frame.directionalPoseCurrent()&&Math.abs(dev.veltraph.client.rotation.SourceRotationStepper.wrap(p.getYRot()-yaw))<.001?result:Vec3.ZERO;
 }
}
