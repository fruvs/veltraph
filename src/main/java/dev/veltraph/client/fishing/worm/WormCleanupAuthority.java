package dev.veltraph.client.fishing.worm;
/** Cancellation-only authority. Never grants use, movement, selection or new camera plans. */
public final class WormCleanupAuthority {
    private WormCleanupAuthority(){}
    public record Observation(boolean clientThread,boolean samePlayerWorldConnection,boolean stillOwner,
            boolean cameraEpochUnchanged,boolean actualPlayerCamera,boolean inventoryMenu,boolean emptyCursor,
            boolean playerAlive,boolean toolSelected,boolean rodMatches,boolean toolMatches,boolean loadoutMatches){}
    public record Decision(boolean cancelCamera,boolean restoreRod){}
    public static Decision decide(Observation o){
        boolean custody=o.clientThread&&o.samePlayerWorldConnection&&o.stillOwner;
        boolean camera=custody&&o.cameraEpochUnchanged;
        boolean restore=custody&&o.actualPlayerCamera&&o.inventoryMenu&&o.emptyCursor&&o.playerAlive
                &&o.toolSelected&&o.rodMatches&&o.toolMatches&&o.loadoutMatches;
        return new Decision(camera,restore);
    }
}
