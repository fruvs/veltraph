package dev.veltraph.client.observation.nativeinput;

/** Before-derived SDK zero-turn arguments. No pose mutation, final-value adoption or Root authority. */
final class NativeStationaryPoseArithmetic {
 private NativeStationaryPoseArithmetic(){}
 static Result calculate(float beforeYaw,float beforePitch,double dx,double dy){
  if(!Float.isFinite(beforeYaw)||!Float.isFinite(beforePitch)||dx!=0.0D||dy!=0.0D)return null;
  // Entity.turn converts each double to float before multiplying by the actual 0.15F constant.
  float pitchDelta=(float)dy*0.15F;
  float yawDelta=(float)dx*0.15F;
  float firstPitch=beforePitch+pitchDelta;
  float yaw=beforeYaw+yawDelta;
  float firstAssigned=Math.clamp(firstPitch%360.0F,-90.0F,90.0F);
  // Actual Mth.clamp: preserve its comparison/Math.min order, including signed zero.
  float secondPitch=firstAssigned<-90.0F?-90.0F:Math.min(firstAssigned,90.0F);
  float finalPitch=Math.clamp(secondPitch%360.0F,-90.0F,90.0F);
  // Entity.setYRot assigns finite yaw directly; it does not wrap or take a remainder.
  return new Result(firstPitch,yaw,secondPitch,yaw,finalPitch);
 }
 static final class Result {
  private final float firstPitch,yaw,secondPitch,finalYaw,finalPitch;
  private Result(float firstPitch,float yaw,float secondPitch,float finalYaw,float finalPitch){this.firstPitch=firstPitch;this.yaw=yaw;this.secondPitch=secondPitch;this.finalYaw=finalYaw;this.finalPitch=finalPitch;}
  float firstPitchArgument(){return firstPitch;}
  float yawArgument(){return yaw;}
  float secondPitchArgument(){return secondPitch;}
  float finalYaw(){return finalYaw;}
  float finalPitch(){return finalPitch;}
 }
}
