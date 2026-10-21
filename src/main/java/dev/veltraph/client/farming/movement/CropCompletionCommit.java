package dev.veltraph.client.farming.movement;
/** Final geometry observation must precede late permission revalidation. */
public final class CropCompletionCommit {
 private CropCompletionCommit(){}
 /** Observe geometry first, then reevaluate late custody and only then publish rotation. */
 public static boolean rotateAfterObservation(java.util.function.BooleanSupplier observe,java.util.function.BooleanSupplier latePermission,Runnable rotate){if(!observe.getAsBoolean()||!latePermission.getAsBoolean())return false;rotate.run();return true;}
 public static boolean mayAttack(boolean actualRayMatches,boolean fullyFreshGeometry,CropCompletionPermission latePermission,boolean sameToolGuardScope){return actualRayMatches&&fullyFreshGeometry&&latePermission!=null&&latePermission.allows()&&sameToolGuardScope;}
}
