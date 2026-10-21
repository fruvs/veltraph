package dev.veltraph.client.mixin;
import dev.veltraph.client.navigation.mc.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.*;
import net.minecraft.world.phys.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
@Mixin(LocalPlayer.class)
public abstract class GroundDirectionalInputVetoMixin implements GroundDirectionalInputCarrier {
 @Unique private int veltraph$depth,veltraph$tick;
 @Unique private Object veltraph$call,veltraph$appliedCall;
 @Unique private LegacyTravelPublicationLease.Leg.ProfileInputFrame veltraph$readFrame,veltraph$appliedFrame;
 @Unique private ClientInput veltraph$input;@Unique private float veltraph$x,veltraph$y,veltraph$z;
 @Unique private LocalPlayer veltraph$local(){var c=Minecraft.getInstance();return c!=null&&c.isSameThread()&&c.player!=null&&c.player.getClass()==LocalPlayer.class&&(Object)c.player==(Object)this?c.player:null;}
 @WrapMethod(method="applyInput()V")
 private void veltraph$apply(Operation<Void> original){veltraph$readFrame=null;veltraph$appliedFrame=null;veltraph$input=null;veltraph$appliedCall=null;veltraph$call=null;
  var p=veltraph$local();var carrier=(Object)p instanceof GroundDirectionalInputCarrier actual?actual:null;
  if(veltraph$depth++!=0){if(carrier!=null)carrier.directionalInvalidateCall();try{original.call();}finally{veltraph$depth--;}return;}
  veltraph$call=carrier==null?null:carrier.directionalApplyCall();boolean normal=false;
  try{original.call();normal=true;}finally{
   if(normal&&p!=null&&carrier!=null&&veltraph$call!=null&&carrier.directionalApplyCallCurrent(veltraph$call,false)&&veltraph$readFrame!=null&&p.input==veltraph$input
      &&veltraph$readFrame.directionalInputCurrent()&&Float.isFinite(p.xxa)&&Float.isFinite(p.yya)&&Float.isFinite(p.zza)){
    veltraph$x=p.xxa;veltraph$y=p.yya;veltraph$z=p.zza;veltraph$tick=p.tickCount;veltraph$appliedCall=veltraph$call;veltraph$appliedFrame=veltraph$readFrame;
   }else{veltraph$appliedFrame=null;veltraph$appliedCall=null;}veltraph$call=null;veltraph$depth--;}}
 @WrapOperation(method="applyInput()V",at=@At(value="INVOKE",target="Lnet/minecraft/client/player/ClientInput;getMoveVector()Lnet/minecraft/world/phys/Vec2;"),require=1)
 private Vec2 veltraph$vector(ClientInput input,Operation<Vec2> original){var vector=original.call(input);var p=veltraph$local();
  var call=(Object)p instanceof GroundDirectionalInputCarrier carrier?carrier:null;
  var frame=(Object)p instanceof GroundProfileMotionCarrier carrier?carrier.expectedProfileFrame():null;
  if(veltraph$depth!=1||veltraph$call==null||call==null||!call.directionalApplyCallCurrent(veltraph$call,false)||frame==null||p.input!=input||!frame.directionalVectorMatches(input,vector))return vector;
  veltraph$readFrame=frame;veltraph$input=input;return frame.directionalPoseCurrent()?vector:Vec2.ZERO;}
 public LegacyTravelPublicationLease.Leg.ProfileInputFrame directionalAppliedFrame(Vec3 exact){var p=veltraph$local();var frame=veltraph$appliedFrame;
  return p!=null&&veltraph$depth==0&&frame!=null&&(Object)p instanceof GroundDirectionalInputCarrier carrier&&carrier.directionalApplyCallCurrent(veltraph$appliedCall,true)
   &&p.tickCount==veltraph$tick&&p.input==veltraph$input&&exact!=null&&exact.x==(double)veltraph$x&&exact.y==(double)veltraph$y&&exact.z==(double)veltraph$z&&frame.directionalInputCurrent()?frame:null;}
}
