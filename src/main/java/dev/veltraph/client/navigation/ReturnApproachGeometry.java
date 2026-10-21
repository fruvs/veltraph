package dev.veltraph.client.navigation;
import dev.veltraph.client.world.Vec3;
import java.util.Optional;
/** Pure approach endpoint only. Native open/support/range/capability/source proofs remain mandatory. */
public final class ReturnApproachGeometry {
 private ReturnApproachGeometry(){}
 public static Optional<Vec3> landing(Vec3 origin,Vec3 canonicalGoal,double actualRange){
  if(!SharedMovementPolicy.finite(origin)||!SharedMovementPolicy.finite(canonicalGoal)
    ||!Double.isFinite(actualRange)||actualRange<=0||actualRange>64)return Optional.empty();
  double horizontal=origin.horizontalDistanceTo(canonicalGoal);
  if(!Double.isFinite(horizontal)||horizontal<actualRange+2.35)return Optional.empty();
  var next=new Vec3(origin.x()+(canonicalGoal.x()-origin.x())*actualRange/horizontal,origin.y(),
    origin.z()+(canonicalGoal.z()-origin.z())*actualRange/horizontal);
  return SharedMovementPolicy.finite(next)&&next.distanceTo(canonicalGoal)<origin.distanceTo(canonicalGoal)
    ?Optional.of(next):Optional.empty();
 }
 public static boolean exact(Vec3 origin,Vec3 canonicalGoal,Vec3 deviceGoal,double actualRange){
  var expected=landing(origin,canonicalGoal,actualRange);
  return expected.isPresent()&&SharedMovementPolicy.finite(deviceGoal)&&expected.get().distanceTo(deviceGoal)<=1e-6;
 }
}
