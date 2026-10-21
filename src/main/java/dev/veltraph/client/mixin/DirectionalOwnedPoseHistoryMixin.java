package dev.veltraph.client.mixin;
import dev.veltraph.client.rotation.OwnedPoseSetterHistory;
import dev.veltraph.client.service.mc.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Entity.class)
public abstract class DirectionalOwnedPoseHistoryMixin implements OwnedPoseHistoryCarrier {
 @Unique private final OwnedPoseSetterHistory veltraph$poses=new OwnedPoseSetterHistory();
 @Unique private boolean veltraph$unrecordablePrefix;
 @Unique private boolean veltraph$recordReceiver(){return ((Object)this).getClass()==LocalPlayer.class;}
 @Unique private boolean veltraph$receiver(){var c=Minecraft.getInstance();return c!=null&&c.player!=null&&c.player.getClass()==LocalPlayer.class&&(Object)c.player==(Object)this||c!=null&&veltraph$birthReceiver();}
 @Unique private boolean veltraph$birthReceiver(){try{return dev.veltraph.client.observation.nativeinput.NativePlayerBirthProof.constructorReceiverCurrent((Entity)(Object)this);}catch(RuntimeException|Error unavailable){return false;}}
 @Unique private Object veltraph$actualPoseClaim(Entity receiver,boolean yaw,float value){
  var owned=McOwnedRotation.claimPoseApplication(receiver,yaw,value);Object actual=owned;
  if(actual==null)try{actual=dev.veltraph.client.observation.nativeinput.McNativeManualInputLineage.claimNativePoseSetter(receiver,yaw,value);}catch(RuntimeException|Error unavailable){}
  if(actual==null)try{actual=dev.veltraph.client.observation.nativeinput.NativePlayerBirthProof.claimNativePoseSetter(receiver,yaw,value);}catch(RuntimeException|Error unavailable){}
  try{dev.veltraph.client.observation.nativeinput.McNativeManualInputLineage.nativePoseSetterEntered(receiver,yaw,value,actual);}catch(RuntimeException|Error unavailable){}
  return actual;
 }
 @Unique private boolean veltraph$native(){var c=Minecraft.getInstance();return c!=null&&c.isSameThread()&&c.player!=null&&c.player.getClass()==LocalPlayer.class&&(Object)c.player==(Object)this||c!=null&&veltraph$birthReceiver();}
 public Object ownedPoseHistorySnapshot(){return !veltraph$unrecordablePrefix&&veltraph$poses!=null&&veltraph$native()?veltraph$poses.snapshot():null;}
 public boolean ownedPoseHistoryCurrent(Object exact){return !veltraph$unrecordablePrefix&&veltraph$poses!=null&&veltraph$native()&&veltraph$poses.current(exact);}
 public boolean ownedPoseHistoryAdvanced(Object before,Object after,java.util.List<?> yaw,java.util.List<?> pitch){return !veltraph$unrecordablePrefix&&veltraph$poses!=null&&veltraph$native()&&veltraph$poses.advanced(before,after,yaw,pitch);}
 @Inject(method="setYRot(F)V",at=@At("HEAD"),require=1)
 private void veltraph$yawBegin(float value,CallbackInfo ci){if(!veltraph$recordReceiver())return;if(veltraph$poses==null){veltraph$unrecordablePrefix=true;return;}boolean nativeThread=veltraph$native()&&!veltraph$unrecordablePrefix;veltraph$poses.begin(true,nativeThread?veltraph$actualPoseClaim((Entity)(Object)this,true,value):null,nativeThread);}
 @Inject(method="setYRot(F)V",at=@At("RETURN"),require=1)
 private void veltraph$yawReturned(float value,CallbackInfo ci){if(!veltraph$recordReceiver())return;if(veltraph$poses==null){veltraph$unrecordablePrefix=true;return;}if(veltraph$native()&&!veltraph$unrecordablePrefix){veltraph$poses.returned(true);try{dev.veltraph.client.observation.nativeinput.McNativeManualInputLineage.nativePoseSetterReturned((Entity)(Object)this,true);}catch(RuntimeException|Error unavailable){}}else veltraph$poses.invalidate();}
 @Inject(method="setXRot(F)V",at=@At("HEAD"),require=1)
 private void veltraph$pitchBegin(float value,CallbackInfo ci){if(!veltraph$recordReceiver())return;if(veltraph$poses==null){veltraph$unrecordablePrefix=true;return;}boolean nativeThread=veltraph$native()&&!veltraph$unrecordablePrefix;veltraph$poses.begin(false,nativeThread?veltraph$actualPoseClaim((Entity)(Object)this,false,value):null,nativeThread);}
 @Inject(method="setXRot(F)V",at=@At("RETURN"),require=1)
 private void veltraph$pitchReturned(float value,CallbackInfo ci){if(!veltraph$recordReceiver())return;if(veltraph$poses==null){veltraph$unrecordablePrefix=true;return;}if(veltraph$native()&&!veltraph$unrecordablePrefix){veltraph$poses.returned(false);try{dev.veltraph.client.observation.nativeinput.McNativeManualInputLineage.nativePoseSetterReturned((Entity)(Object)this,false);}catch(RuntimeException|Error unavailable){}}else veltraph$poses.invalidate();}
}
