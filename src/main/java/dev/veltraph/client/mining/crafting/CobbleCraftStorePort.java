package dev.veltraph.client.mining.crafting;

/** A mining-owned craft/store resource loan. Implementations never run during a scheduled loan. */
public interface CobbleCraftStorePort {
    /** Empty means configured materials, targets and an empty reserved sink are ready. */
    String readiness(CobbleCraftStoreProfile profile);
    /** Observation only: exact materials/context can request stopping; no menu/start permission. */
    default boolean materialsReadyToStop(CobbleCraftStoreProfile profile){return false;}
    boolean start(CobbleCraftStoreProfile profile);
    void tick();
    void stop();
    boolean active();
    boolean failed();
    boolean allowsScreen(String title);
    String status();
    int stored();
}
