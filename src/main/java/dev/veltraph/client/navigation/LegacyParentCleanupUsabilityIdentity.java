package dev.veltraph.client.navigation;
/** Evidence predicate only; physical observations cannot manufacture cleanup or admission receipts. */
public final class LegacyParentCleanupUsabilityIdentity {
 private LegacyParentCleanupUsabilityIdentity(){}
 public record Evidence(boolean thread,boolean playerWorld,boolean alive,boolean lava,boolean passenger,boolean paused,boolean frozen,
  boolean screenAbsent,boolean scoping,boolean playerCamera,boolean inventoryMenu,boolean cursorEmpty,boolean lockOwned,boolean options,boolean rotationInstalled,boolean sameStore){}
 public static boolean usable(Evidence e){return e!=null&&e.thread()&&e.playerWorld()&&e.alive()&&!e.lava()&&!e.passenger()&&!e.paused()&&!e.frozen()
  &&e.screenAbsent()&&!e.scoping()&&e.playerCamera()&&e.inventoryMenu()&&e.cursorEmpty()&&e.lockOwned()&&e.options()&&e.rotationInstalled()&&e.sameStore();}
}
