package dev.veltraph.client.mixin;
import dev.veltraph.client.navigation.mc.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
@Mixin(LivingEntity.class)
public abstract class GroundDirectionalTravelVetoMixin implements GroundDirectionalInputCarrier {
 private static final class Call {final Object ai;boolean revoked,returned;Call(Object ai){this.ai=ai;}}
 @Unique private Object veltraph$issuedApplyAi,veltraph$issuedTravelAi,veltraph$issuedRelativeAi;
 @Unique private Call veltraph$apply,veltraph$lastApply,veltraph$travelCall,veltraph$relative;
 @Unique private Vec3 veltraph$travelInput,veltraph$relativeInput;
 @Unique private LegacyTravelPublicationLease.Leg.ProfileInputFrame veltraph$frame;
 @Unique private boolean veltraph$receiver(LivingEntity entity){var c=Minecraft.getInstance();return c!=null&&c.isSameThread()&&c.player!=null&&c.player.getClass()==LocalPlayer.class&&c.player==entity&&(Object)entity==(Object)this;}
 @Unique private Object veltraph$ai(){var c=Minecraft.getInstance();return c!=null&&c.isSameThread()&&c.player!=null&&c.player.getClass()==LocalPlayer.class&&(Object)c.player==(Object)this&&(Object)c.player instanceof GroundOwnedNativeTickCarrier carrier?carrier.nativeOwnedAiStepIdentity():null;}
 @WrapOperation(method="aiStep()V",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/LivingEntity;applyInput()V"),require=1)
 private void veltraph$applyCall(LivingEntity entity,Operation<Void> original){if(!veltraph$receiver(entity)){directionalInvalidateCall();original.call(entity);return;}var ai=veltraph$ai();if(ai==null||veltraph$apply!=null||veltraph$travelCall!=null||veltraph$relative!=null||veltraph$issuedApplyAi==ai){directionalInvalidateCall();original.call(entity);return;}
  veltraph$issuedApplyAi=ai;var call=new Call(ai);veltraph$lastApply=null;veltraph$apply=call;boolean normal=false;
  try{original.call(entity);normal=true;}finally{call.returned=normal&&!call.revoked&&veltraph$ai()==ai;veltraph$lastApply=call;veltraph$apply=null;}}
 public Object directionalApplyCall(){return veltraph$apply!=null&&!veltraph$apply.revoked&&veltraph$apply.ai==veltraph$ai()?veltraph$apply:null;}
 public boolean directionalApplyCallCurrent(Object exact,boolean returned){var call=returned?veltraph$lastApply:veltraph$apply;return call!=null&&call==exact&&!call.revoked&&call.ai==veltraph$ai()&&(!returned||call.returned);}
 public void directionalInvalidateCall(){if(veltraph$apply!=null)veltraph$apply.revoked=true;if(veltraph$lastApply!=null)veltraph$lastApply.revoked=true;if(veltraph$travelCall!=null)veltraph$travelCall.revoked=true;if(veltraph$relative!=null)veltraph$relative.revoked=true;}
 @WrapOperation(method="aiStep()V",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/LivingEntity;travel(Lnet/minecraft/world/phys/Vec3;)V"),require=1)
 private void veltraph$consume(LivingEntity entity,Vec3 input,Operation<Void> original){if(!veltraph$receiver(entity)){directionalInvalidateCall();original.call(entity,input);return;}var ai=veltraph$ai();
  if(ai==null||veltraph$travelCall!=null||veltraph$issuedTravelAi==ai){directionalInvalidateCall();original.call(entity,input);return;}
  veltraph$issuedTravelAi=ai;
  var frame=(Object)entity instanceof GroundDirectionalInputCarrier carrier?carrier.directionalAppliedFrame(input):null;
  var call=new Call(ai);var sent=frame!=null&&!frame.directionalPoseCurrent()?new Vec3(0,input.y,0):input;
  veltraph$travelCall=call;veltraph$travelInput=sent;veltraph$frame=frame;
  try{original.call(entity,sent);}finally{veltraph$travelCall=null;veltraph$travelInput=null;veltraph$frame=null;}}
 public LegacyTravelPublicationLease.Leg.ProfileInputFrame directionalTravelFrame(Vec3 exact){return veltraph$travelCall!=null&&!veltraph$travelCall.revoked&&veltraph$travelCall.ai==veltraph$ai()
  &&exact==veltraph$travelInput&&veltraph$frame!=null&&veltraph$frame.directionalInputCurrent()?veltraph$frame:null;}
 @WrapOperation(method="handleRelativeFrictionAndCalculateMovement(Lnet/minecraft/world/phys/Vec3;F)Lnet/minecraft/world/phys/Vec3;",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/LivingEntity;moveRelative(FLnet/minecraft/world/phys/Vec3;)V"),require=1)
 private void veltraph$relativeCall(LivingEntity entity,float speed,Vec3 input,Operation<Void> original){if(!veltraph$receiver(entity)){directionalInvalidateCall();original.call(entity,speed,input);return;}var frame=directionalTravelFrame(input);var ai=veltraph$ai();
  if(frame==null||ai==null||veltraph$relative!=null||veltraph$issuedRelativeAi==ai){directionalInvalidateCall();original.call(entity,speed,input);return;}
  veltraph$issuedRelativeAi=ai;var call=new Call(ai);veltraph$relative=call;veltraph$relativeInput=input;
  try{original.call(entity,speed,input);}finally{veltraph$relative=null;veltraph$relativeInput=null;}}
 public Object directionalRelativeCall(Vec3 exact){return veltraph$relative!=null&&!veltraph$relative.revoked&&veltraph$relative.ai==veltraph$ai()&&exact==veltraph$relativeInput&&directionalTravelFrame(exact)!=null?veltraph$relative:null;}
 public boolean directionalRelativeCurrent(Object exact,Vec3 input){return exact!=null&&directionalRelativeCall(input)==exact;}
}
