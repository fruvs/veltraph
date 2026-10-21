package dev.veltraph.client.mining;

import java.util.Objects;

/** Captured native use ownership; cancellation follows activation authority even after body drift. */
public record MiningUseHoldLease(Object owner,int enableRun,Object player,Object world,Object gameMode,String moduleId) {
    public MiningUseHoldLease {Objects.requireNonNull(owner);Objects.requireNonNull(player);Objects.requireNonNull(world);Objects.requireNonNull(gameMode);Objects.requireNonNull(moduleId);}
    public static boolean mayAcquire(Object caller,int activation,Object currentOwner,int currentRun,
            Object registeredOwner,String lockOwner,Object cameraOwner){
        return caller!=null && caller==currentOwner && caller==registeredOwner && activation==currentRun
                && "cobblestone".equals(lockOwner) && cameraOwner==caller;
    }
    public boolean mayRelease(Object currentOwner,int currentEnableRun,Object currentPlayer,Object currentWorld,
            Object currentGameMode,Object registeredOwner,String lockOwner) {
        return currentEnableRun==enableRun && (currentOwner==null || currentOwner==owner)
                && (registeredOwner==owner || registeredOwner==null && currentOwner==null)
                && (lockOwner==null || moduleId.equals(lockOwner));
    }
}
