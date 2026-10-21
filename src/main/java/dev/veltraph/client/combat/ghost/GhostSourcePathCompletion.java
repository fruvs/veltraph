package dev.veltraph.client.combat.ghost;

/** Source completion branch, with the actual visibility helper supplied as a native observation. */
public final class GhostSourcePathCompletion {
    public enum Next { REJECT_STALE, RETRY_SEARCH, DISPATCH_MELEE }
    private GhostSourcePathCompletion() {}
    public static Next decide(boolean exactCallbackTokenAndHandler,String resultLabel,boolean visibleTarget) {
        if (!exactCallbackTokenAndHandler) return Next.REJECT_STALE;
        if ("At destination".equals(resultLabel) && !visibleTarget) return Next.RETRY_SEARCH;
        return Next.DISPATCH_MELEE;
    }
}
