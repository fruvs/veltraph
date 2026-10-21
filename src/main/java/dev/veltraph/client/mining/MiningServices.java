package dev.veltraph.client.mining;

import dev.veltraph.client.service.FarmServices;
import dev.veltraph.client.world.BlockPos;

/** All mutation goes through ordinary vanilla input, hotbar selection and ground navigation. */
public interface MiningServices {
    FarmServices farm();
    default java.util.Optional<dev.veltraph.client.mining.crafting.CobbleCraftStorePort> craftStorePort(){return java.util.Optional.empty();}
    boolean available();
    /** Absolute owned use-key state; default adapters have no native use surface. */
    default boolean setUseHeld(Object caller,int activation,boolean held) {return !held;}
    /** Read only: this caller's accepted captured hardware hold, never a foreign hold. */
    default boolean ownedUseHeld(Object caller,int activation) {return false;}
    /** Stable only for one connected client world; used to prevent cross-world route resumption. */
    default String worldIdentity(){return "local";}
    /** Select a durable, harvest-capable hotbar pickaxe; empty means ready. */
    String selectTool(BlockPos target,int durabilityReserve);
    boolean hasCapacity(BlockPos target);
    /** Attributed cobblestone regeneration only; generic AIR capacity stays conservative. */
    default boolean hasCobbleRegenerationCapacity(BlockPos removed){return hasCapacity(removed);}
    default boolean heldToolReady(BlockPos target,int reserve){return true;}
    /** Empty means loaded, breakable, harvestable, reachable and no exposed hazard. */
    String targetProblem(BlockPos target,boolean removingFloor);
    boolean aimedAt(BlockPos target);
    boolean safeStep(boolean forward);
    boolean endWall(boolean forward);
    /** Start/tick only a supported ground path. */
    boolean walkTo(BlockPos standing);
    void stopWalking();
    default void collectDrops(BlockPos removed) {}
    String navigationStatus();
    /** Explicit resource/menu loan policy; unsupported adapters cannot opt into scheduled rest. */
    default boolean scheduledPauseReady(int reserve){return false;}
    default boolean captureScheduledPause(Object ticket,int reserve){return false;}
    default void releaseScheduledPause(Object ticket){}
    default boolean scheduledArrivalReady(Object ticket,int reserve){return false;}
    default boolean rebindScheduledPause(Object ticket,int reserve){return false;}
    default boolean resumeScheduledPause(Object ticket,int reserve){return false;}
    default void cancelScheduledPause(Object ticket){}
    default void cleanup() { stopWalking();farm().breaking().setBreaking(false);farm().input().releaseAllMovement();farm().rotation().cancelRotation(); }
}
