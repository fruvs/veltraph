package dev.veltraph.client.combat.heal;

/** Source event has two packet producers; it does not require a changed position or changed angle. */
public final class HealSourcePosePacketContract {
    public enum Kind { PLAYER_POSITION, PLAYER_ROTATION, OTHER }
    private HealSourcePosePacketContract() {}
    public static boolean eligible(Kind kind, boolean sameNativeContext, boolean localPlayer,
                                   boolean parentEnabled, boolean currentHandler,
                                   boolean postUseBuffer, boolean emittedUse, boolean hyperionLookDown) {
        return (kind == Kind.PLAYER_POSITION || kind == Kind.PLAYER_ROTATION)
                && sameNativeContext && localPlayer && parentEnabled && currentHandler
                && postUseBuffer && emittedUse && hyperionLookDown;
    }
}
