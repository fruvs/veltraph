package dev.veltraph.client.fishing;

/**
 * The Minecraft seam the {@link dev.veltraph.client.fishing.module.FishingModule} needs: read the world/
 * rod/bobber state and perform the single rod right-click. Kept an interface so the module's glue is
 * unit-testable with a fake (the pure FSM is {@link FishingRuntime}). The MC implementation is
 * {@code fishing/mc/McFishingServices}.
 */
public interface FishingServices {

    /** Only an active context-bound helper may acknowledge its own issued native transport. */
    default boolean consumeExpectedTeleport(dev.veltraph.client.world.Vec3 position,long now){return false;}

    /** Whether a world + local player are present. */
    boolean available();

    /** Whether a fishing rod is held in the main hand. */
    boolean hasFishingRod();

    /** Read-only startup validation; automatic selection can start with another tool held. */
    default String rodSetupConfigurationError(dev.veltraph.client.config.ConfigService config) { return ""; }
    default boolean tickRodSetup() { return false; }
    default boolean rodSetupReady() { return true; }
    default String rodSetupFailure() { return ""; }
    default String rodSetupStatus() { return "OFF"; }
    default void stopRodSetup() {}

    /** This tick's bobber snapshot (or {@link BobberFacts#none()} when no bobber is out). */
    BobberFacts bobber();

    default String trashConfigurationError(dev.veltraph.client.config.ConfigService config){return "";}
    default boolean tickTrash(boolean allowStart){return false;}
    default boolean trashActive(){return false;}
    default boolean trashAllowsScreen(String title){return false;}
    default String trashFailure(){return "";}
    default String trashStatus(){return "IDLE";}
    default void stopTrash(){}

    default String catchGuardConfigurationError(dev.veltraph.client.config.ConfigService config) { return ""; }
    default boolean catchGuardReady() { return true; }
    default String catchGuardFailure() { return ""; }
    default String catchGuardStatus() { return "OFF"; }
    default void stopCatchGuard() {}

    /** Right-click the held rod once (casts when no bobber is out, reels when one is). */
    void useRod();

    /** Snapshot the locally selected cast position and orientation for this session. */
    default void startFishing(dev.veltraph.client.config.ConfigService config) {}
    default boolean tickObstructionReturn(boolean allowStart) { return false; }
    default void stopObstructionReturn() {}
    default boolean obstructionActive() { return false; }
    default String obstructionFailure() { return ""; }
    default String obstructionStatus() { return "IDLE"; }

    default boolean tickWormHunting(boolean allowStart) { return false; }
    default void stopWormHunting() {}
    default String wormFailure() { return ""; }
    default String wormStatus() { return "IDLE"; }

    default void goldenFishMessage(String message) {}
    default boolean tickGoldenFish(boolean enabled) { return false; }
    default void stopGoldenFish() {}
    default boolean goldenFishActive() { return false; }
    default String goldenFishFailure() { return ""; }
    default String goldenFishStatus() { return "IDLE"; }

    default void fishingActionBar(String message) {}
    default boolean tickTotem(boolean allowStart) { return false; }
    default boolean totemActive() { return false; }
    default String totemFailure() { return ""; }
    default String totemStatus() { return "IDLE"; }
    default void stopTotem() {}

    default String baitConfigurationError(dev.veltraph.client.config.ConfigService config) { return ""; }
    default boolean tickBait(boolean allowStart) { return false; }
    default boolean baitActive() { return false; }
    default boolean baitAllowsScreen(String title) { return false; }
    default String baitFailure() { return ""; }
    default String baitStatus() { return "IDLE"; }
    default void baitMessage(String message) {}
    default void stopBait() {}

    default String supportConfigurationError(dev.veltraph.client.config.ConfigService config) { return ""; }
    default boolean tickSupport(boolean allowStart) { return false; }
    default boolean supportActive() { return false; }
    default boolean supportAllowsScreen(String title) { return false; }
    default String supportFailure() { return ""; }
    default String supportStatus() { return "IDLE"; }
    default void supportMessage(String message) {}
    default void stopSupport() {}

    default String grappleConfigurationError(dev.veltraph.client.config.ConfigService config) { return ""; }
    default boolean tickGrapple(boolean allowStart) { return false; }
    default boolean grappleActive() { return false; }
    default String grappleFailure() { return ""; }
    default String grappleStatus() { return "IDLE"; }
    default void stopGrapple() {}

    default boolean submerged() { return false; }
    default double bubbles() { return 10; }
    /** Recovered scan: first fluid/air boundary in the ten blocks above the player. */
    default boolean surfaceReachable() { return false; }
    default void setSwimmingUp(boolean swimming) {}
    /** Owns input while an opted-in sea-creature kill/return subflow is active. */
    default String combatConfigurationError(dev.veltraph.client.config.ConfigService config){return "";}
    default boolean tickCombat() { return false; }
    /** Legacy services retain their tick; native adapters apply this only to new acquisition. */
    default boolean tickCombat(boolean allowStart){return tickCombat();}
    default void stopCombat() {}

    /** A no-op source for tests/headless: no world, no rod, no bobber. */
    FishingServices NONE = new FishingServices() {
        @Override public boolean available() {
            return false;
        }
        @Override public boolean hasFishingRod() {
            return false;
        }
        @Override public BobberFacts bobber() {
            return BobberFacts.none();
        }
        @Override public void useRod() {
        }
    };
}
