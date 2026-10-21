package dev.veltraph.client.combat.ghost;

/** Exact ordered source registry overrides applied to the observed source passable registry. */
public final class GhostSourceSupportPolicy {
    public enum Kind { OTHER, FENCE, FENCE_GATE, WALL, WEB, STANDING_SIGN, CARPET, MOSSY_CARPET, SNOW, LILY_PAD }
    private GhostSourceSupportPolicy() {}
    public static boolean allows(boolean sourceRegistryPassable,Kind kind){
        if(kind==null)throw new IllegalArgumentException("Observed block family required");
        return switch(kind){
            case FENCE,FENCE_GATE,WALL,WEB,STANDING_SIGN -> false;
            case CARPET,MOSSY_CARPET,SNOW,LILY_PAD -> true;
            case OTHER -> !sourceRegistryPassable;
        };
    }
}
