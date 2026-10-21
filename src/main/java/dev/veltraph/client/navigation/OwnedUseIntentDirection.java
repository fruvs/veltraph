package dev.veltraph.client.navigation;
import dev.veltraph.client.world.Vec3;
/** Refusal-only segment math matching actual Shared native Instant Transmission candidate construction. */
public final class OwnedUseIntentDirection {
 private OwnedUseIntentDirection(){}
 public static boolean forward(Vec3 origin,Vec3 originalGoal,Vec3 actualDeviceGoal){
  if(!SharedMovementPolicy.finite(origin)||!SharedMovementPolicy.finite(originalGoal)||!SharedMovementPolicy.finite(actualDeviceGoal))return false;
  double length=origin.distanceTo(originalGoal),step=origin.distanceTo(actualDeviceGoal);
  if(!Double.isFinite(length)||!Double.isFinite(step)||step<=0||length<step+.5)return false;
  var expected=new Vec3(origin.x()+(originalGoal.x()-origin.x())*step/length,origin.y()+(originalGoal.y()-origin.y())*step/length,origin.z()+(originalGoal.z()-origin.z())*step/length);
  return expected.distanceTo(actualDeviceGoal)<=1e-6;
 }
}
