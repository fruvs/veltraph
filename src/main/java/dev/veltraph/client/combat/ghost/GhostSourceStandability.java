package dev.veltraph.client.combat.ghost;

/** Exact Ghost caller mode (allow-side-step=false, direction=null); source registries are observations. */
public final class GhostSourceStandability {
    private GhostSourceStandability() {}
    public static boolean allowed(boolean bothBodyCellsSourcePassable,boolean loadedCandidate,
                                  boolean observedSupportRegistryAllows,boolean belowFence,
                                  boolean belowClosedFenceGate,boolean belowWall,
                                  boolean fluidAtCandidate,boolean fluidBelowCandidate){
        if(!bothBodyCellsSourcePassable||!loadedCandidate)return false;
        if(observedSupportRegistryAllows)return true;
        if(belowFence||belowClosedFenceGate||belowWall)return false;
        return fluidAtCandidate&&fluidBelowCandidate;
    }
}
