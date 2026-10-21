package dev.veltraph.client.combat.ghost;

/** Native block classification observations from the source registry scan and explicit exceptions. */
public final class GhostSourceBlockPassability {
    public enum Kind { AIR, FENCE_GATE, SNOW_LAYER, OTHER }
    private GhostSourceBlockPassability() {}
    public static boolean passable(boolean registryObserved,Kind kind,boolean gateOpen,int snowLayers,
                                   boolean observedRegistryContainsBlock){
        if(!registryObserved||kind==null)return false;
        return switch(kind){case AIR->true;case FENCE_GATE->gateOpen;case SNOW_LAYER->snowLayers==1;case OTHER->observedRegistryContainsBlock;};
    }
}
